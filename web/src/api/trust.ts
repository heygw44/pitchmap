import { apiFetch } from './client';
import type { IdentityVerificationRequest, IdentityVerificationResponse } from './types';

// 서버는 시연용 가짜 제공자로 본인확인하고, 출생연도·성별과 CI 해시만 저장한다.
export function verifyIdentity(req: IdentityVerificationRequest): Promise<IdentityVerificationResponse> {
  return apiFetch<IdentityVerificationResponse>('/api/me/identity-verification', { method: 'POST', body: req });
}
