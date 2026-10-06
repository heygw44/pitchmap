import { useEffect, useId, useRef } from 'react';
import type { ReactNode } from 'react';
import { Icon } from './icons';

type DialogProps = {
  title: string;
  onClose: () => void;
  children: ReactNode;
};

// 네이티브 <dialog>를 모달로 연다. 포커스를 안에 가두고 뒤쪽을 비활성으로 만드는 일은 브라우저가 한다.
// 이 컴포넌트는 화면에 있는 동안이 곧 열려 있는 동안이다. 닫으려면 호출하는 쪽이 그리지 않으면 된다.
export function Dialog({ title, onClose, children }: DialogProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    // 열기 전에 포커스가 있던 자리(보통 다이얼로그를 연 버튼)를 기억했다가 닫을 때 돌려준다.
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    dialog.showModal();
    return () => {
      dialog.close();
      previous?.focus();
    };
  }, []);

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      // Esc를 누르면 브라우저가 먼저 닫아 버리지 않게 막고, 닫는 일은 호출하는 쪽 상태에 맡긴다.
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      className="m-auto w-[calc(100%-2rem)] max-w-md rounded-sheet border border-contour bg-card p-0 text-ink shadow-raise backdrop:bg-ink/40"
    >
      <div className="flex items-center justify-between gap-2 border-b border-contour py-1 pr-1 pl-4">
        <h2 id={titleId} className="font-serif text-lg text-ink">
          {title}
        </h2>
        <button
          type="button"
          aria-label="닫기"
          onClick={onClose}
          className="flex size-11 shrink-0 items-center justify-center rounded-control text-ink hover:bg-paper-deep"
        >
          <Icon name="close" size={24} />
        </button>
      </div>
      <div className="max-h-[70dvh] overflow-y-auto p-4">{children}</div>
    </dialog>
  );
}
