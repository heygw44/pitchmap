import { useEffect, useState } from 'react';
import { listPrograms } from '../../api/programs';
import type { ProgramListStatus } from '../../api/programs';
import type { ProgramSummary } from '../../api/types';
import { Link } from '../../app/router';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { Notice } from '../../components/Notice';
import { PageLayout } from '../../components/PageLayout';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatKstDateTime } from '../../lib/datetime';
import { FilterChips } from './FilterChips';
import { feeText, PROGRAM_FILTERS, PROGRAM_STATUS_META, seatsText } from './programLabels';
import { usePagedList } from './usePagedList';

type ProgramFilter = ProgramListStatus | 'ALL';

function fetchPrograms(filter: ProgramFilter, page: number, signal?: AbortSignal) {
  return listPrograms(filter === 'ALL' ? undefined : filter, page, signal);
}

export function ProgramListPage() {
  const [filter, setFilter] = useState<ProgramFilter>('ALL');
  const { current, loadingMore, loadMore, retry } = usePagedList(true, filter, fetchPrograms);
  const showSkeleton = useDelayedFlag(current === null);

  useEffect(() => {
    document.title = '공식 행사 · 피치맵';
  }, []);

  return (
    <PageLayout title="공식 행사">
      <FilterChips label="신청 상태" options={PROGRAM_FILTERS} value={filter} onChange={setFilter} />
      <div aria-busy={current === null} className="flex flex-col gap-3">
        {current === null ? (
          showSkeleton ? (
            <>
              <Skeleton className="h-32 w-full" />
              <Skeleton className="h-32 w-full" />
            </>
          ) : null
        ) : (
          <>
            {current.error && (
              <div className="flex flex-col gap-3">
                <Notice tone="danger" title="행사를 불러오지 못했어요">
                  <p>{current.error}</p>
                </Notice>
                {current.items.length === 0 && (
                  <div>
                    <Button variant="secondary" onClick={retry}>
                      다시 불러오기
                    </Button>
                  </div>
                )}
              </div>
            )}
            {current.items.length === 0 && !current.error ? (
              <EmptyState
                title="지금 볼 수 있는 행사가 없어요"
                description={filter === 'ALL' ? '새 행사가 올라오면 이곳에서 볼 수 있어요.' : '다른 상태를 골라 보세요.'}
              />
            ) : (
              <ul className="flex flex-col gap-3">
                {current.items.map((item) => (
                  <ProgramCard key={item.programId} item={item} />
                ))}
              </ul>
            )}
            {current.hasNext && (
              <div>
                <Button variant="secondary" loading={loadingMore} onClick={() => void loadMore()}>
                  더 보기
                </Button>
              </div>
            )}
          </>
        )}
      </div>
    </PageLayout>
  );
}

function ProgramCard({ item }: { item: ProgramSummary }) {
  const status = PROGRAM_STATUS_META[item.status];
  return (
    <li>
      <Link
        to={`/programs/${item.programId}`}
        className="flex min-h-11 flex-col gap-2 rounded-control border border-contour bg-card p-4 hover:bg-paper-deep"
      >
        <span className="flex flex-wrap gap-1">
          <Badge tone={status.tone}>{status.label}</Badge>
          {item.overnight && <Badge tone="earth">숙박</Badge>}
        </span>
        <span className="font-serif text-lg text-ink">{item.title}</span>
        <span className="font-mono text-sm tabular-nums text-ink-muted">
          {formatKstDateTime(item.startAt)} ~ {formatKstDateTime(item.endAt)}
        </span>
        <span className="text-sm text-ink-muted">{item.locationText}</span>
        <span className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 text-sm text-ink">
          <span>
            남은 자리{' '}
            <span className="font-mono tabular-nums">{seatsText(item.remainingSeats, item.capacity)}</span>
          </span>
          <span className="font-mono tabular-nums">{feeText(item.fee)}</span>
        </span>
      </Link>
    </li>
  );
}
