import type { ProblemDetail } from './types';

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  token?: string;
  signal?: AbortSignal;
}

export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, problem?: ProblemDetail) {
    super(
      typeof problem?.detail === 'string'
        ? problem.detail
        : `Request failed (${status}).`,
    );
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = problem?.errors ?? {};
  }
}

async function request(
  path: string,
  options: RequestOptions,
  accept: string,
): Promise<Response> {
  const { method = 'GET', body, token, signal } = options;
  const multipart = body instanceof FormData;
  const headers = new Headers({ Accept: accept });
  if (token) headers.set('Authorization', `Bearer ${token}`);
  if (body !== undefined && !multipart)
    headers.set('Content-Type', 'application/json');

  const response = await fetch(`/api/v1${path}`, {
    method,
    headers,
    body: multipart
      ? body
      : body === undefined
        ? undefined
        : JSON.stringify(body),
    signal,
    credentials: 'omit',
    cache: 'no-store',
  });
  if (!response.ok) {
    let problem: ProblemDetail | undefined;
    try {
      problem = (await response.json()) as ProblemDetail;
    } catch {
      // A proxy can return a non-JSON error; keep the HTTP status without exposing HTML.
    }
    throw new ApiError(response.status, problem);
  }
  return response;
}

export async function apiJson<T = void>(
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const response = await request(path, options, 'application/json');
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export async function apiBlob(
  path: string,
  options: RequestOptions = {},
): Promise<Blob> {
  const response = await request(path, options, 'application/pdf');
  return response.blob();
}
