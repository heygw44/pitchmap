import { apiFetch } from './client';
import type {
  BakjiConfirmationResponse,
  BakjiCreateRequest,
  BakjiProblemReportRequest,
  BakjiSubmissionResponse,
} from './types';

// 제보는 이메일 인증을 마친 회원만 할 수 있다. 등록은 중복 후보가 있어도 이미 끝난 상태로 응답한다.
export function createBakji(body: BakjiCreateRequest, signal?: AbortSignal): Promise<BakjiSubmissionResponse> {
  return apiFetch<BakjiSubmissionResponse>('/api/bakjis', { method: 'POST', body, signal });
}

export function confirmBakji(spotId: number, signal?: AbortSignal): Promise<BakjiConfirmationResponse> {
  return apiFetch<BakjiConfirmationResponse>(`/api/bakjis/${spotId}/confirmations`, { method: 'POST', signal });
}

// 서버는 본문 없이 201만 준다.
export function reportBakji(spotId: number, body: BakjiProblemReportRequest, signal?: AbortSignal): Promise<void> {
  return apiFetch<void>(`/api/bakjis/${spotId}/reports`, { method: 'POST', body, signal });
}
