import { loadKakaoMaps } from './kakaoLoader';
import { createClusterButton, createDraftPin, createSpotPin } from './markers';
import type { SpotPin } from './markers';
import type { Bounds, ClusterData, LatLng, MapView, SpotMarkerData } from './types';

// 선택한 핀이 다른 핀과 묶음 위로 올라오게 겹침 순서를 둔다.
const CLUSTER_Z_INDEX = 1;
const PIN_Z_INDEX = 2;
const SELECTED_PIN_Z_INDEX = 3;
const DRAFT_PIN_Z_INDEX = 4;

interface DrawnPin {
  overlay: kakao.maps.CustomOverlay;
  pin: SpotPin;
}

export async function createMapView(
  container: HTMLElement,
  initial: { center: LatLng; level: number },
): Promise<MapView> {
  await loadKakaoMaps();

  const toKakaoLatLng = (position: LatLng) => new kakao.maps.LatLng(position.lat, position.lng);

  const map = new kakao.maps.Map(container, { center: toKakaoLatLng(initial.center), level: initial.level });

  const idleListeners = new Set<() => void>();
  const markerListeners = new Set<(id: number) => void>();
  const clusterListeners = new Set<(cluster: ClusterData) => void>();
  const mapClickListeners = new Set<(position: LatLng) => void>();

  // 구독 해제가 반복 중에 일어나도 이번 알림은 모두 받도록 복사본을 돈다.
  const handleIdle = () => {
    for (const listener of [...idleListeners]) listener();
  };
  kakao.maps.event.addListener(map, 'idle', handleIdle);

  // 핀과 묶음은 clickable 오버레이라서 그 위를 누른 클릭은 지도 이벤트로 오지 않는다.
  const handleMapClick = (event: kakao.maps.MouseEvent) => {
    const position = { lat: event.latLng.getLat(), lng: event.latLng.getLng() };
    for (const listener of [...mapClickListeners]) listener(position);
  };
  kakao.maps.event.addListener(map, 'click', handleMapClick);

  const handleMarkerClick = (id: number) => {
    for (const listener of [...markerListeners]) listener(id);
  };
  const handleClusterClick = (cluster: ClusterData) => {
    for (const listener of [...clusterListeners]) listener(cluster);
  };

  let pins = new Map<number, DrawnPin>();
  let clusterOverlays: kakao.maps.CustomOverlay[] = [];
  let draftOverlay: kakao.maps.CustomOverlay | null = null;
  let selectedId: number | null = null;
  let terrainOn = false;
  let destroyed = false;

  function clearOverlays() {
    for (const { overlay } of pins.values()) overlay.setMap(null);
    for (const overlay of clusterOverlays) overlay.setMap(null);
    pins = new Map();
    clusterOverlays = [];
  }

  function subscribe<T>(listeners: Set<T>, listener: T): () => void {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  }

  return {
    getBounds(): Bounds {
      const bounds = map.getBounds();
      const sw = bounds.getSouthWest();
      const ne = bounds.getNorthEast();
      return { sw: { lat: sw.getLat(), lng: sw.getLng() }, ne: { lat: ne.getLat(), lng: ne.getLng() } };
    },

    getLevel() {
      return map.getLevel();
    },

    setCenter(position: LatLng, level?: number) {
      map.setCenter(toKakaoLatLng(position));
      if (level !== undefined && level !== map.getLevel()) {
        map.setLevel(level, { animate: true });
      }
    },

    showMarkers(markers: SpotMarkerData[], clusters: ClusterData[]) {
      if (destroyed) return;
      clearOverlays();

      for (const marker of markers) {
        // 같은 id가 두 번 오면 앞의 오버레이를 잃어버려 지울 수 없게 되므로 처음 것만 그린다.
        if (pins.has(marker.id)) continue;
        const pin = createSpotPin(marker, handleMarkerClick);
        const selected = marker.id === selectedId;
        pin.setSelected(selected);
        const overlay = new kakao.maps.CustomOverlay({
          map,
          content: pin.element,
          position: toKakaoLatLng(marker.position),
          clickable: true,
          xAnchor: 0.5,
          // 핀의 뾰족한 끝이 좌표에 오도록 요소 아래쪽 끝을 기준으로 놓는다.
          yAnchor: 1,
          zIndex: selected ? SELECTED_PIN_Z_INDEX : PIN_Z_INDEX,
        });
        pins.set(marker.id, { overlay, pin });
      }

      for (const cluster of clusters) {
        clusterOverlays.push(
          new kakao.maps.CustomOverlay({
            map,
            content: createClusterButton(cluster, handleClusterClick),
            position: toKakaoLatLng(cluster.position),
            clickable: true,
            xAnchor: 0.5,
            yAnchor: 0.5,
            zIndex: CLUSTER_Z_INDEX,
          }),
        );
      }
    },

    setSelected(id: number | null) {
      if (id === selectedId) return;
      const previous = selectedId === null ? undefined : pins.get(selectedId);
      if (previous) {
        previous.pin.setSelected(false);
        previous.overlay.setZIndex(PIN_Z_INDEX);
      }
      // 지금 화면에 없는 id라도 기억해 두었다가, 다음 showMarkers에서 그 핀을 선택 상태로 그린다.
      selectedId = id;
      const next = id === null ? undefined : pins.get(id);
      if (next) {
        next.pin.setSelected(true);
        next.overlay.setZIndex(SELECTED_PIN_Z_INDEX);
      }
    },

    onIdle(listener: () => void) {
      return subscribe(idleListeners, listener);
    },

    onMarkerClick(listener: (id: number) => void) {
      return subscribe(markerListeners, listener);
    },

    onClusterClick(listener: (cluster: ClusterData) => void) {
      return subscribe(clusterListeners, listener);
    },

    onMapClick(listener: (position: LatLng) => void) {
      return subscribe(mapClickListeners, listener);
    },

    // 임시 핀은 오버레이 하나를 만들어 두고 위치만 옮긴다.
    showDraft(position: LatLng | null) {
      if (destroyed) return;
      if (position === null) {
        draftOverlay?.setMap(null);
        return;
      }
      if (draftOverlay === null) {
        draftOverlay = new kakao.maps.CustomOverlay({
          content: createDraftPin(),
          position: toKakaoLatLng(position),
          clickable: false,
          xAnchor: 0.5,
          yAnchor: 1,
          zIndex: DRAFT_PIN_Z_INDEX,
        });
      } else {
        draftOverlay.setPosition(toKakaoLatLng(position));
      }
      draftOverlay.setMap(map);
    },

    // 지형도는 기본 지도 위에 겹치는 타일이다. 이미 켜져 있으면 다시 겹치지 않는다.
    setTerrain(on: boolean) {
      if (destroyed || on === terrainOn) return;
      if (on) {
        map.addOverlayMapTypeId(kakao.maps.MapTypeId.TERRAIN);
      } else {
        map.removeOverlayMapTypeId(kakao.maps.MapTypeId.TERRAIN);
      }
      terrainOn = on;
    },

    relayout() {
      map.relayout();
    },

    destroy() {
      if (destroyed) return;
      destroyed = true;
      kakao.maps.event.removeListener(map, 'idle', handleIdle);
      kakao.maps.event.removeListener(map, 'click', handleMapClick);
      clearOverlays();
      draftOverlay?.setMap(null);
      draftOverlay = null;
      idleListeners.clear();
      markerListeners.clear();
      clusterListeners.clear();
      mapClickListeners.clear();
    },
  };
}
