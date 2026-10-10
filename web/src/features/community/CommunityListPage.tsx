import { useEffect, useState } from 'react';
import { listPosts } from '../../api/community';
import { toUserMessage } from '../../api/errors';
import { fetchSpotDetail } from '../../api/spots';
import type { CommunityPostSummary, NumberedPage } from '../../api/types';
import { Link, navigate, useLocation } from '../../app/router';
import { Button } from '../../components/Button';
import { AsideSection, HubLayout, ListPanel } from '../../components/HubLayout';
import { Icon } from '../../components/icons';
import { Notice } from '../../components/Notice';
import { Pagination } from '../../components/Pagination';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatRelativeKst } from '../../lib/datetime';
import { withNext } from '../member/nextPath';
import { useSession } from '../member/session';
import { LINK_BUTTON_CLASS, parseSpotId, SECONDARY_LINK_CLASS, UNVERIFIED_WRITE_REASON } from './communityLabels';

// 주소의 page는 사람이 읽는 1부터 시작하는 번호다. 서버에는 1을 빼서 보낸다.
type ListQuery = { spotId: number | null; page: number };

function parsePage(raw: string | null): number {
  if (raw === null || !/^\d+$/.test(raw)) return 1;
  const value = Number(raw);
  return Number.isSafeInteger(value) && value >= 1 ? value : 1;
}

function parseListQuery(search: string): ListQuery {
  const params = new URLSearchParams(search);
  return { spotId: parseSpotId(params.get('spotId')), page: parsePage(params.get('page')) };
}

function listPath({ spotId, page }: ListQuery): string {
  const params = new URLSearchParams();
  if (spotId !== null) params.set('spotId', String(spotId));
  if (page > 1) params.set('page', String(page));
  const query = params.toString();
  return query ? `/community?${query}` : '/community';
}

type PageResult = { key: string; data: NumberedPage<CommunityPostSummary> | null; error: string | null };

// 조건이나 페이지가 바뀌면 이전 요청을 취소하고 그 페이지만 새로 받는다.
function usePostPage(query: ListQuery) {
  const [result, setResult] = useState<PageResult | null>(null);
  const [attempt, setAttempt] = useState(0);
  const { spotId, page } = query;
  const key = `${spotId ?? ''}|${page}|${attempt}`;

  useEffect(() => {
    const controller = new AbortController();
    listPosts({ spotId: spotId ?? undefined }, page - 1, controller.signal).then(
      (data) => {
        if (!controller.signal.aborted) setResult({ key, data, error: null });
      },
      (caught: unknown) => {
        if (!controller.signal.aborted) setResult({ key, data: null, error: toUserMessage(caught) });
      },
    );
    return () => controller.abort();
  }, [key, spotId, page]);

  const current = result !== null && result.key === key ? result : null;
  return { current, retry: () => setAttempt((value) => value + 1) };
}

// 넓은 화면에서 머리 줄과 행이 같은 열 너비를 쓴다.
const ROW_COLUMNS = 'md:grid md:grid-cols-[minmax(0,1fr)_8rem_6rem_3.5rem_3.5rem] md:items-center md:gap-3';

export function CommunityListPage() {
  const { search } = useLocation();
  const query = parseListQuery(search);
  const { current, retry } = usePostPage(query);
  const showSkeleton = useDelayedFlag(current === null);
  const data = current?.data ?? null;

  useEffect(() => {
    document.title = '커뮤니티 · 피치맵';
  }, []);

  const goTo = (next: Partial<ListQuery>) => navigate(listPath({ ...query, ...next }));

  return (
    <HubLayout
      title="커뮤니티"
      description="백패킹 이야기를 자유롭게 나누는 곳이에요."
      actions={<WriteAction spotId={query.spotId} />}
      aside={<CommunityAside />}
    >
      {query.spotId !== null && (
        <SpotFilterLine
          key={query.spotId}
          spotId={query.spotId}
          onRemove={() => navigate(listPath({ spotId: null, page: 1 }), { replace: true })}
        />
      )}
      {current?.error && (
        <div role="alert" className="flex flex-col gap-3">
          <Notice tone="danger" title="글을 불러오지 못했어요">
            <p>{current.error}</p>
          </Notice>
          <div>
            <Button variant="secondary" onClick={retry}>
              다시 불러오기
            </Button>
          </div>
        </div>
      )}
      <div aria-busy={current === null} className="flex flex-col gap-4">
        <ListPanel header={<ListHeader />}>
          {current === null ? (
            showSkeleton ? (
              Array.from({ length: 5 }, (_, index) => (
                <li key={index} className="flex flex-col gap-2 px-4 py-3">
                  <Skeleton className="h-5 w-2/3" />
                  <Skeleton className="h-4 w-1/3" />
                </li>
              ))
            ) : null
          ) : data === null ? null : data.content.length === 0 ? (
            <li>
              {data.totalElements > 0 ? (
                <PageOutOfRange onFirst={() => goTo({ page: 1 })} />
              ) : (
                <CommunityEmpty spotId={query.spotId} />
              )}
            </li>
          ) : (
            data.content.map((item) => <PostRow key={item.postId} item={item} />)
          )}
        </ListPanel>
        {data !== null && (
          <Pagination
            label="글 목록 페이지"
            page={query.page}
            totalPages={data.totalPages}
            onChange={(page) => goTo({ page })}
          />
        )}
      </div>
    </HubLayout>
  );
}

function ListHeader() {
  return (
    <div className="w-full">
      <span className="md:hidden">최신순</span>
      <div aria-hidden="true" className={`hidden ${ROW_COLUMNS}`}>
        <span>제목</span>
        <span>글쓴이</span>
        <span>작성일</span>
        <span className="text-right">조회</span>
        <span className="text-right">좋아요</span>
      </div>
    </div>
  );
}

function PageOutOfRange({ onFirst }: { onFirst: () => void }) {
  return (
    <div className="flex flex-col items-start gap-3 p-6">
      <p className="text-base text-ink">이 페이지에는 글이 없어요.</p>
      <Button variant="secondary" onClick={onFirst}>
        첫 페이지로
      </Button>
    </div>
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

// 좁은 화면에서는 제목 한 줄과 정보 한 줄(글쓴이 · 작성일 · 조회 · 좋아요)로, 넓은 화면에서는 머리 줄과 같은 열로 보여 준다.
function PostRow({ item }: { item: CommunityPostSummary }) {
  return (
    <li>
      <Link
        to={`/community/${item.postId}`}
        className={`flex min-h-11 flex-col gap-1 px-4 py-3 hover:bg-paper-deep ${ROW_COLUMNS}`}
      >
        <span className="flex min-w-0 items-center gap-2">
          <span className="truncate text-base font-semibold text-ink">{item.title}</span>
          {item.commentCount > 0 && (
            <span className="shrink-0 font-mono text-sm tabular-nums text-forest-deep">
              [<span className="sr-only">댓글 </span>
              {item.commentCount}
              <span className="sr-only">개</span>]
            </span>
          )}
          {item.imageCount > 0 && <Icon name="image" size={16} title="사진 있음" className="shrink-0 text-ink-muted" />}
        </span>
        <span className="flex flex-wrap items-center gap-x-1 text-xs text-ink-muted md:contents md:text-sm">
          <span className="min-w-0 truncate">{item.author.nickname}</span>
          <span aria-hidden="true" className="md:hidden">
            ·
          </span>
          <time dateTime={item.createdAt} className="tabular-nums">
            {formatRelativeKst(item.createdAt)}
          </time>
          <span aria-hidden="true" className="md:hidden">
            ·
          </span>
          <span className="tabular-nums md:text-right">
            <span className="md:sr-only">조회 </span>
            {item.viewCount}
          </span>
          <span aria-hidden="true" className="md:hidden">
            ·
          </span>
          <span className="tabular-nums md:text-right">
            <span className="md:sr-only">좋아요 </span>
            {item.likeCount}
          </span>
        </span>
      </Link>
    </li>
  );
}
