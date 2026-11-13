// @vitest-environment jsdom
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { App } from '../../app/App';
import { apiBlob, apiJson, ApiError } from '../../shared/api/client';
import type {
  DocumentPageResponse,
  DocumentResponse,
  ProcessingResponse,
  TokenResponse,
  UploadResponse,
  UserResponse,
} from '../../shared/api/types';
import { SessionProvider } from '../authentication/SessionProvider';
import { MAX_PDF_BYTES, documentKeys } from './documentHelpers';

vi.mock('../../shared/api/client', async (original) => ({
  ...(await original<typeof import('../../shared/api/client')>()),
  apiJson: vi.fn(),
  apiBlob: vi.fn(),
}));

const api = vi.mocked(apiJson);
const blobApi = vi.mocked(apiBlob);

const user: UserResponse = {
  id: 1,
  username: 'alice',
  email: 'alice@example.test',
  role: 'USER',
  enabled: true,
  mustChangePassword: false,
};

const token: TokenResponse = {
  accessToken: 'doc-token',
  tokenType: 'Bearer',
  expiresIn: 900,
  user,
};

function makeDoc(overrides: Partial<DocumentResponse> = {}): DocumentResponse {
  return {
    id: 10,
    title: 'Quarterly invoice',
    description: 'Utility billing statement',
    originalFilename: 'invoice-2026.pdf',
    contentType: 'application/pdf',
    sizeBytes: 2048,
    createdAt: '2026-11-01T10:00:00Z',
    updatedAt: '2026-11-02T12:30:00Z',
    categories: [{ id: 1, name: 'Invoice', description: null }],
    tags: ['housing', 'utilities'],
    ...overrides,
  };
}

function makeProcessing(documentId = 10): ProcessingResponse {
  return {
    documentId,
    processingAvailable: false,
    ocr: 'NOT_STARTED',
    indexing: 'NOT_STARTED',
    summary: 'NOT_STARTED',
    classification: 'NOT_STARTED',
  };
}

function makePage(
  items: DocumentResponse[],
  page = 0,
  totalElements = items.length,
  totalPages = items.length ? 1 : 0,
): DocumentPageResponse {
  return { items, page, size: 20, totalElements, totalPages };
}

const clients: QueryClient[] = [];

beforeEach(() => {
  api.mockReset();
  blobApi.mockReset();
});

afterEach(() => {
  cleanup();
  clients.forEach((client) => client.clear());
  clients.length = 0;
  vi.restoreAllMocks();
});

async function openSignedIn(initialEntries: string[] = ['/documents']) {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false, networkMode: 'always' },
      mutations: { networkMode: 'always' },
    },
  });
  clients.push(client);
  const view = render(
    <QueryClientProvider client={client}>
      <SessionProvider>
        <MemoryRouter initialEntries={initialEntries}>
          <App />
        </MemoryRouter>
      </SessionProvider>
    </QueryClientProvider>,
  );

  fireEvent.change(screen.getByLabelText('Username or email'), {
    target: { value: 'alice' },
  });
  fireEvent.change(screen.getByLabelText('Password'), {
    target: { value: 'secret-pass' },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));
  await screen.findByRole('button', { name: 'Sign out' });
  return { client, ...view };
}

it('distinguishes loading, request failure with retry, and empty first page', async () => {
  let rejectList!: (error: Error) => void;
  let listCalls = 0;
  api.mockImplementation(async (path) => {
    if (path === '/auth/login') return token;
    if (path === '/auth/me') return user;
    if (path === '/documents?page=0&size=20') {
      listCalls++;
      if (listCalls === 1) {
        return new Promise((_, fail) => {
          rejectList = fail;
        });
      }
      return makePage([]);
    }
    return undefined;
  });

  await openSignedIn();
  expect(screen.getByText('Loading documents...')).toBeTruthy();

  await act(async () => rejectList(new TypeError('offline')));
  expect(await screen.findByRole('alert')).toHaveProperty(
    'textContent',
    expect.stringContaining('Could not load documents'),
  );

  fireEvent.click(screen.getByRole('button', { name: 'Try again' }));
  await screen.findByText('No documents yet.');
  expect(
    screen
      .getByRole('link', { name: 'Upload your first PDF' })
      .getAttribute('href'),
  ).toBe('/documents/new');
});

it('paginates document lists and recovers from an empty later page', async () => {
  const page0Doc = makeDoc({
    id: 21,
    title: 'Newest contract',
    categories: [],
    tags: [],
  });
  const page1Doc = makeDoc({ id: 1, title: 'Archived statement' });

  api.mockImplementation(async (path) => {
    if (path === '/auth/login') return token;
    if (path === '/auth/me') return user;
    if (path === '/documents?page=0&size=20')
      return makePage([page0Doc], 0, 21, 2);
    if (path === '/documents?page=1&size=20')
      return makePage([page1Doc], 1, 21, 2);
    if (path === '/documents?page=2&size=20') return makePage([], 2, 21, 2);
    return undefined;
  });

  await openSignedIn();
  await screen.findByRole('link', { name: 'Newest contract' });
  expect(screen.getByText('Unclassified')).toBeTruthy();
  expect(screen.getByText('Page 1 of 2 (21 documents)')).toBeTruthy();

  const previous = screen.getByRole('button', { name: 'Previous' });
  const next = screen.getByRole('button', { name: 'Next' });
  expect((previous as HTMLButtonElement).disabled).toBe(true);
  expect((next as HTMLButtonElement).disabled).toBe(false);

  fireEvent.click(next);
  await screen.findByRole('link', { name: 'Archived statement' });
  expect(screen.getByText('Page 2 of 2 (21 documents)')).toBeTruthy();

  cleanup();
  api.mockImplementation(async (path) => {
    if (path === '/auth/login') return token;
    if (path === '/auth/me') return user;
    if (path === '/documents?page=2&size=20') return makePage([], 2, 21, 2);
    if (path === '/documents?page=0&size=20')
      return makePage([page0Doc], 0, 21, 2);
    return undefined;
  });

  await openSignedIn(['/documents?page=2']);
  await screen.findByText('No documents on this page.');
  fireEvent.click(screen.getByRole('button', { name: 'First page' }));
  await screen.findByRole('link', { name: 'Newest contract' });
});

it('validates upload bounds locally and submits multipart FormData for 202 navigation', async () => {
  const created = makeDoc({ id: 55, title: 'Uploaded PDF', description: null });
  let capturedBody: FormData | undefined;

  api.mockImplementation(async (path, options) => {
    if (path === '/auth/login') return token;
    if (path === '/auth/me') return user;
    if (path === '/documents' && options?.method === 'POST') {
      capturedBody = options.body as FormData;
      const response: UploadResponse = {
        document: created,
        processingUrl: '/api/v1/documents/55/processing',
        processing: makeProcessing(55),
      };
      return response;
    }
    if (path === '/documents/55') return created;
    if (path === '/documents/55/processing') return makeProcessing(55);
    return makePage([]);
  });

  const { client } = await openSignedIn(['/documents/new']);
  const invalidateSpy = vi.spyOn(client, 'invalidateQueries');

  fireEvent.click(screen.getByRole('button', { name: 'Upload PDF' }));
  expect(screen.getByText('Select a PDF file.')).toBeTruthy();
  expect(screen.getByText('Enter a title.')).toBeTruthy();

  const fileInput = screen.getByLabelText('PDF file');
  fireEvent.change(fileInput, {
    target: {
      files: [new File(['hello'], 'notes.txt', { type: 'text/plain' })],
    },
  });
  fireEvent.change(screen.getByLabelText('Title'), {
    target: { value: 'a'.repeat(256) },
  });
  fireEvent.change(screen.getByLabelText('Description (optional)'), {
    target: { value: 'b'.repeat(256) },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Upload PDF' }));
  expect(
    screen.getByText('Only application/pdf files are supported.'),
  ).toBeTruthy();
  expect(
    screen.getByText('Title must not exceed 255 characters.'),
  ).toBeTruthy();
  expect(
    screen.getByText('Description must not exceed 255 characters.'),
  ).toBeTruthy();

  const oversized = new File([new Uint8Array(1)], 'big.pdf', {
    type: 'application/pdf',
  });
  Object.defineProperty(oversized, 'size', { value: MAX_PDF_BYTES + 1 });
  fireEvent.change(fileInput, { target: { files: [oversized] } });
  fireEvent.click(screen.getByRole('button', { name: 'Upload PDF' }));
  expect(screen.getByText('PDF must not exceed 10 MiB.')).toBeTruthy();

  const emptyPdf = new File([], 'empty.pdf', { type: 'application/pdf' });
  fireEvent.change(fileInput, { target: { files: [emptyPdf] } });
  fireEvent.click(screen.getByRole('button', { name: 'Upload PDF' }));
  expect(screen.getByText('PDF must not be empty.')).toBeTruthy();

  expect(api).not.toHaveBeenCalledWith(
    '/documents',
    expect.objectContaining({ method: 'POST' }),
  );

  const validPdf = new File(['%PDF-1.4\n'], 'sample.pdf', {
    type: 'application/pdf',
  });
  fireEvent.change(fileInput, { target: { files: [validPdf] } });
  fireEvent.change(screen.getByLabelText('Title'), {
    target: { value: '  Uploaded PDF  ' },
  });
  fireEvent.change(screen.getByLabelText('Description (optional)'), {
    target: { value: '   ' },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Upload PDF' }));

  await screen.findByText('PDF stored. Background processing is unavailable.');
  expect(capturedBody).toBeInstanceOf(FormData);
  expect(capturedBody?.get('title')).toBe('Uploaded PDF');
  expect(capturedBody?.has('description')).toBe(false);
  expect(capturedBody?.get('file')).toBe(validPdf);
  expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: documentKeys.lists });
});

it('keeps upload draft inputs when server rejects a PDF or field', async () => {
  api.mockImplementation(async (path, options) => {
    if (path === '/auth/login') return token;
    if (path === '/auth/me') return user;
    if (path === '/documents' && options?.method === 'POST') {
      throw new ApiError(400, {
        detail: 'File must be a parseable, unencrypted PDF',
        errors: { title: 'Title already rejected by server rule' },
      });
    }
    return makePage([]);
  });

  await openSignedIn(['/documents/new']);
  fireEvent.change(screen.getByLabelText('PDF file'), {
    target: {
      files: [
        new File(['not-a-pdf'], 'broken.pdf', { type: 'application/pdf' }),
      ],
    },
  });
  fireEvent.change(screen.getByLabelText('Title'), {
    target: { value: 'Draft title' },
  });
  fireEvent.change(screen.getByLabelText('Description (optional)'), {
    target: { value: 'Draft description' },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Upload PDF' }));

  await screen.findByText('File must be a parseable, unencrypted PDF');
  expect(
    screen.getByText('Title already rejected by server rule'),
  ).toBeTruthy();
  expect((screen.getByLabelText('Title') as HTMLInputElement).value).toBe(
    'Draft title',
  );
  expect(
    (screen.getByLabelText('Description (optional)') as HTMLTextAreaElement)
      .value,
  ).toBe('Draft description');
});

it('displays read-only details, inactive processing stages, and neutral 404 without polling', async () => {
  const doc = makeDoc();
  api.mockImplementation(async (path) => {
    if (path === '/auth/login') return token;
    if (path === '/auth/me') return user;
    if (path === '/documents/10') return doc;
    if (path === '/documents/10/processing') return makeProcessing(10);
    if (path === '/documents/99')
      throw new ApiError(404, { detail: 'Document not found' });
    return makePage([]);
  });

  await openSignedIn(['/documents/10']);
  await screen.findByRole('heading', { name: 'Quarterly invoice' });
  expect(screen.getByText('invoice-2026.pdf')).toBeTruthy();
  expect(screen.getByText('2.0 KiB')).toBeTruthy();
  expect(screen.getByText('Invoice')).toBeTruthy();
  expect(screen.getByText('housing, utilities')).toBeTruthy();
  expect(await screen.findAllByText('NOT_STARTED')).toHaveLength(4);
  expect(
    api.mock.calls.filter(([path]) => path === '/documents/10/processing'),
  ).toHaveLength(1);

  cleanup();
  await openSignedIn(['/documents/99']);
  await screen.findByRole('heading', { name: 'Document not found.' });
  expect(
    api.mock.calls.filter(([path]) => path === '/documents/99/processing'),
  ).toHaveLength(0);
});

it('updates metadata with null description clearing and preserves draft on failed save', async () => {
  let doc = makeDoc();
  let failNextSave = true;

  api.mockImplementation(async (path, options) => {
    if (path === '/auth/login') return token;
    if (path === '/auth/me') return user;
    if (path === '/documents/10') return doc;
    if (path === '/documents/10/processing') return makeProcessing(10);
    if (path === '/documents/10/metadata' && options?.method === 'PUT') {
      if (failNextSave) {
        throw new ApiError(400, {
          detail: 'Invalid request fields',
          errors: { title: 'Server title rejection' },
        });
      }
      const body = options.body as {
        title: string;
        description: string | null;
      };
      doc = { ...doc, title: body.title, description: body.description };
      return doc;
    }
    return makePage([doc]);
  });

  const { client } = await openSignedIn(['/documents/10']);
  await screen.findByRole('heading', { name: 'Quarterly invoice' });
  const invalidateSpy = vi.spyOn(client, 'invalidateQueries');

  fireEvent.change(screen.getByLabelText('Title'), {
    target: { value: '  Renamed invoice  ' },
  });
  fireEvent.change(screen.getByLabelText('Description (optional)'), {
    target: { value: '' },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Save metadata' }));

  await screen.findByText('Server title rejection');
  expect((screen.getByLabelText('Title') as HTMLInputElement).value).toBe(
    '  Renamed invoice  ',
  );

  failNextSave = false;
  fireEvent.click(screen.getByRole('button', { name: 'Save metadata' }));

  await screen.findByText('Metadata saved.');
  expect(api).toHaveBeenCalledWith(
    '/documents/10/metadata',
    expect.objectContaining({
      method: 'PUT',
      body: { title: 'Renamed invoice', description: null },
      token: 'doc-token',
    }),
  );
  expect(invalidateSpy).toHaveBeenCalledWith({
    queryKey: documentKeys.detail(10),
  });
  expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: documentKeys.lists });
});

it('downloads authenticated PDF bytes with original filename and revokes object URLs', async () => {
  const doc = makeDoc();
  api.mockImplementation(async (path) => {
    if (path === '/auth/login') return token;
    if (path === '/auth/me') return user;
    if (path === '/documents/10') return doc;
    if (path === '/documents/10/processing') return makeProcessing(10);
    return makePage([doc]);
  });

  const createObjectUrl = vi
    .spyOn(URL, 'createObjectURL')
    .mockReturnValue('blob:docflow-test');
  const revokeObjectUrl = vi
    .spyOn(URL, 'revokeObjectURL')
    .mockImplementation(() => {});
  const clickSpy = vi
    .spyOn(HTMLAnchorElement.prototype, 'click')
    .mockImplementation(() => {});

  blobApi.mockRejectedValueOnce(new TypeError('network'));
  blobApi.mockResolvedValueOnce(
    new Blob(['%PDF-1.4'], { type: 'application/pdf' }),
  );

  await openSignedIn(['/documents/10']);
  await screen.findByRole('heading', { name: 'Quarterly invoice' });

  fireEvent.click(screen.getByRole('button', { name: 'Download PDF' }));
  await screen.findByText(
    'Could not download the PDF. Check your connection and try again.',
  );

  fireEvent.click(screen.getByRole('button', { name: 'Download PDF' }));
  await waitFor(() => expect(revokeObjectUrl).toHaveBeenCalledTimes(1));

  expect(blobApi).toHaveBeenLastCalledWith(
    '/documents/10/content',
    expect.objectContaining({ token: 'doc-token' }),
  );
  expect(createObjectUrl).toHaveBeenCalledTimes(1);
  expect(clickSpy).toHaveBeenCalledTimes(1);
  expect(revokeObjectUrl).toHaveBeenCalledWith('blob:docflow-test');
});

it('handles deletion cancellation, failure retry, and cache cleanup on 204', async () => {
  const doc = makeDoc();
  let failDelete = true;

  api.mockImplementation(async (path, options) => {
    if (path === '/auth/login') return token;
    if (path === '/auth/me') return user;
    if (path === '/documents/10' && options?.method === 'DELETE') {
      if (failDelete) throw new TypeError('offline');
      return undefined;
    }
    if (path === '/documents/10') return doc;
    if (path === '/documents/10/processing') return makeProcessing(10);
    return makePage([]);
  });

  const { client } = await openSignedIn(['/documents/10']);
  await screen.findByRole('heading', { name: 'Quarterly invoice' });

  fireEvent.click(screen.getByRole('button', { name: 'Delete document' }));
  await screen.findByRole('heading', { name: 'Delete this document?' });
  fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
  expect(
    screen.queryByRole('heading', { name: 'Delete this document?' }),
  ).toBeNull();
  expect(api).not.toHaveBeenCalledWith(
    '/documents/10',
    expect.objectContaining({ method: 'DELETE' }),
  );

  fireEvent.click(screen.getByRole('button', { name: 'Delete document' }));
  fireEvent.click(screen.getByRole('button', { name: 'Confirm delete' }));
  await screen.findByText(
    'Could not delete the document. Check your connection and try again.',
  );
  expect(
    screen.getByRole('heading', { name: 'Quarterly invoice' }),
  ).toBeTruthy();

  failDelete = false;
  fireEvent.click(screen.getByRole('button', { name: 'Confirm delete' }));
  await screen.findByText('Document deleted.');
  expect(client.getQueryData(documentKeys.detail(10))).toBeUndefined();
  expect(client.getQueryData(documentKeys.processing(10))).toBeUndefined();
});

it('aborts pending document requests on logout and ignores late old-session results', async () => {
  let resolveOldDoc!: (value: DocumentResponse) => void;
  let oldSignal: AbortSignal | undefined;
  let logins = 0;

  api.mockImplementation(async (path, options) => {
    if (path === '/auth/login') {
      return { ...token, accessToken: `token-${++logins}` };
    }
    if (path === '/auth/me') return user;
    if (path === '/documents/10') {
      if (logins === 1) {
        oldSignal = options?.signal;
        return new Promise((ok) => {
          resolveOldDoc = ok;
        });
      }
      return makeDoc({ title: 'New session document' });
    }
    if (path === '/documents/10/processing') return makeProcessing(10);
    return makePage([]);
  });

  const { client } = await openSignedIn(['/documents/10']);
  await waitFor(() => expect(oldSignal).toBeDefined());

  fireEvent.click(screen.getByRole('button', { name: 'Sign out' }));
  await screen.findByRole('heading', { name: 'Welcome back.' });
  expect(oldSignal?.aborted).toBe(true);
  expect(client.getQueryData(documentKeys.detail(10))).toBeUndefined();

  fireEvent.change(screen.getByLabelText('Username or email'), {
    target: { value: 'alice' },
  });
  fireEvent.change(screen.getByLabelText('Password'), {
    target: { value: 'secret-pass' },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));
  await screen.findByRole('heading', { name: 'New session document' });

  await act(async () =>
    resolveOldDoc(makeDoc({ title: 'Stale old session document' })),
  );
  expect(screen.queryByText('Stale old session document')).toBeNull();
  expect(
    (client.getQueryData(documentKeys.detail(10)) as DocumentResponse).title,
  ).toBe('New session document');
});
