import type { BasecampSearchItem } from '../../api/types';
import { Link } from '../../app/router';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Checkbox } from '../../components/Checkbox';
import { EmptyState } from '../../components/EmptyState';
import { Icon } from '../../components/icons';
import { Notice } from '../../components/Notice';
import { Skeleton } from '../../components/Skeleton';
import { TextField } from '../../components/TextField';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatLocalDate } from '../../lib/datetime';
import { useSession } from '../member/session';
import { BASECAMP_STATUS_META, headcountText, joinConditionSummary, unmetReasonText } from './basecampLabels';
import type { BasecampFilters, BasecampsInView } from './useBasecampsInView';

type BasecampListPanelProps = {
  result: BasecampsInView;
  filters: BasecampFilters;
  onFiltersChange: (filters: BasecampFilters) => void;
  // 지도에서 고른 장소다. 있으면 그 장소의 베이스캠프만 보여 준다.
  spotFilterId: number | null;
  onClearSpotFilter: () => void;
  waitingForMap: boolean;
};

export function BasecampListPanel({
  result,
  filters,
  onFiltersChange,
  spotFilterId,
  onClearSpotFilter,
  waitingForMap,
}: BasecampListPanelProps) {
  const showSkeleton = useDelayedFlag(result.status === 'loading' || waitingForMap);
  const rangeInvalid = filters.fromDate !== '' && filters.toDate !== '' && filters.fromDate > filters.toDate;
  const visible =
    spotFilterId === null ? result.items : result.items.filter((item) => item.spot.spotId === spotFilterId);
  const filteredSpotName = spotFilterId === null ? null : (visible[0]?.spot.name ?? null);

  return (
    <div className="flex flex-col">
      <form
        aria-label="베이스캠프 찾기 조건"
        onSubmit={(event) => event.preventDefault()}
        className="flex flex-col gap-2 border-b border-contour px-4 py-3"
      >
        <div className="grid grid-cols-2 gap-2">
          <TextField
            label="출발일 시작"
            type="date"
            value={filters.fromDate}
            onChange={(event) => onFiltersChange({ ...filters, fromDate: event.target.value })}
          />
          <TextField
            label="출발일 끝"
            type="date"
            value={filters.toDate}
            min={filters.fromDate || undefined}
            onChange={(event) => onFiltersChange({ ...filters, toDate: event.target.value })}
          />
        </div>
        {rangeInvalid && (
          <p role="alert" className="flex items-start gap-1 text-sm text-danger">
            <Icon name="alert" size={16} className="mt-0.5 shrink-0" />
            <span>출발일 끝이 시작보다 빠를 수 없어요.</span>
          </p>
        )}
        <Checkbox
          label="빈자리 있는 것만"
          checked={filters.hasVacancy}
          onChange={(event) => onFiltersChange({ ...filters, hasVacancy: event.target.checked })}
        />
      </form>

      {spotFilterId !== null && (
        <div className="flex items-center justify-between gap-2 border-b border-contour bg-paper px-4 py-1">
          <p className="min-w-0 truncate text-sm text-ink">{filteredSpotName ?? '고른 장소'}의 베이스캠프만 보고 있어요</p>
          <Button variant="ghost" onClick={onClearSpotFilter} className="shrink-0 px-3">
            모두 보기
          </Button>
        </div>
      )}

      {showSkeleton && result.items.length === 0 ? (
        <div aria-busy="true" className="flex flex-col">
          {[0, 1, 2].map((index) => (
            <div key={index} className="flex min-h-14 flex-col gap-2 border-b border-contour px-4 py-3">
              <Skeleton className="h-4 w-2/3" />
              <Skeleton className="h-3 w-1/2" />
            </div>
          ))}
        </div>
      ) : (
        <>
          {result.status === 'error' && result.error && (
            <div className="flex flex-col gap-3 px-4 pt-4">
              <Notice tone="danger" title="베이스캠프를 불러오지 못했어요">
                {result.error}
              </Notice>
              <div>
                <Button variant="secondary" onClick={result.retry}>
                  다시 불러오기
                </Button>
              </div>
            </div>
          )}

          {result.status === 'ready' && visible.length === 0 && (
            <EmptyState
              title="이 지역에는 모집 중인 베이스캠프가 없어요"
              description={
                spotFilterId === null
                  ? '지도를 옮기거나 출발일 조건을 넓혀 보세요. 장소 상세에서 직접 열 수도 있어요.'
                  : '모두 보기를 눌러 다른 장소의 베이스캠프를 찾아보세요.'
              }
            />
          )}

          {visible.length > 0 && (
            <ul aria-label="이 지역 베이스캠프">
              {visible.map((item) => (
                <BasecampRow key={item.basecampId} item={item} />
              ))}
            </ul>
          )}

          {result.hasNext && (
            <div className="flex flex-col gap-2 px-4 py-4">
              {result.moreError && (
                <p role="alert" className="text-sm text-danger">
                  {result.moreError}
                </p>
              )}
              <Button variant="secondary" fullWidth loading={result.loadingMore} onClick={result.loadMore}>
                더 보기
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  );
}

function BasecampRow({ item }: { item: BasecampSearchItem }) {
  const session = useSession();
  const status = BASECAMP_STATUS_META[item.status];
  const trustLevel = session.me?.trustLevel ?? null;
  const unmet = item.canApply === false ? (item.unmetReasons ?? []) : [];

  return (
    <li>
      <Link
        to={`/basecamps/${item.basecampId}`}
        className="flex min-h-14 flex-col gap-1 border-b border-contour px-4 py-3 hover:bg-paper-deep focus-visible:-outline-offset-2"
      >
        <span className="flex items-start justify-between gap-2">
          <span className="min-w-0 break-words text-base font-semibold text-ink">{item.title}</span>
          <Badge tone={status.tone}>{status.label}</Badge>
        </span>
        <span className="truncate text-sm text-ink-muted">{item.spot.name}</span>
        <span className="flex items-center justify-between gap-2 text-sm text-ink">
          <span className="tabular-nums">
            {formatLocalDate(item.startDate)} ~ {formatLocalDate(item.endDate)}
          </span>
          <span className="shrink-0 font-mono tabular-nums">{headcountText(item.headcount, item.capacity)}명</span>
        </span>
        <span className="text-sm text-ink-muted">합류 조건: {joinConditionSummary(item.joinCondition)}</span>
        {unmet.length > 0 && (
          <span className="text-sm text-warning">
            신청할 수 없어요: {unmet.map((reason) => unmetReasonText(reason, trustLevel).reason).join(' ')}
          </span>
        )}
      </Link>
    </li>
  );
}
