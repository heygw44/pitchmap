import { LoginPage } from '../features/member/LoginPage';
import { SignupPage } from '../features/member/SignupPage';
import { VerifyEmailPage } from '../features/member/VerifyEmailPage';
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
  { path: '/login', render: () => <LoginPage /> },
  { path: '/signup', render: () => <SignupPage /> },
  { path: '/verify-email', render: () => <VerifyEmailPage /> },
];
