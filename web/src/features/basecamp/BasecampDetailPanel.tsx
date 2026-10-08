import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { applyToBasecamp, cancelMyApplication, fetchBasecampDetail } from '../../api/basecamps';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import type { BasecampDetail, BasecampMember, JoinUnmetReason } from '../../api/types';
import { Link, useLocation } from '../../app/router';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Dialog } from '../../components/Dialog';
import { EmptyState } from '../../components/EmptyState';
import { Evidence } from '../../components/Evidence';
import { Icon } from '../../components/icons';
import { Notice } from '../../components/Notice';
import { Skeleton } from '../../components/Skeleton';
import { TextArea } from '../../components/TextArea';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatLocalDate } from '../../lib/datetime';
import { AGE_GROUP_LABELS, GENDER_LABELS, trustLevelBadge } from '../member/memberLabels';
import { withNext } from '../member/nextPath';
import { useSession } from '../member/session';
import {
  BASECAMP_STATUS_GUIDE,
  BASECAMP_STATUS_META,
  headcountText,
  joinConditionSummary,
  unmetReasonText,
} from './basecampLabels';

type BasecampDetailPanelProps = {
  basecampId: number;
  onBack: () => void;
};

type DetailResult = { basecampId: number; detail: BasecampDetail } | { basecampId: number; error: unknown };

const SECTION_CLASS = 'mt-4 border-t border-contour pt-4';
const SECTION_TITLE_CLASS = 'text-sm font-semibold text-ink-muted';
const MESSAGE_MAX = 500;

const CONFIRMED_SAFETY_RULES = [
  '출발 전에 일정을 가족이나 지인에게 알려 주세요.',
  '단독 행동은 되도록 하지 마세요.',
  '야영한 자리에는 흔적을 남기지 마세요.',
];

export function BasecampDetailPanel({ basecampId, onBack }: BasecampDetailPanelProps) {
  const session = useSession();
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<DetailResult | null>(null);
  const viewerKey = session.me?.memberId ?? null;

  // 로그인 여부에 따라 서버가 주는 필드가 달라서 세션이 바뀌면 다시 받는다.
  // 다시 받는 동안에도 이전 내용을 그대로 보여 주고, 응답이 오면 바꾼다.
  useEffect(() => {
    if (session.status === 'loading') return;
    const controller = new AbortController();
    fetchBasecampDetail(basecampId, controller.signal).then(
      (detail) => {
        if (!controller.signal.aborted) setResult({ basecampId, detail });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ basecampId, error });
      },
    );
    return () => controller.abort();
  }, [basecampId, attempt, session.status, viewerKey]);

  const current = result && result.basecampId === basecampId ? result : null;
  const showSkeleton = useDelayedFlag(current === null);
  const loadedTitle = current && 'detail' in current ? current.detail.title : null;

  useEffect(() => {
    if (loadedTitle === null) return;
    document.title = `${loadedTitle} · 피치맵`;
    return () => {
      document.title = '피치맵';
    };
  }, [loadedTitle]);

  const reload = () => setAttempt((value) => value + 1);

  let body: ReactNode;
  if (current === null) {
    body = showSkeleton ? <DetailSkeleton /> : null;
  } else if ('detail' in current) {
    body = <DetailContent detail={current.detail} onChanged={reload} />;
  } else if (current.error instanceof ApiError && current.error.code === 'NOT_FOUND') {
    body = (
      <div className="px-4 py-4">
        <EmptyState
          title="베이스캠프를 찾을 수 없어요"
          description="삭제됐거나 없는 베이스캠프예요."
          action={
            <Link
              to="/basecamps"
              className="inline-flex min-h-11 items-center rounded-control border border-ink-subtle bg-card px-4 font-semibold text-ink hover:bg-paper-deep"
            >
              목록으로
            </Link>
          }
        />
      </div>
    );
  } else {
    body = (
      <div className="flex flex-col gap-3 px-4 py-4">
        <Notice tone="danger" title="베이스캠프를 불러오지 못했어요">
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
    <div className="flex min-h-full flex-col">
      <div className="sticky top-0 z-10 flex items-center border-b border-contour bg-card px-2">
        <button
          type="button"
          aria-label="목록으로"
          onClick={onBack}
          className="flex size-11 items-center justify-center rounded-control text-ink hover:bg-paper-deep"
        >
          <Icon name="chevronLeft" size={24} />
        </button>
      </div>
      <div aria-busy={current === null} className="flex flex-1 flex-col">
        {body}
      </div>
    </div>
  );
}

function DetailSkeleton() {
  return (
    <div className="flex flex-col gap-3 px-4 py-4">
      <Skeleton className="h-7 w-2/3" />
      <Skeleton className="h-5 w-1/3" />
      <Skeleton className="h-24 w-full" />
      <Skeleton className="h-16 w-full" />
    </div>
  );
}

function DetailContent({ detail, onChanged }: { detail: BasecampDetail; onChanged: () => void }) {
  const status = BASECAMP_STATUS_META[detail.status];
  const isMember = detail.myRelation === 'MEMBER' || detail.myRelation === 'LEADER';

  return (
    <>
      <article className="px-4 py-4">
        <header className="flex flex-col gap-2">
          <h2 className="font-serif text-xl font-semibold text-ink">{detail.title}</h2>
          <div className="flex flex-wrap gap-1">
            <Badge tone={status.tone}>{status.label}</Badge>
          </div>
          <Link
            to={`/spots/${detail.spot.spotId}`}
            className="inline-flex min-h-11 items-center gap-1 self-start text-forest underline underline-offset-2"
          >
            <Icon name="backpack" size={18} className="shrink-0" />
            {detail.spot.name}
          </Link>
        </header>

        <div className="mt-2">
          <Evidence
            items={[
              {
                label: '일정',
                value: `${formatLocalDate(detail.startDate)} ~ ${formatLocalDate(detail.endDate)}`,
              },
              { label: '인원', value: `${headcountText(detail.headcount, detail.capacity)}명` },
              { label: '합류 조건', value: joinConditionSummary(detail.joinCondition) },
            ]}
          />
        </div>

        <p className="mt-3 whitespace-pre-line text-base text-ink">{detail.description}</p>

        <section className={SECTION_CLASS} aria-labelledby="basecamp-members-title">
          <h3 id="basecamp-members-title" className={SECTION_TITLE_CLASS}>
            멤버
          </h3>
          <ul className="mt-2 flex flex-col">
            {detail.members.map((member) => (
              <MemberRow key={member.memberId} member={member} />
            ))}
          </ul>
          {detail.leader.trustLevel === undefined && (
            <p className="mt-2 text-sm text-ink-muted">로그인하면 멤버의 신뢰 단계와 동행 기록을 볼 수 있어요.</p>
          )}
        </section>

        <section className={SECTION_CLASS} aria-labelledby="basecamp-contact-title">
          <h3 id="basecamp-contact-title" className={SECTION_TITLE_CLASS}>
            연락 수단
          </h3>
          <ContactInfo contactInfo={detail.contactInfo} isMember={isMember} />
        </section>

        <div className={`${SECTION_CLASS} flex flex-col gap-3`}>
          <Notice tone="info" title="안전 안내">
            <p>{detail.safetyNotice}</p>
          </Notice>
          {detail.status === 'CONFIRMED' && (
            <Notice tone="info" title="출발 전 안전 수칙">
              <ul className="list-disc pl-4">
                {CONFIRMED_SAFETY_RULES.map((rule) => (
                  <li key={rule}>{rule}</li>
                ))}
              </ul>
            </Notice>
          )}
        </div>
      </article>

      <ActionArea detail={detail} onChanged={onChanged} />
    </>
  );
}

function MemberRow({ member }: { member: BasecampMember }) {
  const badge = member.trustLevel === undefined ? null : trustLevelBadge(member.trustLevel);
  const profile: string[] = [];
  if (member.ageGroup) {
    profile.push(`${AGE_GROUP_LABELS[member.ageGroup]} ${member.ageGroupVerified ? '본인확인' : '자기 신고'}`);
  }
  if (member.gender) {
    profile.push(`${GENDER_LABELS[member.gender]} ${member.genderVerified ? '본인확인' : '자기 신고'}`);
  }
  if (member.completedCompanions !== undefined) {
    profile.push(`완료한 동행 ${member.completedCompanions}회`);
  }

  return (
    <li className="flex flex-col gap-1 border-t border-contour py-2 first:border-t-0">
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
      </div>
      {profile.length > 0 && <p className="text-sm text-ink-muted">{profile.join(' · ')}</p>}
    </li>
  );
}

// 연락 수단은 https 주소만 링크로 연다. 그 밖의 값은 글자로만 보여 준다.
function parseHttpsUrl(value: string): URL | null {
  try {
    const url = new URL(value);
    return url.protocol === 'https:' ? url : null;
  } catch {
    return null;
  }
}

function ContactInfo({ contactInfo, isMember }: { contactInfo: string | undefined; isMember: boolean }) {
  if (contactInfo === undefined) {
    return (
      <p className="mt-2 text-sm text-ink-muted">
        {isMember ? '확정되면 연락 수단이 공개돼요.' : '연락 수단은 확정된 멤버에게만 공개돼요.'}
      </p>
    );
  }
  const url = parseHttpsUrl(contactInfo);
  if (url === null) {
    return <p className="mt-2 break-words text-base text-ink">{contactInfo}</p>;
  }
  return (
    <a
      href={url.href}
      target="_blank"
      rel="noopener noreferrer"
      className="mt-1 inline-flex min-h-11 items-center break-all text-forest underline underline-offset-2"
    >
      {url.href}
    </a>
  );
}

function UnmetReasonList({ reasons, trustLevel }: { reasons: JoinUnmetReason[]; trustLevel: number | null }) {
  if (reasons.length === 0) return null;
  return (
    <ul className="flex flex-col gap-1 text-sm text-ink">
      {reasons.map((reason) => {
        const text = unmetReasonText(reason, trustLevel);
        return (
          <li key={reason}>
            <span>{text.reason}</span>
            {text.guide && <span className="text-ink-muted"> {text.guide}</span>}
            {reason === 'TRUST_LEVEL' && trustLevel === 0 && (
              <Link
                to="/identity-verification"
                className="ml-2 inline-flex min-h-11 items-center font-semibold text-forest underline underline-offset-2"
              >
                본인확인하기
              </Link>
            )}
          </li>
        );
      })}
    </ul>
  );
}

type Pending = 'apply' | 'cancel' | null;

// 시트 아래에 붙어서 스크롤해도 보이는 행동 영역이다.
function ActionArea({ detail, onChanged }: { detail: BasecampDetail; onChanged: () => void }) {
  const session = useSession();
  const { pathname, search } = useLocation();
  const [dialog, setDialog] = useState<Pending>(null);
  const [message, setMessage] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);

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
      // 성공이든 실패든 서버의 지금 상태로 화면을 맞춘다. 실패는 대개 다른 곳에서 상태가 바뀌어서 생기기 때문이다.
      onChanged();
    }
  }

  let content: ReactNode;
  if (session.status === 'loading') {
    content = null;
  } else if (session.status === 'anonymous' || session.me === null) {
    content = (
      <>
        <p className="text-sm text-ink-muted">로그인하면 합류 조건을 채웠는지 확인하고 신청할 수 있어요.</p>
        <Link
          to={withNext('/login', pathname + search)}
          className="inline-flex min-h-11 items-center justify-center rounded-control border border-forest bg-forest px-4 text-base font-semibold text-white hover:border-forest-strong hover:bg-forest-strong"
        >
          로그인
        </Link>
      </>
    );
  } else if (session.me.status === 'UNVERIFIED') {
    content = (
      <>
        <p className="text-sm text-ink-muted">이메일 인증을 마치면 합류를 신청할 수 있어요.</p>
        <Link
          to="/verify-email"
          className="inline-flex min-h-11 items-center justify-center rounded-control border border-forest bg-forest px-4 text-base font-semibold text-white hover:border-forest-strong hover:bg-forest-strong"
        >
          이메일 인증하러 가기
        </Link>
      </>
    );
  } else if (detail.myRelation === 'LEADER') {
    content = <p className="text-base text-ink">내가 연 베이스캠프예요.</p>;
  } else if (detail.myRelation === 'MEMBER') {
    content = <p className="text-base text-ink">멤버로 합류했어요.</p>;
  } else if (detail.myRelation === 'APPLICANT') {
    content = (
      <>
        <p className="text-base text-ink">합류 신청을 보냈어요. 캠프 리더가 확인할 때까지 기다려 주세요.</p>
        <Button variant="secondary" onClick={() => setDialog('cancel')}>
          신청 취소
        </Button>
      </>
    );
  } else if (detail.status !== 'RECRUITING') {
    content = <p className="text-base text-ink">{BASECAMP_STATUS_GUIDE[detail.status]}</p>;
  } else if (detail.canApply === true) {
    content = (
      <Button fullWidth onClick={() => setDialog('apply')}>
        합류 신청
      </Button>
    );
  } else {
    content = (
      <>
        <Button fullWidth disabled disabledReason="합류 조건을 채우지 못해서 신청할 수 없어요.">
          합류 신청
        </Button>
        <UnmetReasonList reasons={detail.unmetReasons ?? []} trustLevel={session.me.trustLevel} />
      </>
    );
  }

  if (content === null && !error && !done) return null;

  return (
    <div className="sticky bottom-0 z-10 mt-auto flex flex-col gap-2 border-t border-contour bg-card px-4 py-3">
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
      {content}

      {dialog === 'apply' && (
        <Dialog title="합류 신청" onClose={() => !submitting && setDialog(null)}>
          <div className="flex flex-col gap-4">
            <TextArea
              label="캠프 리더에게 한마디 (선택)"
              name="message"
              maxLength={MESSAGE_MAX}
              value={message}
              onChange={(event) => setMessage(event.target.value)}
            />
            <div className="flex flex-wrap justify-end gap-2">
              <Button variant="secondary" disabled={submitting} onClick={() => setDialog(null)}>
                닫기
              </Button>
              <Button
                loading={submitting}
                onClick={() =>
                  run(async () => {
                    await applyToBasecamp(detail.basecampId, message.trim() || undefined);
                    setMessage('');
                  }, '합류 신청을 보냈어요.')
                }
              >
                신청하기
              </Button>
            </div>
          </div>
        </Dialog>
      )}
      {dialog === 'cancel' && (
        <Dialog title="신청 취소" onClose={() => !submitting && setDialog(null)}>
          <div className="flex flex-col gap-4">
            <p className="text-base text-ink">합류 신청을 취소할까요? 취소해도 나중에 다시 신청할 수 있어요.</p>
            <div className="flex flex-wrap justify-end gap-2">
              <Button variant="secondary" disabled={submitting} onClick={() => setDialog(null)}>
                돌아가기
              </Button>
              <Button
                variant="danger"
                loading={submitting}
                onClick={() => run(() => cancelMyApplication(detail.basecampId), '합류 신청을 취소했어요.')}
              >
                신청 취소
              </Button>
            </div>
          </div>
        </Dialog>
      )}
    </div>
  );
}
