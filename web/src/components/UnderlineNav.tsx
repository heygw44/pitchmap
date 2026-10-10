import type { ReactNode } from 'react';
import { Link } from '../app/router';

// 머리글 띠 아래에 놓는 이동 링크 줄이다. UnderlineTabs와 같은 모양이고, 현재 화면은 aria-current로 알린다.
export function UnderlineNav({ label, children }: { label: string; children: ReactNode }) {
  return (
    <nav aria-label={label} className="-mx-4 flex gap-1 overflow-x-auto px-4">
      {children}
    </nav>
  );
}

export function UnderlineNavLink({ to, active, children }: { to: string; active: boolean; children: ReactNode }) {
  return (
    <Link
      to={to}
      aria-current={active ? 'page' : undefined}
      className={[
        'inline-flex min-h-11 items-center whitespace-nowrap border-b-2 px-3 text-base',
        active ? 'border-forest font-semibold text-forest-deep' : 'border-transparent text-ink-muted hover:text-ink',
      ].join(' ')}
    >
      {children}
    </Link>
  );
}
