import { useEffect, useSyncExternalStore } from 'react';
import { fetchUnreadCount } from '../../api/notifications';
import { useLocation } from '../../app/router';

// 안 읽은 알림 수를 머리글과 알림함이 함께 쓰므로 화면 밖의 모듈 변수에 둔다.
let unreadCount = 0;
let requestSeq = 0;
const listeners = new Set<() => void>();

function emit(next: number) {
  if (next === unreadCount) return;
  unreadCount = next;
  for (const listener of listeners) listener();
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function getSnapshot() {
  return unreadCount;
}

export function useUnreadCount(): number {
  return useSyncExternalStore(subscribe, getSnapshot);
}

// 서버에 다시 묻는다. 실패하면 마지막 값을 그대로 둔다. 배경 요청이라 사용자에게 오류를 보이지 않는다.
// 요청 여러 개가 겹치면 가장 나중에 보낸 요청의 응답만 반영한다.
export async function refreshUnreadCount(): Promise<void> {
  const seq = ++requestSeq;
  try {
    const { count } = await fetchUnreadCount();
    if (seq === requestSeq) emit(count);
  } catch {
    // 마지막 값을 유지한다.
  }
}

// 로그아웃했거나 로그인하지 않은 상태로 돌아갈 때 이전 회원의 수가 남지 않게 한다.
export function resetUnreadCount(): void {
  requestSeq++;
  emit(0);
}

// 로그인한 상태에서 화면 진입, 주소 변경, 탭이 다시 보일 때 수를 새로 받는다. 주기적으로 묻지는 않는다.
export function useUnreadCountSync(enabled: boolean): void {
  const { pathname } = useLocation();

  useEffect(() => {
    if (!enabled) {
      resetUnreadCount();
      return;
    }
    void refreshUnreadCount();
  }, [enabled, pathname]);

  useEffect(() => {
    if (!enabled) return;
    function handleVisibility() {
      if (document.visibilityState === 'visible') void refreshUnreadCount();
    }
    document.addEventListener('visibilitychange', handleVisibility);
    return () => document.removeEventListener('visibilitychange', handleVisibility);
  }, [enabled]);
}
