import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import {
  createCompanionReview,
  fetchPendingCompanionReviews,
  fetchReceivedCompanionReviews,
} from '../../api/companionReviews';
import type {
  PendingCompanionReview,
  PendingCompanionTarget,
  ReceivedCompanionReview,
  RevealedReceivedCompanionReview,
} from '../../api/types';
import { Link, navigate, useLocation } from '../../app/router';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Checkbox } from '../../components/Checkbox';
import { ChoiceGroup } from '../../components/ChoiceGroup';
import type { ChoiceOption } from '../../components/ChoiceGroup';
import { Dialog } from '../../components/Dialog';
import { EmptyState } from '../../components/EmptyState';
import { Notice } from '../../components/Notice';
import { PageCard, PageLayout } from '../../components/PageLayout';
import { Skeleton } from '../../components/Skeleton';
import { TextArea } from '../../components/TextArea';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { daysUntilKst, formatKstDateTime } from '../../lib/datetime';
import { companionTagLabel, NEGATIVE_COMPANION_TAGS, POSITIVE_COMPANION_TAGS } from '../member/memberLabels';
import { withNext } from '../member/nextPath';
import { useSession } from '../member/session';
import { MemberReportDialog } from '../report/MemberReportDialog';

const COMMENT_MAX = 300;

type Tab = 'pending' | 'received';

const REJOIN_OPTIONS: ReadonlyArray<ChoiceOption<'YES' | 'NO'>> = [
  { value: 'YES', label: '예' },
  { value: 'NO', label: '아니오' },
];

const LINK_BUTTON_CLASS =
  'inline-flex min-h-11 items-center justify-center rounded-control border border-ink-subtle bg-card px-4 text-base font-semibold text-ink hover:bg-paper-deep';

function tabFromSearch(search: string): Tab {
  return new URLSearchParams(search).get('tab') === 'received' ? 'received' : 'pending';
}

export function CompanionReviewsPage() {
  const session = useSession();
  const { pathname, search } = useLocation();
  const tab = tabFromSearch(search);
  const showSkeleton = useDelayedFlag(session.status === 'loading');

  useEffect(() => {
    document.title = '동행 후기 · 피치맵';
  }, []);

  // 동행 후기는 로그인한 세션으로만 볼 수 있다. 로그인한 뒤 이 화면으로 돌아오게 한다.
  useEffect(() => {
    if (session.status === 'anonymous') navigate(withNext('/login', pathname + search), { replace: true });
  }, [session.status, pathname, search]);

  if (session.status !== 'authenticated' || !session.me) {
    return (
      <PageLayout title="동행 후기">
        {showSkeleton ? <Skeleton className="h-40 w-full" /> : null}
      </PageLayout>
    );
  }

  return (
    <PageLayout title="동행 후기">
      <div role="tablist" aria-label="동행 후기 종류" className="flex overflow-hidden rounded-control border border-contour">
        <TabButton active={tab === 'pending'} id="pending" to={pathname}>
          작성할 후기
        </TabButton>
        <TabButton active={tab === 'received'} id="received" to={`${pathname}?tab=received`}>
          받은 후기
        </TabButton>
      </div>
      <div role="tabpanel" aria-labelledby={`tab-${tab}`} className="flex flex-col gap-4">
        {tab === 'pending' ? <PendingTab trustLevel={session.me.trustLevel} /> : <ReceivedTab />}
      </div>
    </PageLayout>
  );
}

function TabButton({ active, id, to, children }: { active: boolean; id: Tab; to: string; children: ReactNode }) {
  const base = 'flex min-h-11 flex-1 items-center justify-center px-3 text-base font-semibold';
  return (
    <button
      type="button"
      role="tab"
      id={`tab-${id}`}
      aria-selected={active}
      onClick={() => navigate(to, { replace: true })}
      className={active ? `${base} bg-forest-soft text-forest-deep` : `${base} bg-card text-ink hover:bg-paper-deep`}
    >
      {children}
    </button>
  );
}

type PendingResult = { attempt: number; items: PendingCompanionReview[] } | { attempt: number; error: string };

function PendingTab({ trustLevel }: { trustLevel: number }) {
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<PendingResult | null>(null);
  const [writing, setWriting] = useState<{ review: PendingCompanionReview; target: PendingCompanionTarget } | null>(null);
  const [done, setDone] = useState<string | null>(null);
  const eligible = trustLevel >= 1;

  useEffect(() => {
    if (!eligible) return;
    const controller = new AbortController();
    fetchPendingCompanionReviews(controller.signal).then(
      (items) => {
        if (!controller.signal.aborted) setResult({ attempt, items });
      },
      (caught: unknown) => {
        if (!controller.signal.aborted) setResult({ attempt, error: toUserMessage(caught) });
      },
    );
    return () => controller.abort();
  }, [eligible, attempt]);

  const current = result?.attempt === attempt ? result : null;
  const showSkeleton = useDelayedFlag(eligible && current === null);

  if (!eligible) {
    return (
      <Notice tone="info" title="본인확인을 하면 동행 후기를 쓸 수 있어요">
        <p>동행 후기는 본인확인을 마친 회원만 쓸 수 있어요.</p>
        <Link to="/identity-verification" className="mt-1 inline-flex min-h-11 items-center font-semibold text-forest">
          본인확인 하기
        </Link>
      </Notice>
    );
  }
  if (current === null) return showSkeleton ? <Skeleton className="h-40 w-full" /> : null;
  if ('error' in current) {
    return (
      <div className="flex flex-col gap-3">
        <Notice tone="danger" title="작성할 후기를 불러오지 못했어요">
          <p>{current.error}</p>
        </Notice>
        <div>
          <Button variant="secondary" onClick={() => setAttempt((value) => value + 1)}>
            다시 불러오기
          </Button>
        </div>
      </div>
    );
  }

  return (
    <>
      {done && (
        <p role="status" className="text-sm font-semibold text-forest-deep">
          {done}
        </p>
      )}
      {current.items.length === 0 ? (
        <EmptyState
          title="작성할 후기가 없어요"
          description="함께 다녀온 베이스캠프가 완료되면 14일 안에 동행 후기를 쓸 수 있어요."
        />
      ) : (
        current.items.map((review) => (
          <PageCard key={review.basecampId} title={review.basecampTitle}>
            <p className="font-mono text-sm tabular-nums text-ink-muted">
              {formatKstDateTime(review.completedAt)} 완료 · {deadlineText(review.deadline)}
            </p>
            <ul className="flex flex-col">
              {review.targets.map((target) => (
                <li
                  key={target.memberId}
                  className="flex items-center justify-between gap-2 border-t border-contour py-1 first:border-t-0"
                >
                  <Link
                    to={`/members/${target.memberId}`}
                    className="inline-flex min-h-11 items-center text-base font-semibold text-forest underline underline-offset-2"
                  >
                    {target.nickname}
                  </Link>
                  <Button variant="secondary" onClick={() => setWriting({ review, target })}>
                    후기 쓰기
                  </Button>
                </li>
              ))}
            </ul>
          </PageCard>
        ))
      )}
      {writing && (
        <WriteDialog
          review={writing.review}
          target={writing.target}
          onClose={() => setWriting(null)}
          onSubmitted={() => {
            setDone(`${writing.target.nickname}님에게 동행 후기를 남겼어요.`);
            setWriting(null);
            setAttempt((value) => value + 1);
          }}
        />
      )}
    </>
  );
}

function deadlineText(deadline: string): string {
  const days = daysUntilKst(deadline);
  if (days < 0) return '기한이 지났어요';
  return days === 0 ? '오늘까지 작성' : `D-${days}`;
}

function WriteDialog({
  review,
  target,
  onClose,
  onSubmitted,
}: {
  review: PendingCompanionReview;
  target: PendingCompanionTarget;
  onClose: () => void;
  onSubmitted: () => void;
}) {
  const [rejoin, setRejoin] = useState<'YES' | 'NO' | null>(null);
  const [tags, setTags] = useState<string[]>([]);
  const [comment, setComment] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [commentError, setCommentError] = useState<string | undefined>();

  function toggleTag(tag: string, checked: boolean) {
    setTags((previous) => (checked ? [...previous, tag] : previous.filter((item) => item !== tag)));
  }

  async function submit() {
    if (rejoin === null || submitting) return;
    setSubmitting(true);
    setError(null);
    setCommentError(undefined);
    try {
      await createCompanionReview(review.basecampId, {
        revieweeId: target.memberId,
        rejoinWanted: rejoin === 'YES',
        tags,
        comment: comment.trim() || undefined,
      });
      onSubmitted();
    } catch (caught) {
      const own = caught instanceof ApiError ? caught.fieldErrors.find((item) => item.field === 'comment') : undefined;
      if (own) setCommentError(own.reason);
      else setError(toUserMessage(caught));
      setSubmitting(false);
    }
  }

  return (
    <Dialog title={`${target.nickname}님 동행 후기`} onClose={() => !submitting && onClose()}>
      <div className="flex flex-col gap-4">
        <p className="text-sm text-ink-muted">{review.basecampTitle}</p>
        {error && (
          <div role="alert">
            <Notice tone="danger" title="후기를 남기지 못했어요">
              <p>{error}</p>
            </Notice>
          </div>
        )}
        <ChoiceGroup
          legend="다시 동행하고 싶어요?"
          name="rejoinWanted"
          options={REJOIN_OPTIONS}
          value={rejoin}
          onChange={setRejoin}
          disabled={submitting}
        />
        <fieldset className="flex min-w-0 flex-col gap-1">
          <legend className="mb-1 text-sm font-medium text-ink">함께 다녀온 느낌 (선택)</legend>
          {[...POSITIVE_COMPANION_TAGS, ...NEGATIVE_COMPANION_TAGS].map((tag) => (
            <Checkbox
              key={tag}
              label={companionTagLabel(tag)}
              checked={tags.includes(tag)}
              disabled={submitting}
              onChange={(event) => toggleTag(tag, event.target.checked)}
            />
          ))}
        </fieldset>
        <TextArea
          label="한마디 (선택)"
          name="comment"
          maxLength={COMMENT_MAX}
          value={comment}
          disabled={submitting}
          error={commentError}
          onChange={(event) => setComment(event.target.value)}
        />
        <Notice tone="info" title="작성한 뒤에는 고치거나 지울 수 없어요" />
        <div className="flex flex-wrap items-start justify-end gap-2">
          <Button variant="secondary" disabled={submitting} onClick={onClose}>
            닫기
          </Button>
          <Button
            loading={submitting}
            disabled={rejoin === null}
            disabledReason={rejoin === null ? '다시 동행하고 싶은지 골라 주세요.' : undefined}
            onClick={() => void submit()}
          >
            후기 남기기
          </Button>
        </div>
      </div>
    </Dialog>
  );
}

type ReceivedState = {
  items: ReceivedCompanionReview[];
  page: number;
  hasNext: boolean;
  error: string | null;
};

function ReceivedTab() {
  const [state, setState] = useState<ReceivedState | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const [reportTarget, setReportTarget] = useState<RevealedReceivedCompanionReview | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    fetchReceivedCompanionReviews(0, controller.signal).then(
      (page) => {
        if (!controller.signal.aborted) setState({ items: page.content, page: page.page, hasNext: page.hasNext, error: null });
      },
      (caught: unknown) => {
        if (!controller.signal.aborted) setState({ items: [], page: 0, hasNext: false, error: toUserMessage(caught) });
      },
    );
    return () => controller.abort();
  }, [attempt]);

  const showSkeleton = useDelayedFlag(state === null);

  async function loadMore() {
    if (!state || loadingMore) return;
    setLoadingMore(true);
    try {
      const next = await fetchReceivedCompanionReviews(state.page + 1);
      setState((previous) =>
        previous ? { items: [...previous.items, ...next.content], page: next.page, hasNext: next.hasNext, error: null } : previous,
      );
    } catch (caught) {
      setState((previous) => (previous ? { ...previous, error: toUserMessage(caught) } : previous));
    } finally {
      setLoadingMore(false);
    }
  }

  if (state === null) return showSkeleton ? <Skeleton className="h-40 w-full" /> : null;

  return (
    <>
      {state.error && (
        <div className="flex flex-col gap-3">
          <Notice tone="danger" title="받은 후기를 불러오지 못했어요">
            <p>{state.error}</p>
          </Notice>
          {state.items.length === 0 && (
            <div>
              <Button variant="secondary" onClick={() => setAttempt((value) => value + 1)}>
                다시 불러오기
              </Button>
            </div>
          )}
        </div>
      )}
      {state.items.length === 0 && !state.error ? (
        <EmptyState title="받은 후기가 없어요" description="함께 다녀온 멤버가 후기를 남기면 여기에 보여요." />
      ) : (
        state.items.map((item, index) =>
          item.revealed ? (
            <PageCard key={item.reviewId} title={item.basecampTitle}>
              <div className="flex flex-wrap items-center gap-2">
                <Link
                  to={`/members/${item.reviewer.memberId}`}
                  className="inline-flex min-h-11 items-center text-base font-semibold text-forest underline underline-offset-2"
                >
                  {item.reviewer.nickname}
                </Link>
                <Badge tone={item.rejoinWanted ? 'forest' : 'neutral'}>
                  {item.rejoinWanted ? '다시 동행하고 싶어요' : '다시 동행은 아쉬워요'}
                </Badge>
              </div>
              {item.tags.length > 0 && (
                <div className="flex flex-wrap gap-1">
                  {item.tags.map((tag) => (
                    <Badge key={tag} tone="neutral">
                      {companionTagLabel(tag)}
                    </Badge>
                  ))}
                </div>
              )}
              {item.comment && <p className="whitespace-pre-line break-words text-base text-ink">{item.comment}</p>}
              <p className="font-mono text-sm tabular-nums text-ink-muted">{formatKstDateTime(item.createdAt)}</p>
              <div>
                <Button variant="ghost" onClick={() => setReportTarget(item)}>
                  후기 신고
                </Button>
              </div>
            </PageCard>
          ) : (
            <section
              // 공개 전 후기는 ID가 없고, 한 베이스캠프에서 여러 개가 올 수 있어서 순서로 구분한다.
              key={`sealed-${item.basecampId}-${index}`}
              className="flex flex-col gap-2 rounded-control border border-contour bg-card p-5"
            >
              <p className="text-base text-ink">내 후기를 쓰면 상대 후기를 볼 수 있어요.</p>
              <Link to={`/basecamps/${item.basecampId}`} className={`${LINK_BUTTON_CLASS} self-start`}>
                베이스캠프 보기
              </Link>
            </section>
          ),
        )
      )}
      {state.hasNext && (
        <div>
          <Button variant="secondary" loading={loadingMore} onClick={() => void loadMore()}>
            더 보기
          </Button>
        </div>
      )}
      {reportTarget && (
        <MemberReportDialog
          kind="REVIEW"
          targetMemberId={reportTarget.reviewer.memberId}
          targetNickname={reportTarget.reviewer.nickname}
          basecampId={reportTarget.basecampId}
          companionReviewId={reportTarget.reviewId}
          onClose={() => setReportTarget(null)}
        />
      )}
    </>
  );
}
