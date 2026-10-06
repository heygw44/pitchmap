import type { ReactNode } from 'react';
import { Link } from '../../app/router';

type AuthLayoutProps = {
  title: string;
  description?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
};

// 로그인·가입·인증 코드 화면이 함께 쓰는 틀이다. 종이 바탕 위에 카드 하나만 둔다.
export function AuthLayout({ title, description, children, footer }: AuthLayoutProps) {
  return (
    <div className="min-h-dvh bg-paper bg-contour">
      <main className="mx-auto flex max-w-screen-sm flex-col gap-6 px-4 py-10">
        <Link to="/" className="self-start font-serif text-2xl font-semibold text-forest-deep">
          피치맵
        </Link>
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
