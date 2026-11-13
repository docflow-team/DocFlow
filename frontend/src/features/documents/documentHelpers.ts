import { ApiError } from '../../shared/api/client';

export const MAX_PDF_BYTES = 10 * 1024 * 1024;
export const PAGE_SIZE = 20;

export const documentKeys = {
  lists: ['documents', 'list'] as const,
  list: (page: number) => ['documents', 'list', page] as const,
  detail: (id: number) => ['documents', 'detail', id] as const,
  processing: (id: number) => ['documents', 'processing', id] as const,
};

export function validateMetadata(
  title: string,
  description: string,
): Record<string, string> {
  const errors: Record<string, string> = {};
  const trimmedTitle = title.trim();
  if (!trimmedTitle) errors.title = 'Enter a title.';
  else if (trimmedTitle.length > 255)
    errors.title = 'Title must not exceed 255 characters.';
  if (description.length > 255)
    errors.description = 'Description must not exceed 255 characters.';
  return errors;
}

export function validatePdfFile(file: File | null): string | undefined {
  if (!file) return 'Select a PDF file.';
  if (file.size === 0) return 'PDF must not be empty.';
  if (file.size > MAX_PDF_BYTES) return 'PDF must not exceed 10 MiB.';
  if (file.type !== 'application/pdf')
    return 'Only application/pdf files are supported.';
  return undefined;
}

export function normalizeDescription(description: string): string | null {
  return description.trim().length > 0 ? description : null;
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KiB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MiB`;
}

export function formatTimestamp(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}

export function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback;
}
