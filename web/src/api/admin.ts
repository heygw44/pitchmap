import { apiFetch } from './client';
import type {
  AdminReportActionRequest,
  AdminReportActionResponse,
  AdminReportDetail,
  AdminReportSummary,
  AdminSpotStatus,
  AdminSpotSummary,
  Page,
  ReportStatus,
} from './types';

export const ADMIN_PAGE_SIZE = 20;

export function fetchAdminMemberReports(
  params: { status?: ReportStatus; urgent?: boolean; page: number },
  signal?: AbortSignal,
): Promise<Page<AdminReportSummary>> {
  return apiFetch<Page<AdminReportSummary>>('/api/admin/member-reports', {
    query: { status: params.status, urgent: params.urgent, page: params.page, size: ADMIN_PAGE_SIZE },
    signal,
  });
}

export function fetchAdminMemberReport(reportId: number, signal?: AbortSignal): Promise<AdminReportDetail> {
  return apiFetch<AdminReportDetail>(`/api/admin/member-reports/${reportId}`, { signal });
}

export function startReportReview(reportId: number): Promise<{ reportId: number; status: ReportStatus }> {
  return apiFetch(`/api/admin/member-reports/${reportId}/start-review`, { method: 'POST' });
}

export function actionReport(reportId: number, request: AdminReportActionRequest): Promise<AdminReportActionResponse> {
  return apiFetch<AdminReportActionResponse>(`/api/admin/member-reports/${reportId}/action`, {
    method: 'POST',
    body: request,
  });
}

export function dismissReport(
  reportId: number,
  request: { note?: string },
): Promise<{ reportId: number; status: ReportStatus }> {
  return apiFetch(`/api/admin/member-reports/${reportId}/dismiss`, { method: 'POST', body: request });
}

export function liftSanction(sanctionId: number): Promise<{ sanctionId: number; status: string }> {
  return apiFetch(`/api/admin/sanctions/${sanctionId}/lift`, { method: 'POST' });
}

export function fetchAdminSpots(
  params: { status: AdminSpotStatus; page: number },
  signal?: AbortSignal,
): Promise<Page<AdminSpotSummary>> {
  return apiFetch<Page<AdminSpotSummary>>('/api/admin/spots', {
    query: { status: params.status, page: params.page, size: ADMIN_PAGE_SIZE },
    signal,
  });
}

export function hideSpot(spotId: number): Promise<{ spotId: number; status: string }> {
  return apiFetch(`/api/admin/spots/${spotId}/hide`, { method: 'POST' });
}

export function restoreSpot(spotId: number): Promise<{ spotId: number; status: string }> {
  return apiFetch(`/api/admin/spots/${spotId}/restore`, { method: 'POST' });
}
