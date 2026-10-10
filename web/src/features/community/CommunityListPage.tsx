import { useEffect, useMemo, useState } from 'react';
import { listPosts } from '../../api/community';
import type { CommunityPostQuery } from '../../api/community';
import { fetchSpotDetail } from '../../api/spots';
import type { CommunityPostSummary } from '../../api/types';
import { Link, navigate, useLocation } from '../../app/router';
import { Button } from '../../components/Button';
import { AsideSection, HubLayout, ListPanel } from '../../components/HubLayout';
import { Icon } from '../../components/icons';
import { Notice } from '../../components/Notice';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatRelativeKst } from '../../lib/datetime';
import { withNext } from '../member/nextPath';
import { useSession } from '../member/session';
import { usePagedList } from '../program/usePagedList';
import { LINK_BUTTON_CLASS, parseSpotId, SECONDARY_LINK_CLASS, UNVERIFIED_WRITE_REASON } from './communityLabels';

function fetchPosts(filter: CommunityPostQuery, page: number, signal?: AbortSignal) {
  return listPosts(filter, page, signal);
}

function listPath(spotId: number | null): string {
  return spotId === null ? '/community' : `/community?spotId=${spotId}`;
}

export function CommunityListPage() {
  const { search } = useLocation();
  const spotId = parseSpotId(new URLSearchParams(search).get('spotId'));

  // 필터가 바뀔 때만 새 객체를 만들어야 목록을 처음부터 다시 받는다.
  const filter = useMemo<CommunityPostQuery>(
    () => ({ spotId: spotId ?? undefined }),
    [spotId],
  );
  const { current, loadingMore, loadMore, retry } = usePagedList(true, filter, fetchPosts);
  const showSkeleton = useDelayedFlag(current === null);

  useEffect(() => {
    document.title = '커뮤니티 · 피치맵';
  }, []);

  return (
    <HubLayout
      title="커뮤니티"
      description="백패킹 이야기를 자유롭게 나누는 곳이에요."
      actions={<WriteAction spotId={spotId} />}
      aside={<CommunityAside />}
    >
      {spotId !== null && (
        <SpotFilterLine key={spotId} spotId={spotId} onRemove={() => navigate(listPath(null), { replace: true })} />
      )}
      {current?.error && (
        <div role="alert" className="flex flex-col gap-3">
          <Notice tone="danger" title="글을 불러오지 못했어요">
            <p>{current.error}</p>
          </Notice>
          {current.items.length === 0 && (
            <div>
              <Button variant="secondary" onClick={retry}>
                다시 불러오기
              </Button>
            </div>
          )}
        </div>
      )}
      <div aria-busy={current === null}>
        <ListPanel header="최신순">
          {current === null ? (
            showSkeleton ? (
              Array.from({ length: 5 }, (_, index) => (
                <li key={index} className="flex flex-col gap-2 px-4 py-3">
                  <Skeleton className="h-5 w-2/3" />
                  <Skeleton className="h-4 w-1/3" />
                </li>
              ))
            ) : null
          ) : current.items.length === 0 ? (
            current.error ? null : (
              <li>
                <CommunityEmpty spotId={spotId} />
              </li>
            )
          ) : (
            <>
              {current.items.map((item) => (
                <PostRow key={item.postId} item={item} />
              ))}
              {current.hasNext && (
                <li className="p-2">
                  <Button variant="ghost" fullWidth loading={loadingMore} onClick={() => void loadMore()}>
                    더 보기
                  </Button>
                </li>
              )}
            </>
          )}
        </ListPanel>
      </div>
    </HubLayout>
  );
}

function CommunityEmpty({ spotId }: { spotId: number | null }) {
  return (
    <div className="flex flex-col items-start gap-3 p-6">
      <Icon name="chat" size={24} className="text-ink-muted" />
      <p className="font-serif text-lg text-ink">아직 올라온 글이 없어요</p>
      <p className="text-sm text-ink-muted">다녀온 박지, 써 본 장비, 준비하며 궁금한 것을 첫 글로 남겨 보세요.</p>
      <WriteAction spotId={spotId} />
    </div>
  );
}

function CommunityAside() {
  return (
    <>
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
          <li>신고가 5건 쌓이면 글이 잠시 내려가고 관리자가 검토해요.</li>
        </ul>
      </AsideSection>
      <AsideSection title="둘러보기">
        <ul>
          {[
            { to: '/map', label: '지도에서 박지 찾기' },
            { to: '/basecamps', label: '베이스캠프' },
            { to: '/programs', label: '공식 행사' },
          ].map((item) => (
            <li key={item.to}>
              <Link
                to={item.to}
                className="inline-flex min-h-11 items-center text-base font-semibold text-forest underline underline-offset-2"
              >
                {item.label}
              </Link>
            </li>
          ))}
        </ul>
      </AsideSection>
    </>
  );
}

// 비회원에게는 로그인 링크를, 이메일 미인증 회원에게는 막힌 이유를 보여 준다. 쓰기 권한은 서버가 다시 확인한다.
function WriteAction({ spotId }: { spotId: number | null }) {
  const session = useSession();
  const { pathname, search } = useLocation();
  const target = spotId === null ? '/community/new' : `/community/new?spotId=${spotId}`;

  if (session.status === 'loading') return null;
  if (session.status === 'anonymous' || session.me === null) {
    return (
      <Link
        to={withNext('/login', pathname + search)}
        className={SECONDARY_LINK_CLASS}
      >
        로그인하고 글쓰기
      </Link>
    );
  }
  if (session.me.status === 'UNVERIFIED') {
    return (
      <Button variant="secondary" disabled disabledReason={UNVERIFIED_WRITE_REASON}>
        글쓰기
      </Button>
    );
  }
  return (
    <Link to={target} className={LINK_BUTTON_CLASS}>
      글쓰기
    </Link>
  );
}

// 장소 이름은 처음 한 번만 받는다. 받지 못하면 이름 없이 "장소 관련 글"로 보여 준다.
function SpotFilterLine({ spotId, onRemove }: { spotId: number; onRemove: () => void }) {
  const [name, setName] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    fetchSpotDetail(spotId, controller.signal).then(
      (detail) => {
        if (!controller.signal.aborted) setName(detail.name);
      },
      () => undefined,
    );
    return () => controller.abort();
  }, [spotId]);

  const label = name === null ? '장소 관련 글' : `${name} 관련 글`;
  return (
    <div className="flex flex-wrap items-center justify-between gap-x-4 rounded-control border border-contour bg-card px-4 py-3">
      <p className="text-base font-semibold text-ink">{label}</p>
      <button
        type="button"
        onClick={onRemove}
        className="inline-flex min-h-11 items-center text-base font-semibold text-forest underline underline-offset-2"
      >
        모든 글 보기
      </button>
    </div>
  );
}

function PostRow({ item }: { item: CommunityPostSummary }) {
  return (
    <li>
      <Link to={`/community/${item.postId}`} className="flex min-h-11 gap-3 px-4 py-3 hover:bg-paper-deep">
        <span className="flex min-w-0 flex-1 flex-col gap-1">
          <span className="flex min-w-0 items-center gap-2">
            <span className="truncate text-base font-semibold text-ink">{item.title}</span>
            {item.commentCount > 0 && (
              <span className="shrink-0 font-mono text-sm text-forest-deep">
                [<span className="sr-only">댓글 </span>
                {item.commentCount}
                <span className="sr-only">개</span>]
              </span>
            )}
            {item.imageCount > 0 && (
              <Icon name="image" size={16} title="사진 있음" className="shrink-0 text-ink-muted" />
            )}
          </span>
          <span className="hidden line-clamp-1 break-words text-sm text-ink-muted md:block">{item.excerpt}</span>
          <span className="flex flex-wrap items-center gap-x-1 text-xs text-ink-muted">
            <span>{item.author.nickname}</span>
            <span aria-hidden="true">·</span>
            <time dateTime={item.createdAt}>{formatRelativeKst(item.createdAt)}</time>
            <span aria-hidden="true">·</span>
            <span>좋아요 {item.likeCount}</span>
            {item.spot && (
              <>
                <span aria-hidden="true">·</span>
                <span className="inline-flex items-center gap-1">
                  <Icon name="map" size={14} />
                  {item.spot.name}
                </span>
              </>
            )}
          </span>
        </span>
        {item.thumbnailUrl && (
          <img
            src={item.thumbnailUrl}
            alt=""
            loading="lazy"
            className="size-16 shrink-0 self-center rounded-control border border-contour object-cover"
          />
        )}
      </Link>
    </li>
  );
}
