import { Icon } from '../../components/icons';
import { useSession } from '../../features/member/session';
import { UnreadBadge } from '../../features/notification/NotificationLink';
import { notificationLabel, useUnreadCount } from '../../features/notification/unreadCount';
import { Link, useLocation } from '../router';
import { activeNavKey, NAV_ITEMS, navTarget } from './navItems';

// 모바일 화면 맨 아래에 붙는 다섯 칸 메뉴다. lg부터는 머리글이 메뉴를 맡으므로 숨긴다.
// 높이는 theme.css의 --bottom-nav-h와 같다(56px + 아이폰 홈 막대 여백).
export function BottomTabBar() {
  const { pathname } = useLocation();
  const session = useSession();
  const count = useUnreadCount();
  const signedIn = session.status === 'authenticated' && session.me !== null;
  const active = activeNavKey(pathname);

  return (
    <nav
      aria-label="하단 메뉴"
      className="fixed inset-x-0 bottom-0 z-20 border-t border-contour bg-card pb-[env(safe-area-inset-bottom)] lg:hidden"
    >
      <ul className="grid grid-cols-5">
        {NAV_ITEMS.map((item) => {
          const current = active === item.key;
          const showCount = item.key === 'notifications' && signedIn;
          return (
            <li key={item.key}>
              <Link
                to={navTarget(item, signedIn)}
                aria-current={current ? 'page' : undefined}
                aria-label={showCount ? notificationLabel(count) : undefined}
                className={[
                  'relative flex h-14 flex-col items-center justify-center gap-0.5 border-t-2 text-xs',
                  current ? 'border-forest font-semibold text-forest-deep' : 'border-transparent text-ink-muted',
                ].join(' ')}
              >
                <span className="relative">
                  <Icon name={item.icon} size={22} />
                  {showCount && (
                    <span className="absolute -right-3 -top-1.5">
                      <UnreadBadge count={count} />
                    </span>
                  )}
                </span>
                {item.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
