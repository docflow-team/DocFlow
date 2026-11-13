// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { AppErrorBoundary } from './App';

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
