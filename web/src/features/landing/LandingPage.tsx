import { useEffect } from 'react';
import type { ReactNode } from 'react';
import { SiteFooter } from '../../app/layout/SiteFooter';
import { SiteHeader } from '../../app/layout/SiteHeader';
import { Link } from '../../app/router';
import { Badge } from '../../components/Badge';
import { Icon } from '../../components/icons';
import { trustLevelBadge } from '../member/memberLabels';
import { useSession } from '../member/session';
import { AnnouncementBar } from '../program/AnnouncementBar';
import {
  HeroPreview,
  StepBasecampFragment,
  StepProgramFragment,
  StepSpotFragment,
  TrustPreview,
  WarningPreview,
} from './LandingPreviews';

const PRIMARY_LINK_CLASS =
  'inline-flex min-h-12 items-center justify-center gap-2 rounded-control bg-forest px-5 text-base font-semibold text-white hover:bg-forest-strong';

const TEXT_LINK_CLASS =
  'inline-flex min-h-11 items-center font-semibold text-forest underline underline-offset-4 hover:text-forest-strong';

const SECTION_TITLE_CLASS = 'font-serif text-2xl font-semibold leading-snug text-ink lg:text-[1.75rem]';

// 신뢰 단계마다 무엇을 뜻하는지 한 줄로 적는다. 단계는 서버가 매번 계산하고, 여기서는 기준만 소개한다.
const TRUST_LEVEL_MEANINGS = [
  '이메일 인증을 마친 회원이에요. 장소를 둘러보고 후기를 남길 수 있어요.',
  '본인확인으로 성인임을 확인한 회원이에요. 베이스캠프를 열거나 합류할 수 있어요.',
  '동행을 3번 이상 마쳤고, 함께 간 사람 80% 이상이 다시 가고 싶다고 했어요. 최근 180일 동안 제재가 없어요.',
];

// 처음 온 사람에게 피치맵이 무엇을 하는지 보여 주고 지도로 보내는 첫 화면이다.
// 한 화면에 주 행동은 "지도에서 박지 찾기" 하나만 두고, 아래 섹션은 차별점 세 가지를 차례로 설명한다.
export function LandingPage() {
  const session = useSession();
  const anonymous = session.status === 'anonymous' || (session.status === 'authenticated' && session.me === null);

  useEffect(() => {
    document.title = '피치맵 · 자도 되는 박지와 믿을 수 있는 동행';
  }, []);

  return (
    <div className="flex min-h-dvh flex-col bg-paper">
      <AnnouncementBar />
      <SiteHeader mobileAccount />

      <main className="flex-1 break-keep">
        <section className="mx-auto grid max-w-screen-xl items-center gap-10 px-4 pb-14 pt-10 lg:grid-cols-[1.1fr_1fr] lg:gap-14 lg:pb-20 lg:pt-16">
          <div className="flex flex-col gap-5">
            <h1 className="font-serif text-3xl font-semibold leading-tight text-ink sm:text-4xl xl:text-[2.75rem]">
              자도 되는 곳을 찾고,
              <br />
              믿을 수 있는 사람과 <br className="sm:hidden" />
              함께 가요
            </h1>
            <p className="max-w-lg text-lg leading-relaxed text-ink-muted">
              피치맵은 백패커를 위한 박지 지도예요. 공원 경계 경고는 출처와 기준일까지, 동행은 본인확인과 서로 쓴
              후기까지 보여 드려요.
            </p>
            <div className="flex flex-wrap items-center gap-x-5 gap-y-2 pt-1">
              <Link to="/map" className={PRIMARY_LINK_CLASS}>
                <Icon name="map" size={20} />
                지도에서 박지 찾기
              </Link>
              {anonymous && (
                <Link to="/signup" className={TEXT_LINK_CLASS}>
                  처음이면 가입하기
                </Link>
              )}
            </div>
            <p className="text-sm text-ink-muted">가입하지 않아도 지도와 장소 정보는 볼 수 있어요.</p>
          </div>
          <HeroPreview />
        </section>

        <section aria-labelledby="landing-evidence" className="border-t border-contour bg-card">
          <div className="mx-auto grid max-w-screen-xl items-start gap-10 px-4 py-14 lg:grid-cols-2 lg:gap-16 lg:py-20">
            <div className="flex flex-col gap-4 lg:pt-4">
              <h2 id="landing-evidence" className={SECTION_TITLE_CLASS}>
                여기서 자도 되는지,
                <br />
                근거를 같이 보여 드려요
              </h2>
              <p className="text-base leading-relaxed text-ink-muted">
                장소가 국립공원 같은 자연공원 경계 안에 있을 수 있으면 경고를 띄워요. 어느 기관의 데이터로, 언제
                기준으로 판단했는지도 함께 적어요.
              </p>
              <p className="text-base leading-relaxed text-ink-muted">
                경고가 뜬 박지로는 베이스캠프를 열 수 없어요. 여럿이 함께 야영 금지 구역에 들어가는 일을 막기
                위해서예요.
              </p>
              <ul className="flex flex-col gap-2 pt-2 text-base text-ink">
                <CheckItem>공공 야영장, 자연휴양림, 회원이 제보한 박지를 한 지도에서</CheckItem>
                <CheckItem>휴장 중인 곳도 숨기지 않고 휴장 표시로</CheckItem>
                <CheckItem>날씨와 일몰 시각은 기상청·한국천문연구원 자료로</CheckItem>
              </ul>
            </div>
            <WarningPreview />
          </div>
        </section>

        <section aria-labelledby="landing-trust" className="border-t border-contour">
          <div className="mx-auto grid max-w-screen-xl items-start gap-10 px-4 py-14 lg:grid-cols-[1fr_1.1fr] lg:gap-16 lg:py-20">
            <div className="order-2 lg:order-1">
              <TrustPreview />
            </div>
            <div className="order-1 flex flex-col gap-5 lg:order-2 lg:pt-4">
              <h2 id="landing-trust" className={SECTION_TITLE_CLASS}>
                이 사람과 가도 되는지,
                <br />
                쌓인 기록으로 판단해요
              </h2>
              <dl className="flex flex-col">
                {TRUST_LEVEL_MEANINGS.map((meaning, level) => {
                  const badge = trustLevelBadge(level);
                  return (
                    <div
                      key={badge.label}
                      className="flex flex-col gap-1.5 border-t border-contour py-3 first:border-t-0 first:pt-0 sm:flex-row sm:gap-4"
                    >
                      <dt className="shrink-0 sm:w-28">
                        <Badge tone={badge.tone} icon={badge.icon}>
                          {badge.label}
                        </Badge>
                      </dt>
                      <dd className="text-base text-ink">{meaning}</dd>
                    </div>
                  );
                })}
              </dl>
              <p className="text-base leading-relaxed text-ink-muted">
                동행 후기는 내가 먼저 써야 상대 후기를 볼 수 있어요. 그래서 서로 눈치 보지 않고 솔직하게 남겨요. 불편한
                일이 생기면 신고해 주세요. 성희롱이나 위협 신고는 접수되는 즉시 상대를 임시 정지해요.
              </p>
            </div>
          </div>
        </section>

        <section aria-labelledby="landing-flow" className="border-t border-contour bg-card">
          <div className="mx-auto flex max-w-screen-xl flex-col gap-10 px-4 py-14 lg:py-20">
            <h2 id="landing-flow" className={SECTION_TITLE_CLASS}>
              장소를 고르면 동행과 행사까지 이어져요
            </h2>
            <ol className="grid gap-10 lg:grid-cols-3 lg:gap-0 lg:divide-x lg:divide-contour">
              <FlowStep number="01" title="장소 찾기" fragment={<StepSpotFragment />}>
                지도에서 마음에 드는 곳을 골라요. 시설과 후기, 날씨, 그곳으로 가는 베이스캠프가 한 화면에 모여요.
              </FlowStep>
              <FlowStep number="02" title="베이스캠프 합류" fragment={<StepBasecampFragment />}>
                같은 날 같은 곳으로 가는 모임에 합류 신청을 보내요. 확정되면 멤버끼리만 연락 수단이 공개돼요.
              </FlowStep>
              <FlowStep number="03" title="공식 행사 신청" fragment={<StepProgramFragment />}>
                혼자 가기 망설여지면 공식 행사에 신청해요. 정원만큼만 먼저 신청한 순서대로 받아요.
              </FlowStep>
            </ol>
            <p className="-mt-6 text-xs text-ink-muted">단계마다 붙은 조각은 예시 화면이에요.</p>
          </div>
        </section>

        <section aria-labelledby="landing-cta" className="border-t border-contour bg-forest-soft">
          <div className="mx-auto flex max-w-screen-xl flex-col gap-5 px-4 py-12 md:flex-row md:items-center md:justify-between">
            <div className="flex flex-col gap-2">
              <h2 id="landing-cta" className="font-serif text-2xl font-semibold text-forest-deep">
                이번 주말 박지, 지도에서 먼저 확인해요
              </h2>
              <p className="text-base text-ink">공원 경계 경고와 날씨를 보고 나서 떠나도 늦지 않아요.</p>
            </div>
            <Link to="/map" className={`${PRIMARY_LINK_CLASS} self-start md:self-auto`}>
              지도 열기
            </Link>
          </div>
        </section>
      </main>

      <SiteFooter />
    </div>
  );
}

function CheckItem({ children }: { children: ReactNode }) {
  return (
    <li className="flex items-start gap-2">
      <Icon name="check" size={20} className="mt-0.5 shrink-0 text-forest" />
      <span>{children}</span>
    </li>
  );
}

type FlowStepProps = {
  number: string;
  title: string;
  fragment: ReactNode;
  children: ReactNode;
};

// 순서가 뜻을 가지는 흐름이라 번호를 붙인다. 데스크톱에서는 세 단계를 가로로 두고 세로선으로 나눈다.
function FlowStep({ number, title, fragment, children }: FlowStepProps) {
  return (
    <li className="flex flex-col gap-4 lg:px-8 lg:first:pl-0 lg:last:pr-0">
      <div className="flex items-baseline gap-3">
        <span className="font-mono text-sm tabular-nums text-ink-subtle">{number}</span>
        <h3 className="font-serif text-xl font-semibold text-ink">{title}</h3>
      </div>
      <p className="text-base leading-relaxed text-ink-muted">{children}</p>
      <div className="mt-auto">{fragment}</div>
    </li>
  );
}
