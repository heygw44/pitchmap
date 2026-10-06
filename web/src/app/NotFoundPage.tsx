import { Link } from './router';

export default function NotFoundPage() {
  return (
    <main className="flex min-h-dvh items-center justify-center bg-paper bg-contour px-4">
      <section className="w-full max-w-sm rounded-control border border-contour bg-card p-6">
        <h1 className="font-serif text-xl font-semibold">페이지를 찾을 수 없어요</h1>
        <p className="mt-2 text-ink-muted">주소가 바뀌었거나 없는 페이지예요. 지도에서 다시 찾아보세요.</p>
        <Link
          to="/"
          className="mt-6 inline-flex min-h-11 items-center rounded-control bg-forest px-4 text-white hover:bg-forest-strong"
        >
          지도로 돌아가기
        </Link>
      </section>
    </main>
  );
}
