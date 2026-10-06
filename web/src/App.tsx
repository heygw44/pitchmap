import NotFoundPage from './app/NotFoundPage';
import { Router } from './app/router';
import { routes } from './app/routes';
import { SessionProvider } from './features/member/session';

export default function App() {
  return (
    <SessionProvider>
      <Router routes={routes} fallback={<NotFoundPage />} />
    </SessionProvider>
  );
}
