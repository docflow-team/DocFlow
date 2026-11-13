// @vitest-environment jsdom
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import {
  QueryClient,
  QueryClientProvider,
  useQuery,
} from '@tanstack/react-query';
import { MemoryRouter } from 'react-router';
import { SessionProvider } from '../features/authentication/SessionProvider';
import { useSession } from '../features/authentication/session';
import { apiJson, ApiError } from '../shared/api/client';
import type { TokenResponse, UserResponse } from '../shared/api/types';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { App, AppErrorBoundary } from './App';

it('shows a recoverable message when a child fails to render', () => {
  vi.spyOn(console, 'error').mockImplementation(() => {});
  function BrokenView(): never {
    throw new Error('Synthetic render failure');
  }
  render(
    <AppErrorBoundary>
      <BrokenView />
    </AppErrorBoundary>,
  );
  expect(screen.getByRole('alert')).toBeTruthy();
  expect(
    screen.getByRole('link', { name: 'Reload DocFlow' }).getAttribute('href'),
  ).toBe('/');
  expect(screen.queryByText('Synthetic render failure')).toBeNull();
});

vi.mock('../shared/api/client', async (original) => ({
  ...(await original<typeof import('../shared/api/client')>()),
  apiJson: vi.fn(),
}));
const user: UserResponse = {
  id: 1,
  username: 'alice',
  email: 'alice@example.test',
  role: 'USER',
  enabled: true,
  mustChangePassword: false,
};
const token: TokenResponse = {
  accessToken: 'test-token',
  tokenType: 'Bearer',
  expiresIn: 900,
  user,
};
const api = vi.mocked(apiJson);
const clients: QueryClient[] = [];
beforeEach(() => {
  api.mockReset();
  api.mockImplementation(async (path) => {
    if (path === '/auth/login') return token;
    if (path === '/auth/me') return user;
    return undefined;
  });
});
afterEach(() => {
  cleanup();
  clients.forEach((client) => client.clear());
  clients.length = 0;
  vi.restoreAllMocks();
});
function open(extra?: React.ReactNode) {
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
        <MemoryRouter initialEntries={['/documents']}>
          <App />
          {extra}
        </MemoryRouter>
      </SessionProvider>
    </QueryClientProvider>,
  );
  return { client, ...view };
}
function fill(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } });
}
async function signIn() {
  fill('Username or email', ' alice@example.test ');
  fill('Password', ' secret password ');
  fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));
  await screen.findByRole('button', { name: 'Sign out' });
}
function passwords(next = ' new password ') {
  fill('Current password', ' old password ');
  fill('New password', next);
  fill('Confirm new password', next);
}

it('protects private routes and validates required login fields without API calls', () => {
  open();
  expect(screen.getByRole('heading', { name: 'Welcome back.' })).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));
  expect(screen.getByText('Enter your username or email.')).toBeTruthy();
  expect(document.activeElement).toBe(
    screen.getByLabelText('Username or email'),
  );
  expect(api).not.toHaveBeenCalled();
});

it('signs in with trimmed identifiers, preserves passwords and keeps credentials out of caches/storage', async () => {
  const { client } = open();
  await signIn();
  await screen.findByRole('heading', { name: /Your documents/ });
  expect(api).toHaveBeenCalledWith(
    '/auth/login',
    expect.objectContaining({
      body: { login: 'alice@example.test', password: ' secret password ' },
    }),
  );
  expect(api).toHaveBeenCalledWith(
    '/auth/me',
    expect.objectContaining({ token: 'test-token' }),
  );
  const cached = JSON.stringify({
    queries: client
      .getQueryCache()
      .getAll()
      .map((q) => q.state.data),
    mutations: client
      .getMutationCache()
      .getAll()
      .map((m) => [m.state.data, m.state.variables]),
  });
  expect(cached).not.toContain('test-token');
  expect(cached).not.toContain('secret password');
  expect(localStorage.length).toBe(0);
  expect(sessionStorage.length).toBe(0);
});

it('keeps rejected login on the form and permits retry after a network failure', async () => {
  api.mockRejectedValueOnce(
    new ApiError(401, { detail: 'Invalid credentials.' }),
  );
  open();
  await signInAttempt();
  expect(await screen.findByRole('alert')).toHaveProperty(
    'textContent',
    'Invalid credentials.',
  );
  api.mockRejectedValueOnce(new TypeError('Failed to fetch'));
  fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));
  await screen.findByText(
    'Cannot reach DocFlow. Check your connection and try again.',
  );
  fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));
  await screen.findByRole('button', { name: 'Sign out' });
});
async function signInAttempt() {
  fill('Username or email', 'alice');
  fill('Password', 'wrong');
  fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));
}

it('restricts temporary accounts to password change and never requests forbidden current-user data', async () => {
  api.mockResolvedValueOnce({
    ...token,
    user: { ...user, mustChangePassword: true },
  });
  open();
  await signIn();
  await screen.findByRole('heading', { name: 'Choose a new password.' });
  expect(screen.queryByRole('link', { name: 'Documents' })).toBeNull();
  expect(api.mock.calls.map(([path]) => path)).toEqual(['/auth/login']);
  passwords('short');
  fireEvent.click(screen.getByRole('button', { name: 'Change password' }));
  expect(screen.getByText('Use at least 8 characters.')).toBeTruthy();
  passwords('\u00e9'.repeat(37));
  fireEvent.click(screen.getByRole('button', { name: 'Change password' }));
  expect(
    screen.getByText('Use a shorter password (maximum 72 UTF-8 bytes).'),
  ).toBeTruthy();
  passwords();
  fill('Confirm new password', 'different');
  fireEvent.click(screen.getByRole('button', { name: 'Change password' }));
  expect(screen.getByText('The passwords do not match.')).toBeTruthy();
});

it('shows server password errors and clears private data after a successful password change', async () => {
  const { client } = open();
  await signIn();
  fireEvent.click(screen.getByRole('link', { name: 'Change password' }));
  passwords();
  api.mockRejectedValueOnce(
    new ApiError(400, {
      detail: 'Invalid current password.',
      errors: { currentPassword: 'Incorrect password.' },
    }),
  );
  fireEvent.click(screen.getByRole('button', { name: 'Change password' }));
  await screen.findByText('Incorrect password.');
  expect(document.activeElement).toBe(
    screen.getByLabelText('Current password'),
  );
  client.setQueryData(['private-document'], { title: 'Private' });
  fill('Current password', 'correct password');
  fireEvent.click(screen.getByRole('button', { name: 'Change password' }));
  await screen.findByText('Password changed. Sign in again.');
  expect(api).toHaveBeenLastCalledWith(
    '/auth/password',
    expect.objectContaining({
      method: 'PUT',
      body: {
        currentPassword: 'correct password',
        newPassword: ' new password ',
      },
      token: 'test-token',
    }),
  );
  expect(client.getQueryData(['private-document'])).toBeUndefined();
});

it('clears private data and UI immediately even if server logout fails', async () => {
  const { client } = open();
  await signIn();
  client.setQueryData(['private-document'], 'Private');
  let reject!: (reason: Error) => void;
  api.mockImplementationOnce(
    () =>
      new Promise((_, fail) => {
        reject = fail;
      }),
  );
  fireEvent.click(screen.getByRole('button', { name: 'Sign out' }));
  await screen.findByRole('heading', { name: 'Welcome back.' });
  expect(client.getQueryData(['private-document'])).toBeUndefined();
  expect(screen.queryByText('alice')).toBeNull();
  await act(async () => reject(new TypeError('offline')));
  await screen.findByText(
    'Signed out on this device. The server could not confirm sign-out.',
  );
});

it('ends the session when its lifetime expires', async () => {
  api.mockResolvedValueOnce({ ...token, expiresIn: 1 });
  const { client } = open();
  await signIn();
  client.setQueryData(['private-document'], 'Private');
  await screen.findByText(
    'Your session expired. Sign in again.',
    {},
    { timeout: 2000 },
  );
  expect(client.getQueryData(['private-document'])).toBeUndefined();
});

function PrivateQuery() {
  const session = useSession();
  const query = useQuery({
    queryKey: ['private-document'],
    enabled: session.authenticated,
    queryFn: ({ signal }) => session.json<string>('/documents/1', { signal }),
  });
  return <output>{query.data}</output>;
}

it.each([401, 403, 404])(
  'handles authenticated %s without confusing authorization with expiry',
  async (status) => {
    api.mockImplementation(async (path) => {
      if (path === '/auth/login') return token;
      if (path === '/auth/me') return user;
      throw new ApiError(status);
    });
    open(<PrivateQuery />);
    await signInAttempt();
    if (status === 401)
      await screen.findByText('Your session expired. Sign in again.');
    else await screen.findByRole('heading', { name: /Your documents/ });
    expect(!!screen.queryByRole('button', { name: 'Sign out' })).toBe(
      status !== 401,
    );
  },
);

it.each(['data', '401'])(
  'cancels private queries and ignores late old-session %s after another login',
  async (outcome) => {
    let resolve!: (value: unknown) => void;
    let reject!: (reason: Error) => void;
    let oldSignal: AbortSignal | undefined;
    let logins = 0;
    api.mockImplementation(async (path, options) => {
      if (path === '/auth/login')
        return { ...token, accessToken: `token-${++logins}` };
      if (path === '/auth/me') return user;
      if (path === '/documents/1') {
        if (logins > 1) return 'New private document';
        oldSignal = options?.signal;
        return new Promise((ok, fail) => {
          resolve = ok;
          reject = fail;
        });
      }
      return undefined;
    });
    const { client } = open(<PrivateQuery />);
    await signIn();
    await waitFor(() => expect(oldSignal).toBeDefined());
    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }));
    await screen.findByRole('heading', { name: 'Welcome back.' });
    expect(oldSignal?.aborted).toBe(true);
    await signIn();
    await screen.findByText('New private document');
    await act(async () => {
      if (outcome === 'data') resolve('Old private document');
      else reject(new ApiError(401));
    });
    expect(client.getQueryData(['private-document'])).toBe(
      'New private document',
    );
    expect(screen.queryByText('Old private document')).toBeNull();
    expect(screen.getByRole('button', { name: 'Sign out' })).toBeTruthy();
  },
);

it('requires login after the session provider is recreated', async () => {
  const first = open();
  await signIn();
  first.unmount();
  open();
  expect(screen.getByRole('heading', { name: 'Welcome back.' })).toBeTruthy();
});
