import { useCallback, useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import { createSpotReview, deleteSpotReview, fetchSpotReviews, updateSpotReview } from '../../api/reviews';
import { fetchSpotDetail } from '../../api/spots';
import type { SpotRating, SpotReview } from '../../api/types';
import { Link, useLocation } from '../../app/router';
import { Button } from '../../components/Button';
import { ChoiceGroup } from '../../components/ChoiceGroup';
import type { ChoiceOption } from '../../components/ChoiceGroup';
import { Dialog } from '../../components/Dialog';
import { Notice } from '../../components/Notice';
import { Skeleton } from '../../components/Skeleton';
import { TextArea } from '../../components/TextArea';
import { TextField } from '../../components/TextField';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatKstDateTime, formatLocalDate, todayKst } from '../../lib/datetime';
import { withNext } from '../member/nextPath';
import { useSession } from '../member/session';

// 서버 규칙과 같은 값이다. 내용은 공백뿐일 수 없고 2,000자 이하다.
const CONTENT_MAX = 2000;
const MAX_RATING = 5;

const RATING_OPTIONS: ReadonlyArray<ChoiceOption<string>> = [5, 4, 3, 2, 1].map((value) => ({
  value: String(value),
  label: `${'★'.repeat(value)}${'☆'.repeat(MAX_RATING - value)} ${value}점`,
}));

const UNVERIFIED_REASON = '이메일 인증을 마치면 쓸 수 있어요';

type SpotReviewsProps = {
  spotId: number;
  initialRating: SpotRating;
};

type ListState = {
  reviews: SpotReview[];
  page: number;
  hasNext: boolean;
};

// 장소 상세의 후기 칸. 평점 요약, 후기 목록, 작성·수정·삭제를 맡는다.
// 후기를 바꾸면 평점은 서버가 다시 계산하므로, 장소 상세를 다시 받아 평점만 바꿔 보여 준다.
export function SpotReviews({ spotId, initialRating }: SpotReviewsProps) {
  const session = useSession();
  const myMemberId = session.status === 'authenticated' ? (session.me?.memberId ?? null) : null;
  const [rating, setRating] = useState(initialRating);
  const [list, setList] = useState<ListState | null>(null);
  // 처음 목록은 effect가 받고, 더 보기·다시 불러오기는 버튼을 누른 쪽이 받는다. 이 값은 버튼으로 받는 중인지만 나타낸다.
  const [loading, setLoading] = useState(false);
  const [listError, setListError] = useState<string | null>(null);
  const [writing, setWriting] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [deleting, setDeleting] = useState<SpotReview | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const controllerRef = useRef<AbortController | null>(null);
  const showSkeleton = useDelayedFlag(list === null && listError === null);

  const loadPage = useCallback(
    async (page: number) => {
      controllerRef.current?.abort();
      const controller = new AbortController();
      controllerRef.current = controller;
      setLoading(true);
      setListError(null);
      try {
        const result = await fetchSpotReviews(spotId, page, controller.signal);
        if (controller.signal.aborted) return;
        setList((previous) => ({
          reviews: page === 0 || previous === null ? result.content : [...previous.reviews, ...result.content],
          page: result.page,
          hasNext: result.hasNext,
        }));
      } catch (error) {
        if (controller.signal.aborted) return;
        setListError(toUserMessage(error));
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    },
    [spotId],
  );

  // 컴포넌트는 장소마다 새로 만들어지므로(key) 처음 목록은 한 번만 받는다. 결과를 받았을 때만 상태를 바꾼다.
  useEffect(() => {
    const controller = new AbortController();
    fetchSpotReviews(spotId, 0, controller.signal).then(
      (result) => {
        if (!controller.signal.aborted) setList({ reviews: result.content, page: result.page, hasNext: result.hasNext });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setListError(toUserMessage(error));
      },
    );
    return () => {
      controller.abort();
      controllerRef.current?.abort();
    };
  }, [spotId]);

  // 평점을 다시 받지 못해도 후기 자체는 반영됐으므로 이전 평점을 그대로 둔다.
  async function refreshAfterChange(message: string) {
    setStatus(message);
    await loadPage(0);
    try {
      const detail = await fetchSpotDetail(spotId);
      setRating(detail.rating);
    } catch {
      // 무시한다.
    }
  }

  return (
    <section className="mt-4 border-t border-contour pt-4" aria-labelledby="spot-review-title">
      <div className="flex items-center justify-between gap-2">
        <h3 id="spot-review-title" className="text-sm font-semibold text-ink-muted">
          후기
        </h3>
        <WriteAction
          writing={writing}
          onStart={() => {
            setStatus(null);
            setEditingId(null);
            setWriting(true);
          }}
        />
      </div>
      <p className="mt-2 text-base text-ink tabular-nums">
        {rating.average === null
          ? '아직 후기가 없어요'
          : `평점 ${rating.average.toFixed(1)} · 후기 ${rating.count}개`}
      </p>

      {status && (
        <p role="status" className="mt-2 text-sm text-forest-deep">
          {status}
        </p>
      )}

      {writing && (
        <ReviewForm
          mode="create"
          onCancel={() => setWriting(false)}
          onSubmit={async (values) => {
            await createSpotReview(spotId, values);
            setWriting(false);
            await refreshAfterChange('후기를 남겼어요.');
          }}
        />
      )}

      {listError && (
        <div role="alert" className="mt-3 flex flex-col gap-2">
          <Notice tone="danger" title="후기를 불러오지 못했어요">
            <p>{listError}</p>
          </Notice>
          <div>
            <Button variant="secondary" onClick={() => void loadPage(list?.page ?? 0)}>
              다시 불러오기
            </Button>
          </div>
        </div>
      )}

      {showSkeleton && (
        <div className="mt-3 flex flex-col gap-2">
          <Skeleton className="h-5 w-1/2" />
          <Skeleton className="h-12 w-full" />
        </div>
      )}

      {list && list.reviews.length > 0 && (
        <ul className="mt-3 flex flex-col">
          {list.reviews.map((review) =>
            editingId === review.reviewId ? (
              <li key={review.reviewId} className="border-t border-contour py-3">
                <ReviewForm
                  mode="edit"
                  initial={review}
                  onCancel={() => setEditingId(null)}
                  onSubmit={async (values) => {
                    await updateSpotReview(review.reviewId, { rating: values.rating, content: values.content });
                    setEditingId(null);
                    await refreshAfterChange('후기를 고쳤어요.');
                  }}
                />
              </li>
            ) : (
              <ReviewItem
                key={review.reviewId}
                review={review}
                mine={myMemberId !== null && review.author.memberId === myMemberId}
                onEdit={() => {
                  setStatus(null);
                  setWriting(false);
                  setEditingId(review.reviewId);
                }}
                onDelete={() => setDeleting(review)}
              />
            ),
          )}
        </ul>
      )}

      {list?.hasNext && (
        <div className="mt-2">
          <Button variant="secondary" loading={loading} onClick={() => void loadPage(list.page + 1)}>
            후기 더 보기
          </Button>
        </div>
      )}

      {deleting && (
        <DeleteDialog
          review={deleting}
          onClose={() => setDeleting(null)}
          onDeleted={async () => {
            setDeleting(null);
            await refreshAfterChange('후기를 지웠어요.');
          }}
        />
      )}
    </section>
  );
}

// 비회원에게는 로그인 링크를, 이메일 미인증 회원에게는 막힌 이유를 보여 준다. 쓰기 권한은 서버가 다시 확인한다.
function WriteAction({ writing, onStart }: { writing: boolean; onStart: () => void }) {
  const session = useSession();
  const { pathname, search } = useLocation();

  if (session.status === 'loading' || writing) return null;
  if (session.status === 'anonymous' || session.me === null) {
    return (
      <Link
        to={withNext('/login', pathname + search)}
        className="inline-flex min-h-11 items-center text-sm font-semibold text-forest underline underline-offset-2"
      >
        로그인하고 후기 쓰기
      </Link>
    );
  }
  const unverified = session.me.status === 'UNVERIFIED';
  return (
    <Button
      variant="secondary"
      disabled={unverified}
      disabledReason={unverified ? UNVERIFIED_REASON : undefined}
      onClick={onStart}
    >
      후기 쓰기
    </Button>
  );
}

function Stars({ rating }: { rating: number }) {
  return (
    <span aria-label={`평점 ${rating}점`} className="text-ink">
      <span aria-hidden="true">
        {'★'.repeat(rating)}
        {'☆'.repeat(Math.max(0, MAX_RATING - rating))}
      </span>
    </span>
  );
}

function ReviewItem({
  review,
  mine,
  onEdit,
  onDelete,
}: {
  review: SpotReview;
  mine: boolean;
  onEdit: () => void;
  onDelete: () => void;
}) {
  return (
    <li className="border-t border-contour py-3">
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
        <Stars rating={review.rating} />
        <Link
          to={`/members/${review.author.memberId}`}
          className="font-semibold text-ink underline-offset-2 hover:underline"
        >
          {review.author.nickname}
        </Link>
        <span className="text-ink-muted">{formatLocalDate(review.visitedDate)} 방문</span>
      </div>
      <p className="mt-2 whitespace-pre-line break-words text-base text-ink">{review.content}</p>
      <div className="mt-1 flex flex-wrap items-center gap-2">
        <span className="text-xs text-ink-muted tabular-nums">{formatKstDateTime(review.createdAt)}</span>
        {mine && (
          <>
            <Button variant="ghost" onClick={onEdit} className="px-2">
              수정
            </Button>
            <Button variant="ghost" onClick={onDelete} className="px-2 text-danger">
              삭제
            </Button>
          </>
        )}
      </div>
    </li>
  );
}

type ReviewValues = { visitedDate: string; rating: number; content: string };
type ReviewField = 'visitedDate' | 'rating' | 'content';
type ReviewFieldErrors = Partial<Record<ReviewField, string>>;

function isReviewField(field: string): field is ReviewField {
  return field === 'visitedDate' || field === 'rating' || field === 'content';
}

// 작성과 수정이 함께 쓰는 입력 칸. 수정할 때는 서버 규칙대로 방문일을 바꿀 수 없어서 칸을 보여 주지 않는다.
function ReviewForm({
  mode,
  initial,
  onCancel,
  onSubmit,
}: {
  mode: 'create' | 'edit';
  initial?: SpotReview;
  onCancel: () => void;
  onSubmit: (values: ReviewValues) => Promise<void>;
}) {
  const today = todayKst();
  const [visitedDate, setVisitedDate] = useState(initial?.visitedDate ?? today);
  const [rating, setRating] = useState<string | null>(initial ? String(initial.rating) : null);
  const [content, setContent] = useState(initial?.content ?? '');
  const [fieldErrors, setFieldErrors] = useState<ReviewFieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function validate(): ReviewFieldErrors {
    const errors: ReviewFieldErrors = {};
    if (mode === 'create') {
      if (!/^\d{4}-\d{2}-\d{2}$/.test(visitedDate)) errors.visitedDate = '방문한 날짜를 골라 주세요.';
      else if (visitedDate > today) errors.visitedDate = '오늘 이후 날짜는 고를 수 없어요.';
    }
    if (rating === null) errors.rating = '평점을 골라 주세요.';
    if (content.trim() === '') errors.content = '후기 내용을 적어 주세요.';
    else if (content.length > CONTENT_MAX) errors.content = `${CONTENT_MAX.toLocaleString()}자 이하로 적어 주세요.`;
    return errors;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    setFormError(null);
    const errors = validate();
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0 || rating === null) return;

    setSubmitting(true);
    try {
      await onSubmit({ visitedDate, rating: Number(rating), content: content.trim() });
    } catch (error) {
      setSubmitting(false);
      handleError(error);
    }
  }

  function handleError(error: unknown) {
    if (error instanceof ApiError && error.code === 'INVALID_INPUT' && error.fieldErrors.length > 0) {
      const errors: ReviewFieldErrors = {};
      const others: string[] = [];
      for (const { field, reason } of error.fieldErrors) {
        if (isReviewField(field)) errors[field] = reason;
        else others.push(reason);
      }
      setFieldErrors(errors);
      if (others.length > 0) setFormError(others.join(' '));
      return;
    }
    if (error instanceof ApiError && error.code === 'SPOT_REVIEW_DUPLICATED') {
      setFieldErrors({ visitedDate: toUserMessage(error) });
      return;
    }
    setFormError(toUserMessage(error));
  }

  const title = mode === 'create' ? '후기 쓰기' : '후기 고치기';
  return (
    <form
      noValidate
      onSubmit={handleSubmit}
      aria-label={title}
      className="mt-3 flex flex-col gap-4 rounded-control border border-contour bg-paper p-4"
    >
      {formError && (
        <div role="alert">
          <Notice tone="danger" title={mode === 'create' ? '후기를 남기지 못했어요' : '후기를 고치지 못했어요'}>
            <p>{formError}</p>
          </Notice>
        </div>
      )}
      {mode === 'create' ? (
        <TextField
          label="방문한 날"
          name="visitedDate"
          type="date"
          max={today}
          value={visitedDate}
          onChange={(event) => setVisitedDate(event.target.value)}
          error={fieldErrors.visitedDate}
          hint="같은 장소에 같은 날 다녀온 후기는 하나만 쓸 수 있어요."
        />
      ) : (
        initial && <p className="text-sm text-ink-muted">{formatLocalDate(initial.visitedDate)} 방문 후기</p>
      )}
      <ChoiceGroup
        legend="평점"
        name={`rating-${initial?.reviewId ?? 'new'}`}
        options={RATING_OPTIONS}
        value={rating}
        onChange={setRating}
        error={fieldErrors.rating}
      />
      <TextArea
        label="후기"
        name="content"
        maxLength={CONTENT_MAX}
        value={content}
        onChange={(event) => setContent(event.target.value)}
        error={fieldErrors.content}
        hint="다음에 갈 사람에게 도움이 되는 내용을 적어 주세요. 흔적 남기지 않기도 함께 지켜 주세요."
      />
      <div className="flex flex-wrap justify-end gap-2">
        <Button variant="secondary" onClick={onCancel} disabled={submitting}>
          취소
        </Button>
        <Button type="submit" loading={submitting}>
          {mode === 'create' ? '후기 남기기' : '고치기'}
        </Button>
      </div>
    </form>
  );
}

function DeleteDialog({
  review,
  onClose,
  onDeleted,
}: {
  review: SpotReview;
  onClose: () => void;
  onDeleted: () => Promise<void>;
}) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleDelete() {
    if (submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      await deleteSpotReview(review.reviewId);
      await onDeleted();
    } catch (cause) {
      setSubmitting(false);
      setError(toUserMessage(cause));
    }
  }

  return (
    <Dialog title="후기를 지울까요?" onClose={onClose}>
      <div className="flex flex-col gap-4">
        <p className="text-base text-ink">
          {formatLocalDate(review.visitedDate)} 방문 후기를 지워요. 지운 후기는 되돌릴 수 없어요.
        </p>
        {error && (
          <div role="alert">
            <Notice tone="danger" title="지우지 못했어요">
              <p>{error}</p>
            </Notice>
          </div>
        )}
        <div className="flex flex-wrap justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={submitting}>
            취소
          </Button>
          <Button variant="danger" loading={submitting} onClick={() => void handleDelete()}>
            지우기
          </Button>
        </div>
      </div>
    </Dialog>
  );
}
