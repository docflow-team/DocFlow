import { Fragment, useCallback, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, apiBlob, apiJson } from '../../shared/api/client';
import type { RequestOptions } from '../../shared/api/client';
import type {
  LoginRequest,
  PasswordChangeRequest,
  TokenResponse,
  UserResponse,
} from '../../shared/api/types';
import { SessionContext } from './session';

interface Session {
  id: number;
  token: string;
  expiresAt: number;
  controller: AbortController;
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const client = useQueryClient();
  const [session, setSession] = useState<Session | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const active = useRef<Session | null>(null);
  const sequence = useRef(0);
  const pendingLogout = useRef<Promise<void> | null>(null);

  const endSession = useCallback(
    (message: string | null) => {
      active.current?.controller.abort();
      active.current = null;
      void client.cancelQueries();
      client.clear();
      setSession(null);
      setNotice(message);
    },
    [client],
  );

  useEffect(() => {
    if (!session) return;
    const timer = window.setTimeout(
      () => {
        if (active.current === session)
          endSession('Your session expired. Sign in again.');
      },
      Math.max(0, session.expiresAt - Date.now()),
    );
    return () => window.clearTimeout(timer);
  }, [session, endSession]);

  useEffect(
    () => () => {
      active.current?.controller.abort();
      active.current = null;
      void client.cancelQueries();
      client.clear();
    },
    [client],
  );

  async function authenticated<T>(
    send: (current: Session) => Promise<T>,
  ): Promise<T> {
    const current = session;
    if (!current || active.current !== current)
      throw new DOMException('Session ended', 'AbortError');
    if (Date.now() >= current.expiresAt) {
      endSession('Your session expired. Sign in again.');
      throw new ApiError(401);
    }
    try {
      const result = await send(current);
      // An old response must never update or invalidate a newer session.
      if (active.current !== current)
        throw new DOMException('Session ended', 'AbortError');
      return result;
    } catch (error) {
      if (
        active.current === current &&
        error instanceof ApiError &&
        error.status === 401
      ) {
        endSession('Your session expired. Sign in again.');
      }
      throw error;
    }
  }

  function json<T = void>(
    path: string,
    options: Omit<RequestOptions, 'token'> = {},
  ): Promise<T> {
    return authenticated((current) =>
      apiJson<T>(path, {
        ...options,
        token: current.token,
        signal: options.signal ?? current.controller.signal,
      }),
    );
  }

  function blob(
    path: string,
    options: Omit<RequestOptions, 'token'> = {},
  ): Promise<Blob> {
    return authenticated((current) =>
      apiBlob(path, {
        ...options,
        token: current.token,
        signal: options.signal ?? current.controller.signal,
      }),
    );
  }

  const account = useQuery<UserResponse>({
    queryKey: ['currentUser'],
    queryFn: ({ signal }) => json<UserResponse>('/auth/me', { signal }),
    enabled: (query) =>
      session !== null && !query.state.data?.mustChangePassword,
  });

  async function login(input: LoginRequest, signal: AbortSignal) {
    // Finish revoking the previous token before another login for the same account.
    await pendingLogout.current;
    signal.throwIfAborted();
    const response = await apiJson<TokenResponse>('/auth/login', {
      method: 'POST',
      body: input,
      signal,
    });
    signal.throwIfAborted();
    endSession(null);
    const next = {
      id: ++sequence.current,
      token: response.accessToken,
      expiresAt: Date.now() + response.expiresIn * 1000,
      controller: new AbortController(),
    };
    active.current = next;
    client.setQueryData(['currentUser'], response.user);
    setSession(next);
  }

  async function changePassword(
    input: PasswordChangeRequest,
    signal: AbortSignal,
  ) {
    await json('/auth/password', { method: 'PUT', body: input, signal });
    endSession('Password changed. Sign in again.');
  }

  async function logout() {
    const current = session;
    if (!current || active.current !== current) return;
    endSession('You have been signed out.');
    const completion = apiJson('/auth/logout', {
      method: 'POST',
      token: current.token,
    }).catch(() => {
      if (!active.current)
        setNotice(
          'Signed out on this device. The server could not confirm sign-out.',
        );
    });
    pendingLogout.current = completion;
    await completion;
    if (pendingLogout.current === completion) pendingLogout.current = null;
  }

  return (
    <SessionContext.Provider
      value={{
        authenticated: session !== null,
        user: account.data ?? null,
        accountPending: account.isPending,
        accountError: account.error,
        retryAccount: () => {
          void account.refetch();
        },
        notice,
        login,
        changePassword,
        logout,
        json,
        blob,
      }}
    >
      <Fragment key={session?.id ?? 'signed-out'}>{children}</Fragment>
    </SessionContext.Provider>
  );
}
