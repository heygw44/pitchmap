import { useCallback, useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import {
  createComment,
  deleteComment,
  listComments,
  updateComment,
} from '../../api/community';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import type { CommunityComment } from '../../api/types';
import { Link, useLocation } from '../../app/router';
import { Button } from '../../components/Button';
import { Dialog } from '../../components/Dialog';
import { Notice } from '../../components/Notice';
import { Skeleton } from '../../components/Skeleton';
import { TextArea } from '../../components/TextArea';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatKstDateTime, formatRelativeKst } from '../../lib/datetime';
import { withNext } from '../member/nextPath';
import { useSession } from '../member/session';
import { CommunityReportDialog } from './CommunityReportDialog';
import type { CommunityReportTarget } from './CommunityReportDialog';

// 서버 규칙과 같은 값이다. 댓글은 공백뿐일 수 없고 1,000자 이하다.
const CONTENT_MAX = 1000;
const UNVERIFIED_REASON = '이메일 인증을 마치면 쓸 수 있어요';
const UNVERIFIED_REPORT_REASON = '이메일 인증을 마치면 신고할 수 있어요';

type ListState = {
  comments: CommunityComment[];
  page: number;
  hasNext: boolean;
};

type CommunityCommentsProps = {
  postId: number;
  // 머리에 보여 줄 댓글 수. 글을 다시 받으면 바뀐다.
  count: number;
  // 댓글을 쓰거나 고치거나 지운 뒤 부른다. 부모가 글의 댓글 수를 다시 받는다.
  onChanged: () => void;
};

// 글 상세의 댓글 칸. 댓글 목록과 작성·답글·수정·삭제·신고를 맡는다.
export function CommunityComments({ postId, count, onChanged }: CommunityCommentsProps) {
  const session = useSession();
  const myMemberId = session.status === 'authenticated' ? (session.me?.memberId ?? null) : null;
  const [list, setList] = useState<ListState | null>(null);
  const [loading, setLoading] = useState(false);
  const [listError, setListError] = useState<string | null>(null);
  const [replyingId, setReplyingId] = useState<number | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [deleting, setDeleting] = useState<number | null>(null);
  const [reporting, setReporting] = useState<CommunityReportTarget | null>(null);
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
        const result = await listComments(postId, page, controller.signal);
        if (controller.signal.aborted) return;
        setList((previous) => ({
          comments: page === 0 || previous === null ? result.content : [...previous.comments, ...result.content],
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
    [postId],
  );

  // 처음 목록은 한 번만 받는다. 결과를 받았을 때만 상태를 바꾼다.
  useEffect(() => {
    const controller = new AbortController();
    listComments(postId, 0, controller.signal).then(
      (result) => {
        if (!controller.signal.aborted) setList({ comments: result.content, page: result.page, hasNext: result.hasNext });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setListError(toUserMessage(error));
      },
    );
    return () => {
      controller.abort();
      controllerRef.current?.abort();
    };
  }, [postId]);

  async function refreshAfterChange(message: string) {
    setStatus(message);
    setReplyingId(null);
    setEditingId(null);
    onChanged();
    await loadPage(0);
  }

  return (
    <section
      id="comments"
      aria-labelledby="community-comments-title"
      className="scroll-mt-20 rounded-control border border-contour bg-card"
    >
      <h2
        id="community-comments-title"
        className="border-b border-contour px-4 py-3 font-serif text-base font-semibold text-ink"
      >
        댓글 <span className="font-mono tabular-nums">{count}</span>
      </h2>

      <div className="border-b border-contour px-4 py-3">
        <NewCommentArea
          onSubmit={async (content) => {
            await createComment(postId, { content });
            await refreshAfterChange('댓글을 남겼어요.');
          }}
        />
        {status && (
          <p role="status" className="mt-2 text-sm text-forest-deep">
            {status}
          </p>
        )}
      </div>

      {listError && (
        <div role="alert" className="flex flex-col gap-2 px-4 py-3">
          <Notice tone="danger" title="댓글을 불러오지 못했어요">
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
        <div className="flex flex-col gap-2 px-4 py-3">
          <Skeleton className="h-5 w-1/2" />
          <Skeleton className="h-12 w-full" />
        </div>
      )}

      {list && list.comments.length === 0 && !listError && (
        <p className="px-4 py-3 text-base text-ink-muted">아직 댓글이 없어요. 첫 댓글을 남겨 보세요.</p>
      )}

      {list && (list.comments.length > 0 || list.hasNext) && (
        <ul className="divide-y divide-contour">
          {list.comments.map((comment) => (
            <li key={comment.commentId} className="px-4 py-3">
              <CommentItem
                comment={comment}
                myMemberId={myMemberId}
                editingId={editingId}
                onEdit={(id) => {
                  setStatus(null);
                  setReplyingId(null);
                  setEditingId(id);
                }}
                onCancelEdit={() => setEditingId(null)}
                onSaveEdit={async (id, content) => {
                  await updateComment(id, { content });
                  await refreshAfterChange('댓글을 고쳤어요.');
                }}
                onDelete={setDeleting}
                onReport={(id) => setReporting({ type: 'comment', id })}
                topLevel
                replying={replyingId === comment.commentId}
                onReply={() => {
                  setStatus(null);
                  setEditingId(null);
                  setReplyingId(comment.commentId);
                }}
                onCancelReply={() => setReplyingId(null)}
                onSubmitReply={async (content) => {
                  await createComment(postId, { content, parentId: comment.commentId });
                  await refreshAfterChange('답글을 남겼어요.');
                }}
              />
              {comment.replies.length > 0 && (
                <ul className="mt-2 ml-4 flex flex-col gap-3 border-l-2 border-contour pl-3">
                  {comment.replies.map((reply) => (
                    <li key={reply.commentId}>
                      <CommentItem
                        comment={reply}
                        myMemberId={myMemberId}
                        editingId={editingId}
                        onEdit={(id) => {
                          setStatus(null);
                          setReplyingId(null);
                          setEditingId(id);
                        }}
                        onCancelEdit={() => setEditingId(null)}
                        onSaveEdit={async (id, content) => {
                          await updateComment(id, { content });
                          await refreshAfterChange('답글을 고쳤어요.');
                        }}
                        onDelete={setDeleting}
                        onReport={(id) => setReporting({ type: 'comment', id })}
                      />
                    </li>
                  ))}
                </ul>
              )}
            </li>
          ))}
          {list.hasNext && (
            <li>
              <Button
                variant="ghost"
                fullWidth
                loading={loading}
                onClick={() => void loadPage(list.page + 1)}
                className="rounded-none"
              >
                댓글 더 보기
              </Button>
            </li>
          )}
        </ul>
      )}

      {deleting !== null && (
        <DeleteCommentDialog
          commentId={deleting}
          onClose={() => setDeleting(null)}
          onDeleted={async () => {
            setDeleting(null);
            await refreshAfterChange('댓글을 지웠어요.');
          }}
        />
      )}
      {reporting && <CommunityReportDialog target={reporting} onClose={() => setReporting(null)} />}
    </section>
  );
}

// 비회원에게는 로그인 링크를, 이메일 미인증 회원에게는 막힌 이유를 보여 준다. 쓰기 권한은 서버가 다시 확인한다.
function NewCommentArea({ onSubmit }: { onSubmit: (content: string) => Promise<void> }) {
  const session = useSession();
  const { pathname, search } = useLocation();

  if (session.status === 'loading') return null;
  if (session.status === 'anonymous' || session.me === null) {
    return (
      <Link
        to={withNext('/login', pathname + search)}
        className="inline-flex min-h-11 items-center text-base font-semibold text-forest underline underline-offset-2"
      >
        로그인하고 댓글 쓰기
      </Link>
    );
  }
  if (session.me.status === 'UNVERIFIED') {
    return (
      <div>
        <Button variant="secondary" disabled disabledReason={UNVERIFIED_REASON}>
          댓글 쓰기
        </Button>
      </div>
    );
  }
  return <CommentForm label="댓글" submitLabel="댓글 남기기" onSubmit={onSubmit} />;
}

type CommentItemProps = {
  comment: CommunityComment;
  myMemberId: number | null;
  editingId: number | null;
  onEdit: (commentId: number) => void;
  onCancelEdit: () => void;
  onSaveEdit: (commentId: number, content: string) => Promise<void>;
  onDelete: (commentId: number) => void;
  onReport: (commentId: number) => void;
  // 답글에는 아래 값을 넘기지 않는다. 답글에 다는 답글은 없다.
  topLevel?: boolean;
  replying?: boolean;
  onReply?: () => void;
  onCancelReply?: () => void;
  onSubmitReply?: (content: string) => Promise<void>;
};

function CommentItem({
  comment,
  myMemberId,
  editingId,
  onEdit,
  onCancelEdit,
  onSaveEdit,
  onDelete,
  onReport,
  topLevel = false,
  replying = false,
  onReply,
  onCancelReply,
  onSubmitReply,
}: CommentItemProps) {
  const session = useSession();

  if (comment.deleted) {
    return <p className="text-base text-ink-subtle">삭제된 댓글입니다</p>;
  }

  const signedIn = session.status === 'authenticated' && session.me !== null;
  const unverified = signedIn && session.me?.status === 'UNVERIFIED';
  const mine = myMemberId !== null && comment.author?.memberId === myMemberId;
  const content = comment.content ?? '';

  if (editingId === comment.commentId) {
    return (
      <CommentForm
        label="댓글 고치기"
        submitLabel="고치기"
        initial={content}
        onCancel={onCancelEdit}
        onSubmit={(next) => onSaveEdit(comment.commentId, next)}
      />
    );
  }

  const edited = comment.updatedAt !== undefined && comment.updatedAt !== comment.createdAt;
  return (
    <div>
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
        {comment.author && (
          <Link
            to={`/members/${comment.author.memberId}`}
            className="text-sm font-semibold text-ink underline-offset-2 hover:underline"
          >
            {comment.author.nickname}
          </Link>
        )}
        {comment.createdAt && (
          <time
            dateTime={comment.createdAt}
            title={formatKstDateTime(comment.createdAt)}
            className="text-xs text-ink-muted"
          >
            {formatRelativeKst(comment.createdAt)}
          </time>
        )}
        {edited && <span className="text-xs text-ink-muted">수정됨</span>}
      </div>
      <p className="mt-1 whitespace-pre-wrap break-words text-base text-ink">{content}</p>
      <div className="-mx-2 flex flex-wrap items-center">
        {topLevel && signedIn && (
          <Button
            variant="ghost"
            onClick={onReply}
            disabled={unverified}
            disabledReason={unverified ? UNVERIFIED_REASON : undefined}
            className="px-2"
          >
            답글
          </Button>
        )}
        {mine ? (
          <>
            <Button variant="ghost" onClick={() => onEdit(comment.commentId)} className="px-2">
              수정
            </Button>
            <Button variant="ghost" onClick={() => onDelete(comment.commentId)} className="px-2 text-danger">
              삭제
            </Button>
          </>
        ) : (
          signedIn && (
            <Button
              variant="ghost"
              onClick={() => onReport(comment.commentId)}
              disabled={unverified}
              disabledReason={unverified ? UNVERIFIED_REPORT_REASON : undefined}
              className="px-2"
            >
              신고
            </Button>
          )
        )}
      </div>
      {topLevel && replying && onSubmitReply && (
        <div className="mt-2">
          <CommentForm label="답글" submitLabel="답글 남기기" onCancel={onCancelReply} onSubmit={onSubmitReply} />
        </div>
      )}
    </div>
  );
}

type CommentFormProps = {
  label: string;
  submitLabel: string;
  initial?: string;
  onCancel?: () => void;
  onSubmit: (content: string) => Promise<void>;
};

// 댓글 작성, 답글 작성, 댓글 수정이 함께 쓰는 입력 칸이다.
function CommentForm({ label, submitLabel, initial = '', onCancel, onSubmit }: CommentFormProps) {
  const [content, setContent] = useState(initial);
  const [fieldError, setFieldError] = useState<string | undefined>();
  const [formError, setFormError] = useState<ApiError | string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    setFormError(null);
    const trimmed = content.trim();
    if (trimmed === '') {
      setFieldError('내용을 적어 주세요.');
      return;
    }
    setFieldError(undefined);
    setSubmitting(true);
    try {
      await onSubmit(trimmed);
      // 성공하면 목록을 다시 받으면서 이 입력 칸이 사라지거나, 새 댓글 입력 칸은 비워 둔다.
      setContent(initial);
      setSubmitting(false);
    } catch (error) {
      setSubmitting(false);
      if (error instanceof ApiError && error.code === 'INVALID_INPUT' && error.fieldErrors.length > 0) {
        const contentItem = error.fieldErrors.find((item) => item.field === 'content');
        if (contentItem) setFieldError(contentItem.reason);
        else setFormError(error);
        return;
      }
      setFormError(error instanceof ApiError ? error : toUserMessage(error));
    }
  }

  return (
    <form noValidate onSubmit={handleSubmit} aria-label={label} className="flex flex-col gap-3">
      {formError && (
        <div role="alert">
          <Notice tone="danger" title="저장하지 못했어요">
            <p>{typeof formError === 'string' ? formError : formError.message}</p>
            {typeof formError !== 'string' && formError.status >= 500 && formError.traceId && (
              <p className="mt-1 font-mono text-xs text-ink-muted">문의 번호 {formError.traceId}</p>
            )}
          </Notice>
        </div>
      )}
      <TextArea
        label={label}
        name="content"
        maxLength={CONTENT_MAX}
        value={content}
        disabled={submitting}
        onChange={(event) => setContent(event.target.value)}
        error={fieldError}
      />
      <div className="flex flex-wrap justify-end gap-2">
        {onCancel && (
          <Button variant="secondary" onClick={onCancel} disabled={submitting}>
            취소
          </Button>
        )}
        <Button type="submit" loading={submitting}>
          {submitLabel}
        </Button>
      </div>
    </form>
  );
}

function DeleteCommentDialog({
  commentId,
  onClose,
  onDeleted,
}: {
  commentId: number;
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
      await deleteComment(commentId);
      await onDeleted();
    } catch (cause) {
      setSubmitting(false);
      setError(toUserMessage(cause));
    }
  }

  return (
    <Dialog title="댓글을 지울까요?" onClose={onClose}>
      <div className="flex flex-col gap-4">
        <p className="text-base text-ink">지운 댓글은 되돌릴 수 없어요. 달린 답글은 그대로 남아요.</p>
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
