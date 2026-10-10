import { Link } from '../../app/router';
import { Icon } from '../../components/icons';
import { useSession } from '../member/session';
import { notificationLabel, useUnreadCount } from './unreadCount';

// 안 읽은 알림 수 배지. 100개부터는 99+로 줄여 적는다.
export function UnreadBadge({ count }: { count: number }) {
  if (count <= 0) return null;
  return (
    <span
      aria-hidden="true"
      className="rounded-control bg-forest px-1.5 font-mono text-xs font-semibold tabular-nums text-white"
    >
      {count > 99 ? '99+' : count}
    </span>
  );
}

// 로그인한 회원에게만 보인다. 수는 앱 전체에서 한 번만 받아 오고(UnreadCountSync), 여기서는 읽기만 한다.
export function NotificationLink() {
  const session = useSession();
  const count = useUnreadCount();

  if (session.status !== 'authenticated' || session.me === null) return null;

  return (
    <Link
      to="/me/notifications"
      aria-label={notificationLabel(count)}
      className="inline-flex min-h-11 items-center gap-2 rounded-control border border-contour bg-card px-3 text-ink hover:bg-paper-deep"
    >
      <Icon name="bell" size={20} />
      <span className="text-sm">알림</span>
      <UnreadBadge count={count} />
    </Link>
  );
}
