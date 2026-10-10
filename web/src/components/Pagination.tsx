import { Icon } from './icons';

type PaginationProps = {
  // 사람이 읽는 1부터 시작하는 번호다.
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
  label?: string;
};

const WINDOW = 5;

// 현재 페이지를 가운데에 두고 번호를 최대 5개 보여 준다. 앞이나 끝에 닿으면 반대쪽으로 채워 늘 5개를 맞춘다.
function pageWindow(page: number, totalPages: number): number[] {
  const count = Math.min(WINDOW, totalPages);
  const start = Math.min(Math.max(1, page - Math.floor(WINDOW / 2)), totalPages - count + 1);
  return Array.from({ length: count }, (_, index) => start + index);
}

// 360px 폭에서 버튼 7개(44px)와 간격이 한 줄에 들어가도록 간격을 2px로 둔다.
const BUTTON_CLASS =
  'inline-flex min-h-11 min-w-11 items-center justify-center rounded-control border px-2 text-base tabular-nums disabled:cursor-not-allowed disabled:text-ink-subtle';

export function Pagination({ page, totalPages, onChange, label = '페이지' }: PaginationProps) {
  if (totalPages <= 1) return null;
  const current = Math.min(page, totalPages);
  return (
    <nav aria-label={label} className="flex flex-wrap items-center justify-center gap-0.5">
      <button
        type="button"
        aria-label="이전 페이지"
        disabled={current <= 1}
        onClick={() => onChange(current - 1)}
        className={`${BUTTON_CLASS} border-transparent text-ink hover:bg-paper-deep`}
      >
        <Icon name="chevronLeft" size={18} />
      </button>
      {pageWindow(current, totalPages).map((number) => (
        <button
          key={number}
          type="button"
          aria-current={number === page ? 'page' : undefined}
          aria-label={`${number}페이지`}
          onClick={() => onChange(number)}
          className={[
            BUTTON_CLASS,
            number === page
              ? 'border-forest bg-forest font-semibold text-white'
              : 'border-transparent text-ink hover:bg-paper-deep',
          ].join(' ')}
        >
          {number}
        </button>
      ))}
      <button
        type="button"
        aria-label="다음 페이지"
        disabled={current >= totalPages}
        onClick={() => onChange(current + 1)}
        className={`${BUTTON_CLASS} border-transparent text-ink hover:bg-paper-deep`}
      >
        <Icon name="chevronRight" size={18} />
      </button>
    </nav>
  );
}
