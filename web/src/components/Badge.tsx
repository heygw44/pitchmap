import type { ReactNode } from 'react';
import type { IconName } from './iconPaths';
import { Icon } from './icons';

type BadgeTone = 'neutral' | 'forest' | 'sea' | 'earth' | 'warning' | 'danger' | 'closed';

type BadgeProps = {
  tone: BadgeTone;
  icon?: IconName;
  children: ReactNode;
};

const TONE_CLASSES: Record<BadgeTone, string> = {
  neutral: 'bg-paper-deep text-ink-muted',
  forest: 'bg-forest-soft text-forest-deep',
  sea: 'bg-sea-soft text-sea',
  earth: 'bg-earth-soft text-earth-strong',
  warning: 'bg-warning-soft text-warning',
  danger: 'bg-danger-soft text-danger',
  closed: 'bg-paper-deep text-ink-muted',
};

// 색만으로 뜻을 전하지 않도록 배지에는 항상 글자를 넣고, 아이콘은 글자를 돕는 장식으로 둔다.
export function Badge({ tone, icon, children }: BadgeProps) {
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-control px-2 py-0.5 text-xs font-medium ${TONE_CLASSES[tone]}`}
    >
      {icon && <Icon name={icon} size={14} className="shrink-0" />}
      {children}
    </span>
  );
}
