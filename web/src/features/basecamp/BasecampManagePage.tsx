import { useEffect, useState } from 'react';
import type { FormEvent, ReactNode } from 'react';
import {
  approveApplication,
  cancelBasecamp,
  closeBasecamp,
  confirmBasecamp,
  fetchBasecampApplications,
  fetchBasecampDetail,
  kickMember,
  rejectApplication,
  reopenBasecamp,
  updateBasecampContact,
} from '../../api/basecamps';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import type { BasecampApplicationItem, BasecampDetail, BasecampMember, KickReason } from '../../api/types';
import { Link, navigate, useLocation } from '../../app/router';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { ChoiceGroup } from '../../components/ChoiceGroup';
import type { ChoiceOption } from '../../components/ChoiceGroup';
import { Dialog } from '../../components/Dialog';
import { EmptyState } from '../../components/EmptyState';
import { Notice } from '../../components/Notice';
import { BackLink } from '../../components/BackLink';
import { PageCard, PageLayout } from '../../components/PageLayout';
import { Skeleton } from '../../components/Skeleton';
import { TextField } from '../../components/TextField';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatKstDateTime } from '../../lib/datetime';
import { trustLevelBadge } from '../member/memberLabels';
import { withNext } from '../member/nextPath';
import { useSession } from '../member/session';
import { BASECAMP_STATUS_META, headcountText, profileSummary } from './basecampLabels';

const CONTACT_MAX = 255;
const MIN_CONFIRM_HEADCOUNT = 2;

const KICK_OPTIONS: ReadonlyArray<ChoiceOption<KickReason>> = [
  { value: 'NO_CONTACT', label: '연락 두절' },
  { value: 'CONDITION_MISMATCH', label: '일정·조건 불일치' },
  { value: 'INAPPROPRIATE_BEHAVIOR', label: '부적절한 행동' },
  { value: 'OTHER', label: '기타' },
];

const LINK_BUTTON_CLASS =
  'inline-flex min-h-11 items-center justify-center rounded-control border border-ink-subtle bg-card px-4 text-base font-semibold text-ink hover:bg-paper-deep';

type DetailResult = { basecampId: number; attempt: number; detail: BasecampDetail } | { basecampId: number; attempt: number; error: unknown };

type ApplicationsState = {
  basecampId: number;
  attempt: number;
  items: BasecampApplicationItem[];
  page: number;
  hasNext: boolean;
  error: string | null;
};

type DialogState =
  | { kind: 'confirm' }
  | { kind: 'cancel' }
  | { kind: 'reject'; application: BasecampApplicationItem }
  | { kind: 'kick'; member: BasecampMember }
  | null;

export function BasecampManagePage({ basecampId }: { basecampId: number }) {
  const session = useSession();
  const { pathname, search } = useLocation();
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<DetailResult | null>(null);
  const [applications, setApplications] = useState<ApplicationsState | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const [dialog, setDialog] = useState<DialogState>(null);
  const [kickReason, setKickReason] = useState<KickReason | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);

  useEffect(() => {
    document.title = '베이스캠프 관리 · 피치맵';
  }, []);

  // 관리 화면은 로그인한 세션으로만 볼 수 있다. 로그인한 뒤 이 화면으로 돌아오게 한다.
  useEffect(() => {
    if (session.status === 'anonymous') navigate(withNext('/login', pathname + search), { replace: true });
  }, [session.status, pathname, search]);

  const authenticated = session.status === 'authenticated';

  useEffect(() => {
    if (!authenticated) return;
    const controller = new AbortController();
    fetchBasecampDetail(basecampId, controller.signal).then(
      (detail) => {
        if (!controller.signal.aborted) setResult({ basecampId, attempt, detail });
      },
      (caught: unknown) => {
        if (!controller.signal.aborted) setResult({ basecampId, attempt, error: caught });
      },
    );
    return () => controller.abort();
  }, [authenticated, basecampId, attempt]);

  const current = result && result.basecampId === basecampId ? result : null;
  const detail = current && 'detail' in current ? current.detail : null;
  const isLeader = detail?.myRelation === 'LEADER';

  // 신청 목록은 캠프 리더만 받을 수 있어서 리더로 확인된 뒤에 요청한다.
  useEffect(() => {
    if (!isLeader) return;
    const controller = new AbortController();
    fetchBasecampApplications(basecampId, 0, controller.signal).then(
      (page) => {
        if (controller.signal.aborted) return;
        setApplications({ basecampId, attempt, items: page.content, page: page.page, hasNext: page.hasNext, error: null });
      },
      (caught: unknown) => {
        if (controller.signal.aborted) return;
        setApplications((previous) => ({
          basecampId,
          attempt,
          items: previous?.basecampId === basecampId ? previous.items : [],
          page: previous?.page ?? 0,
          hasNext: false,
          error: toUserMessage(caught),
        }));
      },
    );
    return () => controller.abort();
  }, [isLeader, basecampId, attempt]);

  const reload = () => setAttempt((value) => value + 1);
  const showSkeleton = useDelayedFlag(session.status === 'loading' || (authenticated && current === null));

  // 성공이든 실패든 서버의 지금 상태로 화면을 맞춘다. 실패는 대개 다른 곳에서 상태가 바뀌어서 생기기 때문이다.
  async function run(action: () => Promise<unknown>, successText: string) {
    setSubmitting(true);
    setError(null);
    setDone(null);
    try {
      await action();
      setDone(successText);
    } catch (caught) {
      setError(toUserMessage(caught));
    } finally {
      setSubmitting(false);
      setDialog(null);
      setKickReason(null);
      reload();
    }
  }

  async function loadMore() {
    if (!applications || loadingMore) return;
    setLoadingMore(true);
    try {
      const next = await fetchBasecampApplications(basecampId, applications.page + 1);
      setApplications((previous) =>
        previous && previous.basecampId === basecampId
          ? { ...previous, items: [...previous.items, ...next.content], page: next.page, hasNext: next.hasNext, error: null }
          : previous,
      );
    } catch (caught) {
      setApplications((previous) => (previous ? { ...previous, error: toUserMessage(caught) } : previous));
    } finally {
      setLoadingMore(false);
    }
  }

  let body: ReactNode;
  if (!authenticated || current === null) {
    body = showSkeleton ? <Skeleton className="h-40 w-full" /> : null;
  } else if ('error' in current) {
    body =
      current.error instanceof ApiError && current.error.code === 'NOT_FOUND' ? (
        <PageCard title="베이스캠프를 찾을 수 없어요">
          <p className="text-base text-ink">삭제됐거나 없는 베이스캠프예요.</p>
          <Link to="/basecamps" className={`${LINK_BUTTON_CLASS} self-start`}>
            목록으로
          </Link>
        </PageCard>
      ) : (
        <div className="flex flex-col gap-3">
          <Notice tone="danger" title="베이스캠프를 불러오지 못했어요">
            <p>{toUserMessage(current.error)}</p>
          </Notice>
          <div>
            <Button variant="secondary" onClick={reload}>
              다시 불러오기
            </Button>
          </div>
        </div>
      );
  } else if (current.detail.myRelation !== 'LEADER') {
    body = (
      <EmptyState
        title="캠프 리더만 관리할 수 있어요"
        description="내가 연 베이스캠프만 관리할 수 있어요."
        action={
          <Link to={`/basecamps/${basecampId}`} className={LINK_BUTTON_CLASS}>
            베이스캠프 보기
          </Link>
        }
      />
    );
  } else {
    const view = current.detail;
    const active = view.status !== 'COMPLETED' && view.status !== 'CANCELED';
    body = (
      <>
        {done && (
          <p role="status" className="text-sm font-semibold text-forest-deep">
            {done}
          </p>
        )}
        {error && (
          <div role="alert">
            <Notice tone="danger" title="처리하지 못했어요">
              <p>{error}</p>
            </Notice>
          </div>
        )}
        <HeaderCard detail={view} />
        {active && <StatusActions detail={view} submitting={submitting} run={run} onDialog={setDialog} />}
        <ApplicationsCard
          detail={view}
          state={applications && applications.basecampId === basecampId ? applications : null}
          submitting={submitting}
          loadingMore={loadingMore}
          onApprove={(application) =>
            run(() => approveApplication(basecampId, application.applicationId), `${application.applicant.nickname}님의 합류를 승인했어요.`)
          }
          onReject={(application) => setDialog({ kind: 'reject', application })}
          onLoadMore={loadMore}
          onRetry={reload}
        />
        <MembersCard
          detail={view}
          onKick={(member) => {
            setKickReason(null);
            setDialog({ kind: 'kick', member });
          }}
        />
        {active && (
          <ContactCard
            key={view.contactInfo ?? ''}
            detail={view}
            onSaved={() => {
              setError(null);
              setDone('연락 수단을 저장했어요.');
              reload();
            }}
          />
        )}
        <Notice tone="info" title="안전 안내">
          <p>{view.safetyNotice}</p>
        </Notice>
        {dialog?.kind === 'confirm' && (
          <Dialog title="베이스캠프 확정" onClose={() => !submitting && setDialog(null)}>
            <div className="flex flex-col gap-4">
              <p className="text-base text-ink">
                확정하면 더 이상 신청을 받을 수 없고, 대기 중인 신청은 모두 사라져요. 확정은 되돌릴 수 없어요.
              </p>
              <DialogButtons
                submitting={submitting}
                confirmLabel="확정"
                onBack={() => setDialog(null)}
                onConfirm={() => run(() => confirmBasecamp(basecampId), '베이스캠프를 확정했어요.')}
              />
            </div>
          </Dialog>
        )}
        {dialog?.kind === 'cancel' && (
          <Dialog title="베이스캠프 취소" onClose={() => !submitting && setDialog(null)}>
            <div className="flex flex-col gap-4">
              <p className="text-base text-ink">취소하면 되돌릴 수 없고 멤버에게 알림이 가요.</p>
              <DialogButtons
                submitting={submitting}
                danger
                confirmLabel="베이스캠프 취소"
                onBack={() => setDialog(null)}
                onConfirm={() => run(() => cancelBasecamp(basecampId), '베이스캠프를 취소했어요.')}
              />
            </div>
          </Dialog>
        )}
        {dialog?.kind === 'reject' && (
          <Dialog title="합류 신청 거절" onClose={() => !submitting && setDialog(null)}>
            <div className="flex flex-col gap-4">
              <p className="text-base text-ink">
                {dialog.application.applicant.nickname}님의 신청을 거절할까요? 거절하면 이 회원은 다시 신청할 수 없어요.
              </p>
              <DialogButtons
                submitting={submitting}
                danger
                confirmLabel="거절"
                onBack={() => setDialog(null)}
                onConfirm={() =>
                  run(() => rejectApplication(basecampId, dialog.application.applicationId), '합류 신청을 거절했어요.')
                }
              />
            </div>
          </Dialog>
        )}
        {dialog?.kind === 'kick' && (
          <Dialog title="멤버 강퇴" onClose={() => !submitting && setDialog(null)}>
            <div className="flex flex-col gap-4">
              <p className="text-base text-ink">
                {dialog.member.nickname}님을 강퇴할까요? 강퇴된 회원은 다시 신청할 수 없어요.
              </p>
              <ChoiceGroup
                legend="강퇴 사유"
                name="kickReason"
                options={KICK_OPTIONS}
                value={kickReason}
                onChange={setKickReason}
                disabled={submitting}
              />
              <DialogButtons
                submitting={submitting}
                danger
                confirmLabel="강퇴"
                confirmDisabledReason={kickReason === null ? '강퇴 사유를 골라 주세요.' : undefined}
                onBack={() => setDialog(null)}
                onConfirm={() => {
                  if (kickReason === null) return;
                  void run(() => kickMember(basecampId, dialog.member.memberId, kickReason), `${dialog.member.nickname}님을 강퇴했어요.`);
                }}
              />
            </div>
          </Dialog>
        )}
      </>
    );
  }

  return (
    <PageLayout title="베이스캠프 관리" breadcrumb={<BackLink to={`/basecamps/${basecampId}`}>베이스캠프</BackLink>}>
      <div aria-busy={authenticated && current === null} className="flex flex-col gap-4">
        {body}
      </div>
    </PageLayout>
  );
}

function DialogButtons({
  submitting,
  danger = false,
  confirmLabel,
  confirmDisabledReason,
  onBack,
  onConfirm,
}: {
  submitting: boolean;
  danger?: boolean;
  confirmLabel: string;
  confirmDisabledReason?: string;
  onBack: () => void;
  onConfirm: () => void;
}) {
  return (
    <div className="flex flex-wrap items-start justify-end gap-2">
      <Button variant="secondary" disabled={submitting} onClick={onBack}>
        돌아가기
      </Button>
      <Button
        variant={danger ? 'danger' : 'primary'}
        loading={submitting}
        disabled={confirmDisabledReason !== undefined}
        disabledReason={confirmDisabledReason}
        onClick={onConfirm}
      >
        {confirmLabel}
      </Button>
    </div>
  );
}

function HeaderCard({ detail }: { detail: BasecampDetail }) {
  const status = BASECAMP_STATUS_META[detail.status];
  return (
    <section className="flex flex-col gap-2 rounded-control border border-contour bg-card p-5">
      <h2 className="font-serif text-lg text-ink">{detail.title}</h2>
      <div className="flex flex-wrap items-center gap-2">
        <Badge tone={status.tone}>{status.label}</Badge>
        <span className="font-mono text-sm tabular-nums text-ink-muted">
          {headcountText(detail.headcount, detail.capacity)}명
        </span>
      </div>
      <Link
        to={`/basecamps/${detail.basecampId}`}
        className="inline-flex min-h-11 items-center self-start text-forest underline underline-offset-2"
      >
        상세 화면 보기
      </Link>
    </section>
  );
}

function StatusActions({
  detail,
  submitting,
  run,
  onDialog,
}: {
  detail: BasecampDetail;
  submitting: boolean;
  run: (action: () => Promise<unknown>, successText: string) => Promise<void>;
  onDialog: (dialog: DialogState) => void;
}) {
  const open = detail.status === 'RECRUITING' || detail.status === 'CLOSED';
  const enoughMembers = detail.headcount >= MIN_CONFIRM_HEADCOUNT;
  return (
    <PageCard title="진행 상태">
      <div className="flex flex-wrap items-start gap-2">
        {detail.status === 'RECRUITING' && (
          <Button
            variant="secondary"
            disabled={submitting}
            onClick={() => void run(() => closeBasecamp(detail.basecampId), '모집을 마감했어요.')}
          >
            모집 마감
          </Button>
        )}
        {detail.status === 'CLOSED' && (
          <Button
            variant="secondary"
            disabled={submitting}
            onClick={() => void run(() => reopenBasecamp(detail.basecampId), '모집을 다시 시작했어요.')}
          >
            모집 재개
          </Button>
        )}
        {open && (
          <Button
            disabled={submitting || !enoughMembers}
            disabledReason={enoughMembers ? undefined : '2명 이상이어야 확정할 수 있어요.'}
            onClick={() => onDialog({ kind: 'confirm' })}
          >
            확정
          </Button>
        )}
        <Button variant="danger" disabled={submitting} onClick={() => onDialog({ kind: 'cancel' })}>
          취소
        </Button>
      </div>
    </PageCard>
  );
}

function ApplicationsCard({
  detail,
  state,
  submitting,
  loadingMore,
  onApprove,
  onReject,
  onLoadMore,
  onRetry,
}: {
  detail: BasecampDetail;
  state: ApplicationsState | null;
  submitting: boolean;
  loadingMore: boolean;
  onApprove: (application: BasecampApplicationItem) => void;
  onReject: (application: BasecampApplicationItem) => void;
  onLoadMore: () => void;
  onRetry: () => void;
}) {
  const recruiting = detail.status === 'RECRUITING';
  return (
    <PageCard title="대기 중인 합류 신청">
      {state === null ? (
        <Skeleton className="h-20 w-full" />
      ) : (
        <>
          {state.error && (
            <div className="flex flex-col gap-2">
              <Notice tone="danger" title="신청 목록을 불러오지 못했어요">
                <p>{state.error}</p>
              </Notice>
              <div>
                <Button variant="secondary" onClick={onRetry}>
                  다시 불러오기
                </Button>
              </div>
            </div>
          )}
          {!recruiting && state.items.length > 0 && (
            <p className="text-sm text-ink-muted">모집 중일 때만 승인·거절할 수 있어요.</p>
          )}
          {state.items.length === 0 && !state.error ? (
            <p className="text-sm text-ink-muted">대기 중인 신청이 없어요.</p>
          ) : (
            <ul className="flex flex-col">
              {state.items.map((application) => (
                <ApplicationRow
                  key={application.applicationId}
                  application={application}
                  recruiting={recruiting}
                  submitting={submitting}
                  onApprove={() => onApprove(application)}
                  onReject={() => onReject(application)}
                />
              ))}
            </ul>
          )}
          {state.hasNext && (
            <div>
              <Button variant="secondary" loading={loadingMore} onClick={onLoadMore}>
                더 보기
              </Button>
            </div>
          )}
        </>
      )}
    </PageCard>
  );
}

function ApplicationRow({
  application,
  recruiting,
  submitting,
  onApprove,
  onReject,
}: {
  application: BasecampApplicationItem;
  recruiting: boolean;
  submitting: boolean;
  onApprove: () => void;
  onReject: () => void;
}) {
  const { applicant } = application;
  const badge = applicant.trustLevel === undefined ? null : trustLevelBadge(applicant.trustLevel);
  const profile = profileSummary(applicant);
  return (
    <li className="flex flex-col gap-1 border-t border-contour py-3 first:border-t-0 first:pt-0">
      <div className="flex flex-wrap items-center gap-2">
        <Link
          to={`/members/${applicant.memberId}`}
          className="inline-flex min-h-11 items-center text-base font-semibold text-forest underline underline-offset-2"
        >
          {applicant.nickname}
        </Link>
        {badge && (
          <Badge tone={badge.tone} icon={badge.icon}>
            {badge.label}
          </Badge>
        )}
      </div>
      {profile.length > 0 && <p className="text-sm text-ink-muted">{profile.join(' · ')}</p>}
      {application.message && <p className="whitespace-pre-line break-words text-base text-ink">{application.message}</p>}
      <p className="font-mono text-sm tabular-nums text-ink-muted">{formatKstDateTime(application.appliedAt)} 신청</p>
      {recruiting && (
        <div className="mt-1 flex flex-wrap gap-2">
          <Button disabled={submitting} onClick={onApprove}>
            승인
          </Button>
          <Button variant="secondary" disabled={submitting} onClick={onReject}>
            거절
          </Button>
        </div>
      )}
    </li>
  );
}

function MembersCard({ detail, onKick }: { detail: BasecampDetail; onKick: (member: BasecampMember) => void }) {
  const canKick = detail.status === 'RECRUITING' || detail.status === 'CLOSED';
  return (
    <PageCard title="멤버">
      <ul className="flex flex-col">
        {detail.members.map((member) => {
          const badge = member.trustLevel === undefined ? null : trustLevelBadge(member.trustLevel);
          const profile = profileSummary(member);
          return (
            <li key={member.memberId} className="flex flex-col gap-1 border-t border-contour py-2 first:border-t-0 first:pt-0">
              <div className="flex flex-wrap items-center gap-2">
                <Link
                  to={`/members/${member.memberId}`}
                  className="inline-flex min-h-11 items-center text-base font-semibold text-forest underline underline-offset-2"
                >
                  {member.nickname}
                </Link>
                {member.role === 'LEADER' && <Badge tone="earth">캠프 리더</Badge>}
                {badge && (
                  <Badge tone={badge.tone} icon={badge.icon}>
                    {badge.label}
                  </Badge>
                )}
                {canKick && member.role !== 'LEADER' && (
                  <Button variant="secondary" className="ml-auto" onClick={() => onKick(member)}>
                    강퇴
                  </Button>
                )}
              </div>
              {profile.length > 0 && <p className="text-sm text-ink-muted">{profile.join(' · ')}</p>}
            </li>
          );
        })}
      </ul>
    </PageCard>
  );
}

// 연락 수단은 https 주소만 받는다. 서버도 같은 기준으로 다시 검사한다.
function validateContact(value: string): string | null {
  if (value === '') return '연락 수단을 적어 주세요.';
  if (value.length > CONTACT_MAX) return `${CONTACT_MAX}자 이하로 적어 주세요.`;
  try {
    return new URL(value).protocol === 'https:' ? null : 'https:// 로 시작하는 주소를 적어 주세요.';
  } catch {
    return 'https:// 로 시작하는 주소를 적어 주세요.';
  }
}

function ContactCard({ detail, onSaved }: { detail: BasecampDetail; onSaved: () => void }) {
  const [value, setValue] = useState(detail.contactInfo ?? '');
  const [fieldError, setFieldError] = useState<string | undefined>();
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    setFormError(null);
    const trimmed = value.trim();
    const invalid = validateContact(trimmed);
    setFieldError(invalid ?? undefined);
    if (invalid) return;

    setSubmitting(true);
    try {
      await updateBasecampContact(detail.basecampId, trimmed);
      onSaved();
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === 'INVALID_INPUT' && caught.fieldErrors.length > 0) {
        const own = caught.fieldErrors.find((item) => item.field === 'contactInfo');
        if (own) setFieldError(own.reason);
        else setFormError(caught.fieldErrors.map((item) => item.reason).join(' '));
      } else {
        setFormError(toUserMessage(caught));
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <PageCard title="연락 수단">
      <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-3">
        {formError && (
          <div role="alert">
            <Notice tone="danger" title="저장하지 못했어요">
              <p>{formError}</p>
            </Notice>
          </div>
        )}
        <TextField
          label="오픈채팅 링크 등 https 주소"
          name="contactInfo"
          type="url"
          inputMode="url"
          value={value}
          maxLength={CONTACT_MAX}
          onChange={(event) => setValue(event.target.value)}
          error={fieldError}
          hint="확정되면 멤버에게 공개돼요."
        />
        <div className="flex justify-end">
          <Button type="submit" loading={submitting}>
            저장
          </Button>
        </div>
      </form>
    </PageCard>
  );
}
