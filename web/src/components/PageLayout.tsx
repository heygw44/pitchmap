import type { ReactNode } from 'react';
import { Link } from '../app/router';

type PageLayoutProps = {
  title: string;
  children: ReactNode;
};

// 지도가 없는 화면(내 정보, 회원 프로필)의 틀이다. 가운데 한 열에 서비스 이름과 제목을 두고, 내용은 카드 여러 개로 나눈다.
export function PageLayout({ title, children }: PageLayoutProps) {
  return (
    <div className="min-h-dvh bg-paper bg-contour">
      <main className="mx-auto flex max-w-screen-sm flex-col gap-4 px-4 py-10">
        <Link to="/" className="self-start font-serif text-2xl font-semibold text-forest-deep">
          피치맵
        </Link>
        <h1 className="font-serif text-xl font-semibold text-ink">{title}</h1>
        {children}
      </main>
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
