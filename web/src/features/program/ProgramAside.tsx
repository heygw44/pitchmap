import { Link } from '../../app/router';
import { AsideSection } from '../../components/HubLayout';
import { withNext } from '../member/nextPath';

const TEXT_LINK_CLASS = 'font-semibold text-forest underline underline-offset-2';

// 공식 행사 목록과 상세의 오른쪽 안내 열이다. 신청 규칙과 내 신청으로 가는 길을 보여 준다.
export function ProgramAside({ signedIn }: { signedIn: boolean }) {
  return (
    <>
      <AsideSection title="신청 안내">
        <ul className="list-disc pl-5">
          <li>선착순이고 한 사람이 한 건만 신청해요.</li>
          <li>신청하면 결제 기한(보통 15분) 안에 결제해야 확정돼요.</li>
          <li>
            숙박 행사는{' '}
            <Link to="/identity-verification" className={TEXT_LINK_CLASS}>
              본인확인
            </Link>
            (성인)이 필요해요.
          </li>
          <li>행사 시작 3일 전까지 취소하면 환불돼요.</li>
          <li>자리가 다 차면 행사 상세에서 빈자리 알림을 신청할 수 있어요.</li>
        </ul>
      </AsideSection>
      <AsideSection title="내 신청">
        {signedIn ? (
          <Link to="/me/program-applications" className={`inline-flex min-h-11 items-center text-base ${TEXT_LINK_CLASS}`}>
            내 행사 신청 보기
          </Link>
        ) : (
          <Link
            to={withNext('/login', '/me/program-applications')}
            className={`inline-flex min-h-11 items-center text-base ${TEXT_LINK_CLASS}`}
          >
            로그인하고 신청 내역 보기
          </Link>
        )}
      </AsideSection>
    </>
  );
}
