import { useCallback, useEffect, useRef, useState } from 'react';
import { fetchSpotsInArea } from '../../api/spots';
import { toUserMessage } from '../../api/errors';
import type { SpotCluster, SpotMarker } from '../../api/types';
import type { MapView } from '../../map/types';

type AreaStatus = 'idle' | 'loading' | 'ready' | 'error';

export type SpotsInView = {
  markers: SpotMarker[];
  clusters: SpotCluster[];
  status: AreaStatus;
  error: string | null;
  retry(): void;
};

type AreaState = {
  markers: SpotMarker[];
  clusters: SpotCluster[];
  status: AreaStatus;
  error: string | null;
};

const INITIAL_STATE: AreaState = { markers: [], clusters: [], status: 'idle', error: null };

// 지도 이동이 멈출 때마다 지금 보이는 영역의 장소를 받는다.
// 빠르게 여러 번 움직이면 앞선 요청을 취소해서 마지막 영역의 응답만 화면에 남긴다.
// 서버가 묶음으로 주면 그대로 그리고, 클라이언트에서 다시 묶지 않는다.
export function useSpotsInView(map: MapView | null): SpotsInView {
  const [state, setState] = useState<AreaState>(INITIAL_STATE);
  const controllerRef = useRef<AbortController | null>(null);
  const loadRef = useRef<() => void>(() => {});

  useEffect(() => {
    if (!map) return;
    // 요청만 보낸다. 상태는 응답이 온 뒤에만 바꾼다.
    const request = () => {
      controllerRef.current?.abort();
      const controller = new AbortController();
      controllerRef.current = controller;
      const { sw, ne } = map.getBounds();
      const { width, height } = map.getSize();

      fetchSpotsInArea(
        { swLat: sw.lat, swLng: sw.lng, neLat: ne.lat, neLng: ne.lng, zoom: map.getLevel(), width, height },
        controller.signal,
      ).then(
        (response) => {
          if (controller.signal.aborted) return;
          map.showMarkers(
            response.markers.map((marker) => ({
              id: marker.spotId,
              kind: marker.type,
              name: marker.name,
              position: { lat: marker.lat, lng: marker.lng },
              warning: marker.parkWarning,
              closed: marker.closedNow,
            })),
            response.clusters.map((cluster) => ({
              position: { lat: cluster.lat, lng: cluster.lng },
              count: cluster.count,
            })),
          );
          setState({ markers: response.markers, clusters: response.clusters, status: 'ready', error: null });
        },
        (error: unknown) => {
          // 취소한 요청의 실패는 새 요청이 이어받으므로 화면에 반영하지 않는다.
          if (controller.signal.aborted) return;
          setState((previous) => ({ ...previous, status: 'error', error: toUserMessage(error) }));
        },
      );
    };

    // 지도를 움직였거나 다시 불러오기를 눌렀을 때는 이전 결과를 남긴 채 불러오는 중으로 바꾼다.
    const reload = () => {
      setState((previous) => ({ ...previous, status: 'loading', error: null }));
      request();
    };

    loadRef.current = reload;
    const unsubscribe = map.onIdle(reload);
    // 지도가 처음 준비됐을 때는 idle을 기다리지 않고 바로 한 번 받는다. 이때 상태는 아래에서 불러오는 중으로 본다.
    request();

    return () => {
      unsubscribe();
      controllerRef.current?.abort();
      controllerRef.current = null;
      loadRef.current = () => {};
      setState(INITIAL_STATE);
    };
  }, [map]);

  const retry = useCallback(() => loadRef.current(), []);

  // 지도가 준비된 뒤 첫 응답 전에는 idle로 남아 있으므로 불러오는 중으로 돌려준다.
  const status: AreaStatus = map && state.status === 'idle' ? 'loading' : state.status;
  return { ...state, status, retry };
}
