import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { logout as logoutRequest, fetchMe } from '../../api/auth';
import { setUnauthorizedHandler } from '../../api/client';
import type { Me } from '../../api/types';
import { navigate } from '../../app/router';

export type SessionStatus = 'loading' | 'anonymous' | 'authenticated';

export interface Session {
  status: SessionStatus;
  me: Me | null;
  refresh(): Promise<Me | null>;
  logout(): Promise<void>;
}

interface SessionState {
  status: SessionStatus;
  me: Me | null;
}

const ANONYMOUS: SessionState = { status: 'anonymous', me: null };

const SessionContext = createContext<Session | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<SessionState>({ status: 'loading', me: null });

  // 앱을 열 때 서버에 지금 세션이 누구인지 한 번 묻는다.
  // 네트워크 오류로 확인하지 못하면 로그인하지 않은 상태로 두고, 로그인이 필요한 요청에서 서버가 401로 알려 준다.
  useEffect(() => {
    const controller = new AbortController();
    fetchMe(controller.signal).then(
      (me) => setState(me ? { status: 'authenticated', me } : ANONYMOUS),
      () => {
        if (!controller.signal.aborted) setState(ANONYMOUS);
      },
    );
    return () => controller.abort();
  }, []);

  // 세션이 끊기면(401) 로그인 화면으로 보내고, 로그인한 뒤 지금 보던 화면으로 돌아오도록 경로를 next에 담는다.
  // 로그인·가입 화면에서는 이미 그 화면에 있으므로 옮기지 않는다.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      setState(ANONYMOUS);
      const { pathname, search } = window.location;
      if (pathname === '/login' || pathname === '/signup') return;
      navigate(`/login?next=${encodeURIComponent(pathname + search)}`, { replace: true });
    });
    return () => setUnauthorizedHandler(null);
  }, []);

  const refresh = useCallback(async () => {
    const me = await fetchMe();
    setState(me ? { status: 'authenticated', me } : ANONYMOUS);
    return me;
  }, []);

  const logout = useCallback(async () => {
    // 서버 세션을 지우지 못해도(이미 만료 등) 화면에서는 로그아웃한 상태로 돌린다.
    try {
      await logoutRequest();
    } catch {
      // 무시한다.
    }
    setState(ANONYMOUS);
    navigate('/');
  }, []);

  const value = useMemo<Session>(
    () => ({ status: state.status, me: state.me, refresh, logout }),
    [state, refresh, logout],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): Session {
  const session = useContext(SessionContext);
  if (!session) {
    throw new Error('useSession은 SessionProvider 안에서만 쓸 수 있습니다.');
  }
  return session;
}
