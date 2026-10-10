import type { ReactNode } from 'react';
import { SiteHeader } from '../../app/layout/SiteHeader';

type AuthLayoutProps = {
  title: string;
  description?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
};

// 로그인·가입·인증 코드 화면이 함께 쓰는 틀이다. 한 화면에서 한 가지 일만 하도록 머리글 아래 카드 하나만 두고,
// 알림 띠·하단 탭·바닥글은 두지 않는다. 머리글의 로그인 링크도 이 화면에서는 겹치므로 계정 표시를 숨긴다.
export function AuthLayout({ title, description, children, footer }: AuthLayoutProps) {
  return (
    <div className="min-h-dvh bg-paper bg-contour">
      <SiteHeader minimal />
      <main className="mx-auto flex max-w-screen-sm flex-col gap-6 px-4 py-10">
        <section className="flex flex-col gap-4 rounded-control border border-contour bg-card p-6">
          <h1 className="font-serif text-xl font-semibold text-ink">{title}</h1>
          {description !== undefined && description !== null && <div className="text-ink-muted">{description}</div>}
          {children}
        </section>
        {footer !== undefined && footer !== null && (
          <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-ink-muted">{footer}</div>
        )}
      </main>
    </div>
  );
}
