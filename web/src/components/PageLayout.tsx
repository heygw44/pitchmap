import type { ReactNode } from 'react';
import { BottomTabBar } from '../app/layout/BottomTabBar';
import { SiteFooter } from '../app/layout/SiteFooter';
import { SiteHeader } from '../app/layout/SiteHeader';
import { AnnouncementBar } from '../features/program/AnnouncementBar';

type PageLayoutProps = {
  title: string;
  // 표나 목록을 넓게 보여 줄 화면에서 켠다. 기본은 한 열(sm) 폭이다.
  wide?: boolean;
  children: ReactNode;
};

// 지도가 없는 화면(내 정보, 회원 프로필, 행사)의 틀이다. 위에 알림 띠와 머리글, 가운데 한 열에 제목과 카드, 아래에 바닥글을 둔다.
// 모바일에서는 하단 탭이 화면 아래를 덮으므로 그 높이만큼 아래 여백을 둔다.
export function PageLayout({ title, wide = false, children }: PageLayoutProps) {
  return (
    <div className="flex min-h-dvh flex-col bg-paper pb-(--bottom-nav-h)">
      <AnnouncementBar />
      <SiteHeader />
      <div className="flex-1 bg-paper bg-contour">
        <main className={`mx-auto flex ${wide ? 'max-w-screen-lg' : 'max-w-screen-sm'} flex-col gap-4 px-4 py-8`}>
          <h1 className="font-serif text-xl font-semibold text-ink">{title}</h1>
          {children}
        </main>
      </div>
      <SiteFooter />
      <BottomTabBar />
    </div>
  );
}

// 화면 안의 한 묶음. 글자는 등고선 무늬 위가 아니라 카드 바탕 위에 둔다.
export function PageCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-3 rounded-control border border-contour bg-card p-5">
      <h2 className="font-serif text-lg text-ink">{title}</h2>
      {children}
    </section>
  );
}
