import { expect, it, vi } from 'vitest';
import { ApiError, apiBlob, apiJson } from './client';
import type {
  DocumentResponse,
  LoginRequest,
  MetadataRequest,
  UploadResponse,
} from './types';

const document: DocumentResponse = {
  id: 7,
  title: 'Invoice',
  description: null,
  originalFilename: 'invoice.pdf',
  contentType: 'application/pdf',
  sizeBytes: 512,
  createdAt: '2026-11-13T12:00:00Z',
  updatedAt: '2026-11-13T12:00:00Z',
  categories: [{ id: 2, name: 'Invoice', description: null }],
  tags: ['housing'],
};

function mockResponse(response: Response) {
  const mock = vi.fn<typeof fetch>().mockResolvedValue(response);
  vi.stubGlobal('fetch', mock);
  return mock;
}

it('reads typed JSON from the relative API with an explicit bearer token and no persisted credentials/cache', async () => {
  const fetch = mockResponse(Response.json(document));
  const result = await apiJson<DocumentResponse>('/documents/7', {
    token: 'synthetic-token',
  });
  expect(result).toEqual(document);
  const [url, options] = fetch.mock.calls[0];
  expect(url).toBe('/api/v1/documents/7');
  expect(new Headers(options?.headers).get('Authorization')).toBe(
    'Bearer synthetic-token',
  );
  expect(new Headers(options?.headers).get('Accept')).toBe('application/json');
  expect(options?.credentials).toBe('omit');
  expect(options?.cache).toBe('no-store');
  expect(new Headers(options?.headers).has('Content-Type')).toBe(false);
});

it('encodes a complete metadata PUT including an explicit nullable description', async () => {
  const fetch = mockResponse(Response.json(document));
  const body: MetadataRequest = { title: 'Renamed', description: null };
  await apiJson<DocumentResponse>('/documents/7/metadata', {
    method: 'PUT',
    body,
  });
  const options = fetch.mock.calls[0][1];
  expect(options?.method).toBe('PUT');
  expect(new Headers(options?.headers).get('Content-Type')).toBe(
    'application/json',
  );
  expect(JSON.parse(options?.body as string)).toEqual(body);
});

it('sends login JSON without an implicit bearer token', async () => {
  const fetch = mockResponse(Response.json({ accessToken: 'synthetic-token' }));
  const body: LoginRequest = {
    login: 'student',
    password: 'synthetic-password',
  };
  await apiJson('/auth/login', { method: 'POST', body });
  const options = fetch.mock.calls[0][1];
  expect(options?.method).toBe('POST');
  expect(JSON.parse(options?.body as string)).toEqual(body);
  expect(new Headers(options?.headers).has('Authorization')).toBe(false);
});

it('sends multipart upload bytes unchanged and lets the browser choose the boundary', async () => {
  const uploaded: UploadResponse = {
    document,
    processingUrl: '/api/v1/documents/7/processing',
    processing: {
      documentId: 7,
      processingAvailable: false,
      ocr: 'NOT_STARTED',
      indexing: 'NOT_STARTED',
      summary: 'NOT_STARTED',
      classification: 'NOT_STARTED',
    },
  };
  const fetch = mockResponse(Response.json(uploaded, { status: 202 }));
  const body = new FormData();
  body.set(
    'file',
    new Blob(['%PDF-example'], { type: 'application/pdf' }),
    'invoice.pdf',
  );
  body.set('title', 'Invoice');
  expect(
    await apiJson<UploadResponse>('/documents', { method: 'POST', body }),
  ).toEqual(uploaded);
  const options = fetch.mock.calls[0][1];
  expect(options?.body).toBe(body);
  expect(new Headers(options?.headers).has('Content-Type')).toBe(false);
});

it('downloads a PDF as binary rather than JSON', async () => {
  const bytes = new Uint8Array([37, 80, 68, 70, 0, 255]);
  const fetch = mockResponse(
    new Response(bytes, { headers: { 'Content-Type': 'application/pdf' } }),
  );
  const result = await apiBlob('/documents/7/content', {
    token: 'synthetic-token',
  });
  expect(new Uint8Array(await result.arrayBuffer())).toEqual(bytes);
  expect(result.type).toBe('application/pdf');
  expect(new Headers(fetch.mock.calls[0][1]?.headers).get('Accept')).toBe(
    'application/pdf',
  );
});

it('accepts an empty 204 response without trying to parse JSON', async () => {
  const fetch = mockResponse(new Response(null, { status: 204 }));
  expect(await apiJson('/documents/7', { method: 'DELETE' })).toBeUndefined();
  expect(fetch.mock.calls[0][1]?.body).toBeUndefined();
});

it('preserves safe problem details and field validation messages', async () => {
  mockResponse(
    Response.json(
      {
        detail: 'Invalid request fields',
        errors: { title: 'must not be blank' },
      },
      { status: 400 },
    ),
  );
  await expect(apiJson('/documents/7/metadata')).rejects.toMatchObject({
    status: 400,
    message: 'Invalid request fields',
    fieldErrors: { title: 'must not be blank' },
  });
});

it('preserves an authentication failure status for the session layer', async () => {
  mockResponse(
    Response.json(
      { detail: 'Authentication is required or the token is invalid' },
      { status: 401 },
    ),
  );
  await expect(
    apiJson('/auth/me', { token: 'expired-token' }),
  ).rejects.toMatchObject({ status: 401 });
});

it('uses the same JSON problem handling for a failed binary download', async () => {
  mockResponse(
    Response.json({ detail: 'Document not found' }, { status: 404 }),
  );
  await expect(apiBlob('/documents/7/content')).rejects.toMatchObject({
    status: 404,
    message: 'Document not found',
  });
});

it('reports non-JSON upstream failures without exposing their HTML', async () => {
  mockResponse(
    new Response('<html>Private proxy diagnostics</html>', { status: 503 }),
  );
  await expect(apiJson('/documents')).rejects.toEqual(new ApiError(503));
});

it('forwards cancellation and preserves the abort error', async () => {
  const controller = new AbortController();
  controller.abort();
  const error = new DOMException('Request aborted', 'AbortError');
  const fetch = vi.fn<typeof globalThis.fetch>().mockRejectedValue(error);
  vi.stubGlobal('fetch', fetch);
  await expect(
    apiJson('/documents', { signal: controller.signal }),
  ).rejects.toBe(error);
  expect(fetch.mock.calls[0][1]?.signal).toBe(controller.signal);
});
