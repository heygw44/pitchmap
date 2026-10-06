import { ApiError, apiFetch } from './client';
import type { LoginRequest, LoginResponse, Me, MemberStatus, SignupRequest, SignupResponse } from './types';

// 가입하면 서버가 인증 코드 메일을 보낸다.
export function signUp(req: SignupRequest): Promise<SignupResponse> {
  return apiFetch<SignupResponse>('/api/members', { method: 'POST', body: req });
}

// 이메일·비밀번호가 틀려도 401(LOGIN_FAILED)이라서 로그인 화면으로 다시 보내는 처리를 건너뛴다.
export function login(req: LoginRequest): Promise<LoginResponse> {
  return apiFetch<LoginResponse>('/api/auth/login', { method: 'POST', body: req, skipUnauthorizedHandler: true });
}

export function logout(): Promise<void> {
  return apiFetch<void>('/api/auth/logout', { method: 'POST', skipUnauthorizedHandler: true });
}

export function verifyEmail(code: string): Promise<{ status: MemberStatus }> {
  return apiFetch<{ status: MemberStatus }>('/api/me/email-verification', { method: 'POST', body: { code } });
}

export function resendVerification(): Promise<void> {
  return apiFetch<void>('/api/me/email-verification/resend', { method: 'POST' });
}

// 로그인하지 않은 상태도 정상 흐름이라서 401이면 오류 대신 null을 돌려준다.
export async function fetchMe(signal?: AbortSignal): Promise<Me | null> {
  try {
    return await apiFetch<Me>('/api/me', { signal, skipUnauthorizedHandler: true });
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null;
    }
    throw error;
  }
}
