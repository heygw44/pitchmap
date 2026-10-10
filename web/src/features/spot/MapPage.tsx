import { useEffect, useRef, useState } from 'react';
import { BottomTabBar } from '../../app/layout/BottomTabBar';
import { SiteHeader } from '../../app/layout/SiteHeader';
import { Link, navigate } from '../../app/router';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { Icon } from '../../components/icons';
import { Notice } from '../../components/Notice';
import { Sheet } from '../../components/Sheet';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { createMapView } from '../../map/KakaoMap';
import { MapLoadError } from '../../map/kakaoLoader';
import type { MapLoadErrorReason } from '../../map/kakaoLoader';
import type { LatLng, MapView } from '../../map/types';
import { BasecampDetailPanel } from '../basecamp/BasecampDetailPanel';
import { BasecampListPanel } from '../basecamp/BasecampListPanel';
import { BasecampOpenPanel } from '../basecamp/BasecampOpenPanel';
import { useBasecampsInView } from '../basecamp/useBasecampsInView';
import type { BasecampFilters, BasecampsInView } from '../basecamp/useBasecampsInView';
import { withNext } from '../member/nextPath';
import { NotificationBellLink } from '../notification/NotificationLink';
import { useSession } from '../member/session';
import { AnnouncementBar } from '../program/AnnouncementBar';
import { BakjiReportPanel } from './BakjiReportPanel';
import { MapSafetyNotice } from './MapSafetyNotice';
import { SpotDetailPanel } from './SpotDetailPanel';
import { SpotListRow } from './SpotListRow';
import { useSpotsInView } from './useSpotsInView';
import type { SpotsInView } from './useSpotsInView';

type SheetSnap = 'peek' | 'half' | 'full';

type MapFailure = { reason: MapLoadErrorReason; message: string };

// 처음에는 전국이 한 화면에 들어오도록 국토 가운데쯤을 중심으로 크게 축소해서 연다.
const INITIAL_VIEW = { center: { lat: 36.35, lng: 127.85 }, level: 12 };

// 묶음을 누르면 이만큼 확대해서 안의 장소가 풀려 보이게 한다.
const CLUSTER_ZOOM_STEP = 2;

const CHIP_CLASS =
  'pointer-events-auto inline-flex min-h-11 items-center gap-2 rounded-control border border-contour bg-card px-3';

type MapPageProps = {
  spotId?: number;
  // basecampId는 mode가 basecamps일 때 베이스캠프 상세를 연다.
  basecampId?: number;
  // basecampOpen에서 베이스캠프를 열 장소다.
  openSpotId?: number;
  // report는 지도를 눌러 박지 위치를 찍는 제보 화면이고, basecamps는 베이스캠프 목록과 상세,
  // basecampOpen은 장소에서 베이스캠프를 여는 화면이다.
  mode?: 'browse' | 'report' | 'basecamps' | 'basecampOpen';
};

const NO_FILTERS: BasecampFilters = { fromDate: '', toDate: '', hasVacancy: false };

export function MapPage({ spotId, basecampId, openSpotId, mode = 'browse' }: MapPageProps) {
  const reporting = mode === 'report';
  const basecampMode = mode === 'basecamps';
  const opening = mode === 'basecampOpen';
  // 시트에 목록이 아니라 상세·폼이 열려 있는지다.
  const contentOpen = spotId !== undefined || reporting || basecampId !== undefined || opening;
  const session = useSession();
  const hostRef = useRef<HTMLDivElement>(null);
  const [map, setMap] = useState<MapView | null>(null);
  const [mapFailure, setMapFailure] = useState<MapFailure | null>(null);
  const [mapAttempt, setMapAttempt] = useState(0);
  const [terrainOn, setTerrainOn] = useState(false);
  const [snap, setSnap] = useState<SheetSnap>(contentOpen ? 'half' : 'peek');
  const [snapOpen, setSnapOpen] = useState(contentOpen);
  const [draft, setDraft] = useState<LatLng | null>(null);
  const [submitted, setSubmitted] = useState(false);
  const [filters, setFilters] = useState<BasecampFilters>(NO_FILTERS);
  // 지도에서 장소 마커를 눌러 고른 장소다. 베이스캠프 목록을 그 장소로 거른다.
  const [spotFilterId, setSpotFilterId] = useState<number | null>(null);
  const rangeInvalid = filters.fromDate !== '' && filters.toDate !== '' && filters.fromDate > filters.toDate;
  // 베이스캠프를 찾는 동안에는 지도의 마커를 베이스캠프가 맡고, 장소 조회는 쉰다.
  const area = useSpotsInView(map, !basecampMode);
  const basecamps = useBasecampsInView(
    map,
    basecampMode && session.status !== 'loading' && !rangeInvalid,
    filters,
    session.me?.memberId ?? null,
  );

  // 제보 화면을 벗어나면 이전 제보의 완료 상태를 버린다. 그래야 다시 들어왔을 때 지도를 눌러 위치를 찍을 수 있다.
  if (!reporting && submitted) setSubmitted(false);

  // 상세나 폼이 열리면 내용이 보이도록 시트를 절반으로 열고, 목록으로 돌아오면 접는다.
  // 렌더 중에 이전 값과 비교해서 바꾸므로, 화면이 한 번 그려진 뒤 다시 그리는 일이 없다.
  if (snapOpen !== contentOpen) {
    setSnapOpen(contentOpen);
    setSnap(contentOpen ? 'half' : 'peek');
  }

  // 지도는 시도할 때마다 새 요소에 만든다. 개발 모드에서 효과가 두 번 실행돼도 지도가 한 요소에 겹쳐 생기지 않게 하기 위해서다.
  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    const element = document.createElement('div');
    element.className = 'absolute inset-0';
    host.appendChild(element);

    let cancelled = false;
    let created: MapView | null = null;
    createMapView(element, INITIAL_VIEW).then(
      (view) => {
        if (cancelled) {
          view.destroy();
          return;
        }
        created = view;
        setMap(view);
      },
      (error: unknown) => {
        if (cancelled) return;
        const failure = error instanceof MapLoadError ? error : new MapLoadError('LOAD_FAILED');
        setMapFailure({ reason: failure.reason, message: failure.message });
      },
    );

    return () => {
      cancelled = true;
      created?.destroy();
      element.remove();
      setMap(null);
    };
  }, [mapAttempt]);

  // 지도 요소의 크기가 바뀌면 지도에 알린다. 모바일·데스크톱 배치가 바뀔 때와, 위의 알림 띠를 닫아 지도 칸이 커질 때다.
  useEffect(() => {
    const host = hostRef.current;
    if (!map || !host) return;
    const observer = new ResizeObserver(() => map.relayout());
    observer.observe(host);
    return () => observer.disconnect();
  }, [map]);

  useEffect(() => {
    if (!map) return;
    // 제보 중에는 핀을 눌러도 화면을 옮기지 않는다. 위치를 찍다가 실수로 상세로 나가지 않게 하기 위해서다.
    const offMarker = map.onMarkerClick((id) => {
      if (reporting) return;
      if (basecampMode) {
        // 베이스캠프 화면에서는 마커가 장소의 베이스캠프 목록을 거르는 스위치다. 같은 마커를 다시 누르면 푼다.
        setSpotFilterId((previous) => (previous === id ? null : id));
        if (basecampId !== undefined) navigate('/basecamps');
        return;
      }
      navigate(`/spots/${id}`);
    });
    const offCluster = map.onClusterClick((cluster) => {
      map.setCenter(cluster.position, Math.max(1, map.getLevel() - CLUSTER_ZOOM_STEP));
    });
    return () => {
      offMarker();
      offCluster();
    };
  }, [map, reporting, basecampMode, basecampId]);

  // 제보 화면에서만 지도를 눌러 박지 위치를 찍는다. 화면을 벗어나면 임시 핀을 지우고 찍은 위치도 버린다.
  useEffect(() => {
    if (!map || !reporting || submitted) return;
    const offClick = map.onMapClick(setDraft);
    return () => {
      offClick();
      map.showDraft(null);
      setDraft(null);
    };
  }, [map, reporting, submitted]);

  useEffect(() => {
    map?.showDraft(draft);
  }, [map, draft]);

  useEffect(() => {
    map?.setSelected(basecampMode ? spotFilterId : opening ? (openSpotId ?? null) : (spotId ?? null));
  }, [map, spotId, basecampMode, spotFilterId, opening, openSpotId]);

  useEffect(() => {
    map?.setTerrain(terrainOn);
  }, [map, terrainOn]);

  // 상세 화면에서는 장소 이름을 불러온 뒤 상세 패널이 제목을 바꾼다.
  useEffect(() => {
    if (spotId !== undefined || basecampId !== undefined) return;
    if (reporting) document.title = '박지 제보 · 피치맵';
    else if (opening) document.title = '베이스캠프 열기 · 피치맵';
    else if (basecampMode) document.title = '베이스캠프 · 피치맵';
    else document.title = '지도 · 피치맵';
  }, [spotId, basecampId, reporting, opening, basecampMode]);

  let sheetLabel = '장소 목록';
  if (reporting) sheetLabel = '박지 제보';
  else if (opening) sheetLabel = '베이스캠프 열기';
  else if (basecampId !== undefined) sheetLabel = '베이스캠프 상세';
  else if (basecampMode) sheetLabel = '베이스캠프 목록';
  else if (spotId !== undefined) sheetLabel = '장소 상세';

  function retryMap() {
    setMapFailure(null);
    setMapAttempt((value) => value + 1);
  }

  return (
    <div className="flex h-dvh w-full flex-col overflow-hidden bg-paper">
      <AnnouncementBar />
      <SiteHeader fluid className="hidden lg:block" />
      <div className="relative min-h-0 flex-1 lg:flex">
        <Sheet
          aboveBottomNav={!contentOpen}
          snap={snap}
          onSnapChange={setSnap}
          label={sheetLabel}
          header={
            contentOpen ? undefined : basecampMode ? <BasecampListHeader result={basecamps} /> : <ListHeader area={area} />
          }
        >
          {opening && openSpotId !== undefined ? (
            <BasecampOpenPanel key={openSpotId} spotId={openSpotId} onBack={() => navigate(`/spots/${openSpotId}`)} />
          ) : basecampId !== undefined ? (
            <BasecampDetailPanel key={basecampId} basecampId={basecampId} onBack={() => navigate('/basecamps')} />
          ) : basecampMode ? (
            <BasecampListPanel
              result={basecamps}
              filters={filters}
              onFiltersChange={setFilters}
              spotFilterId={spotFilterId}
              onClearSpotFilter={() => setSpotFilterId(null)}
              waitingForMap={map === null && mapFailure === null}
            />
          ) : reporting ? (
            <BakjiReportPanel
              draft={draft}
              onCancel={() => navigate('/map')}
              onSubmitted={() => {
                // 새 박지가 지도에 보이도록 마커를 다시 불러오고, 등록이 끝났으니 임시 핀은 치운다.
                setSubmitted(true);
                setDraft(null);
                area.retry();
              }}
            />
          ) : spotId === undefined ? (
            <SpotList area={area} waitingForMap={map === null && mapFailure === null} />
          ) : (
            <SpotDetailPanel key={spotId} spotId={spotId} onBack={() => navigate('/map')} />
          )}
        </Sheet>

        {/* z-0으로 쌓임 맥락을 따로 만들어서, 지도와 위에 뜬 버튼이 모바일 시트보다 아래에 그려지게 한다. */}
        <div className="absolute inset-0 z-0 lg:relative lg:flex-1">
          <div ref={hostRef} role="region" aria-label="지도" className="absolute inset-0" />

          {mapFailure && (
            <div className="absolute inset-0 flex items-center justify-center bg-paper p-4">
              <div className="w-full max-w-sm">
                <Notice tone="danger" title="지도를 불러오지 못했어요">
                  <div className="flex flex-col gap-3">
                    <p>{mapFailure.message}</p>
                    {mapFailure.reason === 'NO_KEY' ? (
                      <p>저장소 루트의 .env에 VITE_KAKAO_JS_KEY를 넣고 개발 서버를 다시 켜 주세요.</p>
                    ) : (
                      <div>
                        <Button variant="secondary" onClick={retryMap}>
                          다시 시도
                        </Button>
                      </div>
                    )}
                  </div>
                </Notice>
              </div>
            </div>
          )}

          <div className="pointer-events-none absolute inset-x-0 top-0 z-10 flex flex-col gap-2 px-4 pt-3">
            <h1 className="sr-only">피치맵 지도</h1>
            <div className="flex items-start justify-between gap-2">
              {/* 데스크톱은 위의 머리글이 서비스 이름과 메뉴를 보여 주므로, 지도 위에는 모바일에서만 이름을 띄운다. */}
              <Link to="/" className={`${CHIP_CLASS} font-serif text-lg font-semibold text-forest-deep lg:invisible`}>
                피치맵
              </Link>
              <div className="flex flex-wrap justify-end gap-2">
                <NotificationBellLink className="pointer-events-auto inline-flex min-h-11 items-center gap-2 rounded-control border border-contour bg-card px-3 text-ink hover:bg-paper-deep lg:hidden" />
                {map && (
                  <>
                    {!reporting && !basecampMode && !opening && <ReportButton />}
                    <button
                      type="button"
                      aria-pressed={terrainOn}
                      onClick={() => setTerrainOn((value) => !value)}
                      className={
                        terrainOn
                          ? 'pointer-events-auto inline-flex min-h-11 items-center gap-2 rounded-control border border-forest bg-forest-soft px-3 text-base font-semibold text-forest-deep'
                          : 'pointer-events-auto inline-flex min-h-11 items-center gap-2 rounded-control border border-contour bg-card px-3 text-base font-semibold text-ink hover:bg-paper-deep'
                      }
                    >
                      <Icon name="terrain" size={20} />
                      지형
                    </button>
                  </>
                )}
              </div>
            </div>
            <MapSafetyNotice />
          </div>
        </div>
      </div>
      {/* 상세·폼이 열려 있을 때는 시트 아래 고정 버튼 자리를 지키려고 하단 탭을 숨긴다. 패널의 뒤로 가기로 목록에 돌아오면 다시 보인다. */}
      {!contentOpen && <BottomTabBar />}
    </div>
  );
}

// 서버는 장소가 너무 많으면 markers를 비우고 clusters만 준다. 그래서 묶음일 때는 묶음 개수의 합이 이 지역 장소 수다.
function totalCount(area: SpotsInView): number {
  if (area.markers.length > 0) return area.markers.length;
  return area.clusters.reduce((sum, cluster) => sum + cluster.count, 0);
}

function ListHeader({ area }: { area: SpotsInView }) {
  const counted = area.status === 'ready' || area.markers.length > 0 || area.clusters.length > 0;
  return (
    <h2 className="font-serif text-lg text-ink">
      이 지역 장소{counted && <span className="tabular-nums"> {totalCount(area)}곳</span>}
    </h2>
  );
}

function BasecampListHeader({ result }: { result: BasecampsInView }) {
  const counted = result.status === 'ready' || result.items.length > 0;
  return (
    <h2 className="font-serif text-lg text-ink">
      이 지역 베이스캠프
      {counted && (
        <span className="tabular-nums">
          {' '}
          {result.items.length}
          {result.hasNext ? '개 이상' : '개'}
        </span>
      )}
    </h2>
  );
}

function SpotList({ area, waitingForMap }: { area: SpotsInView; waitingForMap: boolean }) {
  const showSkeleton = useDelayedFlag(area.status === 'loading' || waitingForMap);

  if (showSkeleton) {
    return (
      <div aria-busy="true" className="flex flex-col">
        {[0, 1, 2].map((index) => (
          <div key={index} className="flex min-h-14 items-center gap-3 border-b border-contour px-4 py-3">
            <Skeleton className="size-8 shrink-0" />
            <div className="flex flex-1 flex-col gap-2">
              <Skeleton className="h-4 w-2/3" />
              <Skeleton className="h-3 w-1/3" />
            </div>
          </div>
        ))}
      </div>
    );
  }

  return (
    <div className="flex flex-col">
      {area.status === 'error' && area.error && (
        <div className="flex flex-col gap-3 px-4 pt-4">
          <Notice tone="danger" title="장소를 불러오지 못했어요">
            {area.error}
          </Notice>
          <div>
            <Button variant="secondary" onClick={area.retry}>
              다시 불러오기
            </Button>
          </div>
        </div>
      )}

      {area.status === 'ready' && area.markers.length === 0 && area.clusters.length > 0 && (
        <div className="px-4 pt-4">
          <Notice tone="info" title="장소가 많아서 묶어서 보여 주고 있어요">
            <p>
              지도를 확대하면 장소를 하나씩 볼 수 있어요.{' '}
              <span className="tabular-nums">모두 {totalCount(area)}곳이에요.</span>
            </p>
          </Notice>
        </div>
      )}

      {area.status === 'ready' && area.markers.length === 0 && area.clusters.length === 0 && (
        <EmptyState title="이 지역에는 장소가 없어요" description="지도를 옮기거나 축소해 보세요." />
      )}

      {area.markers.length > 0 && (
        <ul aria-label="이 지역 장소">
          {area.markers.map((marker) => (
            <SpotListRow key={marker.spotId} marker={marker} />
          ))}
        </ul>
      )}
    </div>
  );
}

// 비회원은 로그인한 뒤 제보 화면으로 돌아오게 보낸다. 이메일 인증 여부는 제보 화면이 안내한다.
function ReportButton() {
  const session = useSession();
  const anonymous = session.status === 'anonymous' || (session.status === 'authenticated' && session.me === null);
  const target = anonymous ? withNext('/login', '/bakjis/new') : '/bakjis/new';

  return (
    <Link
      to={target}
      className="pointer-events-auto inline-flex min-h-11 items-center gap-2 rounded-control border border-contour bg-card px-3 text-base font-semibold text-ink hover:bg-paper-deep"
    >
      <Icon name="backpack" size={20} />
      박지 제보
    </Link>
  );
}
