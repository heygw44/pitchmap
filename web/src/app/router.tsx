import { useSyncExternalStore } from 'react';
import type { AnchorHTMLAttributes, MouseEvent, ReactNode } from 'react';

export type RouteParams = Record<string, string>;

export interface RouteDef {
  path: string;
  render: (params: RouteParams) => ReactNode;
}

const listeners = new Set<() => void>();

function notify() {
  for (const listener of listeners) listener();
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  window.addEventListener('popstate', listener);
  return () => {
    listeners.delete(listener);
    window.removeEventListener('popstate', listener);
  };
}

// useSyncExternalStore는 같은 값을 돌려받아야 다시 그리지 않으므로, 주소가 바뀔 때만 새 객체를 만든다.
let cachedHref = '';
let cachedLocation = { pathname: '/', search: '', hash: '' };

function getSnapshot() {
  const { href, pathname, search, hash } = window.location;
  if (href !== cachedHref) {
    cachedHref = href;
    cachedLocation = { pathname, search, hash };
  }
  return cachedLocation;
}

export function navigate(to: string, options?: { replace?: boolean }): void {
  const target = new URL(to, window.location.href);
  if (target.href === window.location.href) return;
  if (options?.replace) {
    window.history.replaceState(null, '', target.href);
  } else {
    window.history.pushState(null, '', target.href);
    window.scrollTo(0, 0);
  }
  notify();
}

export function useLocation(): { pathname: string; search: string; hash: string } {
  return useSyncExternalStore(subscribe, getSnapshot);
}

function splitPath(path: string): string[] {
  return path.split('/').filter((segment) => segment !== '');
}

export function matchPath(pattern: string, pathname: string): RouteParams | null {
  const patternSegments = splitPath(pattern);
  const pathSegments = splitPath(pathname);
  if (patternSegments.length !== pathSegments.length) return null;

  const params: RouteParams = {};
  for (let i = 0; i < patternSegments.length; i++) {
    const patternSegment = patternSegments[i]!;
    const pathSegment = pathSegments[i]!;
    if (patternSegment.startsWith(':')) {
      try {
        params[patternSegment.slice(1)] = decodeURIComponent(pathSegment);
      } catch {
        // 잘못 인코딩된 주소는 어떤 경로와도 맞지 않는 것으로 본다.
        return null;
      }
    } else if (patternSegment !== pathSegment) {
      return null;
    }
  }
  return params;
}

type LinkProps = AnchorHTMLAttributes<HTMLAnchorElement> & { to: string; replace?: boolean };

export function Link({ to, replace, onClick, target, ...anchorProps }: LinkProps) {
  function handleClick(event: MouseEvent<HTMLAnchorElement>) {
    onClick?.(event);
    // 새 탭 열기처럼 브라우저가 처리해야 하는 클릭은 가로채지 않는다.
    if (
      event.defaultPrevented ||
      event.button !== 0 ||
      event.metaKey ||
      event.ctrlKey ||
      event.shiftKey ||
      event.altKey ||
      target
    ) {
      return;
    }
    event.preventDefault();
    navigate(to, { replace });
  }

  return <a {...anchorProps} href={to} target={target} onClick={handleClick} />;
}

export function Router({ routes, fallback }: { routes: RouteDef[]; fallback: ReactNode }) {
  const { pathname } = useLocation();
  for (const route of routes) {
    const params = matchPath(route.path, pathname);
    if (params) return <>{route.render(params)}</>;
  }
  return <>{fallback}</>;
}
