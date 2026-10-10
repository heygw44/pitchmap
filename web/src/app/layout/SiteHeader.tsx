import { Icon } from '../../components/icons';
import { AccountChip } from '../../features/member/AccountChip';
import { withNext } from '../../features/member/nextPath';
import { useSession } from '../../features/member/session';
import { NotificationBellLink, UnreadBadge } from '../../features/notification/NotificationLink';
import { notificationLabel, useUnreadCount } from '../../features/notification/unreadCount';
import { Link, useLocation } from '../router';
import { activeNavKey, NAV_ITEMS } from './navItems';

type SiteHeaderProps = {
  // 하단 탭이 없는 화면(랜딩)에서 켠다. 모바일에서도 계정 표시를 머리글에 둔다.
  mobileAccount?: boolean;
  // 로그인·가입 화면처럼 계정 표시가 그 화면의 할 일과 겹치는 곳에서 켠다. 알림·계정 자리를 비운다.
  minimal?: boolean;
  // 지도 화면처럼 본문이 화면 끝까지 차는 곳에서 켠다. 가운데 최대 폭을 두지 않아 로고가 왼쪽 패널과 줄을 맞춘다.
  fluid?: boolean;
  className?: string;
};

const PRIMARY_KEYS = new Set(['map', 'basecamps', 'community', 'programs']);

// 지도가 없는 화면과 데스크톱 지도 화면 위에 붙는 머리글이다. 모바일에서는 메뉴를 하단 탭이 맡으므로 서비스 이름만 남긴다.
export function SiteHeader({ mobileAccount = false, minimal = false, fluid = false, className = '' }: SiteHeaderProps) {
  const { pathname, search } = useLocation();
  const session = useSession();
  const count = useUnreadCount();
  const signedIn = session.status === 'authenticated' && session.me !== null;
  const active = activeNavKey(pathname);

  return (
    <header className={`sticky top-0 z-20 border-b border-contour bg-paper ${className}`}>
      <div className={`mx-auto flex min-h-14 items-center gap-6 px-4 ${fluid ? '' : 'max-w-screen-xl'}`}>
        <Link to="/" className="font-serif text-xl font-semibold text-forest-deep">
          피치맵
        </Link>

        <nav aria-label="주 메뉴" className="hidden h-14 items-stretch gap-1 lg:flex">
          {NAV_ITEMS.filter((item) => PRIMARY_KEYS.has(item.key)).map((item) => {
            const current = active === item.key;
            return (
              <Link
                key={item.key}
                to={item.path}
                aria-current={current ? 'page' : undefined}
                className={[
                  'inline-flex items-center border-b-2 px-3 text-base font-semibold',
                  current ? 'border-forest text-forest-deep' : 'border-transparent text-ink-muted hover:text-ink',
                ].join(' ')}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>

        {!minimal && (
          <div className="ml-auto flex min-w-0 items-center gap-2">
            {signedIn && (
              <Link
                to="/me/notifications"
                aria-label={notificationLabel(count)}
                aria-current={pathname === '/me/notifications' ? 'page' : undefined}
                className="hidden min-h-11 items-center gap-2 rounded-control px-3 text-ink hover:bg-paper-deep lg:inline-flex"
              >
                <Icon name="bell" size={20} />
                <UnreadBadge count={count} />
              </Link>
            )}
            <NotificationBellLink className="inline-flex min-h-11 min-w-11 items-center justify-center gap-1 rounded-control px-2 text-ink hover:bg-paper-deep lg:hidden" />
            <div className={mobileAccount ? 'flex' : 'hidden lg:flex'}>
              <AccountChip />
            </div>
            {!mobileAccount && !signedIn && session.status !== 'loading' && (
              <Link
                to={withNext('/login', pathname + search)}
                className="inline-flex min-h-11 items-center px-2 font-semibold text-forest lg:hidden"
              >
                로그인
              </Link>
            )}
          </div>
        )}
      </div>
    </header>
  );
}
