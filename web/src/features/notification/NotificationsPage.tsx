import { useEffect, useState } from 'react';
import {
  fetchNotifications,
  fetchNotificationSettings,
  markAllNotificationsRead,
  markNotificationRead,
  updateNotificationSettings,
} from '../../api/notifications';
import { toUserMessage } from '../../api/errors';
import type { NotificationItem, NotificationSetting, NotificationType } from '../../api/types';
import { navigate, useLocation } from '../../app/router';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Checkbox } from '../../components/Checkbox';
import { EmptyState } from '../../components/EmptyState';
import { Notice } from '../../components/Notice';
import { PageCard, PageLayout } from '../../components/PageLayout';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatKstDateTime } from '../../lib/datetime';
import { withNext } from '../member/nextPath';
import { useSession } from '../member/session';
import { NOTIFICATION_LABELS, NOTIFICATION_TYPES } from './notificationLabels';
import { refreshUnreadCount } from './unreadCount';

// 서버가 주는 link는 앱 안의 경로다. 다른 사이트로 보내는 값(//, 스킴이 붙은 주소)은 따라가지 않는다.
function isInternalPath(link: string): boolean {
  return link.startsWith('/') && !link.startsWith('//') && !link.includes('\\');
}

export function NotificationsPage() {
  const session = useSession();
  const { pathname, search } = useLocation();
  const showSkeleton = useDelayedFlag(session.status === 'loading');

  useEffect(() => {
    document.title = '알림 · 피치맵';
  }, []);

  // 알림은 로그인한 세션으로만 볼 수 있다. 로그인한 뒤 이 화면으로 돌아오게 한다.
  useEffect(() => {
    if (session.status === 'anonymous') navigate(withNext('/login', pathname + search), { replace: true });
  }, [session.status, pathname, search]);

  if (session.status !== 'authenticated' || !session.me) {
    return <PageLayout title="알림">{showSkeleton ? <Skeleton className="h-40 w-full" /> : null}</PageLayout>;
  }

  return (
    <PageLayout title="알림">
      <NotificationList />
      <EmailSettingsCard />
    </PageLayout>
  );
}

type ListState = {
  items: NotificationItem[];
  page: number;
  hasNext: boolean;
  error: string | null;
};

function NotificationList() {
  const [state, setState] = useState<ListState | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const [markingAll, setMarkingAll] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const showSkeleton = useDelayedFlag(state === null);

  useEffect(() => {
    const controller = new AbortController();
    fetchNotifications(0, controller.signal).then(
      (page) => {
        if (!controller.signal.aborted) {
          setState({ items: page.content, page: page.page, hasNext: page.hasNext, error: null });
        }
      },
      (caught: unknown) => {
        if (!controller.signal.aborted) setState({ items: [], page: 0, hasNext: false, error: toUserMessage(caught) });
      },
    );
    return () => controller.abort();
  }, [attempt]);

  async function loadMore() {
    if (!state || loadingMore) return;
    setLoadingMore(true);
    try {
      const next = await fetchNotifications(state.page + 1);
      setState((previous) =>
        previous
          ? { items: [...previous.items, ...next.content], page: next.page, hasNext: next.hasNext, error: null }
          : previous,
      );
    } catch (caught) {
      setState((previous) => (previous ? { ...previous, error: toUserMessage(caught) } : previous));
    } finally {
      setLoadingMore(false);
    }
  }

  function markLocallyRead(notificationId: number) {
    const readAt = new Date().toISOString();
    setState((previous) =>
      previous
        ? {
            ...previous,
            items: previous.items.map((item) =>
              item.notificationId === notificationId && item.readAt === null ? { ...item, readAt } : item,
            ),
          }
        : previous,
    );
  }

  async function open(item: NotificationItem) {
    setActionError(null);
    try {
      if (item.readAt === null) {
        await markNotificationRead(item.notificationId);
        markLocallyRead(item.notificationId);
        void refreshUnreadCount();
      }
    } catch (caught) {
      // 읽음 처리에 실패해도 이동할 곳이 있으면 이동한다. 이동할 곳이 없을 때만 오류를 보여 준다.
      if (!item.link || !isInternalPath(item.link)) {
        setActionError(toUserMessage(caught));
        return;
      }
    }
    if (item.link && isInternalPath(item.link)) navigate(item.link);
  }

  async function readAll() {
    if (markingAll) return;
    setMarkingAll(true);
    setActionError(null);
    try {
      await markAllNotificationsRead();
      const readAt = new Date().toISOString();
      setState((previous) =>
        previous
          ? { ...previous, items: previous.items.map((item) => (item.readAt === null ? { ...item, readAt } : item)) }
          : previous,
      );
      void refreshUnreadCount();
    } catch (caught) {
      setActionError(toUserMessage(caught));
    } finally {
      setMarkingAll(false);
    }
  }

  if (state === null) return showSkeleton ? <Skeleton className="h-40 w-full" /> : null;

  const hasUnread = state.items.some((item) => item.readAt === null);

  return (
    <section className="flex flex-col gap-3" aria-label="알림 목록">
      {(state.error || actionError) && (
        <div role="alert" className="flex flex-col gap-3">
          <Notice tone="danger" title={state.error ? '알림을 불러오지 못했어요' : '알림을 처리하지 못했어요'}>
            <p>{state.error ?? actionError}</p>
          </Notice>
          {state.error && state.items.length === 0 && (
            <div>
              <Button variant="secondary" onClick={() => setAttempt((value) => value + 1)}>
                다시 불러오기
              </Button>
            </div>
          )}
        </div>
      )}
      {hasUnread && (
        <div>
          <Button variant="secondary" loading={markingAll} onClick={() => void readAll()}>
            모두 읽음
          </Button>
        </div>
      )}
      {state.items.length === 0 && !state.error ? (
        <EmptyState title="알림이 없어요" description="합류 신청, 신고 처리 같은 소식이 생기면 여기에 보여요." />
      ) : (
        <ul className="flex flex-col rounded-control border border-contour bg-card">
          {state.items.map((item) => (
            <NotificationRow key={item.notificationId} item={item} onOpen={() => void open(item)} />
          ))}
        </ul>
      )}
      {state.hasNext && (
        <div>
          <Button variant="secondary" loading={loadingMore} onClick={() => void loadMore()}>
            더 보기
          </Button>
        </div>
      )}
    </section>
  );
}

function NotificationRow({ item, onOpen }: { item: NotificationItem; onOpen: () => void }) {
  const unread = item.readAt === null;
  // 제목과 본문은 서버가 만든 글자이지만 HTML로 넣지 않고 글자로만 그린다.
  return (
    <li className="border-t border-contour first:border-t-0">
      <button
        type="button"
        onClick={onOpen}
        className={`flex min-h-11 w-full flex-col gap-1 px-4 py-3 text-left ${unread ? 'bg-forest-soft' : 'bg-card hover:bg-paper-deep'}`}
      >
        <span className="flex flex-wrap items-center gap-2">
          {unread && <Badge tone="sea">안 읽음</Badge>}
          <span className={`text-base text-ink ${unread ? 'font-semibold' : ''}`}>{item.title}</span>
        </span>
        <span className="whitespace-pre-line break-words text-sm text-ink">{item.body}</span>
        <span className="font-mono text-sm tabular-nums text-ink-muted">{formatKstDateTime(item.createdAt)}</span>
      </button>
    </li>
  );
}

type SettingsState =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; enabled: Record<NotificationType, boolean> };

function toEnabledMap(items: NotificationSetting[]): Record<NotificationType, boolean> {
  // 서버가 빠뜨린 종류는 기본값(받기)으로 본다.
  const map = Object.fromEntries(NOTIFICATION_TYPES.map((type) => [type, true])) as Record<NotificationType, boolean>;
  for (const item of items) {
    if (item.type in map) map[item.type] = item.emailEnabled;
  }
  return map;
}

type SaveResult = 'saved' | { failed: string } | null;

function EmailSettingsCard() {
  const [state, setState] = useState<SettingsState>({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);
  const [saving, setSaving] = useState(false);
  const [result, setResult] = useState<SaveResult>(null);

  useEffect(() => {
    const controller = new AbortController();
    fetchNotificationSettings(controller.signal).then(
      (items) => {
        if (!controller.signal.aborted) setState({ status: 'ready', enabled: toEnabledMap(items) });
      },
      (caught: unknown) => {
        if (!controller.signal.aborted) setState({ status: 'error', message: toUserMessage(caught) });
      },
    );
    return () => controller.abort();
  }, [attempt]);

  function toggle(type: NotificationType, checked: boolean) {
    setResult(null);
    setState((previous) =>
      previous.status === 'ready' ? { status: 'ready', enabled: { ...previous.enabled, [type]: checked } } : previous,
    );
  }

  async function save() {
    if (state.status !== 'ready' || saving) return;
    setSaving(true);
    setResult(null);
    try {
      const saved = await updateNotificationSettings(
        NOTIFICATION_TYPES.map((type) => ({ type, emailEnabled: state.enabled[type] })),
      );
      setState({ status: 'ready', enabled: toEnabledMap(saved) });
      setResult('saved');
    } catch (caught) {
      setResult({ failed: toUserMessage(caught) });
    } finally {
      setSaving(false);
    }
  }

  return (
    <PageCard title="이메일 수신 설정">
      <p className="text-sm text-ink-muted">끄더라도 알림함에는 알림이 계속 쌓여요.</p>
      {state.status === 'loading' && <Skeleton className="h-40 w-full" />}
      {state.status === 'error' && (
        <div className="flex flex-col gap-2">
          <Notice tone="danger" title="이메일 수신 설정을 불러오지 못했어요">
            <p>{state.message}</p>
          </Notice>
          <div>
            <Button variant="secondary" onClick={() => setAttempt((value) => value + 1)}>
              다시 불러오기
            </Button>
          </div>
        </div>
      )}
      {state.status === 'ready' && (
        <>
          <fieldset className="flex min-w-0 flex-col">
            <legend className="sr-only">이메일로 받을 알림</legend>
            {NOTIFICATION_TYPES.map((type) => (
              <Checkbox
                key={type}
                label={NOTIFICATION_LABELS[type]}
                checked={state.enabled[type]}
                disabled={saving}
                onChange={(event) => toggle(type, event.target.checked)}
              />
            ))}
          </fieldset>
          {result === 'saved' && (
            <p role="status" className="text-sm font-semibold text-forest-deep">
              저장했어요.
            </p>
          )}
          {result !== null && result !== 'saved' && (
            <div role="alert">
              <Notice tone="danger" title="저장하지 못했어요">
                <p>{result.failed}</p>
              </Notice>
            </div>
          )}
          <div>
            <Button loading={saving} onClick={() => void save()}>
              저장
            </Button>
          </div>
        </>
      )}
    </PageCard>
  );
}
