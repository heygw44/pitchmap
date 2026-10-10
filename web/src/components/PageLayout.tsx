import type { ReactNode } from 'react';
import { HubLayout } from './HubLayout';

type PageLayoutProps = {
  title: string;
  // 표나 목록을 넓게 보여 줄 화면에서 켠다. 기본은 좁은 한 열(max-w-2xl)이다.
  wide?: boolean;
  description?: string;
  breadcrumb?: ReactNode;
  meta?: ReactNode;
  actions?: ReactNode;
  tabs?: ReactNode;
  aside?: ReactNode;
  children: ReactNode;
};

// 지도가 없는 화면(내 정보, 회원 프로필, 행사 등)의 틀이다. 목록 중심 화면과 같은 머리글 띠(HubLayout)를 쓴다.
export function PageLayout({ wide = false, children, ...rest }: PageLayoutProps) {
  return (
    <HubLayout width={wide ? 'wide' : 'narrow'} {...rest}>
      {children}
    </HubLayout>
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
