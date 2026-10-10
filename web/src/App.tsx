import NotFoundPage from './app/NotFoundPage';
import { Router } from './app/router';
import { routes } from './app/routes';
import { SessionProvider, useSession } from './features/member/session';
import { UnreadCountSync } from './features/notification/unreadCount';

export default function App() {
  return (
    <SessionProvider>
      <SignedInUnreadSync />
      <Router routes={routes} fallback={<NotFoundPage />} />
    </SessionProvider>
  );
}

// 로그인한 회원이면 화면을 옮길 때마다 안 읽은 알림 수를 새로 받는다.
function SignedInUnreadSync() {
  const session = useSession();
  return <UnreadCountSync enabled={session.status === 'authenticated' && session.me !== null} />;
}
