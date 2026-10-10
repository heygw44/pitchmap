import type { ReactNode } from 'react';
import { useSession } from '../../features/member/session';
import { Link } from '../router';

// 화면에 보여 주는 데이터의 출처다. 장소·경계는 공공데이터, 날씨·일몰은 기상청·천문연, 바탕 지도는 카카오맵이다.
const DATA_SOURCES = [
  '한국관광공사 고캠핑',
  '산림청 국립자연휴양림관리소',
  '한국보호지역통합DB(KDPA)',
  '기상청',
  '한국천문연구원',
  '카카오맵',
];

const LINK_CLASS = 'inline-flex min-h-11 items-center text-sm text-ink-muted hover:text-ink lg:min-h-8';

// 랜딩과 지도가 없는 화면 맨 아래에 둔다. 지도 화면은 화면 전체를 지도가 쓰므로 바닥글이 없다.
export function SiteFooter() {
  const session = useSession();
  const signedIn = session.status === 'authenticated' && session.me !== null;

  return (
    <footer className="border-t border-contour bg-paper">
      <div className="mx-auto grid max-w-screen-xl gap-8 px-4 py-10 md:grid-cols-[1.4fr_1fr_1fr_1.4fr]">
        <div className="flex flex-col gap-2">
          <Link to="/" className="self-start font-serif text-xl font-semibold text-forest-deep">
            피치맵
          </Link>
          <p className="max-w-xs text-sm text-ink-muted">
            자도 되는 곳을 찾고, 믿을 수 있는 사람과 함께 가는 백패킹 서비스예요.
          </p>
        </div>

        <FooterColumn title="서비스">
          <Link to="/map" className={LINK_CLASS}>
            지도
          </Link>
          <Link to="/basecamps" className={LINK_CLASS}>
            베이스캠프
          </Link>
          <Link to="/programs" className={LINK_CLASS}>
            공식 행사
          </Link>
        </FooterColumn>

        <FooterColumn title="계정">
          {signedIn ? (
            <>
              <Link to="/me" className={LINK_CLASS}>
                내 정보
              </Link>
              <Link to="/me/notifications" className={LINK_CLASS}>
                알림
              </Link>
            </>
          ) : (
            <>
              <Link to="/login" className={LINK_CLASS}>
                로그인
              </Link>
              <Link to="/signup" className={LINK_CLASS}>
                가입하기
              </Link>
            </>
          )}
        </FooterColumn>

        <FooterColumn title="데이터 출처">
          {DATA_SOURCES.map((source) => (
            <span key={source} className="text-sm text-ink-muted">
              {source}
            </span>
          ))}
        </FooterColumn>
      </div>

      <div className="border-t border-contour">
        <div className="mx-auto flex max-w-screen-xl flex-col gap-2 px-4 py-5 text-xs text-ink-muted md:flex-row md:items-center md:justify-between">
          <p>공원 경계는 법적 효력이 없는 참고 자료예요. 공식 경계는 고시 도면을 확인해 주세요.</p>
          <p className="flex items-center gap-3">
            <span>© 2026 피치맵</span>
            <a
              href="https://github.com/heygw44/pitchmap"
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex min-h-11 items-center underline underline-offset-2 hover:text-ink md:min-h-0"
            >
              GitHub
            </a>
          </p>
        </div>
      </div>
    </footer>
  );
}

function FooterColumn({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1">
      <h2 className="mb-1 text-sm font-semibold text-ink">{title}</h2>
      {children}
    </div>
  );
}
