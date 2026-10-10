// 아이콘 모양을 React 컴포넌트가 아니라 SVG 문자열로 둔다.
// 지도 마커는 React 밖에서 HTML 문자열로 그리기 때문에, 마커와 화면의 아이콘이 같은 모양을 함께 쓰려면 문자열이어야 한다.
// 모든 값은 viewBox 0 0 24 24 기준의 선 아이콘이다. 선 색과 굵기는 감싸는 svg 요소가 정하므로 여기서는 stroke·fill을 쓰지 않는다.

export type IconName =
  | 'tent'
  | 'tree'
  | 'backpack'
  | 'alert'
  | 'closed'
  | 'check'
  | 'search'
  | 'terrain'
  | 'chevronLeft'
  | 'chevronRight'
  | 'close'
  | 'info'
  | 'bell'
  | 'map'
  | 'calendar'
  | 'user'
  | 'chat'
  | 'heart'
  | 'image'
  | 'arrowUp'
  | 'eye'
  | 'eyeOff';

export const ICON_PATHS: Record<IconName, string> = {
  tent:
    '<path d="M3 20h18"/>' +
    '<path d="M12 5 4.5 20"/>' +
    '<path d="m12 5 7.5 15"/>' +
    '<path d="m12 12.5-3 7.5"/>' +
    '<path d="m12 12.5 3 7.5"/>' +
    '<path d="M10.5 3.5 12 5l1.5-1.5"/>',
  tree: '<path d="M12 3 7 10h3l-4 6h12l-4-6h3z"/>' + '<path d="M12 16v5"/>',
  backpack:
    '<path d="M6 10a5 5 0 0 1 5-5h2a5 5 0 0 1 5 5v9a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2z"/>' +
    '<path d="M10 5V4a2 2 0 0 1 4 0v1"/>' +
    '<path d="M6 11h12"/>' +
    '<path d="M10.5 11v2h3v-2"/>' +
    '<path d="M9 21v-4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v4"/>',
  alert:
    '<path d="M10.3 3.9 2.4 18a2 2 0 0 0 1.7 3h15.8a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"/>' +
    '<path d="M12 9v4"/>' +
    '<path d="M12 17h.01"/>',
  closed: '<circle cx="12" cy="12" r="9"/>' + '<path d="m5.6 5.6 12.8 12.8"/>',
  check: '<path d="m5 12.5 4.5 4.5L19 7.5"/>',
  search: '<circle cx="11" cy="11" r="6.5"/>' + '<path d="m20 20-4.4-4.4"/>',
  terrain:
    '<path d="M2 20 9 7l4.5 8.3"/>' +
    '<path d="m11 20 5.5-9L22 20"/>' +
    '<path d="M2 20h20"/>' +
    '<path d="M5.8 13c2 1 4.4 1 6.4 0"/>',
  chevronLeft: '<path d="m15 18-6-6 6-6"/>',
  chevronRight: '<path d="m9 18 6-6-6-6"/>',
  close: '<path d="M18 6 6 18"/>' + '<path d="m6 6 12 12"/>',
  info: '<circle cx="12" cy="12" r="9"/>' + '<path d="M12 11v5"/>' + '<path d="M12 8h.01"/>',
  bell:
    '<path d="M6 17V11a6 6 0 0 1 12 0v6"/>' +
    '<path d="M4 17h16"/>' +
    '<path d="M10 20.5a2 2 0 0 0 4 0"/>',
  map: '<path d="M9 4 3 6.5v13L9 17l6 2.5 6-2.5v-13L15 6.5z"/>' + '<path d="M9 4v13"/>' + '<path d="M15 6.5v13"/>',
  calendar:
    '<rect x="3.5" y="5" width="17" height="15.5" rx="1.5"/>' +
    '<path d="M3.5 10h17"/>' +
    '<path d="M8 3v4"/>' +
    '<path d="M16 3v4"/>',
  user: '<circle cx="12" cy="8" r="4"/>' + '<path d="M4.5 20.5a7.5 7.5 0 0 1 15 0"/>',
  chat:
    '<path d="M4 5.5h16a1 1 0 0 1 1 1V16a1 1 0 0 1-1 1h-8l-5 4v-4H4a1 1 0 0 1-1-1V6.5a1 1 0 0 1 1-1z"/>' +
    '<path d="M7.5 10h9"/>' +
    '<path d="M7.5 13h5"/>',
  heart: '<path d="M12 20.5 4.2 12.7a4.8 4.8 0 0 1 6.8-6.8l1 1 1-1a4.8 4.8 0 0 1 6.8 6.8z"/>',
  image:
    '<rect x="3.5" y="4.5" width="17" height="15" rx="1.5"/>' +
    '<circle cx="9" cy="10" r="1.75"/>' +
    '<path d="m3.5 17 5-4.5 4 3.5 3-2.5 5 4.5"/>',
  arrowUp: '<path d="M12 19V5"/>' + '<path d="m5.5 11.5 6.5-6.5 6.5 6.5"/>',
  eye: '<path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7z"/>' + '<circle cx="12" cy="12" r="3"/>',
  eyeOff:
    '<path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7z"/>' +
    '<circle cx="12" cy="12" r="3"/>' +
    '<path d="M4 4l16 16"/>',
};
