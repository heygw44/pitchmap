import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { ApiError, newIdempotencyKey } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import {
  applyProgram,
  cancelApplication,
  getProgram,
  payApplication,
  subscribeVacancyAlert,
  unsubscribeVacancyAlert,
} from '../../api/programs';
import type { ProgramDetail } from '../../api/types';
import { Link } from '../../app/router';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Dialog } from '../../components/Dialog';
import { EmptyState } from '../../components/EmptyState';
import { Evidence } from '../../components/Evidence';
import { Notice } from '../../components/Notice';
import { PageLayout } from '../../components/PageLayout';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatKstDateTime } from '../../lib/datetime';
import { withNext } from '../member/nextPath';
import { useSession } from '../member/session';
import { APPLICATION_STATUS_META, countdownText, feeText, PROGRAM_STATUS_META, seatsText } from './programLabels';
import { useNow } from './useNow';

type LoadResult = { programId: number; detail: ProgramDetail } | { programId: number; error: unknown };

const LINK_BUTTON_CLASS =
  'inline-flex min-h-11 items-center justify-center rounded-control border border-forest bg-forest px-4 text-base font-semibold text-white hover:bg-forest-strong hover:border-forest-strong';
const SECONDARY_LINK_CLASS =
  'inline-flex min-h-11 items-center rounded-control border border-ink-subtle bg-card px-4 font-semibold text-ink hover:bg-paper-deep';

export function ProgramDetailPage({ programId }: { programId: number }) {
  const session = useSession();
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<LoadResult | null>(null);
  const sessionReady = session.status !== 'loading';

  // 로그인 여부에 따라 내 신청(myApplication)이 달라지므로 세션을 확인한 뒤에 받는다.
  // 다시 받을 때는 화면을 비우지 않고 이전 내용을 둔 채 바꾼다.
  useEffect(() => {
    if (!sessionReady) return;
    const controller = new AbortController();
    getProgram(programId, controller.signal).then(
      (detail) => {
        if (!controller.signal.aborted) setResult({ programId, detail });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ programId, error });
      },
    );
    return () => controller.abort();
  }, [programId, sessionReady, attempt, session.status]);

  const current = result?.programId === programId ? result : null;
  const showSkeleton = useDelayedFlag(current === null);
  const loadedTitle = current !== null && 'detail' in current ? current.detail.title : null;

  useEffect(() => {
    document.title = loadedTitle ? `${loadedTitle} · 피치맵` : '공식 행사 · 피치맵';
  }, [loadedTitle]);

  const reload = () => setAttempt((value) => value + 1);

  let body: ReactNode;
  if (current === null) {
    body = showSkeleton ? (
      <div className="flex flex-col gap-3">
        <Skeleton className="h-8 w-2/3" />
        <Skeleton className="h-40 w-full" />
      </div>
    ) : null;
  } else if ('detail' in current) {
    body = <DetailContent detail={current.detail} onChanged={reload} />;
  } else if (current.error instanceof ApiError && current.error.code === 'NOT_FOUND') {
    body = (
      <EmptyState
        title="행사를 찾을 수 없어요"
        description="없는 행사예요."
        action={
          <Link to="/programs" className={SECONDARY_LINK_CLASS}>
            행사 목록으로
          </Link>
        }
      />
    );
  } else {
    body = (
      <div className="flex flex-col gap-3">
        <Notice tone="danger" title="행사를 불러오지 못했어요">
          {toUserMessage(current.error)}
        </Notice>
        <div>
          <Button variant="secondary" onClick={reload}>
            다시 불러오기
          </Button>
        </div>
      </div>
    );
  }

  return (
    <PageLayout title="공식 행사">
      <Link to="/programs" className="inline-flex min-h-11 items-center self-start text-forest underline underline-offset-2">
        행사 목록
      </Link>
      <div aria-busy={current === null}>{body}</div>
    </PageLayout>
  );
}

function DetailContent({ detail, onChanged }: { detail: ProgramDetail; onChanged: () => void }) {
  const status = PROGRAM_STATUS_META[detail.status];
  return (
    <div className="flex flex-col gap-4">
      <article className="flex flex-col gap-3 rounded-control border border-contour bg-card p-5">
        <header className="flex flex-col gap-2">
          <h2 className="font-serif text-xl font-semibold text-ink">{detail.title}</h2>
          <div className="flex flex-wrap gap-1">
            <Badge tone={status.tone}>{status.label}</Badge>
            {detail.overnight && <Badge tone="earth">숙박</Badge>}
          </div>
        </header>

        {detail.status === 'CANCELED' && (
          <Notice tone="danger" title="운영자가 행사를 취소했어요">
            <p>신청했던 분께는 취소 알림을 보냈어요. 결제한 금액은 환불돼요.</p>
          </Notice>
        )}

        <Evidence
          items={[
            { label: '일시', value: `${formatKstDateTime(detail.startAt)} ~ ${formatKstDateTime(detail.endAt)}` },
            {
              label: '장소',
              value:
                detail.spotId !== null ? (
                  <Link to={`/spots/${detail.spotId}`} className="font-sans text-forest underline underline-offset-2">
                    {detail.locationText}
                  </Link>
                ) : (
                  detail.locationText
                ),
            },
            {
              label: '신청 기간',
              value: `${formatKstDateTime(detail.applyOpenAt)} ~ ${formatKstDateTime(detail.applyCloseAt)}`,
            },
            { label: '정원 · 남은 자리', value: `${detail.capacity}명 · ${seatsText(detail.remainingSeats, detail.capacity)}` },
            { label: '참가비', value: feeText(detail.fee) },
            { label: '결제 기한', value: `신청 후 ${detail.paymentDeadlineMinutes}분 안에 결제` },
          ]}
        />

        {detail.overnight && (
          <p className="text-sm text-ink-muted">숙박 행사는 본인확인을 마친 회원만 신청할 수 있어요.</p>
        )}

        <p className="whitespace-pre-line text-base text-ink">{detail.description}</p>
      </article>

      {detail.status !== 'CANCELED' && <ActionArea detail={detail} onChanged={onChanged} />}
    </div>
  );
}

type Busy = 'apply' | 'pay' | 'cancel' | 'alert' | null;

type KeyRef = { current: { scope: number; key: string } | null };

const HOUR_MS = 60 * 60 * 1000;
const REFUND_BEFORE_START_HOURS = 72;

function ActionArea({ detail, onChanged }: { detail: ProgramDetail; onChanged: () => void }) {
  const session = useSession();
  const [busy, setBusy] = useState<Busy>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [soldOut, setSoldOut] = useState(false);
  const [alertOn, setAlertOn] = useState(false);
  const [confirmCancel, setConfirmCancel] = useState(false);
  // 사용자가 버튼을 한 번 누를 때마다 키를 만든다. 서버에 닿지 못해 응답을 못 받았을 때만 다음 시도가 같은 키를 쓴다.
  const applyKey: KeyRef = useRef(null);
  const payKey: KeyRef = useRef(null);

  const mine = detail.myApplication;
  const pending = mine?.status === 'PENDING_PAYMENT';
  const confirmed = mine?.status === 'CONFIRMED';
  const now = useNow(pending || confirmed);
  const dueAt = pending ? (mine.paymentDueAt ?? null) : null;
  const payExpired = dueAt !== null && now >= Date.parse(dueAt);

  // 기한이 지나도 서버가 만료 처리하기 전에는 결제 대기로 보인다. 만료 처리 결과를 받으려고 가끔 다시 받는다.
  useEffect(() => {
    if (!payExpired) return;
    const timer = window.setInterval(onChanged, 15_000);
    return () => window.clearInterval(timer);
  }, [payExpired, onChanged]);

  async function run(
    kind: Exclude<Busy, null>,
    keyRef: KeyRef | null,
    call: () => Promise<void>,
  ): Promise<void> {
    if (busy !== null) return;
    setBusy(kind);
    setError(null);
    setNotice(null);
    try {
      await call();
      if (keyRef) keyRef.current = null;
    } catch (caught) {
      const apiError = caught instanceof ApiError ? caught : new ApiError(0, 'INTERNAL_ERROR', null);
      // 응답을 받지 못한 경우(네트워크 오류)만 같은 키를 남겨 다시 시도할 때 중복 처리를 막는다.
      if (keyRef && apiError.code !== 'NETWORK_ERROR') keyRef.current = null;
      if (apiError.code === 'PROGRAM_SOLD_OUT') setSoldOut(true);
      setError(apiError);
      if (STALE_CODES.has(apiError.code)) onChanged();
    } finally {
      setBusy(null);
    }
  }

  function keyFor(keyRef: KeyRef, scope: number): string {
    if (keyRef.current === null || keyRef.current.scope !== scope) {
      keyRef.current = { scope, key: newIdempotencyKey() };
    }
    return keyRef.current.key;
  }

  const apply = () =>
    run('apply', applyKey, async () => {
      await applyProgram(detail.programId, keyFor(applyKey, detail.programId));
      setNotice('신청했어요. 결제 기한 안에 결제해 주세요.');
      onChanged();
    });

  const pay = () => {
    if (!mine) return;
    return run('pay', payKey, async () => {
      await payApplication(mine.applicationId, keyFor(payKey, mine.applicationId));
      setNotice('결제를 마쳤어요. 신청이 확정됐어요.');
      onChanged();
    });
  };

  const cancel = async () => {
    if (!mine) return;
    await run('cancel', null, async () => {
      const response = await cancelApplication(mine.applicationId);
      setNotice(response.refunded ? '신청을 취소하고 환불했어요.' : '신청을 취소했어요.');
      onChanged();
    });
    setConfirmCancel(false);
  };

  const subscribe = () =>
    run('alert', null, async () => {
      await subscribeVacancyAlert(detail.programId);
      setAlertOn(true);
      setNotice('빈자리 알림을 신청했어요. 자리가 나면 알려 드려요.');
    });

  const unsubscribe = () =>
    run('alert', null, async () => {
      await unsubscribeVacancyAlert(detail.programId);
      setAlertOn(false);
      setNotice('빈자리 알림을 해제했어요.');
    });

  if (session.status === 'loading') return null;

  const authenticated = session.status === 'authenticated';
  const active = pending || confirmed;
  const noSeats = detail.remainingSeats === 0 || soldOut;
  const canAlert = authenticated && !active && detail.status === 'OPEN' && (noSeats || alertOn);
  const refundDeadlineMs = Date.parse(detail.startAt) - REFUND_BEFORE_START_HOURS * HOUR_MS;
  const refundClosed = now > refundDeadlineMs;

  return (
    <section
      aria-label="신청"
      className="sticky bottom-(--bottom-nav-h) z-10 flex flex-col gap-3 rounded-control border border-contour bg-card p-4 shadow-raise"
    >
      {notice && (
        <p role="status" className="text-sm font-semibold text-forest-deep">
          {notice}
        </p>
      )}
      {error && <ActionError error={error} />}

      {pending && (
        <>
          <Notice tone="warning" title="결제를 기다리고 있어요">
            {dueAt && (
              <p>
                {payExpired ? (
                  '결제 기한이 지났어요. 곧 자리가 반환돼요.'
                ) : (
                  <>
                    남은 시간{' '}
                    <span className="font-mono text-base tabular-nums">{countdownText(dueAt, now)}</span>
                  </>
                )}
              </p>
            )}
            <p className="mt-1">참가비 {feeText(detail.fee)}을 가짜 카드로 결제해요. 실제 결제는 일어나지 않아요.</p>
          </Notice>
          <Button
            fullWidth
            loading={busy === 'pay'}
            disabled={payExpired || busy !== null}
            disabledReason={payExpired ? '결제 기한이 지났어요' : undefined}
            onClick={() => void pay()}
          >
            결제하기
          </Button>
          <Button fullWidth variant="secondary" disabled={busy !== null} onClick={() => setConfirmCancel(true)}>
            신청 취소
          </Button>
        </>
      )}

      {confirmed && (
        <>
          <p className="flex flex-wrap items-center gap-2 text-base text-ink">
            <Badge tone={APPLICATION_STATUS_META.CONFIRMED.tone}>{APPLICATION_STATUS_META.CONFIRMED.label}</Badge>
            신청이 확정됐어요.
          </p>
          <p className="text-sm text-ink-muted">
            {refundClosed
              ? '환불 기한이 지나서 취소할 수 없어요.'
              : `행사 시작 ${REFUND_BEFORE_START_HOURS}시간 전인 ${formatKstDateTime(new Date(refundDeadlineMs).toISOString())}까지 취소하면 환불해요.`}
          </p>
          <Button
            fullWidth
            variant="danger"
            disabled={refundClosed || busy !== null}
            disabledReason={refundClosed ? '환불 기한이 지나면 취소할 수 없어요' : undefined}
            onClick={() => setConfirmCancel(true)}
          >
            신청 취소
          </Button>
        </>
      )}

      {!active && !authenticated && detail.status === 'OPEN' && (
        <Link to={withNext('/login', `/programs/${detail.programId}`)} className={`${LINK_BUTTON_CLASS} w-full`}>
          로그인하고 신청하기
        </Link>
      )}

      {!active && !authenticated && detail.status !== 'OPEN' && <ClosedApplyButton detail={detail} />}

      {!active && authenticated && detail.status !== 'OPEN' && <ClosedApplyButton detail={detail} />}

      {!active && authenticated && detail.status === 'OPEN' && (
        <>
          {noSeats && <p className="text-base font-semibold text-ink">남은 자리가 없어요</p>}
          {canAlert && !alertOn && noSeats && (
            <Button fullWidth loading={busy === 'alert'} disabled={busy !== null} onClick={() => void subscribe()}>
              빈자리 알림 받기
            </Button>
          )}
          <Button
            fullWidth
            variant={noSeats ? 'secondary' : 'primary'}
            loading={busy === 'apply'}
            disabled={busy !== null}
            onClick={() => void apply()}
          >
            신청하기
          </Button>
          {canAlert && alertOn && (
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="text-sm text-ink">빈자리 알림을 신청했어요</p>
              <Button variant="ghost" loading={busy === 'alert'} disabled={busy !== null} onClick={() => void unsubscribe()}>
                알림 해제
              </Button>
            </div>
          )}
        </>
      )}

      {confirmCancel && (
        <Dialog title="신청을 취소할까요?" onClose={() => (busy === null ? setConfirmCancel(false) : undefined)}>
          <div className="flex flex-col gap-4">
            <p className="text-base text-ink">
              {confirmed
                ? '취소하면 결제한 금액을 환불하고 자리를 돌려줘요. 환불 기한이 지나면 취소할 수 없어요.'
                : '취소하면 자리를 돌려줘요. 다시 신청하려면 남은 자리가 있어야 해요.'}
            </p>
            <div className="flex flex-col gap-2">
              <Button variant="danger" fullWidth loading={busy === 'cancel'} disabled={busy !== null} onClick={() => void cancel()}>
                신청 취소하기
              </Button>
              <Button variant="secondary" fullWidth disabled={busy !== null} onClick={() => setConfirmCancel(false)}>
                그대로 두기
              </Button>
            </div>
          </div>
        </Dialog>
      )}
    </section>
  );
}

// 서버 상태와 화면이 어긋났다는 뜻이라서 응답 뒤에 행사 상세를 다시 받는다.
const STALE_CODES = new Set([
  'PROGRAM_ALREADY_APPLIED',
  'PROGRAM_INVALID_STATE',
  'PROGRAM_NOT_IN_APPLY_PERIOD',
  'PROGRAM_PAYMENT_EXPIRED',
]);

function ClosedApplyButton({ detail }: { detail: ProgramDetail }) {
  const reason =
    detail.status === 'UPCOMING' ? `신청은 ${formatKstDateTime(detail.applyOpenAt)}부터예요` : '신청이 마감됐어요';
  return (
    <Button fullWidth disabled disabledReason={reason}>
      신청하기
    </Button>
  );
}

function ActionError({ error }: { error: ApiError }) {
  return (
    <Notice tone="danger" title="처리하지 못했어요">
      <p>{error.message}</p>
      {error.code === 'TRUST_LEVEL_INSUFFICIENT' && (
        <p className="mt-2">
          <Link to="/identity-verification" className="inline-flex min-h-11 items-center text-forest underline underline-offset-2">
            본인확인하러 가기
          </Link>
        </p>
      )}
      {error.status >= 500 && error.traceId && (
        <p className="mt-1 font-mono text-xs text-ink-muted">문의 번호 {error.traceId}</p>
      )}
    </Notice>
  );
}
