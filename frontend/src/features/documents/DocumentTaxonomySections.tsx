import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError } from '../../shared/api/client';
import type {
  CategoriesRequest,
  CategoryResponse,
  DocumentResponse,
  TagsRequest,
} from '../../shared/api/types';
import { useSession } from '../authentication/session';
import { documentKeys, errorMessage, validateTagName } from './documentHelpers';
import styles from './Documents.module.css';

function DocumentCategoriesSection({ doc }: { doc: DocumentResponse }) {
  const session = useSession();
  const queryClient = useQueryClient();
  const request = useRef<AbortController | null>(null);
  const [draft, setDraft] = useState<{ docId: number; ids: number[] } | null>(
    null,
  );
  const [saved, setSaved] = useState(false);

  useEffect(() => () => request.current?.abort(), []);

  const categoriesQuery = useQuery<CategoryResponse[]>({
    queryKey: documentKeys.categories,
    enabled: session.authenticated,
    queryFn: ({ signal }) =>
      session.json<CategoryResponse[]>('/categories', { signal }),
  });

  const mutation = useMutation({
    mutationFn: async (categoryIds: number[]) => {
      const controller = new AbortController();
      request.current = controller;
      const body: CategoriesRequest = { categoryIds };
      return session.json<DocumentResponse>(`/documents/${doc.id}/categories`, {
        method: 'PUT',
        body,
        signal: controller.signal,
      });
    },
    onSuccess: async (updated) => {
      setDraft({
        docId: updated.id,
        ids: updated.categories.map((category) => category.id),
      });
      setSaved(true);
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: documentKeys.detail(updated.id),
        }),
        queryClient.invalidateQueries({ queryKey: documentKeys.lists }),
      ]);
    },
  });

  const selectedIds =
    draft && draft.docId === doc.id
      ? draft.ids
      : doc.categories.map((category) => category.id);

  function toggleCategory(categoryId: number) {
    setSaved(false);
    if (mutation.error) mutation.reset();
    const next = selectedIds.includes(categoryId)
      ? selectedIds.filter((id) => id !== categoryId)
      : [...selectedIds, categoryId];
    setDraft({ docId: doc.id, ids: next });
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (mutation.isPending || !categoriesQuery.isSuccess) return;
    mutation.reset();
    setSaved(false);
    const uniquePositiveIds = Array.from(
      new Set(selectedIds.filter((id) => id > 0)),
    );
    mutation.mutate(uniquePositiveIds);
  }

  const queryFailed =
    categoriesQuery.isError && categoriesQuery.error.name !== 'AbortError';
  const saveFailed = mutation.error && mutation.error.name !== 'AbortError';

  return (
    <section className={styles.card} aria-labelledby="categories-heading">
      <h2 id="categories-heading">Categories</h2>

      {categoriesQuery.isPending && (
        <p className={styles.hint} role="status">
          Loading categories...
        </p>
      )}

      {queryFailed && (
        <div className={styles.errorBox} role="alert">
          <p>
            {errorMessage(
              categoriesQuery.error,
              'Could not load categories. Current assignments are unchanged.',
            )}
          </p>
          <div>
            <button
              className={styles.secondaryButton}
              type="button"
              onClick={() => void categoriesQuery.refetch()}
            >
              Try again
            </button>
          </div>
        </div>
      )}

      {categoriesQuery.isSuccess && (
        <form
          onSubmit={submit}
          noValidate
          aria-busy={mutation.isPending}
          style={{ display: 'grid', gap: '1rem' }}
        >
          {saved && (
            <p className={styles.notice} role="status">
              Categories saved.
            </p>
          )}

          {saveFailed && (
            <p className={styles.errorBox} role="alert">
              {errorMessage(
                mutation.error,
                'Could not save categories. Check your connection and try again.',
              )}
            </p>
          )}

          <fieldset disabled={mutation.isPending}>
            <legend className={styles.hint} style={{ marginBottom: '0.75rem' }}>
              Choose one or more categories, or leave all unchecked for
              Unclassified.
            </legend>
            <div className={styles.choiceList}>
              {categoriesQuery.data.map((category) => (
                <label className={styles.choiceItem} key={category.id}>
                  <input
                    type="checkbox"
                    checked={selectedIds.includes(category.id)}
                    onChange={() => toggleCategory(category.id)}
                  />
                  <span className={styles.choiceText}>
                    <span className={styles.choiceName}>{category.name}</span>
                    {category.description && (
                      <span className={styles.hint}>
                        {category.description}
                      </span>
                    )}
                  </span>
                </label>
              ))}
            </div>
          </fieldset>

          {selectedIds.length === 0 && (
            <p className={styles.hint}>
              Selected state: Unclassified (no categories assigned).
            </p>
          )}

          <div className={styles.actions}>
            <button
              className={styles.primaryButton}
              type="submit"
              disabled={mutation.isPending}
            >
              {mutation.isPending ? 'Saving...' : 'Save categories'}
            </button>
          </div>
        </form>
      )}
    </section>
  );
}

function DocumentTagsSection({ doc }: { doc: DocumentResponse }) {
  const session = useSession();
  const queryClient = useQueryClient();
  const request = useRef<AbortController | null>(null);
  const tagInputRef = useRef<HTMLInputElement | null>(null);
  const [draft, setDraft] = useState<{ docId: number; tags: string[] } | null>(
    null,
  );
  const [tagInput, setTagInput] = useState('');
  const [tagInputError, setTagInputError] = useState<string | undefined>(
    undefined,
  );
  const [saved, setSaved] = useState(false);

  useEffect(() => () => request.current?.abort(), []);

  const mutation = useMutation({
    mutationFn: async (tags: string[]) => {
      const controller = new AbortController();
      request.current = controller;
      const body: TagsRequest = { tags };
      return session.json<DocumentResponse>(`/documents/${doc.id}/tags`, {
        method: 'PUT',
        body,
        signal: controller.signal,
      });
    },
    onSuccess: async (updated) => {
      setDraft({ docId: updated.id, tags: updated.tags });
      setSaved(true);
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: documentKeys.detail(updated.id),
        }),
        queryClient.invalidateQueries({ queryKey: documentKeys.lists }),
      ]);
    },
  });

  const activeTags = draft && draft.docId === doc.id ? draft.tags : doc.tags;

  function addTag(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (mutation.isPending) return;
    setSaved(false);
    if (mutation.error) mutation.reset();

    const error = validateTagName(tagInput, activeTags);
    if (error) {
      setTagInputError(error);
      tagInputRef.current?.focus();
      return;
    }

    setDraft({ docId: doc.id, tags: [...activeTags, tagInput.trim()] });
    setTagInput('');
    setTagInputError(undefined);
  }

  function removeTag(target: string) {
    if (mutation.isPending) return;
    setSaved(false);
    setTagInputError(undefined);
    if (mutation.error) mutation.reset();
    setDraft({
      docId: doc.id,
      tags: activeTags.filter((tag) => tag !== target),
    });
  }

  function clearDraftTags() {
    if (mutation.isPending || activeTags.length === 0) return;
    setSaved(false);
    setTagInputError(undefined);
    if (mutation.error) mutation.reset();
    setDraft({ docId: doc.id, tags: [] });
  }

  function saveTags() {
    if (mutation.isPending) return;
    mutation.reset();
    setSaved(false);
    setTagInputError(undefined);
    mutation.mutate(activeTags);
  }

  const saveFailed = mutation.error && mutation.error.name !== 'AbortError';
  const serverTagError =
    mutation.error instanceof ApiError
      ? (mutation.error.fieldErrors.tags ??
        (mutation.error.status === 409
          ? 'Could not save tags because another change created a tag at the same time. Your list is preserved—try saving again.'
          : mutation.error.message))
      : 'Could not save tags. Check your connection and try again.';

  return (
    <section className={styles.card} aria-labelledby="tags-heading">
      <h2 id="tags-heading">Tags</h2>

      {saved && (
        <p
          className={styles.notice}
          role="status"
          style={{ marginBottom: '1rem' }}
        >
          Tags saved.
        </p>
      )}

      {saveFailed && (
        <p
          className={styles.errorBox}
          role="alert"
          style={{ marginBottom: '1rem' }}
        >
          {serverTagError}
        </p>
      )}

      {activeTags.length > 0 ? (
        <ul className={styles.tagEditorList} aria-label="Selected tags">
          {activeTags.map((tag) => (
            <li className={styles.tagEditorPill} key={tag}>
              <span>#{tag}</span>
              <button
                className={styles.removeTagButton}
                type="button"
                aria-label={`Remove tag ${tag}`}
                disabled={mutation.isPending}
                onClick={() => removeTag(tag)}
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      ) : (
        <p className={styles.hint} style={{ marginBottom: '1rem' }}>
          No tags selected. Save an empty list to clear existing tags.
        </p>
      )}

      <form onSubmit={addTag} noValidate style={{ marginBottom: '1.25rem' }}>
        <div className={styles.field} style={{ marginBottom: '0.5rem' }}>
          <label htmlFor="tag-input">Add tag</label>
          <div className={styles.inlineForm}>
            <input
              ref={tagInputRef}
              id="tag-input"
              name="tag"
              type="text"
              value={tagInput}
              disabled={mutation.isPending}
              aria-invalid={!!tagInputError}
              aria-describedby={
                tagInputError ? 'tag-input-error' : 'tag-input-hint'
              }
              onChange={(event) => {
                setTagInputError(undefined);
                setSaved(false);
                if (mutation.error) mutation.reset();
                setTagInput(event.currentTarget.value);
              }}
            />
            <button
              className={styles.secondaryButton}
              type="submit"
              disabled={mutation.isPending}
            >
              Add tag
            </button>
          </div>
          <span className={styles.hint} id="tag-input-hint">
            Up to 20 unique tags, 1-50 characters each. Saved tags are trimmed,
            lowercased and sorted.
          </span>
          {tagInputError && (
            <span className={styles.fieldError} id="tag-input-error">
              {tagInputError}
            </span>
          )}
        </div>
      </form>

      <div className={styles.actions}>
        <button
          className={styles.primaryButton}
          type="button"
          disabled={mutation.isPending}
          onClick={saveTags}
        >
          {mutation.isPending ? 'Saving...' : 'Save tags'}
        </button>
        <button
          className={styles.secondaryButton}
          type="button"
          disabled={mutation.isPending || activeTags.length === 0}
          onClick={clearDraftTags}
        >
          Clear all
        </button>
      </div>
    </section>
  );
}

export function DocumentTaxonomySections({ doc }: { doc: DocumentResponse }) {
  return (
    <div className={styles.detailGrid}>
      <DocumentCategoriesSection doc={doc} />
      <DocumentTagsSection doc={doc} />
    </div>
  );
}
