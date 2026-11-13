import { createContext, useContext } from 'react';
import type { RequestOptions } from '../../shared/api/client';
import type {
  LoginRequest,
  PasswordChangeRequest,
  UserResponse,
} from '../../shared/api/types';

type AuthOptions = Omit<RequestOptions, 'token'>;

interface SessionContextValue {
  authenticated: boolean;
  user: UserResponse | null;
  accountPending: boolean;
  accountError: Error | null;
  retryAccount(): void;
  notice: string | null;
  login(input: LoginRequest, signal: AbortSignal): Promise<void>;
  changePassword(
    input: PasswordChangeRequest,
    signal: AbortSignal,
  ): Promise<void>;
  logout(): Promise<void>;
  json<T = void>(path: string, options?: AuthOptions): Promise<T>;
  blob(path: string, options?: AuthOptions): Promise<Blob>;
}

export const SessionContext = createContext<SessionContextValue | null>(null);

export function useSession() {
  const session = useContext(SessionContext);
  if (!session) throw new Error('SessionProvider is required');
  return session;
}
