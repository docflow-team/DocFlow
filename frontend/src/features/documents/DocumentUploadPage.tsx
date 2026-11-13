import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router';
import { ApiError } from '../../shared/api/client';
import type { UploadResponse } from '../../shared/api/types';
import { useSession } from '../authentication/session';
import {
  documentKeys,
  errorMessage,
  formatBytes,
  normalizeDescription,
  validateMetadata,
  validatePdfFile,
} from './documentHelpers';
import styles from './Documents.module.css';

interface UploadDraft {
  file: File;
  title: string;
  description: string;
}

export function DocumentUploadPage() {
  const session = useSession();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const request = useRef<AbortController | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [validation, setValidation] = useState<Record<string, string>>({});

  useEffect(() => () => request.current?.abort(), []);

  const mutation = useMutation({
    mutationFn: async (draft: UploadDraft) => {
      const formData = new FormData();
      formData.append('file', draft.file);
      formData.append('title', draft.title.trim());
      const normalizedDescription = normalizeDescription(draft.description);
      if (normalizedDescription !== null) {
        formData.append('description', normalizedDescription);
      }
      const controller = new AbortController();
      request.current = controller;
      return session.json<UploadResponse>('/documents', {
        method: 'POST',
        body: formData,
        signal: controller.signal,
      });
    },
    onSuccess: async (result) => {
      await queryClient.invalidateQueries({ queryKey: documentKeys.lists });
      navigate(`/documents/${result.document.id}`, {
        replace: true,
        state: { justUploaded: true },
      });
    },
  });

  const fields = Object.keys(validation).length
    ? validation
    : mutation.error instanceof ApiError
      ? mutation.error.fieldErrors
      : {};
  const firstError = Object.keys(fields)[0];
  useEffect(() => {
    if (firstError) document.getElementById(firstError)?.focus();
  }, [firstError]);

  function clearFeedback() {
    if (Object.keys(validation).length) setValidation({});
    if (mutation.error) mutation.reset();
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (mutation.isPending) return;
    mutation.reset();

    const errors = validateMetadata(title, description);
    const fileError = validatePdfFile(file);
    if (fileError) errors.file = fileError;

    setValidation(errors);
    if (Object.keys(errors).length || !file) return;

    mutation.mutate({ file, title, description });
  }

  const failed = mutation.error && mutation.error.name !== 'AbortError';

  return (
    <section className={styles.page}>
      <div>
        <Link className={styles.backLink} to="/documents">
          Back to documents
        </Link>
        <div className={styles.pageHeader}>
          <div>
            <p className={styles.eyebrow}>New document</p>
            <h1 tabIndex={-1}>Upload PDF</h1>
          </div>
        </div>
      </div>

      <form
        className={styles.card}
        onSubmit={submit}
        noValidate
        aria-busy={mutation.isPending}
      >
        {failed && (
          <p
            className={styles.errorBox}
            role="alert"
            style={{ marginBottom: '1.25rem' }}
          >
            {errorMessage(
              mutation.error,
              'Cannot upload PDF. Check your connection and try again.',
            )}
          </p>
        )}

        <fieldset disabled={mutation.isPending}>
          <div className={styles.field}>
            <label htmlFor="file">PDF file</label>
            <input
              id="file"
              name="file"
              type="file"
              accept="application/pdf"
              required
              aria-invalid={!!fields.file}
              aria-describedby={fields.file ? 'file-error' : 'file-hint'}
              onChange={(event) => {
                clearFeedback();
                setFile(event.currentTarget.files?.[0] ?? null);
              }}
            />
            <span className={styles.hint} id="file-hint">
              {file
                ? `${file.name} (${formatBytes(file.size)})`
                : 'Unencrypted PDF, 1-50 pages, up to 10 MiB.'}
            </span>
            {fields.file && (
              <span className={styles.fieldError} id="file-error">
                {fields.file}
              </span>
            )}
          </div>

          <div className={styles.field}>
            <label htmlFor="title">Title</label>
            <input
              id="title"
              name="title"
              type="text"
              required
              value={title}
              aria-invalid={!!fields.title}
              aria-describedby={fields.title ? 'title-error' : undefined}
              onChange={(event) => {
                clearFeedback();
                setTitle(event.currentTarget.value);
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
              value={description}
              aria-invalid={!!fields.description}
              aria-describedby={
                fields.description ? 'description-error' : undefined
              }
              onChange={(event) => {
                clearFeedback();
                setDescription(event.currentTarget.value);
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
            disabled={mutation.isPending}
          >
            {mutation.isPending ? 'Uploading PDF...' : 'Upload PDF'}
          </button>
          <Link className={styles.secondaryButton} to="/documents">
            Cancel
          </Link>
        </div>

        <p className={styles.hint} style={{ marginTop: '1rem' }}>
          Uploads store your PDF immediately. Background OCR, indexing, summary
          and classification are unavailable.
        </p>
      </form>
    </section>
  );
}
