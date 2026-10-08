import { useCallback, useEffect, useRef, useState } from 'react';
import { toUserMessage } from '../../api/errors';
import { searchBasecamps } from '../../api/basecamps';
import type { BasecampSearchItem } from '../../api/types';
import type { MapView, SpotMarkerData } from '../../map/types';

// 첫 페이지와 "더 보기"가 한 번에 받는 건수다. 서버가 허용하는 최대값이다.
const PAGE_SIZE = 50;

type SearchStatus = 'idle' | 'loading' | 'ready' | 'error';

// 빈 문자열은 "고르지 않음"이다.
export type BasecampFilters = {
  fromDate: string;
  toDate: string;
  hasVacancy: boolean;
};

export type BasecampsInView = {
  items: BasecampSearchItem[];
  hasNext: boolean;
  status: SearchStatus;
  error: string | null;
  loadingMore: boolean;
  moreError: string | null;
  retry(): void;
  loadMore(): void;
};

type SearchState = {
  items: BasecampSearchItem[];
  page: number;
  hasNext: boolean;
  status: SearchStatus;
  error: string | null;
  loadingMore: boolean;
  moreError: string | null;
};

const INITIAL_STATE: SearchState = {
  items: [],
  page: 0,
  hasNext: false,
  status: 'idle',
  error: null,
  loadingMore: false,
  moreError: null,
};

type Area = { swLat: number; swLng: number; neLat: number; neLng: number };

// 한 장소에 베이스캠프가 여럿이어도 마커는 장소마다 하나만 그리고, 그 장소의 베이스캠프 수를 마커에 적는다.
function toMarkers(items: BasecampSearchItem[]): SpotMarkerData[] {
  const bySpot = new Map<number, SpotMarkerData>();
  for (const { spot } of items) {
    const existing = bySpot.get(spot.spotId);
    if (existing) {
      existing.count = (existing.count ?? 0) + 1;
    } else {
      bySpot.set(spot.spotId, {
        id: spot.spotId,
        kind: spot.type,
        name: spot.name,
        position: { lat: spot.lat, lng: spot.lng },
        warning: false,
        closed: false,
        count: 1,
      });
    }
  }
  return [...bySpot.values()];
}

// 지도 이동이 멈출 때마다 지금 보이는 영역의 모집 중 베이스캠프를 받는다.
// 빠르게 여러 번 움직이면 앞선 요청을 취소해서 마지막 영역의 응답만 화면에 남긴다.
// enabled가 false이면 아무것도 보내지 않고 지도의 마커도 건드리지 않는다.
// viewerKey가 바뀌면(로그인, 로그아웃) 신청 가능 여부가 달라지므로 다시 받는다.
export function useBasecampsInView(
  map: MapView | null,
  enabled: boolean,
  filters: BasecampFilters,
  viewerKey: number | null,
): BasecampsInView {
  const [state, setState] = useState<SearchState>(INITIAL_STATE);
  const controllerRef = useRef<AbortController | null>(null);
  const moreControllerRef = useRef<AbortController | null>(null);
  const areaRef = useRef<Area | null>(null);
  const stateRef = useRef<SearchState>(INITIAL_STATE);
  const loadRef = useRef<() => void>(() => {});
  const moreRef = useRef<() => void>(() => {});

  const { fromDate, toDate, hasVacancy } = filters;

  useEffect(() => {
    stateRef.current = state;
  }, [state]);

  useEffect(() => {
    if (!map || !enabled) return;
    const filterQuery = {
      fromDate: fromDate === '' ? undefined : fromDate,
      toDate: toDate === '' ? undefined : toDate,
      hasVacancy: hasVacancy ? true : undefined,
      size: PAGE_SIZE,
    };

    // 요청만 보낸다. 상태는 응답이 온 뒤에만 바꾼다.
    const request = () => {
      controllerRef.current?.abort();
      moreControllerRef.current?.abort();
      const controller = new AbortController();
      controllerRef.current = controller;
      const { sw, ne } = map.getBounds();
      const area: Area = { swLat: sw.lat, swLng: sw.lng, neLat: ne.lat, neLng: ne.lng };
      areaRef.current = area;

      searchBasecamps({ ...area, ...filterQuery, page: 0 }, controller.signal).then(
        (response) => {
          if (controller.signal.aborted) return;
          map.showMarkers(toMarkers(response.content), []);
          setState({
            items: response.content,
            page: response.page,
            hasNext: response.hasNext,
            status: 'ready',
            error: null,
            loadingMore: false,
            moreError: null,
          });
        },
        (error: unknown) => {
          // 취소한 요청의 실패는 새 요청이 이어받으므로 화면에 반영하지 않는다.
          if (controller.signal.aborted) return;
          setState((previous) => ({ ...previous, status: 'error', error: toUserMessage(error), loadingMore: false }));
        },
      );
    };

    const reload = () => {
      setState((previous) => ({ ...previous, status: 'loading', error: null, moreError: null }));
      request();
    };

    // 다음 페이지는 첫 페이지를 받은 영역 그대로 요청한다. 그 사이 지도를 움직였다면 새 영역의 첫 페이지가 이 요청을 취소한다.
    const loadMore = () => {
      const current = stateRef.current;
      const area = areaRef.current;
      if (!area || !current.hasNext || current.loadingMore || current.status !== 'ready') return;
      const controller = new AbortController();
      moreControllerRef.current = controller;
      setState((previous) => ({ ...previous, loadingMore: true, moreError: null }));

      searchBasecamps({ ...area, ...filterQuery, page: current.page + 1 }, controller.signal).then(
        (response) => {
          if (controller.signal.aborted) return;
          const known = new Set(stateRef.current.items.map((item) => item.basecampId));
          const items = [...stateRef.current.items, ...response.content.filter((item) => !known.has(item.basecampId))];
          map.showMarkers(toMarkers(items), []);
          setState({
            items,
            page: response.page,
            hasNext: response.hasNext,
            status: 'ready',
            error: null,
            loadingMore: false,
            moreError: null,
          });
        },
        (error: unknown) => {
          if (controller.signal.aborted) return;
          setState((previous) => ({ ...previous, loadingMore: false, moreError: toUserMessage(error) }));
        },
      );
    };

    loadRef.current = reload;
    moreRef.current = loadMore;
    const unsubscribe = map.onIdle(reload);
    // 지도가 준비됐거나 조건이 바뀌었을 때는 idle을 기다리지 않고 바로 받는다. 이때 상태는 아래에서 불러오는 중으로 본다.
    request();

    return () => {
      unsubscribe();
      controllerRef.current?.abort();
      moreControllerRef.current?.abort();
      controllerRef.current = null;
      moreControllerRef.current = null;
      areaRef.current = null;
      loadRef.current = () => {};
      moreRef.current = () => {};
      setState(INITIAL_STATE);
    };
  }, [map, enabled, fromDate, toDate, hasVacancy, viewerKey]);

  const retry = useCallback(() => loadRef.current(), []);
  const loadMore = useCallback(() => moreRef.current(), []);

  // 켜진 뒤 첫 응답 전에는 idle로 남아 있으므로 불러오는 중으로 돌려준다.
  const status: SearchStatus = map && enabled && state.status === 'idle' ? 'loading' : state.status;
  return { ...state, status, retry, loadMore };
}
