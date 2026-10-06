import type { ReactNode } from 'react';

type EvidenceItem = {
  label: string;
  value: ReactNode;
};

type EvidenceProps = {
  items: EvidenceItem[];
};

// 판단 근거(출처, 기준일, 좌표 등)를 "라벨 — 값" 행으로 적는다. 값은 고정폭 숫자로 오른쪽에 맞춘다.
export function Evidence({ items }: EvidenceProps) {
  if (items.length === 0) return null;

  return (
    <dl>
      {items.map((item, index) => (
        <div
          key={`${index}-${item.label}`}
          className="flex justify-between gap-4 border-t border-contour py-2 first:border-t-0"
        >
          <dt className="shrink-0 text-sm text-ink-muted">{item.label}</dt>
          <dd className="min-w-0 text-right font-mono text-sm tabular-nums text-ink break-words">{item.value}</dd>
        </div>
      ))}
    </dl>
  );
}
