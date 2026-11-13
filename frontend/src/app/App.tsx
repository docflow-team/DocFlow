import { Component, useEffect } from 'react';
import type { ReactNode } from 'react';
import {
  Link,
  Navigate,
  Outlet,
  Route,
  Routes,
  useLocation,
} from 'react-router';
import { useMutation } from '@tanstack/react-query';
import { LoginPage, PasswordPage } from '../features/authentication/AuthPages';
import { useSession } from '../features/authentication/session';
import styles from './App.module.css';

function ProtectedRoutes() {
  const session = useSession();
  const location = useLocation();
  if (!session.authenticated) return <Navigate to="/login" replace />;
  if (session.accountError)
    return (
      <section className={styles.intro}>
        <h1 tabIndex={-1}>Your account could not be loaded.</h1>
        <p role="alert">Check your connection and try again.</p>
        <button onClick={session.retryAccount}>Try again</button>
      </section>
    );
  if (session.accountPending)
    return <p role="status">Checking your account...</p>;
  if (session.user?.mustChangePassword && location.pathname !== '/password')
    return <Navigate to="/password" replace />;
  return <Outlet />;
}

export function App() {
  const session = useSession();
  const location = useLocation();
  const logout = useMutation({ mutationFn: session.logout });
  useEffect(() => {
    document.querySelector<HTMLElement>('h1')?.focus();
    document.title = 'DocFlow';
  }, [location.pathname]);

  return (
    <main className={styles.workspace}>
      <header className={styles.header}>
        <span className={styles.wordmark}>
          DocFlow<span aria-hidden="true">.</span>
        </span>
        {session.authenticated && session.user ? (
          <nav className={styles.navigation} aria-label="Account">
            <span>{session.user.username}</span>
            {!session.user.mustChangePassword && (
              <>
                <Link to="/documents">Documents</Link>
                <Link to="/password">Change password</Link>
              </>
            )}
            <button type="button" onClick={() => logout.mutate()}>
              Sign out
            </button>
          </nav>
        ) : (
          <span className={styles.caption}>Document workspace</span>
        )}
      </header>
      <Routes>
        <Route
          path="/login"
          element={
            session.authenticated ? (
              <Navigate
                to={
                  session.user?.mustChangePassword ? '/password' : '/documents'
                }
                replace
              />
            ) : (
              <LoginPage />
            )
          }
        />
        <Route element={<ProtectedRoutes />}>
          <Route path="/password" element={<PasswordPage />} />
          <Route
            path="/documents"
            element={
              <section className={styles.intro}>
                <p className={styles.eyebrow}>A little less paperwork.</p>
                <h1 tabIndex={-1}>
                  Your documents.
                  <br />
                  <span>One clear space.</span>
                </h1>
                <p className={styles.description}>
                  A home for your PDFs, tags and categories.
                </p>
              </section>
            }
          />
        </Route>
        <Route path="*" element={<Navigate to="/documents" replace />} />
      </Routes>
    </main>
  );
}

export class AppErrorBoundary extends Component<
  { children: ReactNode },
  { failed: boolean }
> {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  render() {
    if (this.state.failed) {
      return (
        <main className={styles.workspace} role="alert">
          <h1>DocFlow could not open.</h1>
          <p>Reload the page to try again.</p>
          <a href="/">Reload DocFlow</a>
        </main>
      );
    }
    return this.props.children;
  }
}
