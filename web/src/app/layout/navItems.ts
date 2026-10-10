import type { IconName } from '../../components/iconPaths';
import { withNext } from '../../features/member/nextPath';

export type NavKey = 'map' | 'basecamps' | 'programs' | 'notifications' | 'me';

export type NavItem = {
  key: NavKey;
  label: string;
  icon: IconName;
  // 로그인이 필요한 메뉴는 비회원이면 로그인 화면으로 보내고, 로그인한 뒤 그 메뉴로 돌아오게 한다.
  needsLogin: boolean;
  path: string;
};

// 머리글과 하단 탭이 같은 순서·같은 이름으로 메뉴를 보여 주도록 한곳에 둔다.
export const NAV_ITEMS: readonly NavItem[] = [
  { key: 'map', label: '지도', icon: 'map', needsLogin: false, path: '/map' },
  { key: 'basecamps', label: '베이스캠프', icon: 'tent', needsLogin: false, path: '/basecamps' },
  { key: 'programs', label: '행사', icon: 'calendar', needsLogin: false, path: '/programs' },
  { key: 'notifications', label: '알림', icon: 'bell', needsLogin: true, path: '/me/notifications' },
  { key: 'me', label: '내 정보', icon: 'user', needsLogin: true, path: '/me' },
];

export function navTarget(item: NavItem, signedIn: boolean): string {
  return item.needsLogin && !signedIn ? withNext('/login', item.path) : item.path;
}

// 지금 주소가 어느 메뉴 아래에 있는지 고른다. 장소 상세와 박지 제보는 지도 화면이라 지도 메뉴로 본다.
export function activeNavKey(pathname: string): NavKey | null {
  if (pathname === '/map' || pathname.startsWith('/spots/') || pathname.startsWith('/bakjis/')) return 'map';
  if (pathname === '/basecamps' || pathname.startsWith('/basecamps/')) return 'basecamps';
  if (pathname === '/programs' || pathname.startsWith('/programs/')) return 'programs';
  if (pathname === '/me/notifications') return 'notifications';
  if (pathname === '/me' || pathname.startsWith('/me/') || pathname === '/identity-verification') return 'me';
  return null;
}
