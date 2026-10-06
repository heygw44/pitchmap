// 지도 마커는 카카오맵 CustomOverlay 안에 들어가는 DOM 요소라서 React 밖에서 직접 만든다.
// Tailwind는 소스 글자를 훑어 클래스를 찾으므로 클래스 이름은 항상 완성된 문자열로 적는다.
import { ICON_PATHS } from '../components/iconPaths';
import type { IconName } from '../components/iconPaths';
import type { ClusterData, MarkerKind, SpotMarkerData } from './types';

const KIND_STYLE: Record<MarkerKind, { label: string; icon: IconName; fill: string }> = {
  CAMPSITE: { label: '공공 야영장', icon: 'tent', fill: 'fill-forest' },
  FOREST: { label: '자연휴양림', icon: 'tree', fill: 'fill-forest-deep' },
  BAKJI: { label: '박지', icon: 'backpack', fill: 'fill-earth' },
};

// 표지기 핀 모양이다. 28×28 몸통(좌표 4~32) 아래로 뾰족한 끝이 (18, 42)에 온다.
// 버튼 높이가 44px이고 오버레이를 아래쪽 끝(yAnchor 1)에 맞추므로, 핀 끝이 장소 좌표에 거의 그대로 놓인다.
const PIN_PATH = 'M7 4h22a3 3 0 0 1 3 3v22a3 3 0 0 1-3 3h-5l-6 10-6-10H7a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3z';

// 흰 윤곽선 굵기다. 선은 경로 양쪽으로 반씩 퍼지고 안쪽 반은 핀 색이 덮으므로, 바깥에 보이는 두께는 절반이다.
// 기본은 바깥 1px, 경고 핀은 황토 테두리(바깥 1px) 밖으로 1px, 선택하면 흰 링이 2px 더 두꺼워진다.
const OUTLINE_WIDTH = 2;
const WARNING_OUTLINE_WIDTH = 4;
const SELECTED_EXTRA_WIDTH = 4;

const FOCUS_RING =
  'outline-none focus-visible:ring-2 focus-visible:ring-sea focus-visible:ring-offset-2 focus-visible:ring-offset-paper';

function iconMarkup(name: IconName, attributes: string): string {
  return (
    `<svg ${attributes} viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" ` +
    `stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICON_PATHS[name]}</svg>`
  );
}

export interface SpotPin {
  element: HTMLButtonElement;
  setSelected(selected: boolean): void;
}

export function createSpotPin(data: SpotMarkerData, onActivate: (id: number) => void): SpotPin {
  const style = KIND_STYLE[data.kind];
  const baseOutline = data.warning ? WARNING_OUTLINE_WIDTH : OUTLINE_WIDTH;

  const button = document.createElement('button');
  button.type = 'button';
  button.className =
    'relative flex h-11 w-11 cursor-pointer items-end justify-center rounded-control origin-bottom ' +
    'transition-[scale] duration-150 motion-reduce:transition-none ' +
    FOCUS_RING;

  const labelParts = [data.name, style.label];
  if (data.warning) labelParts.push('공원 경계 경고');
  if (data.closed) labelParts.push('휴장');
  button.setAttribute('aria-label', labelParts.join(', '));

  // 경고 핀은 몸통에 황토 테두리를 두르고, 그 밖을 흰 윤곽선이 한 번 더 감싼다.
  const bodyAttributes = data.warning
    ? `class="${style.fill} stroke-warning-line" stroke-width="2"`
    : `class="${style.fill}"`;
  let markup =
    '<svg class="block overflow-visible" width="36" height="44" viewBox="0 0 36 44" aria-hidden="true">' +
    `<path data-pin-outline class="fill-white stroke-white" stroke-width="${baseOutline}" stroke-linejoin="round" d="${PIN_PATH}"/>` +
    `<path ${bodyAttributes} stroke-linejoin="round" d="${PIN_PATH}"/>` +
    iconMarkup(style.icon, 'class="text-white" x="8" y="8" width="20" height="20"') +
    '</svg>';

  // 배지는 몸통 모서리에 걸친다. 휴장이어도 핀을 숨기거나 흐리게 하지 않고 배지만 더한다.
  if (data.warning) {
    markup +=
      '<span class="pointer-events-none absolute -left-0.5 -top-1.5 flex h-5 w-5 items-center justify-center rounded-control border border-warning-line bg-warning-soft text-warning">' +
      iconMarkup('alert', 'width="14" height="14"') +
      '</span>';
  }
  if (data.closed) {
    markup +=
      '<span class="pointer-events-none absolute -right-0.5 -top-1.5 flex h-5 w-5 items-center justify-center rounded-control border border-white bg-ink text-white">' +
      iconMarkup('closed', 'width="14" height="14"') +
      '</span>';
  }
  button.innerHTML = markup;

  const outline = button.querySelector('[data-pin-outline]');

  // 키보드의 Enter·Space도 click으로 들어온다. 지도까지 클릭이 전달되지 않게 여기서 멈춘다.
  button.addEventListener('click', (event) => {
    event.stopPropagation();
    onActivate(data.id);
  });

  function setSelected(selected: boolean) {
    button.classList.toggle('scale-125', selected);
    button.setAttribute('aria-pressed', String(selected));
    outline?.setAttribute('stroke-width', String(selected ? baseOutline + SELECTED_EXTRA_WIDTH : baseOutline));
  }
  setSelected(false);

  return { element: button, setSelected };
}

export function createClusterButton(
  cluster: ClusterData,
  onActivate: (cluster: ClusterData) => void,
): HTMLButtonElement {
  const button = document.createElement('button');
  button.type = 'button';
  button.className =
    'flex h-11 min-w-11 cursor-pointer items-center justify-center rounded-full border border-white bg-ink px-2 ' +
    'font-mono text-sm tabular-nums text-white ' +
    FOCUS_RING;
  button.textContent = cluster.count.toLocaleString('ko-KR');
  button.setAttribute('aria-label', `장소 ${cluster.count}곳, 눌러서 확대`);

  button.addEventListener('click', (event) => {
    event.stopPropagation();
    onActivate(cluster);
  });

  return button;
}

// 박지를 제보할 위치를 임시로 보여 주는 핀이다. 박지 핀과 같은 모양에 점선 테두리를 둬서 아직 등록 전임을 드러낸다.
// 위치는 제보 폼에서 글자로도 보여 주므로 스크린 리더에는 숨긴다. 핀 위를 다시 눌러도 지도 클릭으로 위치를 옮길 수 있게 클릭을 받지 않는다.
export function createDraftPin(): HTMLElement {
  const element = document.createElement('div');
  element.setAttribute('aria-hidden', 'true');
  element.className = 'pointer-events-none flex h-11 w-11 items-end justify-center';
  element.innerHTML =
    '<svg class="block overflow-visible" width="36" height="44" viewBox="0 0 36 44">' +
    `<path class="fill-white stroke-white" stroke-width="${OUTLINE_WIDTH}" stroke-linejoin="round" d="${PIN_PATH}"/>` +
    `<path class="fill-earth stroke-white" stroke-width="2" stroke-dasharray="4 3" stroke-linejoin="round" d="${PIN_PATH}"/>` +
    iconMarkup('backpack', 'class="text-white" x="8" y="8" width="20" height="20"') +
    '</svg>';
  return element;
}
