import { useEffect, useRef, useState } from 'react';
import type { FormEvent, InputHTMLAttributes } from 'react';
import { useMutation } from '@tanstack/react-query';
import { ApiError } from '../../shared/api/client';
import { useSession } from './session';
import styles from './AuthPages.module.css';

type Mode = 'login' | 'password';

function validate(mode: Mode, values: FormData) {
  const errors: Record<string, string> = {};
  const value = (name: string) => String(values.get(name) ?? '');
  if (mode === 'login') {
    if (!value('login').trim()) errors.login = 'Enter your username or email.';
    if (!value('password')) errors.password = 'Enter your password.';
  } else {
    if (!value('currentPassword'))
      errors.currentPassword = 'Enter your current password.';
    const password = value('newPassword');
    if (password.length < 8) errors.newPassword = 'Use at least 8 characters.';
    else if (new TextEncoder().encode(password).byteLength > 72)
      errors.newPassword = 'Use a shorter password (maximum 72 UTF-8 bytes).';
    if (value('confirmation') !== password)
      errors.confirmation = 'The passwords do not match.';
  }
  return errors;
}

function Field({
  label,
  error,
  ...input
}: InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string }) {
  return (
    <div className={styles.field}>
      <label htmlFor={input.name}>{label}</label>
      <input
        {...input}
        id={input.name}
        aria-invalid={!!error}
        aria-describedby={error ? `${input.name}-error` : undefined}
      />
      {error && (
        <span className={styles.fieldError} id={`${input.name}-error`}>
          {error}
        </span>
      )}
    </div>
  );
}

function AuthenticationForm({ mode }: { mode: Mode }) {
  const session = useSession();
  const submitted = useRef<FormData | null>(null);
  const request = useRef<AbortController | null>(null);
  const [validation, setValidation] = useState<Record<string, string>>({});
  useEffect(() => () => request.current?.abort(), []);

  const mutation = useMutation({
    // Keep credentials in the form/request, never in mutation variables or cached results.
    mutationFn: async () => {
      const values = submitted.current!;
      submitted.current = null;
      const value = (name: string) => String(values.get(name) ?? '');
      const controller = new AbortController();
      request.current = controller;
      if (mode === 'login') {
        await session.login(
          { login: value('login').trim(), password: value('password') },
          controller.signal,
        );
      } else {
        await session.changePassword(
          {
            currentPassword: value('currentPassword'),
            newPassword: value('newPassword'),
          },
          controller.signal,
        );
      }
    },
  });
  const fields = Object.keys(validation).length
    ? validation
    : mutation.error instanceof ApiError
      ? mutation.error.fieldErrors
      : {};
  const firstError = Object.keys(fields)[0];
  useEffect(() => {
    if (firstError) document.getElementById(firstError)?.focus();
  }, [firstError]);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (mutation.isPending) return;
    mutation.reset();
    const values = new FormData(event.currentTarget);
    const errors = validate(mode, values);
    setValidation(errors);
    if (!Object.keys(errors).length) {
      submitted.current = values;
      mutation.mutate();
    }
  }

  const isLogin = mode === 'login';
  const title = isLogin
    ? 'Welcome back.'
    : session.user?.mustChangePassword
      ? 'Choose a new password.'
      : 'Change your password.';
  const failed = mutation.error && mutation.error.name !== 'AbortError';
  const message =
    mutation.error instanceof ApiError
      ? mutation.error.message
      : 'Cannot reach DocFlow. Check your connection and try again.';

  return (
    <section className={styles.layout}>
      <div className={styles.intro}>
        <p className={styles.eyebrow}>A little less paperwork.</p>
        <h1 tabIndex={-1}>{title}</h1>
        <p>
          {isLogin
            ? 'Sign in to your document workspace.'
            : session.user?.mustChangePassword
              ? 'Change your temporary password before opening your documents.'
              : 'After changing your password, sign in again with the new one.'}
        </p>
      </div>
      <form
        className={styles.card}
        onSubmit={submit}
        noValidate
        aria-busy={mutation.isPending}
        onChange={() => {
          setValidation({});
          mutation.reset();
        }}
      >
        {isLogin && session.notice && (
          <p className={styles.notice} role="status">
            {session.notice}
          </p>
        )}
        {failed && (
          <p className={styles.error} role="alert">
            {message}
          </p>
        )}
        <fieldset disabled={mutation.isPending}>
          {isLogin ? (
            <>
              <Field
                name="login"
                label="Username or email"
                autoComplete="username"
                required
                error={fields.login}
              />
              <Field
                name="password"
                label="Password"
                type="password"
                autoComplete="current-password"
                required
                error={fields.password}
              />
            </>
          ) : (
            <>
              <Field
                name="currentPassword"
                label="Current password"
                type="password"
                autoComplete="current-password"
                required
                error={fields.currentPassword}
              />
              <Field
                name="newPassword"
                label="New password"
                type="password"
                autoComplete="new-password"
                required
                error={fields.newPassword}
              />
              <p className={styles.hint}>
                At least 8 characters; maximum 72 UTF-8 bytes.
              </p>
              <Field
                name="confirmation"
                label="Confirm new password"
                type="password"
                autoComplete="new-password"
                required
                error={fields.confirmation}
              />
            </>
          )}
        </fieldset>
        <button
          className={styles.submit}
          type="submit"
          disabled={mutation.isPending}
        >
          {mutation.isPending
            ? isLogin
              ? 'Signing in...'
              : 'Updating password...'
            : isLogin
              ? 'Sign in'
              : 'Change password'}
        </button>
        {isLogin && (
          <p className={styles.hint}>
            Accounts are created by your administrator. Reloading the page ends
            your session.
          </p>
        )}
      </form>
    </section>
  );
}

export function LoginPage() {
  return <AuthenticationForm mode="login" />;
}
export function PasswordPage() {
  return <AuthenticationForm mode="password" />;
}
