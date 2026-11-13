import { Component } from 'react';
import type { ReactNode } from 'react';
import styles from './App.module.css';

export function App() {
  return (
    <main className={styles.workspace}>
      <header className={styles.header}>
        <span className={styles.wordmark}>
          DocFlow<span aria-hidden="true">.</span>
        </span>
        <span className={styles.caption}>Document workspace</span>
      </header>
      <section className={styles.intro} aria-labelledby="workspace-title">
        <p className={styles.eyebrow}>A little less paperwork.</p>
        <h1 id="workspace-title">
          Your documents.
          <br />
          <span>One clear space.</span>
        </h1>
        <p className={styles.description}>
          A home for your PDFs, tags and categories.
        </p>
      </section>
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
