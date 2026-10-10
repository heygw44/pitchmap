import type { CSSProperties, ReactNode } from 'react';
import type { MarkerKind } from '../../map/types';
import { KIND_STYLE, PIN_PATH } from '../../map/markers';
import { Badge } from '../../components/Badge';
import { Evidence } from '../../components/Evidence';
import { Icon } from '../../components/icons';
import { Notice } from '../../components/Notice';
import { ParkWarningNotice } from '../spot/ParkWarningNotice';
import { SPOT_TYPE_META } from '../spot/spotLabels';
import { trustLevelBadge } from '../member/memberLabels';

// 랜딩에서 제품이 무엇을 보여 주는지 미리 보여 주는 예시 조각이다. 실제 화면과 같은 컴포넌트·색으로 그리되,
// 누를 수 있는 것처럼 보이면 안 되므로 버튼·링크 대신 span을 쓰고, 화면 부분은 스크린 리더와 키보드에서 뺀다.
// 그 대신 캡션으로 예시라는 것을 글로 알린다.
// caption을 끄는 곳(흐름 단계의 작은 조각)은 묶음 아래에 예시라는 안내를 한 번만 따로 적는다.
export function PreviewFrame({
  children,
  className = '',
  caption = true,
}: {
  children: ReactNode;
  className?: string;
  caption?: boolean;
}) {
  return (
    <figure className="flex min-w-0 flex-col gap-2">
      <div aria-hidden="true" inert className={className}>
        {children}
      </div>
      {caption && <figcaption className="text-xs text-ink-muted">예시 화면이에요</figcaption>}
    </figure>
  );
}

type PreviewPinProps = {
  kind: MarkerKind;
  warning?: boolean;
  selected?: boolean;
  style: CSSProperties;
};

// 지도 마커(map/markers.ts)와 같은 핀 모양·색을 React로 그린다. 위치는 핀 끝이 style의 좌표에 오게 맞춘다.
function PreviewPin({ kind, warning = false, selected = false, style }: PreviewPinProps) {
  const kindStyle = KIND_STYLE[kind];
  const outline = (warning ? 4 : 2) + (selected ? 4 : 0);
  return (
    <span
      style={style}
      className={`absolute flex h-11 w-11 -translate-x-1/2 -translate-y-full origin-bottom items-end justify-center ${selected ? 'scale-125' : ''}`}
    >
      <svg className="block overflow-visible" width="36" height="44" viewBox="0 0 36 44">
        <path className="fill-white stroke-white" strokeWidth={outline} strokeLinejoin="round" d={PIN_PATH} />
        <path
          className={warning ? `${kindStyle.fill} stroke-warning-line` : kindStyle.fill}
          strokeWidth={warning ? 2 : undefined}
          strokeLinejoin="round"
          d={PIN_PATH}
        />
        <Icon name={kindStyle.icon} x={8} y={8} size={20} className="text-white" />
      </svg>
      {warning && (
        <span className="absolute -left-0.5 -top-1.5 flex h-5 w-5 items-center justify-center rounded-control border border-warning-line bg-warning-soft text-warning">
          <Icon name="alert" size={14} />
        </span>
      )}
    </span>
  );
}

const CHIP_CLASS =
  'inline-flex min-h-9 items-center gap-1.5 rounded-control border border-contour bg-card px-2.5 text-sm font-semibold';

type PreviewSpot = { name: string; kind: MarkerKind; warning?: boolean; note?: ReactNode };

function PreviewSpotRow({ name, kind, warning = false, note }: PreviewSpot) {
  const meta = SPOT_TYPE_META[kind];
  return (
    <span className="flex min-h-14 items-center gap-3 border-b border-contour px-4 py-2.5 last:border-b-0">
      <span className={`flex size-8 shrink-0 items-center justify-center rounded-control ${meta.tileClass}`}>
        <Icon name={meta.icon} size={18} />
      </span>
      <span className="flex min-w-0 flex-1 flex-col gap-1">
        <span className="truncate text-base text-ink">{name}</span>
        <span className="flex flex-wrap gap-1">
          <Badge tone="neutral">{meta.label}</Badge>
          {warning && (
            <Badge tone="warning" icon="alert">
              공원 경계 경고
            </Badge>
          )}
          {note}
        </span>
      </span>
    </span>
  );
}

// 첫 화면 오른쪽의 지도 미리보기. 등고선 바탕에 핀 몇 개와 묶음, 아래에 장소 목록 시트를 겹친다.
export function HeroPreview() {
  return (
    <PreviewFrame className="relative h-[22rem] overflow-hidden rounded-sheet border border-contour bg-paper bg-contour sm:h-[26rem]">
      <span className="absolute left-3 top-3 flex gap-2">
        <span className={`${CHIP_CLASS} font-serif text-forest-deep`}>피치맵</span>
      </span>
      <span className="absolute right-3 top-3 flex gap-2">
        <span className={`${CHIP_CLASS} text-ink`}>
          <Icon name="backpack" size={16} />
          박지 제보
        </span>
        <span className={`${CHIP_CLASS} border-forest bg-forest-soft text-forest-deep`}>
          <Icon name="terrain" size={16} />
          지형
        </span>
      </span>

      <PreviewPin kind="CAMPSITE" style={{ left: '22%', top: '34%' }} />
      <PreviewPin kind="BAKJI" warning style={{ left: '58%', top: '28%' }} />
      <PreviewPin kind="BAKJI" selected style={{ left: '40%', top: '46%' }} />
      <PreviewPin kind="FOREST" style={{ left: '78%', top: '44%' }} />
      <span
        className="absolute flex h-11 min-w-11 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full border border-white bg-ink px-2 font-mono text-sm tabular-nums text-white"
        style={{ left: '88%', top: '22%' }}
      >
        12
      </span>

      <span className="absolute inset-x-3 bottom-0 flex flex-col rounded-t-sheet border border-b-0 border-contour bg-card shadow-raise">
        <span className="flex h-7 items-center justify-center">
          <span className="h-1 w-9 rounded-control bg-ink-subtle" />
        </span>
        <span className="border-b border-contour px-4 pb-2 font-serif text-lg text-ink">
          이 지역 장소 <span className="tabular-nums">17곳</span>
        </span>
        <PreviewSpotRow name="물소리 계곡 위 평지" kind="BAKJI" />
        <PreviewSpotRow name="능선 끝 바위 쉼터" kind="BAKJI" warning />
      </span>
    </PreviewFrame>
  );
}

// 경고가 뜬 박지의 상세 일부. 실제 공원 경계 경고 컴포넌트와, 베이스캠프 열기가 막힌 모습을 그대로 보여 준다.
export function WarningPreview() {
  return (
    <PreviewFrame className="flex flex-col gap-4 rounded-sheet border border-contour bg-card p-4 sm:p-5">
      <span className="flex flex-col gap-1.5">
        <span className="font-serif text-lg text-ink">능선 끝 바위 쉼터</span>
        <span className="flex flex-wrap gap-1">
          <Badge tone="earth" icon="backpack">
            박지
          </Badge>
          <Badge tone="warning" icon="alert">
            공원 경계 경고
          </Badge>
        </span>
      </span>
      <ParkWarningNotice
        warning={{ warned: true, areaName: '설악산국립공원', source: 'KDPA', sourceDate: '2025-12-31' }}
      />
      <span className="flex flex-col gap-2 border-t border-contour pt-4">
        <span className="font-serif text-lg text-ink">베이스캠프</span>
        <span className="inline-flex min-h-11 items-center justify-center self-start rounded-control bg-paper-deep px-4 text-base font-semibold text-ink-muted">
          이 장소로 베이스캠프 열기
        </span>
        <span className="text-sm text-ink-muted">공원 경계 경고가 있는 박지에서는 베이스캠프를 열 수 없어요.</span>
      </span>
    </PreviewFrame>
  );
}

// 함께 갈 사람의 프로필 일부와 블라인드 후기 안내.
export function TrustPreview() {
  const badge = trustLevelBadge(2);
  return (
    <PreviewFrame className="flex flex-col gap-4 rounded-sheet border border-contour bg-card p-4 sm:p-5">
      <span className="flex flex-col gap-3">
        <span className="flex flex-wrap items-center gap-2">
          <span className="font-serif text-lg text-ink">솔바람</span>
          <Badge tone={badge.tone} icon={badge.icon}>
            {badge.label}
          </Badge>
        </span>
        <Evidence
          items={[
            { label: '연령대', value: '30대 본인확인' },
            { label: '완료한 동행', value: '5회' },
            { label: '"다시 동행하고 싶어요"', value: '92%' },
          ]}
        />
        <span className="flex flex-wrap gap-1">
          <Badge tone="forest">시간 약속</Badge>
          <Badge tone="forest">흔적 남기지 않기 실천</Badge>
        </span>
      </span>
      <Notice tone="info" title="내 후기를 쓰면 상대 후기를 볼 수 있어요">
        동행 후기는 서로 쓰기 전까지 가려 둬요.
      </Notice>
    </PreviewFrame>
  );
}

// 흐름 단계마다 붙는 작은 조각들이다.
export function StepSpotFragment() {
  return (
    <PreviewFrame caption={false} className="overflow-hidden rounded-control border border-contour bg-card">
      <PreviewSpotRow name="솔숲 야영장" kind="CAMPSITE" note={<Badge tone="forest">베이스캠프 2</Badge>} />
    </PreviewFrame>
  );
}

export function StepBasecampFragment() {
  return (
    <PreviewFrame
      caption={false}
      className="flex flex-col gap-2 rounded-control border border-contour bg-card px-4 py-3"
    >
      <span className="flex items-center justify-between gap-2">
        <span className="truncate text-base text-ink">주말 1박, 솔숲 야영장</span>
        <Badge tone="forest">모집 중</Badge>
      </span>
      <span className="flex justify-between gap-2 text-sm text-ink-muted">
        <span>11월 7일 (토) · 1박</span>
        <span>
          인원 <span className="font-mono tabular-nums text-ink">2/4</span>
        </span>
      </span>
    </PreviewFrame>
  );
}

export function StepProgramFragment() {
  return (
    <PreviewFrame
      caption={false}
      className="flex flex-col gap-2 rounded-control border border-contour bg-card px-4 py-3"
    >
      <span className="flex items-center justify-between gap-2">
        <span className="truncate text-base text-ink">가을 능선 백패킹</span>
        <Badge tone="forest">신청 중</Badge>
      </span>
      <span className="flex justify-between gap-2 text-sm text-ink-muted">
        <span>30,000원</span>
        <span>
          남은 자리 <span className="font-mono tabular-nums text-ink">12</span>
        </span>
      </span>
    </PreviewFrame>
  );
}
