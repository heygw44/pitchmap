import type { ReactNode } from 'react';
import { BottomTabBar } from '../app/layout/BottomTabBar';
import { SiteFooter } from '../app/layout/SiteFooter';
import { SiteHeader } from '../app/layout/SiteHeader';
import { AnnouncementBar } from '../features/program/AnnouncementBar';

type HubLayoutProps = {
  title: string;
  description?: string;
  // 제목 위에 놓는 이동 경로(뒤로 가기 링크 등)
  breadcrumb?: ReactNode;
  // 제목 바로 아래 한 줄(분류, 글쓴이, 시각 같은 정보)
  meta?: ReactNode;
  actions?: ReactNode;
  tabs?: ReactNode;
  aside?: ReactNode;
  children: ReactNode;
};

// 목록 중심 화면(커뮤니티, 공식 행사)의 틀이다. 제목과 탭은 카드 색 띠 위에 두고, 목록은 등고선 무늬 위 패널에 둔다.
// 오른쪽 안내 열(aside)은 넓은 화면에서만 옆에 놓고, 좁은 화면에서는 목록 아래로 내려간다.
export function HubLayout({ title, description, breadcrumb, meta, actions, tabs, aside, children }: HubLayoutProps) {
  return (
    <div className="flex min-h-dvh flex-col bg-paper pb-(--bottom-nav-h)">
      <AnnouncementBar />
      <SiteHeader />
      <div className="border-b border-contour bg-card">
        <div className="mx-auto max-w-screen-xl px-4 pt-6">
          {breadcrumb && <div className="-mt-2 mb-1 text-sm text-ink-muted">{breadcrumb}</div>}
          <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-3 pb-3">
            <div className="flex min-w-0 flex-col gap-1">
              <h1 className="break-words font-serif text-xl font-semibold text-ink">{title}</h1>
              {description && <p className="text-sm text-ink-muted">{description}</p>}
              {meta}
            </div>
            {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
          </div>
          {tabs}
        </div>
      </div>
      <div className="flex-1 bg-paper bg-contour">
        <main className="mx-auto max-w-screen-xl px-4 py-6 lg:grid lg:grid-cols-[minmax(0,1fr)_18rem] lg:items-start lg:gap-6">
          <div className="flex min-w-0 flex-col gap-4">{children}</div>
          {aside && <aside className="mt-6 flex flex-col gap-4 lg:mt-0">{aside}</aside>}
        </main>
      </div>
      <SiteFooter />
      <BottomTabBar />
    </div>
  );
}

// 안내 열의 한 묶음이다. 본문 목록은 text-sm이고 항목 사이를 띄운다.
export function AsideSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-2 rounded-control border border-contour bg-card p-4">
      <h2 className="font-serif text-base font-semibold text-ink">{title}</h2>
      <div className="flex flex-col gap-2 text-sm text-ink-muted [&_ul]:flex [&_ul]:flex-col [&_ul]:gap-2">{children}</div>
    </section>
  );
}

// 목록을 감싸는 패널이다. header가 있으면 위에 한 줄 머리를 두고, 항목 사이에는 선을 긋는다.
export function ListPanel({ header, children }: { header?: string; children: ReactNode }) {
  return (
    <div className="rounded-control border border-contour bg-card">
      {header && (
        <div className="flex items-center justify-between border-b border-contour px-4 py-2 text-sm text-ink-muted">
          {header}
        </div>
      )}
      <ul className="divide-y divide-contour">{children}</ul>
    </div>
  );
}
