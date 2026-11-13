import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useLocation, useNavigate, useParams } from 'react-router';
import { ApiError } from '../../shared/api/client';
import type {
  DocumentResponse,
  MetadataRequest,
  ProcessingResponse,
} from '../../shared/api/types';
import { useSession } from '../authentication/session';
import {
  documentKeys,
  errorMessage,
  formatBytes,
  formatTimestamp,
  normalizeDescription,
  validateMetadata,
} from './documentHelpers';
import { DocumentTaxonomySections } from './DocumentTaxonomySections';
import styles from './Documents.module.css';

function parseId(raw: string | undefined): number | null {
  if (!raw || !/^\d+$/.test(raw)) return null;
  const parsed = Number(raw);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}

const STAGE_LABELS: Array<{
  key: keyof Pick<
    ProcessingResponse,
    'ocr' | 'indexing' | 'summary' | 'classification'
  >;
  label: string;
}> = [
  { key: 'ocr', label: 'OCR' },
  { key: 'indexing', label: 'Indexing' },
  { key: 'summary', label: 'Summary' },
  { key: 'classification', label: 'Classification' },
];

export function DocumentDetailPage() {
  const { id: rawId } = useParams();
  const documentId = parseId(rawId);
  const session = useSession();
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();

  const justUploaded = Boolean(
    (location.state as { justUploaded?: unknown } | null)?.justUploaded,
  );

  const [draft, setDraft] = useState<{
    docId: number;
    title: string;
    description: string;
  } | null>(null);
  const [validation, setValidation] = useState<Record<string, string>>({});
  const [metadataSaved, setMetadataSaved] = useState(false);
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  const metadataRequest = useRef<AbortController | null>(null);
  const downloadRequest = useRef<AbortController | null>(null);
  const deleteRequest = useRef<AbortController | null>(null);
  const deleteButtonRef = useRef<HTMLButtonElement | null>(null);
  const confirmHeadingRef = useRef<HTMLHeadingElement | null>(null);

  useEffect(() => {
    return () => {
      metadataRequest.current?.abort();
      downloadRequest.current?.abort();
      deleteRequest.current?.abort();
    };
  }, []);

  const documentQuery = useQuery<DocumentResponse>({
    queryKey: documentKeys.detail(documentId ?? 0),
    enabled: session.authenticated && documentId !== null,
    queryFn: ({ signal }) =>
      session.json<DocumentResponse>(`/documents/${documentId}`, { signal }),
  });

  const processingQuery = useQuery<ProcessingResponse>({
    queryKey: documentKeys.processing(documentId ?? 0),
    enabled:
      session.authenticated && documentId !== null && documentQuery.isSuccess,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    queryFn: ({ signal }) =>
      session.json<ProcessingResponse>(`/documents/${documentId}/processing`, {
        signal,
      }),
  });

  const metadataMutation = useMutation({
    mutationFn: async (payload: MetadataRequest) => {
      const controller = new AbortController();
      metadataRequest.current = controller;
      return session.json<DocumentResponse>(
        `/documents/${documentId}/metadata`,
        {
          method: 'PUT',
          body: payload,
          signal: controller.signal,
        },
      );
    },
    onSuccess: async (updated) => {
      setDraft({
        docId: updated.id,
        title: updated.title,
        description: updated.description ?? '',
      });
      setValidation({});
      setMetadataSaved(true);
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: documentKeys.detail(updated.id),
        }),
        queryClient.invalidateQueries({ queryKey: documentKeys.lists }),
      ]);
    },
  });

  const downloadMutation = useMutation({
    mutationFn: async (filename: string) => {
      const controller = new AbortController();
      downloadRequest.current = controller;
      const fileBlob = await session.blob(`/documents/${documentId}/content`, {
        signal: controller.signal,
      });
      const url = URL.createObjectURL(fileBlob);
      const link = document.createElement('a');
      link.href = url;
      link.download = filename;
      link.rel = 'noopener';
      document.body.appendChild(link);
      try {
        link.click();
      } finally {
        link.remove();
        URL.revokeObjectURL(url);
      }
    },
  });

  const deleteMutation = useMutation({
    mutationFn: async () => {
      const controller = new AbortController();
      deleteRequest.current = controller;
      await session.json(`/documents/${documentId}`, {
        method: 'DELETE',
        signal: controller.signal,
      });
    },
    onSuccess: async () => {
      if (documentId !== null) {
        queryClient.removeQueries({
          queryKey: documentKeys.detail(documentId),
        });
        queryClient.removeQueries({
          queryKey: documentKeys.processing(documentId),
        });
      }
      await queryClient.invalidateQueries({ queryKey: documentKeys.lists });
      navigate('/documents', {
        replace: true,
        state: { notice: 'Document deleted.' },
      });
    },
  });

  const serverFieldErrors =
    metadataMutation.error instanceof ApiError
      ? metadataMutation.error.fieldErrors
      : {};
  const fields = Object.keys(validation).length
    ? validation
    : serverFieldErrors;
  const firstError = Object.keys(fields)[0];
  useEffect(() => {
    if (firstError) document.getElementById(firstError)?.focus();
  }, [firstError]);

  const notFound =
    documentId === null ||
    (documentQuery.error instanceof ApiError &&
      documentQuery.error.status === 404);
  const doc = notFound ? undefined : documentQuery.data;

  const headingTitle = notFound
    ? 'Document not found.'
    : doc
      ? doc.title
      : 'Document details';

  const activeDraft =
    doc && draft && draft.docId === doc.id
      ? draft
      : {
          docId: doc?.id ?? 0,
          title: doc?.title ?? '',
          description: doc?.description ?? '',
        };

  function submitMetadata(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!doc || metadataMutation.isPending) return;
    metadataMutation.reset();
    setMetadataSaved(false);

    const errors = validateMetadata(activeDraft.title, activeDraft.description);
    setValidation(errors);
    if (Object.keys(errors).length) return;

    metadataMutation.mutate({
      title: activeDraft.title.trim(),
      description: normalizeDescription(activeDraft.description),
    });
  }

  function openDeleteConfirmation() {
    deleteMutation.reset();
    setConfirmingDelete(true);
    window.setTimeout(() => confirmHeadingRef.current?.focus(), 0);
  }

  function closeDeleteConfirmation() {
    if (deleteMutation.isPending) return;
    deleteMutation.reset();
    setConfirmingDelete(false);
    window.setTimeout(() => deleteButtonRef.current?.focus(), 0);
  }

  const metadataFailed =
    metadataMutation.error && metadataMutation.error.name !== 'AbortError';
  const downloadFailed =
    downloadMutation.error && downloadMutation.error.name !== 'AbortError';
  const deleteFailed =
    deleteMutation.error && deleteMutation.error.name !== 'AbortError';
  const processingFailed =
    processingQuery.isError && processingQuery.error.name !== 'AbortError';

  return (
    <section className={styles.page}>
      <div>
        <Link className={styles.backLink} to="/documents">
          Back to documents
        </Link>
        <div className={styles.pageHeader}>
          <div>
            {doc && <p className={styles.eyebrow}>Document #{doc.id}</p>}
            <h1 tabIndex={-1}>{headingTitle}</h1>
          </div>
          {doc && (
            <div className={styles.actions}>
              <button
                className={styles.primaryButton}
                type="button"
                disabled={downloadMutation.isPending}
                onClick={() => {
                  if (downloadMutation.isPending) return;
                  downloadMutation.reset();
                  downloadMutation.mutate(doc.originalFilename);
                }}
              >
                {downloadMutation.isPending ? 'Downloading...' : 'Download PDF'}
              </button>
              <button
                ref={deleteButtonRef}
                className={styles.dangerButton}
                type="button"
                disabled={deleteMutation.isPending}
                onClick={openDeleteConfirmation}
              >
                Delete document
              </button>
            </div>
          )}
        </div>
      </div>

      {!notFound &&
        documentQuery.isError &&
        documentQuery.error.name !== 'AbortError' && (
          <div className={styles.errorBox} role="alert">
            <p>
              {errorMessage(
                documentQuery.error,
                'Could not load this document. Check your connection and try again.',
              )}
            </p>
            <div>
              <button
                className={styles.secondaryButton}
                type="button"
                onClick={() => void documentQuery.refetch()}
              >
                Try again
              </button>
            </div>
          </div>
        )}

      {notFound ? (
        <div className={styles.card}>
          <p className={styles.hint}>
            This document is not available in your workspace.
          </p>
        </div>
      ) : documentQuery.isPending ? (
        <p className={styles.hint} role="status">
          Loading document...
        </p>
      ) : (
        doc && (
          <>
            {justUploaded && (
              <p className={styles.notice} role="status">
                PDF stored. Background processing is unavailable.
              </p>
            )}

            {downloadFailed && (
              <div className={styles.errorBox} role="alert">
                <p>
                  {errorMessage(
                    downloadMutation.error,
                    'Could not download the PDF. Check your connection and try again.',
                  )}
                </p>
              </div>
            )}

            {confirmingDelete && (
              <section
                className={styles.confirmBox}
                role="region"
                aria-labelledby="delete-confirm-heading"
              >
                <h3
                  id="delete-confirm-heading"
                  ref={confirmHeadingRef}
                  tabIndex={-1}
                >
                  Delete this document?
                </h3>
                <p>
                  This permanently deletes &ldquo;{doc.title}&rdquo; (
                  {doc.originalFilename}) and its stored PDF.
                </p>
                {deleteFailed && (
                  <p className={styles.errorText} role="alert">
                    {errorMessage(
                      deleteMutation.error,
                      'Could not delete the document. Check your connection and try again.',
                    )}
                  </p>
                )}
                <div className={styles.actions}>
                  <button
                    className={styles.dangerButton}
                    type="button"
                    disabled={deleteMutation.isPending}
                    onClick={() => {
                      if (deleteMutation.isPending) return;
                      deleteMutation.mutate();
                    }}
                  >
                    {deleteMutation.isPending
                      ? 'Deleting...'
                      : 'Confirm delete'}
                  </button>
                  <button
                    className={styles.secondaryButton}
                    type="button"
                    disabled={deleteMutation.isPending}
                    onClick={closeDeleteConfirmation}
                  >
                    Cancel
                  </button>
                </div>
              </section>
            )}

            <div className={styles.detailGrid}>
              <section
                className={styles.card}
                aria-labelledby="details-heading"
              >
                <h2 id="details-heading">File and metadata</h2>
                <dl className={styles.detailsList}>
                  <dt>Document ID</dt>
                  <dd>{doc.id}</dd>
                  <dt>Original file</dt>
                  <dd>{doc.originalFilename}</dd>
                  <dt>Media type</dt>
                  <dd>{doc.contentType}</dd>
                  <dt>Size</dt>
                  <dd>{formatBytes(doc.sizeBytes)}</dd>
                  <dt>Uploaded</dt>
                  <dd>{formatTimestamp(doc.createdAt)}</dd>
                  <dt>Last updated</dt>
                  <dd>{formatTimestamp(doc.updatedAt)}</dd>
                  <dt>Description</dt>
                  <dd>{doc.description ?? 'No description'}</dd>
                  <dt>Categories</dt>
                  <dd>
                    {doc.categories.length > 0
                      ? doc.categories
                          .map((category) => category.name)
                          .join(', ')
                      : 'Unclassified'}
                  </dd>
                  <dt>Tags</dt>
                  <dd>
                    {doc.tags.length > 0 ? doc.tags.join(', ') : 'No tags'}
                  </dd>
                </dl>
              </section>

              <section
                className={styles.card}
                aria-labelledby="processing-heading"
              >
                <h2 id="processing-heading">Processing status</h2>
                <p className={styles.hint}>
                  Your PDF is stored. Background processing is unavailable, so
                  stages stay not started and are not queued.
                </p>

                {processingQuery.isPending && (
                  <p
                    className={styles.hint}
                    role="status"
                    style={{ marginTop: '0.75rem' }}
                  >
                    Checking processing status...
                  </p>
                )}

                {processingFailed && (
                  <div
                    className={styles.errorBox}
                    role="alert"
                    style={{ marginTop: '0.75rem' }}
                  >
                    <p>
                      {errorMessage(
                        processingQuery.error,
                        'Could not read processing status.',
                      )}
                    </p>
                    <div>
                      <button
                        className={styles.secondaryButton}
                        type="button"
                        onClick={() => void processingQuery.refetch()}
                      >
                        Try again
                      </button>
                    </div>
                  </div>
                )}

                {processingQuery.isSuccess && (
                  <ul className={styles.stageList}>
                    {STAGE_LABELS.map((stage) => (
                      <li className={styles.stageRow} key={stage.key}>
                        <span>{stage.label}</span>
                        <span className={styles.stageBadge}>
                          {processingQuery.data[stage.key]}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            </div>

            <form
              className={styles.card}
              onSubmit={submitMetadata}
              noValidate
              aria-busy={metadataMutation.isPending}
              aria-labelledby="edit-metadata-heading"
            >
              <h2 id="edit-metadata-heading">Edit metadata</h2>

              {metadataSaved && (
                <p
                  className={styles.notice}
                  role="status"
                  style={{ marginBottom: '1.25rem' }}
                >
                  Metadata saved.
                </p>
              )}

              {metadataFailed && (
                <p
                  className={styles.errorBox}
                  role="alert"
                  style={{ marginBottom: '1.25rem' }}
                >
                  {errorMessage(
                    metadataMutation.error,
                    'Could not save metadata. Check your connection and try again.',
                  )}
                </p>
              )}

              <fieldset disabled={metadataMutation.isPending}>
                <div className={styles.field}>
                  <label htmlFor="title">Title</label>
                  <input
                    id="title"
                    name="title"
                    type="text"
                    required
                    value={activeDraft.title}
                    aria-invalid={!!fields.title}
                    aria-describedby={fields.title ? 'title-error' : undefined}
                    onChange={(event) => {
                      setValidation({});
                      setMetadataSaved(false);
                      if (metadataMutation.error) metadataMutation.reset();
                      setDraft({
                        ...activeDraft,
                        title: event.currentTarget.value,
                      });
                    }}
                  />
                  {fields.title && (
                    <span className={styles.fieldError} id="title-error">
                      {fields.title}
                    </span>
                  )}
                </div>

                <div className={styles.field}>
                  <label htmlFor="description">Description (optional)</label>
                  <textarea
                    id="description"
                    name="description"
                    rows={3}
                    value={activeDraft.description}
                    aria-invalid={!!fields.description}
                    aria-describedby={
                      fields.description ? 'description-error' : undefined
                    }
                    onChange={(event) => {
                      setValidation({});
                      setMetadataSaved(false);
                      if (metadataMutation.error) metadataMutation.reset();
                      setDraft({
                        ...activeDraft,
                        description: event.currentTarget.value,
                      });
                    }}
                  />
                  {fields.description && (
                    <span className={styles.fieldError} id="description-error">
                      {fields.description}
                    </span>
                  )}
                </div>
              </fieldset>

              <div className={styles.actions}>
                <button
                  className={styles.primaryButton}
                  type="submit"
                  disabled={metadataMutation.isPending}
                >
                  {metadataMutation.isPending ? 'Saving...' : 'Save metadata'}
                </button>
              </div>
            </form>

            <DocumentTaxonomySections doc={doc} />
          </>
        )
      )}
    </section>
  );
}
