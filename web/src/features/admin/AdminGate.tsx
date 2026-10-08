import { useEffect } from 'react';
import type { ReactNode } from 'react';
import { Link, navigate, useLocation } from '../../app/router';
import { EmptyState } from '../../components/EmptyState';
import { PageLayout } from '../../components/PageLayout';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { withNext } from '../member/nextPath';
import { useSession } from '../member/session';

const NAV_ITEMS = [
  { to: '/admin/reports', label: '신고 처리' },
  { to: '/admin/spots', label: '박지 검토' },
] as const;

// 관리자 화면의 공통 틀이다. 관리자가 아니면 안에 있는 화면을 그리지 않아서 관리자 API도 부르지 않는다. 권한은 서버도 검사한다.
export function AdminPage({ title, current, children }: { title: string; current: string; children: ReactNode }) {
  const session = useSession();
  const { pathname, search } = useLocation();
  const showSkeleton = useDelayedFlag(session.status === 'loading');

  useEffect(() => {
    document.title = `${title} · 관리자 · 피치맵`;
  }, [title]);

  // 로그인한 뒤 이 화면으로 돌아오게 한다.
  useEffect(() => {
    if (session.status === 'anonymous') navigate(withNext('/login', pathname + search), { replace: true });
  }, [session.status, pathname, search]);

  if (session.status !== 'authenticated' || !session.me) {
    return (
      <PageLayout title={title} wide>
        {showSkeleton ? <Skeleton className="h-40 w-full" /> : null}
      </PageLayout>
    );
  }

  if (session.me.role !== 'ADMIN') {
    return (
      <PageLayout title={title} wide>
        <EmptyState title="관리자만 볼 수 있어요" description="관리자 계정으로 로그인한 뒤 다시 들어와 주세요." />
      </PageLayout>
    );
  }

  return (
    <PageLayout title={title} wide>
      <nav aria-label="관리자 메뉴" className="flex flex-wrap gap-2">
        {NAV_ITEMS.map((item) => (
          <Link
            key={item.to}
            to={item.to}
            aria-current={item.to === current ? 'page' : undefined}
            className={`inline-flex min-h-11 items-center rounded-control border px-4 font-semibold ${
              item.to === current
                ? 'border-forest bg-forest-soft text-forest-deep'
                : 'border-ink-subtle bg-card text-ink hover:bg-paper-deep'
            }`}
          >
            {item.label}
          </Link>
        ))}
      </nav>
      {children}
    </PageLayout>
  );
}
