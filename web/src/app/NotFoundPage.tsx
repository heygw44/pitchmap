import { SiteHeader } from './layout/SiteHeader';
import { Link } from './router';

export default function NotFoundPage() {
  return (
    <div className="flex min-h-dvh flex-col bg-paper">
      <SiteHeader mobileAccount />
      <main className="flex flex-1 items-center justify-center bg-paper bg-contour px-4 py-10">
        <section className="w-full max-w-sm rounded-control border border-contour bg-card p-6">
          <h1 className="font-serif text-xl font-semibold">페이지를 찾을 수 없어요</h1>
          <p className="mt-2 text-ink-muted">주소가 바뀌었거나 없는 페이지예요. 지도에서 다시 찾아보세요.</p>
          <div className="mt-6 flex flex-wrap items-center gap-x-4 gap-y-2">
            <Link
              to="/map"
              className="inline-flex min-h-11 items-center rounded-control bg-forest px-4 font-semibold text-white hover:bg-forest-strong"
            >
              지도로 가기
            </Link>
            <Link
              to="/"
              className="inline-flex min-h-11 items-center font-semibold text-forest underline underline-offset-4 hover:text-forest-strong"
            >
              첫 화면으로
            </Link>
          </div>
        </section>
      </main>
    </div>
  );
}
