import { useEffect, useState } from 'react';
import { fetchAdminMemberReports } from '../../api/admin';
import type { AdminReportSummary, ReportStatus } from '../../api/types';
import { Link } from '../../app/router';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Checkbox } from '../../components/Checkbox';
import { EmptyState } from '../../components/EmptyState';
import { Notice } from '../../components/Notice';
import { SelectField } from '../../components/SelectField';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatKstDateTime } from '../../lib/datetime';
import { REPORT_KIND_LABELS, REPORT_STATUS_META, REPORT_TYPE_LABELS } from '../report/reportLabels';
import { AdminPage } from './AdminGate';
import { adminErrorMessage } from './adminErrors';

const ALL = 'ALL';

const STATUS_OPTIONS = [
  { value: ALL, label: '전체' },
  { value: 'RECEIVED', label: '접수' },
  { value: 'IN_REVIEW', label: '검토 중' },
  { value: 'ACTIONED', label: '조치' },
  { value: 'DISMISSED', label: '기각' },
];

type ListState = { items: AdminReportSummary[]; page: number; hasNext: boolean; error: string | null };

export function AdminReportsPage() {
  return (
    <AdminPage title="신고 처리" current="/admin/reports">
      <ReportList />
    </AdminPage>
  );
}

function ReportList() {
  const [status, setStatus] = useState<string>(ALL);
  const [urgentOnly, setUrgentOnly] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<ListState | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const showSkeleton = useDelayedFlag(state === null);

  const statusParam = status === ALL ? undefined : (status as ReportStatus);
  const urgentParam = urgentOnly ? true : undefined;

  // 필터를 바꾸거나 다시 불러오면 첫 페이지부터 읽는다.
  useEffect(() => {
    const controller = new AbortController();
    fetchAdminMemberReports({ status: statusParam, urgent: urgentParam, page: 0 }, controller.signal).then(
      (page) => {
        if (!controller.signal.aborted) {
          setState({ items: page.content, page: page.page, hasNext: page.hasNext, error: null });
        }
      },
      (caught: unknown) => {
        if (!controller.signal.aborted) {
          setState({ items: [], page: 0, hasNext: false, error: adminErrorMessage(caught) });
        }
      },
    );
    return () => controller.abort();
  }, [statusParam, urgentParam, attempt]);

  function changeFilter(change: () => void) {
    setState(null);
    change();
  }

  async function loadMore() {
    if (!state || loadingMore) return;
    setLoadingMore(true);
    try {
      const next = await fetchAdminMemberReports({ status: statusParam, urgent: urgentParam, page: state.page + 1 });
      setState((previous) =>
        previous
          ? { items: [...previous.items, ...next.content], page: next.page, hasNext: next.hasNext, error: null }
          : previous,
      );
    } catch (caught) {
      setState((previous) => (previous ? { ...previous, error: adminErrorMessage(caught) } : previous));
    } finally {
      setLoadingMore(false);
    }
  }

  return (
    <section className="flex flex-col gap-4" aria-label="신고 목록">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:gap-6">
        <SelectField
          label="처리 상태"
          className="sm:w-48"
          value={status}
          options={STATUS_OPTIONS}
          onChange={(event) => changeFilter(() => setStatus(event.target.value))}
        />
        <Checkbox
          label="긴급만"
          checked={urgentOnly}
          onChange={(event) => changeFilter(() => setUrgentOnly(event.target.checked))}
        />
      </div>
      {state?.error && (
        <div role="alert" className="flex flex-col gap-3">
          <Notice tone="danger" title="신고를 불러오지 못했어요">
            <p>{state.error}</p>
          </Notice>
          {state.items.length === 0 && (
            <div>
              <Button
                variant="secondary"
                onClick={() => {
                  setState(null);
                  setAttempt((value) => value + 1);
                }}
              >
                다시 불러오기
              </Button>
            </div>
          )}
        </div>
      )}
      {state === null && showSkeleton && <Skeleton className="h-40 w-full" />}
      {state !== null && state.items.length === 0 && !state.error && (
        <EmptyState title="조건에 맞는 신고가 없어요" description="처리 상태나 긴급 조건을 바꿔 보세요." />
      )}
      {state !== null && state.items.length > 0 && (
        <ul className="flex flex-col rounded-control border border-contour bg-card">
          {state.items.map((item) => (
            <ReportRow key={item.reportId} item={item} />
          ))}
        </ul>
      )}
      {state?.hasNext && (
        <div>
          <Button variant="secondary" loading={loadingMore} onClick={() => void loadMore()}>
            더 보기
          </Button>
        </div>
      )}
    </section>
  );
}

function ReportRow({ item }: { item: AdminReportSummary }) {
  const status = REPORT_STATUS_META[item.status];
  return (
    <li className="border-t border-contour first:border-t-0">
      <Link
        to={`/admin/reports/${item.reportId}`}
        className="flex min-h-11 flex-col gap-1 px-4 py-3 hover:bg-paper-deep sm:flex-row sm:items-center sm:gap-4"
      >
        <span className="flex flex-wrap items-center gap-2 sm:w-72 sm:shrink-0">
          {item.urgent && <Badge tone="danger">긴급</Badge>}
          <span className="text-base font-semibold text-ink">{REPORT_TYPE_LABELS[item.type]}</span>
          <span className="text-sm text-ink-muted">{REPORT_KIND_LABELS[item.kind]}</span>
          <Badge tone={status.tone}>{status.label}</Badge>
        </span>
        <span className="min-w-0 flex-1 break-words text-base text-ink">
          {item.reporter.nickname} → {item.target.nickname}
        </span>
        <span className="font-mono text-sm tabular-nums text-ink-muted">{formatKstDateTime(item.createdAt)}</span>
      </Link>
    </li>
  );
}
