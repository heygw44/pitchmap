import type { ReactNode } from 'react';
import type { IconName } from './iconPaths';
import { Icon } from './icons';

type NoticeTone = 'warning' | 'danger' | 'info';

type NoticeProps = {
  tone: NoticeTone;
  title: string;
  icon?: IconName;
  children?: ReactNode;
};

const BOX_CLASSES: Record<NoticeTone, string> = {
  warning: 'border-warning-line bg-warning-soft',
  danger: 'border-danger bg-danger-soft',
  info: 'border-sea bg-sea-soft',
};

const TITLE_CLASSES: Record<NoticeTone, string> = {
  warning: 'text-warning',
  danger: 'text-danger',
  info: 'text-sea',
};

// 경고 아이콘은 테두리와 같은 황토색으로, 나머지는 제목과 같은 색으로 그린다.
const ICON_CLASSES: Record<NoticeTone, string> = {
  warning: 'text-warning-line',
  danger: 'text-danger',
  info: 'text-sea',
};

const DEFAULT_ICONS: Record<NoticeTone, IconName> = {
  warning: 'alert',
  danger: 'alert',
  info: 'info',
};

// 경고와 안내는 판단 근거라서 접거나 닫을 수 없게 둔다. 본문은 햇빛 아래에서도 읽히도록 먹색으로 쓴다.
export function Notice({ tone, title, icon, children }: NoticeProps) {
  return (
    <div role="note" className={`flex gap-3 rounded-control border-l-4 p-4 ${BOX_CLASSES[tone]}`}>
      <Icon name={icon ?? DEFAULT_ICONS[tone]} size={20} className={`mt-0.5 shrink-0 ${ICON_CLASSES[tone]}`} />
      <div className="flex min-w-0 flex-1 flex-col gap-2">
        <p className={`font-semibold ${TITLE_CLASSES[tone]}`}>{title}</p>
        {children !== undefined && children !== null && <div className="text-sm text-ink">{children}</div>}
      </div>
    </div>
  );
}
