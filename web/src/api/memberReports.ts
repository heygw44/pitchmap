import { apiFetch } from './client';
import type { MemberReportCreateRequest, MemberReportCreateResponse } from './types';

export function createMemberReport(request: MemberReportCreateRequest): Promise<MemberReportCreateResponse> {
  return apiFetch<MemberReportCreateResponse>('/api/member-reports', { method: 'POST', body: request });
}
