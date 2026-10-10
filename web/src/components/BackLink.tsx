import type { ReactNode } from 'react';
import { Link } from '../app/router';
import { Icon } from './icons';

// 머리글 띠 위쪽에 놓는 되돌아가기 링크다.
export function BackLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link to={to} className="inline-flex min-h-11 items-center gap-1 text-forest underline underline-offset-2">
      <Icon name="chevronLeft" size={16} />
      {children}
    </Link>
  );
}
