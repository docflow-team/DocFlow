import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App, AppErrorBoundary } from './app/App';
import './shared/styles/global.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AppErrorBoundary>
      <App />
    </AppErrorBoundary>
  </StrictMode>,
);
