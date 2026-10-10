import { useEffect, useState } from 'react';
import { listPrograms } from '../../api/programs';
import type { ProgramListStatus } from '../../api/programs';
import type { ProgramSummary } from '../../api/types';
import { Link } from '../../app/router';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { HubLayout, ListPanel } from '../../components/HubLayout';
import { Icon } from '../../components/icons';
import { Notice } from '../../components/Notice';
import { Skeleton } from '../../components/Skeleton';
import { UnderlineTabs } from '../../components/UnderlineTabs';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatKstDateTime, kstDateBlock } from '../../lib/datetime';
import { useSession } from '../member/session';
import { feeText, PROGRAM_FILTERS, PROGRAM_STATUS_META, seatsText } from './programLabels';
import { ProgramAside } from './ProgramAside';
import { usePagedList } from './usePagedList';

type ProgramFilter = ProgramListStatus | 'ALL';

const SECONDARY_LINK_CLASS =
  'inline-flex min-h-11 items-center justify-center rounded-control border border-ink-subtle bg-card px-4 text-base font-semibold text-ink hover:bg-paper-deep';

function fetchPrograms(filter: ProgramFilter, page: number, signal?: AbortSignal) {
  return listPrograms(filter === 'ALL' ? undefined : filter, page, signal);
}

export function ProgramListPage() {
  const [filter, setFilter] = useState<ProgramFilter>('ALL');
  const { current, loadingMore, loadMore, retry } = usePagedList(true, filter, fetchPrograms);
  const showSkeleton = useDelayedFlag(current === null);
  const session = useSession();
  const signedIn = session.status === 'authenticated' && session.me !== null;

  useEffect(() => {
    document.title = '공식 행사 · 피치맵';
  }, []);

  return (
    <HubLayout
      title="공식 행사"
      description="지자체와 브랜드가 여는 백패킹 행사를 선착순으로 신청해요."
      actions={
        signedIn ? (
          <Link to="/me/program-applications" className={SECONDARY_LINK_CLASS}>
            내 신청 보기
          </Link>
        ) : null
      }
      tabs={<UnderlineTabs label="신청 상태" options={PROGRAM_FILTERS} value={filter} onChange={setFilter} />}
      aside={<ProgramAside signedIn={signedIn} />}
    >
      {current?.error && (
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
      <div aria-busy={current === null}>
        <ListPanel header="시작일 빠른 순">
          {current === null ? (
            showSkeleton ? (
              Array.from({ length: 3 }, (_, index) => (
                <li key={index} className="flex gap-4 px-4 py-4">
                  <Skeleton className="h-16 w-14 shrink-0" />
                  <div className="flex flex-1 flex-col gap-2">
                    <Skeleton className="h-5 w-1/2" />
                    <Skeleton className="h-4 w-2/3" />
                  </div>
                </li>
              ))
            ) : null
          ) : current.items.length === 0 ? (
            current.error ? null : (
              <li>
                <ProgramEmpty filtered={filter !== 'ALL'} onReset={() => setFilter('ALL')} />
              </li>
            )
          ) : (
            <>
              {current.items.map((item) => (
                <ProgramRow key={item.programId} item={item} />
              ))}
              {current.hasNext && (
                <li className="p-2">
                  <Button variant="ghost" fullWidth loading={loadingMore} onClick={() => void loadMore()}>
                    더 보기
                  </Button>
                </li>
              )}
            </>
          )}
        </ListPanel>
      </div>
    </HubLayout>
  );
}

function ProgramEmpty({ filtered, onReset }: { filtered: boolean; onReset: () => void }) {
  return (
    <div className="flex flex-col items-start gap-3 p-6">
      <Icon name="calendar" size={24} className="text-ink-muted" />
      <p className="font-serif text-lg text-ink">지금 볼 수 있는 행사가 없어요</p>
      <p className="text-sm text-ink-muted">
        행사가 열리면 이 목록에 올라와요. 그동안 지도에서 합법 야영지를 찾거나 베이스캠프에 합류해 보세요.
      </p>
      <div className="flex flex-wrap items-center gap-2">
        <Link to="/map" className={SECONDARY_LINK_CLASS}>
          지도 보기
        </Link>
        <Link to="/basecamps" className={SECONDARY_LINK_CLASS}>
          베이스캠프 찾기
        </Link>
        {filtered && (
          <button
            type="button"
            onClick={onReset}
            className="inline-flex min-h-11 items-center text-base font-semibold text-forest underline underline-offset-2"
          >
            전체 보기
          </button>
        )}
      </div>
    </div>
  );
}


function ProgramRow({ item }: { item: ProgramSummary }) {
  const status = PROGRAM_STATUS_META[item.status];
  const date = kstDateBlock(item.startAt);
  const open = item.status === 'OPEN';
  const ratio = item.capacity > 0 ? Math.min(100, Math.max(0, (item.remainingSeats / item.capacity) * 100)) : 0;
  const seats = (
    <>
      <span className="text-sm text-ink">
        남은 자리 <span className="font-mono tabular-nums">{seatsText(item.remainingSeats, item.capacity)}</span>
      </span>
      {open && (
        <span aria-hidden="true" className="block h-1 w-full rounded-control bg-forest-soft">
          <span className="block h-1 rounded-control bg-forest" style={{ width: `${ratio}%` }} />
        </span>
      )}
      <span className="font-mono text-sm tabular-nums text-ink">{feeText(item.fee)}</span>
    </>
  );
  return (
    <li>
      <Link to={`/programs/${item.programId}`} className="flex min-h-11 gap-4 px-4 py-4 hover:bg-paper-deep">
        <span className="flex w-14 shrink-0 flex-col items-center border-r border-contour pr-3 text-center">
          <span className="text-xs text-ink-muted">{date.month}</span>
          <span className="font-mono text-xl tabular-nums text-ink">{date.day}</span>
          <span className="text-xs text-ink-muted">{date.weekday}</span>
        </span>
        <span className="flex min-w-0 flex-1 flex-col gap-1">
          <span className="flex flex-wrap gap-1">
            <Badge tone={status.tone}>{status.label}</Badge>
            {item.overnight && <Badge tone="earth">숙박</Badge>}
          </span>
          <span className="break-words font-serif text-lg text-ink">{item.title}</span>
          <span className="text-sm text-ink-muted">{item.locationText}</span>
          <span className="font-mono text-sm tabular-nums text-ink-muted">
            {formatKstDateTime(item.startAt)} ~ {formatKstDateTime(item.endAt)}
          </span>
          <span className="text-sm text-ink-muted">
            신청 {formatKstDateTime(item.applyOpenAt)} ~ {formatKstDateTime(item.applyCloseAt)}
          </span>
          <span className="mt-1 flex flex-col gap-1 md:hidden">{seats}</span>
        </span>
        <span className="hidden w-32 shrink-0 flex-col gap-1 text-right md:flex">{seats}</span>
      </Link>
    </li>
  );
}
