import { useEffect, useRef, useState } from 'react';
import { deletePost, getPost, likePost, unlikePost } from '../../api/community';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import type { CommunityLikeResponse, CommunityPostDetail } from '../../api/types';
import { Link, navigate, useLocation } from '../../app/router';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { AsideSection, HubLayout } from '../../components/HubLayout';
import { Dialog } from '../../components/Dialog';
import { EmptyState } from '../../components/EmptyState';
import { Icon } from '../../components/icons';
import { Notice } from '../../components/Notice';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatKstDateTime, formatRelativeKst } from '../../lib/datetime';
import { withNext } from '../member/nextPath';
import { useSession } from '../member/session';
import { CommunityComments } from './CommunityComments';
import { CommunityReportDialog } from './CommunityReportDialog';
import { CATEGORY_META, SECONDARY_LINK_CLASS } from './communityLabels';

type LoadResult = { postId: number; post: CommunityPostDetail } | { postId: number; error: unknown };

export function CommunityPostDetailPage({ postId }: { postId: number }) {
  const session = useSession();
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<LoadResult | null>(null);
  // 이미지 조회 URL은 1시간 뒤 만료된다. 열어 둔 화면에서 이미지가 깨지면 글을 한 번만 다시 받아 새 URL을 얻는다.
  const imageRetried = useRef(false);
  const sessionReady = session.status !== 'loading';

  // 로그인 여부에 따라 likedByMe가 달라지므로 세션을 확인한 뒤에 받는다.
  // 다시 받을 때는 화면을 비우지 않고 이전 내용을 둔 채 바꾼다.
  useEffect(() => {
    if (!sessionReady) return;
    const controller = new AbortController();
    getPost(postId, controller.signal).then(
      (post) => {
        if (!controller.signal.aborted) setResult({ postId, post });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ postId, error });
      },
    );
    return () => controller.abort();
  }, [postId, sessionReady, attempt, session.status]);

  const current = result?.postId === postId ? result : null;
  const showSkeleton = useDelayedFlag(current === null);
  const loadedTitle = current !== null && 'post' in current ? current.post.title : null;

  useEffect(() => {
    document.title = loadedTitle ? `${loadedTitle} · 피치맵` : '커뮤니티 · 피치맵';
  }, [loadedTitle]);

  const reload = () => setAttempt((value) => value + 1);

  function handleImageError() {
    if (imageRetried.current) return;
    imageRetried.current = true;
    reload();
  }

  function applyLike(response: CommunityLikeResponse) {
    setResult((previous) =>
      previous !== null && 'post' in previous
        ? {
            ...previous,
            post: { ...previous.post, likedByMe: response.liked, likeCount: response.likeCount },
          }
        : previous,
    );
  }

  const asideNode = (
    <PostAside post={current !== null && 'post' in current ? current.post : null} />
  );

  if (current === null) {
    return (
      <HubLayout title="커뮤니티" breadcrumb={<BackToCommunity />} aside={asideNode}>
        <div aria-busy="true">
          {showSkeleton && (
            <div className="flex flex-col gap-3 rounded-control border border-contour bg-card p-5">
              <Skeleton className="h-8 w-2/3" />
              <Skeleton className="h-40 w-full" />
            </div>
          )}
        </div>
      </HubLayout>
    );
  }

  if ('post' in current) {
    const post = current.post;
    return (
      <HubLayout
        title={post.title}
        breadcrumb={<PostBreadcrumb post={post} />}
        meta={<PostMeta post={post} />}
        actions={<PostActions post={post} />}
        aside={asideNode}
      >
        <PostContent post={post} onLikeChanged={applyLike} onImageError={handleImageError} />
        <CommunityComments postId={postId} count={post.commentCount} onChanged={reload} />
        <div>
          <Link to={`/community?category=${post.category}`} className={SECONDARY_LINK_CLASS}>
            목록으로
          </Link>
        </div>
      </HubLayout>
    );
  }

  if (current.error instanceof ApiError && current.error.code === 'NOT_FOUND') {
    return (
      <HubLayout title="글을 찾을 수 없어요" breadcrumb={<BackToCommunity />} aside={asideNode}>
        <div className="rounded-control border border-contour bg-card">
          <EmptyState
            title="지워졌거나 볼 수 없는 글이에요."
            action={
              <Link to="/community" className={SECONDARY_LINK_CLASS}>
                커뮤니티로 가기
              </Link>
            }
          />
        </div>
      </HubLayout>
    );
  }

  return (
    <HubLayout title="커뮤니티" breadcrumb={<BackToCommunity />} aside={asideNode}>
      <div className="flex flex-col gap-3">
        <Notice tone="danger" title="글을 불러오지 못했어요">
          {toUserMessage(current.error)}
        </Notice>
        <div>
          <Button variant="secondary" onClick={reload}>
            다시 불러오기
          </Button>
        </div>
      </div>
    </HubLayout>
  );
}

const CRUMB_LINK = 'inline-flex min-h-11 items-center gap-1 text-forest underline underline-offset-2';

function BackToCommunity() {
  return (
    <Link to="/community" className={CRUMB_LINK}>
      <Icon name="chevronLeft" size={16} />
      커뮤니티
    </Link>
  );
}

function PostBreadcrumb({ post }: { post: CommunityPostDetail }) {
  return (
    <nav aria-label="현재 위치" className="flex flex-wrap items-center gap-x-2">
      <BackToCommunity />
      <span aria-hidden="true">/</span>
      <Link to={`/community?category=${post.category}`} className={CRUMB_LINK}>
        {CATEGORY_META[post.category].label}
      </Link>
    </nav>
  );
}

function PostMeta({ post }: { post: CommunityPostDetail }) {
  const category = CATEGORY_META[post.category];
  const edited = post.updatedAt !== post.createdAt;
  return (
    <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-ink-muted">
      <Badge tone={category.tone}>{category.label}</Badge>
      <Link to={`/members/${post.author.memberId}`} className="font-semibold text-ink underline-offset-2 hover:underline">
        {post.author.nickname}
      </Link>
      <span aria-hidden="true">·</span>
      <time dateTime={post.createdAt} title={formatKstDateTime(post.createdAt)}>
        {formatRelativeKst(post.createdAt)}
      </time>
      {edited && (
        <>
          <span aria-hidden="true">·</span>
          <span>수정됨</span>
        </>
      )}
      {post.spot && (
        <>
          <span aria-hidden="true">·</span>
          <Link
            to={`/spots/${post.spot.spotId}`}
            className="inline-flex items-center gap-1 text-forest underline underline-offset-2"
          >
            <Icon name="map" size={14} />
            {post.spot.name}
          </Link>
        </>
      )}
    </div>
  );
}

// 작성자에게는 수정·삭제를, 로그인한 다른 회원에게는 신고를 보여 준다. 권한은 서버가 다시 확인한다.
function PostActions({ post }: { post: CommunityPostDetail }) {
  const session = useSession();
  const signedIn = session.status === 'authenticated' && session.me !== null;
  const unverified = signedIn && session.me?.status === 'UNVERIFIED';
  const mine = signedIn && session.me?.memberId === post.author.memberId;
  const [deleting, setDeleting] = useState(false);
  const [reporting, setReporting] = useState(false);

  if (!signedIn) return null;
  return (
    <>
      {mine ? (
        <>
          <Link to={`/community/${post.postId}/edit`} className={SECONDARY_LINK_CLASS}>
            수정
          </Link>
          <Button variant="ghost" onClick={() => setDeleting(true)} className="text-danger hover:bg-danger-soft">
            삭제
          </Button>
        </>
      ) : (
        <Button
          variant="ghost"
          onClick={() => setReporting(true)}
          disabled={unverified}
          disabledReason={unverified ? '이메일 인증을 마치면 신고할 수 있어요' : undefined}
        >
          신고
        </Button>
      )}
      {deleting && <DeletePostDialog postId={post.postId} onClose={() => setDeleting(false)} />}
      {reporting && <CommunityReportDialog target={{ type: 'post', id: post.postId }} onClose={() => setReporting(false)} />}
    </>
  );
}

function PostAside({ post }: { post: CommunityPostDetail | null }) {
  return (
    <>
      {post?.spot && (
        <AsideSection title="이 글의 장소">
          <p className="text-base font-semibold text-ink">{post.spot.name}</p>
          <ul>
            <li>
              <Link to={`/spots/${post.spot.spotId}`} className={ASIDE_LINK}>
                장소 보기
              </Link>
            </li>
            <li>
              <Link to={`/community?spotId=${post.spot.spotId}`} className={ASIDE_LINK}>
                이 장소 관련 글
              </Link>
            </li>
          </ul>
        </AsideSection>
      )}
      {post && (
        <AsideSection title="글쓴이">
          <Link to={`/members/${post.author.memberId}`} className={ASIDE_LINK}>
            {post.author.nickname}
          </Link>
        </AsideSection>
      )}
      <AsideSection title="이용 안내">
        <ul className="list-disc pl-5">
          <li>
            동행은{' '}
            <Link to="/basecamps" className="font-semibold text-forest underline underline-offset-2">
              베이스캠프
            </Link>
            에서 구해요.
          </li>
          <li>송금을 요구하면 신고해 주세요.</li>
          <li>공원 안 야영을 권하는 글은 신고 대상이에요.</li>
        </ul>
      </AsideSection>
    </>
  );
}

const ASIDE_LINK = 'inline-flex min-h-11 items-center text-base font-semibold text-forest underline underline-offset-2';

type PostContentProps = {
  post: CommunityPostDetail;
  onLikeChanged: (response: CommunityLikeResponse) => void;
  onImageError: () => void;
};

function PostContent({ post, onLikeChanged, onImageError }: PostContentProps) {
  return (
    <article className="rounded-control border border-contour bg-card">
      <p className="whitespace-pre-wrap break-words px-5 py-5 text-base leading-7 text-ink">{post.content}</p>

      {post.images.length > 0 && (
        <ul className="flex flex-col gap-3 px-5 pb-5">
          {post.images.map((image, index) => (
            <li key={image.imageId}>
              <img
                src={image.url}
                alt={`글 이미지 ${index + 1} / ${post.images.length}`}
                loading="lazy"
                onError={onImageError}
                className="max-h-[32rem] w-full rounded-control border border-contour bg-paper-deep object-contain"
              />
            </li>
          ))}
        </ul>
      )}

      <div className="flex flex-wrap items-center justify-between gap-2 border-t border-contour px-5 py-3">
        <LikeButton post={post} onChanged={onLikeChanged} />
        <a href="#comments" className="inline-flex min-h-11 items-center gap-1 text-base text-ink-muted hover:text-ink">
          <Icon name="chat" size={18} />
          댓글 <span className="font-mono tabular-nums">{post.commentCount}</span>
        </a>
      </div>
    </article>
  );
}

function LikeButton({ post, onChanged }: { post: CommunityPostDetail; onChanged: (response: CommunityLikeResponse) => void }) {
  const session = useSession();
  const { pathname, search } = useLocation();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const liked = post.likedByMe === true;

  const content = (
    <>
      <Icon name="heart" size={20} fill={liked ? 'currentColor' : 'none'} />
      <span>좋아요</span>
      <span className="font-mono tabular-nums">{post.likeCount}</span>
    </>
  );

  if (session.status === 'loading') return null;
  if (session.status === 'anonymous' || session.me === null) {
    return (
      <Link to={withNext('/login', pathname + search)} className={SECONDARY_LINK_CLASS}>
        <span className="inline-flex items-center gap-2">
          {content}
          <span className="text-sm font-normal text-ink-muted">로그인하고 누르기</span>
        </span>
      </Link>
    );
  }
  if (session.me.status === 'UNVERIFIED') {
    return (
      <Button variant="secondary" disabled disabledReason="이메일 인증을 마치면 누를 수 있어요">
        {content}
      </Button>
    );
  }

  async function toggle() {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      onChanged(liked ? await unlikePost(post.postId) : await likePost(post.postId));
    } catch (caught) {
      setError(toUserMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-1">
      <Button
        variant={liked ? 'primary' : 'secondary'}
        aria-pressed={liked}
        loading={busy}
        onClick={() => void toggle()}
      >
        {content}
      </Button>
      {error && (
        <p role="alert" className="text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  );
}

function DeletePostDialog({ postId, onClose }: { postId: number; onClose: () => void }) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleDelete() {
    if (submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      await deletePost(postId);
      navigate('/community');
    } catch (cause) {
      setSubmitting(false);
      setError(toUserMessage(cause));
    }
  }

  return (
    <Dialog title="글을 지울까요?" onClose={() => !submitting && onClose()}>
      <div className="flex flex-col gap-4">
        <p className="text-base text-ink">지운 글은 되돌릴 수 없어요. 댓글도 함께 보이지 않게 돼요.</p>
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
