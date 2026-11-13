import { useQuery } from '@tanstack/react-query';
import { Link, useLocation, useSearchParams } from 'react-router';
import type { DocumentPageResponse } from '../../shared/api/types';
import { useSession } from '../authentication/session';
import {
  PAGE_SIZE,
  documentKeys,
  errorMessage,
  formatBytes,
  formatTimestamp,
} from './documentHelpers';
import styles from './Documents.module.css';

function parsePage(raw: string | null): number {
  if (raw === null || !/^\d+$/.test(raw)) return 0;
  const parsed = Number(raw);
  return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : 0;
}

export function DocumentsListPage() {
  const session = useSession();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const page = parsePage(searchParams.get('page'));
  const notice =
    typeof (location.state as { notice?: unknown } | null)?.notice === 'string'
      ? (location.state as { notice: string }).notice
      : null;

  const query = useQuery<DocumentPageResponse>({
    queryKey: documentKeys.list(page),
    enabled: session.authenticated,
    queryFn: ({ signal }) =>
      session.json<DocumentPageResponse>(
        `/documents?page=${page}&size=${PAGE_SIZE}`,
        { signal },
      ),
  });

  function openPage(nextPage: number) {
    if (nextPage <= 0) setSearchParams({});
    else setSearchParams({ page: String(nextPage) });
  }

  const data = query.data;
  const failed = query.isError && query.error.name !== 'AbortError';

  return (
    <section className={styles.page}>
      <div className={styles.pageHeader}>
        <div>
          <p className={styles.eyebrow}>Workspace</p>
          <h1 tabIndex={-1}>Your documents</h1>
        </div>
        <Link className={styles.primaryButton} to="/documents/new">
          Upload PDF
        </Link>
      </div>

      {notice && (
        <p className={styles.notice} role="status">
          {notice}
        </p>
      )}

      {query.isPending && (
        <p className={styles.hint} role="status">
          Loading documents...
        </p>
      )}

      {failed && (
        <div className={styles.errorBox} role="alert">
          <p>
            {errorMessage(
              query.error,
              'Could not load documents. Check your connection and try again.',
            )}
          </p>
          <div>
            <button
              className={styles.secondaryButton}
              type="button"
              onClick={() => void query.refetch()}
            >
              Try again
            </button>
          </div>
        </div>
      )}

      {data && data.items.length === 0 && page === 0 && (
        <div className={styles.card}>
          <h2>No documents yet.</h2>
          <p className={styles.hint}>
            Upload a PDF (up to 10 MiB and 50 pages) to store it in your
            workspace.
          </p>
          <div className={styles.actions} style={{ marginTop: '1rem' }}>
            <Link className={styles.primaryButton} to="/documents/new">
              Upload your first PDF
            </Link>
          </div>
        </div>
      )}

      {data && data.items.length === 0 && page > 0 && (
        <div className={styles.card}>
          <h2>No documents on this page.</h2>
          <p className={styles.hint}>
            Page {page + 1} has no items. Return to an earlier page to view your
            documents.
          </p>
          <div className={styles.actions} style={{ marginTop: '1rem' }}>
            <button
              className={styles.secondaryButton}
              type="button"
              onClick={() => openPage(page - 1)}
            >
              Previous page
            </button>
            <button
              className={styles.primaryButton}
              type="button"
              onClick={() => openPage(0)}
            >
              First page
            </button>
          </div>
        </div>
      )}

      {data && data.items.length > 0 && (
        <>
          <ul className={styles.list}>
            {data.items.map((doc) => (
              <li className={styles.listItem} key={doc.id}>
                <h2 className={styles.itemTitle}>
                  <Link to={`/documents/${doc.id}`}>{doc.title}</Link>
                </h2>
                <p className={styles.metaLine}>
                  {doc.originalFilename} · {formatBytes(doc.sizeBytes)} ·
                  Uploaded {formatTimestamp(doc.createdAt)}
                </p>
                {doc.description && (
                  <p className={styles.itemDescription}>{doc.description}</p>
                )}
                <div className={styles.chips}>
                  {doc.categories.length > 0 ? (
                    doc.categories.map((category) => (
                      <span className={styles.chip} key={category.id}>
                        {category.name}
                      </span>
                    ))
                  ) : (
                    <span className={styles.chip}>Unclassified</span>
                  )}
                  {doc.tags.map((tag) => (
                    <span className={styles.tagChip} key={tag}>
                      #{tag}
                    </span>
                  ))}
                </div>
              </li>
            ))}
          </ul>

          <nav className={styles.pagination} aria-label="Document pages">
            <span className={styles.hint}>
              Page {data.page + 1} of {Math.max(1, data.totalPages)} (
              {data.totalElements}{' '}
              {data.totalElements === 1 ? 'document' : 'documents'})
            </span>
            <div className={styles.paginationButtons}>
              <button
                className={styles.secondaryButton}
                type="button"
                disabled={data.page <= 0 || query.isFetching}
                onClick={() => openPage(page - 1)}
              >
                Previous
              </button>
              <button
                className={styles.secondaryButton}
                type="button"
                disabled={data.page + 1 >= data.totalPages || query.isFetching}
                onClick={() => openPage(page + 1)}
              >
                Next
              </button>
            </div>
          </nav>
        </>
      )}
    </section>
  );
}
