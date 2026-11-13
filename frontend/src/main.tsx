import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, AppErrorBoundary } from './app/App';
import { SessionProvider } from './features/authentication/SessionProvider';
import './shared/styles/global.css';

const client = new QueryClient({
  defaultOptions: {
    queries: { retry: false, networkMode: 'always' },
    mutations: { networkMode: 'always' },
  },
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AppErrorBoundary>
      <QueryClientProvider client={client}>
        <SessionProvider>
          <BrowserRouter>
            <App />
          </BrowserRouter>
        </SessionProvider>
      </QueryClientProvider>
    </AppErrorBoundary>
  </StrictMode>,
);
