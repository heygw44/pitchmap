import { BasecampManagePage } from '../features/basecamp/BasecampManagePage';
import { MyBasecampsPage } from '../features/basecamp/MyBasecampsPage';
import { CompanionReviewsPage } from '../features/review/CompanionReviewsPage';
import { LoginPage } from '../features/member/LoginPage';
import { MemberProfilePage } from '../features/member/MemberProfilePage';
import { MyPage } from '../features/member/MyPage';
import { SignupPage } from '../features/member/SignupPage';
import { VerifyEmailPage } from '../features/member/VerifyEmailPage';
import { IdentityVerificationPage } from '../features/trust/IdentityVerificationPage';
import { MapPage } from '../features/spot/MapPage';
import NotFoundPage from './NotFoundPage';
import type { RouteDef } from './router';

export const routes: RouteDef[] = [
  { path: '/', render: () => <MapPage /> },
  {
    path: '/spots/:spotId',
    // 장소 ID는 양의 정수만 있다. 그 밖의 값은 서버에 묻지 않고 없는 페이지로 보여 준다.
    render: (params) => {
      const raw = params.spotId ?? '';
      const spotId = Number(raw);
      return /^\d+$/.test(raw) && Number.isSafeInteger(spotId) && spotId > 0 ? (
        <MapPage spotId={spotId} />
      ) : (
        <NotFoundPage />
      );
    },
  },
  { path: '/bakjis/new', render: () => <MapPage mode="report" /> },
  { path: '/basecamps', render: () => <MapPage mode="basecamps" /> },
  {
    path: '/basecamps/new',
    // 장소 ID는 주소의 spotId에서 읽는다. 양의 정수가 아니면 서버에 묻지 않고 없는 페이지로 보여 준다.
    render: () => {
      const spotId = toPositiveId(new URLSearchParams(window.location.search).get('spotId') ?? undefined);
      return spotId === null ? <NotFoundPage /> : <MapPage mode="basecampOpen" openSpotId={spotId} />;
    },
  },
  {
    path: '/basecamps/:basecampId',
    // 베이스캠프 ID도 양의 정수만 있다. 위의 '/basecamps/new'가 먼저 맞아야 하므로 이 경로는 그 뒤에 둔다.
    render: (params) => {
      const basecampId = toPositiveId(params.basecampId);
      return basecampId === null ? <NotFoundPage /> : <MapPage mode="basecamps" basecampId={basecampId} />;
    },
  },
  {
    path: '/basecamps/:basecampId/manage',
    // 세그먼트 수가 달라서 '/basecamps/:basecampId'와 겹치지 않는다.
    render: (params) => {
      const basecampId = toPositiveId(params.basecampId);
      return basecampId === null ? <NotFoundPage /> : <BasecampManagePage key={basecampId} basecampId={basecampId} />;
    },
  },
  { path: '/me/basecamps', render: () => <MyBasecampsPage /> },
  { path: '/me/companion-reviews', render: () => <CompanionReviewsPage /> },
  { path: '/login', render: () => <LoginPage /> },
  { path: '/signup', render: () => <SignupPage /> },
  { path: '/verify-email', render: () => <VerifyEmailPage /> },
  { path: '/identity-verification', render: () => <IdentityVerificationPage /> },
  { path: '/me', render: () => <MyPage /> },
  {
    path: '/members/:memberId',
    // 회원 ID도 양의 정수만 있다. 그 밖의 값은 서버에 묻지 않고 없는 페이지로 보여 준다.
    render: (params) => {
      const memberId = toPositiveId(params.memberId);
      return memberId === null ? <NotFoundPage /> : <MemberProfilePage key={memberId} memberId={memberId} />;
    },
  },
];

function toPositiveId(raw: string | undefined): number | null {
  const value = Number(raw ?? '');
  return /^\d+$/.test(raw ?? '') && Number.isSafeInteger(value) && value > 0 ? value : null;
}
