import { apiFetch } from './client';
import type { Me, MeUpdateRequest, MemberProfile } from './types';

export function updateMe(req: MeUpdateRequest): Promise<Me> {
  return apiFetch<Me>('/api/me', { method: 'PATCH', body: req });
}

// 비로그인으로 부르면 서버가 닉네임만 준다. 없는 회원과 탈퇴한 회원은 404다.
export function fetchMemberProfile(memberId: number, signal?: AbortSignal): Promise<MemberProfile> {
  return apiFetch<MemberProfile>(`/api/members/${memberId}/profile`, { signal });
}
