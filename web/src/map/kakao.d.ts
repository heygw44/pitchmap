// 카카오맵 JS SDK 중에서 지도 래퍼가 쓰는 API만 타입으로 적는다.
// SDK는 script 태그로 불러와 전역 kakao 객체로 들어오므로 모듈이 아닌 전역 선언으로 둔다.

declare namespace kakao.maps {
  class LatLng {
    constructor(lat: number, lng: number);
    getLat(): number;
    getLng(): number;
  }

  class LatLngBounds {
    constructor(sw?: LatLng, ne?: LatLng);
    getSouthWest(): LatLng;
    getNorthEast(): LatLng;
  }

  // SDK 안에서는 숫자 상수다. 값은 kakao.maps.MapTypeId에서 이름으로 꺼내 쓴다.
  type MapTypeId = number;

  const MapTypeId: {
    readonly ROADMAP: MapTypeId;
    readonly SKYVIEW: MapTypeId;
    readonly HYBRID: MapTypeId;
    readonly TERRAIN: MapTypeId;
  };

  interface MapOptions {
    center: LatLng;
    level?: number;
  }

  interface SetLevelOptions {
    animate?: boolean | { duration: number };
    anchor?: LatLng;
  }

  class Map {
    constructor(container: HTMLElement, options: MapOptions);
    setCenter(latlng: LatLng): void;
    getCenter(): LatLng;
    setLevel(level: number, options?: SetLevelOptions): void;
    getLevel(): number;
    getBounds(): LatLngBounds;
    relayout(): void;
    addOverlayMapTypeId(mapTypeId: MapTypeId): void;
    removeOverlayMapTypeId(mapTypeId: MapTypeId): void;
  }

  interface CustomOverlayOptions {
    clickable?: boolean;
    content: HTMLElement | string;
    map?: Map;
    position: LatLng;
    xAnchor?: number;
    yAnchor?: number;
    zIndex?: number;
  }

  class CustomOverlay {
    constructor(options: CustomOverlayOptions);
    setMap(map: Map | null): void;
    getMap(): Map | null;
    setZIndex(zIndex: number): void;
    getZIndex(): number;
    setPosition(position: LatLng): void;
  }

  interface MouseEvent {
    latLng: LatLng;
  }

  namespace event {
    function addListener(target: Map, type: 'idle', handler: () => void): void;
    function removeListener(target: Map, type: 'idle', handler: () => void): void;
    function addListener(target: Map, type: 'click', handler: (event: MouseEvent) => void): void;
    function removeListener(target: Map, type: 'click', handler: (event: MouseEvent) => void): void;
  }

  // 스크립트 주소에 autoload=false를 붙여 불러왔을 때, SDK 나머지 파일을 받은 뒤 callback을 부른다.
  function load(callback: () => void): void;
}

// 스크립트를 불러오기 전에는 전역 kakao가 없으므로 window에서 있는지 먼저 확인한다.
interface Window {
  kakao?: typeof kakao;
}
