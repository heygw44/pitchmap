import { useEffect, useRef, useState } from 'react';
import { Link, navigate, useLocation } from '../../app/router';
import { Badge } from '../../components/Badge';
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
import { withNext } from '../member/nextPath';
import { useSession } from '../member/session';
import { BakjiReportPanel } from './BakjiReportPanel';
import { SpotDetailPanel } from './SpotDetailPanel';
import { SpotListRow } from './SpotListRow';
import { useSpotsInView } from './useSpotsInView';
import type { SpotsInView } from './useSpotsInView';

type SheetSnap = 'peek' | 'half' | 'full';

type MapFailure = { reason: MapLoadErrorReason; message: string };

// 처음에는 전국이 한 화면에 들어오도록 국토 가운데쯤을 중심으로 크게 축소해서 연다.
const INITIAL_VIEW = { center: { lat: 36.35, lng: 127.85 }, level: 12 };

// Sheet가 데스크톱 패널로 바뀌는 너비(lg)와 같은 값이다.
const DESKTOP_QUERY = '(min-width: 64rem)';

// 묶음을 누르면 이만큼 확대해서 안의 장소가 풀려 보이게 한다.
const CLUSTER_ZOOM_STEP = 2;

const CHIP_CLASS =
  'pointer-events-auto inline-flex min-h-11 items-center gap-2 rounded-control border border-contour bg-card px-3';

type MapPageProps = {
  spotId?: number;
  // report는 지도를 눌러 박지 위치를 찍는 제보 화면이다.
  mode?: 'browse' | 'report';
};

export function MapPage({ spotId, mode = 'browse' }: MapPageProps) {
  const reporting = mode === 'report';
  const hostRef = useRef<HTMLDivElement>(null);
  const [map, setMap] = useState<MapView | null>(null);
  const [mapFailure, setMapFailure] = useState<MapFailure | null>(null);
  const [mapAttempt, setMapAttempt] = useState(0);
  const [terrainOn, setTerrainOn] = useState(false);
  const [snap, setSnap] = useState<SheetSnap>(spotId === undefined && !reporting ? 'peek' : 'half');
  const [snapSpotId, setSnapSpotId] = useState(spotId);
  const [snapReporting, setSnapReporting] = useState(reporting);
  const [draft, setDraft] = useState<LatLng | null>(null);
  const [submitted, setSubmitted] = useState(false);
  const area = useSpotsInView(map);

  // 제보 화면을 벗어나면 이전 제보의 완료 상태를 버린다. 그래야 다시 들어왔을 때 지도를 눌러 위치를 찍을 수 있다.
  if (!reporting && submitted) setSubmitted(false);

  // 장소를 고르거나 제보 화면에 들어오면 내용이 보이도록 시트를 절반으로 열고, 목록으로 돌아오면 접는다.
  // 렌더 중에 이전 값과 비교해서 바꾸므로, 화면이 한 번 그려진 뒤 다시 그리는 일이 없다.
  if (snapSpotId !== spotId || snapReporting !== reporting) {
    setSnapSpotId(spotId);
    setSnapReporting(reporting);
    const wasOpen = snapSpotId !== undefined || snapReporting;
    const isOpen = spotId !== undefined || reporting;
    if (wasOpen !== isOpen) {
      setSnap(isOpen ? 'half' : 'peek');
    }
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

  // 모바일과 데스크톱 배치가 바뀌면 지도 요소의 크기가 달라지므로 지도에 알린다.
  useEffect(() => {
    if (!map) return;
    const query = window.matchMedia(DESKTOP_QUERY);
    const handleChange = () => map.relayout();
    query.addEventListener('change', handleChange);
    return () => query.removeEventListener('change', handleChange);
  }, [map]);

  useEffect(() => {
    if (!map) return;
    // 제보 중에는 핀을 눌러도 화면을 옮기지 않는다. 위치를 찍다가 실수로 상세로 나가지 않게 하기 위해서다.
    const offMarker = map.onMarkerClick((id) => {
      if (!reporting) navigate(`/spots/${id}`);
    });
    const offCluster = map.onClusterClick((cluster) => {
      map.setCenter(cluster.position, Math.max(1, map.getLevel() - CLUSTER_ZOOM_STEP));
    });
    return () => {
      offMarker();
      offCluster();
    };
  }, [map, reporting]);

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
    map?.setSelected(spotId ?? null);
  }, [map, spotId]);

  useEffect(() => {
    map?.setTerrain(terrainOn);
  }, [map, terrainOn]);

  // 상세 화면에서는 장소 이름을 불러온 뒤 상세 패널이 제목을 바꾼다.
  useEffect(() => {
    if (spotId === undefined) document.title = reporting ? '박지 제보 · 피치맵' : '피치맵';
  }, [spotId, reporting]);

  function retryMap() {
    setMapFailure(null);
    setMapAttempt((value) => value + 1);
  }

  return (
    <div className="relative h-dvh w-full overflow-hidden bg-paper lg:flex">
      <Sheet
        snap={snap}
        onSnapChange={setSnap}
        label={reporting ? '박지 제보' : spotId === undefined ? '장소 목록' : '장소 상세'}
        header={spotId === undefined && !reporting ? <ListHeader area={area} /> : undefined}
      >
        {reporting ? (
          <BakjiReportPanel
            draft={draft}
            onCancel={() => navigate('/')}
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
          <SpotDetailPanel key={spotId} spotId={spotId} onBack={() => navigate('/')} />
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
          <div className="flex items-start justify-between gap-2">
            <h1 className="pointer-events-auto">
              <Link to="/" className={`${CHIP_CLASS} font-serif text-lg font-semibold text-forest-deep`}>
                피치맵
              </Link>
            </h1>
            <AccountChip />
          </div>
          {map && (
            <div className="flex flex-wrap justify-end gap-2">
              {!reporting && <ReportButton />}
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
            </div>
          )}
        </div>
      </div>
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

function AccountChip() {
  const session = useSession();
  const { pathname, search } = useLocation();
  const [loggingOut, setLoggingOut] = useState(false);

  if (session.status === 'loading') return null;

  if (session.status === 'anonymous' || session.me === null) {
    return (
      <Link to={withNext('/login', pathname + search)} className={`${CHIP_CLASS} text-base font-semibold text-forest`}>
        로그인
      </Link>
    );
  }

  if (session.me.status === 'UNVERIFIED') {
    return (
      <Link to="/verify-email" className={CHIP_CLASS}>
        <Badge tone="warning" icon="alert">
          이메일 인증 필요
        </Badge>
      </Link>
    );
  }

  async function handleLogout() {
    setLoggingOut(true);
    try {
      await session.logout();
    } finally {
      setLoggingOut(false);
    }
  }

  return (
    <div className="pointer-events-auto inline-flex min-w-0 items-center rounded-control border border-contour bg-card pl-3">
      <span className="max-w-32 truncate text-sm text-ink">{session.me.nickname}</span>
      <Button variant="ghost" loading={loggingOut} onClick={handleLogout} className="px-3">
        로그아웃
      </Button>
    </div>
  );
}
