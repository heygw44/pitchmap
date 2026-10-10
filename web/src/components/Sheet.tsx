import { useRef, useState } from 'react';
import type { KeyboardEvent, MouseEvent, PointerEvent, ReactNode } from 'react';

type SheetSnap = 'peek' | 'half' | 'full';

type SheetProps = {
  snap: SheetSnap;
  onSnapChange: (snap: SheetSnap) => void;
  header?: ReactNode;
  children: ReactNode;
  label: string;
  // 모바일 하단 탭 위에 시트를 띄울 때 켠다. 시트의 아래 끝을 탭 높이만큼 올리고, 전체 높이에서도 그만큼 뺀다.
  aboveBottomNav?: boolean;
};

const SNAP_ORDER: readonly SheetSnap[] = ['peek', 'half', 'full'];

// 화면 높이 가운데 시트가 차지하는 비율. 아래 높이 클래스(28dvh, 55dvh, 92dvh)와 같은 값으로 맞춘다.
const SNAP_RATIO: Record<SheetSnap, number> = {
  peek: 0.28,
  half: 0.55,
  full: 0.92,
};

const SNAP_HEIGHT_CLASSES: Record<SheetSnap, string> = {
  peek: 'h-[28dvh]',
  half: 'h-[55dvh]',
  full: 'h-[92dvh]',
};

// 하단 탭 위에 뜰 때의 전체 높이. 접힘·절반은 탭이 있어도 같은 높이라서 목록 미리보기 양이 바뀌지 않는다.
const FULL_ABOVE_NAV_CLASS = 'h-[calc(92dvh-var(--bottom-nav-h))]';

const SNAP_LABELS: Record<SheetSnap, string> = {
  peek: '접힘',
  half: '절반',
  full: '전체',
};

const NEXT_SNAP: Record<SheetSnap, SheetSnap> = {
  peek: 'half',
  half: 'full',
  full: 'peek',
};

// 이 거리보다 적게 움직이면 끌기가 아니라 누르기로 본다.
const DRAG_THRESHOLD_PX = 6;

const DESKTOP_QUERY = '(min-width: 64rem)';

type DragState = {
  pointerId: number;
  startY: number;
  startHeight: number;
  // 시트 아래 끝이 화면 아래에서 떨어진 거리(px). 하단 탭 위에 뜨면 탭 높이다.
  bottomInset: number;
  moved: boolean;
};

// 단계별 목표 높이(px). 전체 단계만 시트 아래 여백(하단 탭 높이)을 뺀다.
function snapHeight(snap: SheetSnap, viewportHeight: number, bottomInset: number): number {
  const height = SNAP_RATIO[snap] * viewportHeight;
  return snap === 'full' ? height - bottomInset : height;
}

function nearestSnap(height: number, viewportHeight: number, bottomInset: number): SheetSnap {
  let best: SheetSnap = 'peek';
  let bestDistance = Number.POSITIVE_INFINITY;
  for (const snap of SNAP_ORDER) {
    const distance = Math.abs(height - snapHeight(snap, viewportHeight, bottomInset));
    if (distance < bestDistance) {
      best = snap;
      bestDistance = distance;
    }
  }
  return best;
}

// 모바일에서는 지도 위에 뜨는 하단 시트이고, lg 이상에서는 같은 내용을 왼쪽 400px 고정 패널로 그린다.
export function Sheet({ snap, onSnapChange, header, children, label, aboveBottomNav = false }: SheetProps) {
  const sectionRef = useRef<HTMLElement>(null);
  const dragRef = useRef<DragState | null>(null);
  // 끌기를 마친 직후에 브라우저가 보내는 click을 무시하려고 기억해 둔다.
  const suppressClickRef = useRef(false);
  const [dragHeight, setDragHeight] = useState<number | null>(null);

  function handlePointerDown(event: PointerEvent<HTMLButtonElement>) {
    if (event.pointerType === 'mouse' && event.button !== 0) return;
    const section = sectionRef.current;
    if (!section) return;
    suppressClickRef.current = false;
    dragRef.current = {
      pointerId: event.pointerId,
      startY: event.clientY,
      startHeight: section.getBoundingClientRect().height,
      bottomInset: parseFloat(getComputedStyle(section).bottom) || 0,
      moved: false,
    };
    event.currentTarget.setPointerCapture(event.pointerId);
  }

  function handlePointerMove(event: PointerEvent<HTMLButtonElement>) {
    const drag = dragRef.current;
    if (!drag || drag.pointerId !== event.pointerId) return;
    const deltaY = event.clientY - drag.startY;
    if (!drag.moved && Math.abs(deltaY) < DRAG_THRESHOLD_PX) return;
    drag.moved = true;
    const viewportHeight = window.innerHeight;
    const minHeight = SNAP_RATIO.peek * viewportHeight;
    const maxHeight = snapHeight('full', viewportHeight, drag.bottomInset);
    setDragHeight(Math.min(maxHeight, Math.max(minHeight, drag.startHeight - deltaY)));
  }

  function finishDrag(event: PointerEvent<HTMLButtonElement>) {
    const drag = dragRef.current;
    if (!drag || drag.pointerId !== event.pointerId) return;
    dragRef.current = null;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
    if (!drag.moved) return;
    suppressClickRef.current = true;
    const finalHeight = drag.startHeight - (event.clientY - drag.startY);
    setDragHeight(null);
    const next = nearestSnap(finalHeight, window.innerHeight, drag.bottomInset);
    if (next !== snap) onSnapChange(next);
  }

  function handlePointerCancel(event: PointerEvent<HTMLButtonElement>) {
    const drag = dragRef.current;
    if (!drag || drag.pointerId !== event.pointerId) return;
    dragRef.current = null;
    setDragHeight(null);
  }

  // 마우스 클릭, Enter, Space 모두 click으로 들어온다. 접힘 → 절반 → 전체 → 접힘 순서로 바꾼다.
  function handleClick(event: MouseEvent<HTMLButtonElement>) {
    const fromKeyboard = event.detail === 0;
    if (suppressClickRef.current) {
      suppressClickRef.current = false;
      if (!fromKeyboard) return;
    }
    onSnapChange(NEXT_SNAP[snap]);
  }

  // 시트 안에 포커스가 있을 때 Esc를 누르면 시트를 접는다. 데스크톱 패널에서는 높이가 없으므로 무시한다.
  function handleKeyDown(event: KeyboardEvent<HTMLElement>) {
    if (event.key !== 'Escape' || snap === 'peek') return;
    if (window.matchMedia(DESKTOP_QUERY).matches) return;
    event.preventDefault();
    onSnapChange('peek');
  }

  const dragging = dragHeight !== null;

  return (
    <section
      ref={sectionRef}
      aria-label={label}
      onKeyDown={handleKeyDown}
      style={dragging ? { height: `${dragHeight}px` } : undefined}
      className={[
        'fixed inset-x-0 z-10 flex flex-col rounded-t-sheet border-t border-contour bg-card shadow-raise',
        aboveBottomNav ? 'bottom-(--bottom-nav-h)' : 'bottom-0',
        dragging
          ? 'transition-none'
          : `${aboveBottomNav && snap === 'full' ? FULL_ABOVE_NAV_CLASS : SNAP_HEIGHT_CLASSES[snap]} transition-[height] duration-200 ease-out motion-reduce:transition-none`,
        'lg:static lg:z-auto lg:h-full lg:w-[400px] lg:shrink-0 lg:rounded-none lg:border-t-0 lg:border-r lg:shadow-none',
      ].join(' ')}
    >
      <button
        type="button"
        aria-label={`시트 크기 바꾸기, 지금 ${SNAP_LABELS[snap]}`}
        onClick={handleClick}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={finishDrag}
        onPointerCancel={handlePointerCancel}
        className="flex h-11 w-full shrink-0 cursor-grab touch-none items-center justify-center active:cursor-grabbing lg:hidden"
      >
        <span aria-hidden="true" className="h-1 w-9 rounded-control bg-ink-subtle" />
      </button>
      {header && <div className="shrink-0 border-b border-contour px-4 pb-3 lg:pt-4">{header}</div>}
      <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain">{children}</div>
    </section>
  );
}
