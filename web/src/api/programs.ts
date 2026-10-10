import { apiFetch } from './client';
import type {
  MyProgramApplicationItem,
  Page,
  ProgramApplicationStatus,
  ProgramApplyResponse,
  ProgramCancelResponse,
  ProgramDetail,
  ProgramPayResponse,
  ProgramStatus,
  ProgramSummary,
} from './types';

// 목록 필터에는 취소된 행사가 없다. 서버가 취소된 행사를 목록에서 뺀다.
export type ProgramListStatus = Exclude<ProgramStatus, 'CANCELED'>;

export function listPrograms(
  status: ProgramListStatus | undefined,
  page: number,
  signal?: AbortSignal,
): Promise<Page<ProgramSummary>> {
  return apiFetch<Page<ProgramSummary>>('/api/programs', { query: { status, page }, signal });
}

export function getProgram(programId: number, signal?: AbortSignal): Promise<ProgramDetail> {
  return apiFetch<ProgramDetail>(`/api/programs/${programId}`, { signal });
}

export function applyProgram(
  programId: number,
  idempotencyKey: string,
  signal?: AbortSignal,
): Promise<ProgramApplyResponse> {
  return apiFetch<ProgramApplyResponse>(`/api/programs/${programId}/applications`, {
    method: 'POST',
    idempotencyKey,
    signal,
  });
}

// 가짜 결제만 있다. 실제 결제 수단은 만들지 않는다.
export function payApplication(
  applicationId: number,
  idempotencyKey: string,
  signal?: AbortSignal,
): Promise<ProgramPayResponse> {
  return apiFetch<ProgramPayResponse>(`/api/program-applications/${applicationId}/pay`, {
    method: 'POST',
    body: { method: 'FAKE_CARD' },
    idempotencyKey,
    signal,
  });
}

// 취소는 Idempotency-Key를 받지 않는다. 같은 요청을 다시 보내면 서버가 PROGRAM_INVALID_STATE로 답한다.
export function cancelApplication(applicationId: number, signal?: AbortSignal): Promise<ProgramCancelResponse> {
  return apiFetch<ProgramCancelResponse>(`/api/program-applications/${applicationId}/cancel`, {
    method: 'POST',
    signal,
  });
}

// 서버는 본문 없이 201(이미 신청했으면 200)만 준다.
export function subscribeVacancyAlert(programId: number, signal?: AbortSignal): Promise<void> {
  return apiFetch<void>(`/api/programs/${programId}/vacancy-alerts`, { method: 'POST', signal });
}

export function unsubscribeVacancyAlert(programId: number, signal?: AbortSignal): Promise<void> {
  return apiFetch<void>(`/api/programs/${programId}/vacancy-alerts`, { method: 'DELETE', signal });
}

export function listMyProgramApplications(
  status: ProgramApplicationStatus | undefined,
  page: number,
  signal?: AbortSignal,
): Promise<Page<MyProgramApplicationItem>> {
  return apiFetch<Page<MyProgramApplicationItem>>('/api/me/program-applications', {
    query: { status, page },
    signal,
  });
}
