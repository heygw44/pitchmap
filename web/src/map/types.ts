// 지도 래퍼의 공개 계약이다. 화면 코드는 이 타입만 쓰고, 카카오맵 SDK는 src/map 안에서만 다룬다.
// 그래서 지도 SDK를 바꿀 때 이 폴더만 고치면 된다.

export interface LatLng {
  lat: number;
  lng: number;
}

export interface Bounds {
  sw: LatLng;
  ne: LatLng;
}

export type MarkerKind = 'CAMPSITE' | 'FOREST' | 'BAKJI';

export interface SpotMarkerData {
  id: number;
  kind: MarkerKind;
  name: string;
  position: LatLng;
  warning: boolean;
  closed: boolean;
}

// 서버가 묶어서 보낸 장소 묶음이다. 클라이언트에서 다시 묶지 않는다.
export interface ClusterData {
  position: LatLng;
  count: number;
}

export interface MapView {
  getBounds(): Bounds;
  getLevel(): number;
  // level을 넘기고 지금 확대 수준과 다르면 확대·축소를 애니메이션으로 보여준다.
  setCenter(position: LatLng, level?: number): void;
  // 지금 그려진 마커와 묶음을 모두 지우고 새로 그린다.
  showMarkers(markers: SpotMarkerData[], clusters: ClusterData[]): void;
  // 선택한 id는 showMarkers를 다시 불러도 유지된다.
  setSelected(id: number | null): void;
  // 지도 이동·확대가 멈출 때마다 부른다. 반환한 함수를 부르면 구독을 끊는다.
  onIdle(listener: () => void): () => void;
  onMarkerClick(listener: (id: number) => void): () => void;
  onClusterClick(listener: (cluster: ClusterData) => void): () => void;
  // 지도의 빈 곳을 눌렀을 때 그 위치를 알려 준다. 마커와 묶음을 누른 클릭은 오지 않는다.
  onMapClick(listener: (position: LatLng) => void): () => void;
  // 제보할 위치에 임시 핀 하나를 그린다. null이면 지운다.
  showDraft(position: LatLng | null): void;
  setTerrain(on: boolean): void;
  // 지도를 담은 요소의 크기가 바뀐 뒤에 부른다.
  relayout(): void;
  // 오버레이와 이벤트 구독을 모두 지운다.
  destroy(): void;
}
