import { apiFetch } from './client';
import type {
  BasecampApplicationItem,
  BasecampApplyResponse,
  BasecampApproveResponse,
  BasecampRejectResponse,
  BasecampStatusResponse,
  KickReason,
  MyBasecampItem,
  MyBasecampsQuery,
  BasecampDetail,
  BasecampOpenRequest,
  BasecampOpenResponse,
  BasecampSearchItem,
  BasecampSearchQuery,
  Page,
} from './types';

// 지도 영역 안의 모집 중 베이스캠프를 출발일이 빠른 순서로 받는다. 로그인했으면 항목마다 신청 가능 여부가 온다.
export function searchBasecamps(query: BasecampSearchQuery, signal?: AbortSignal): Promise<Page<BasecampSearchItem>> {
  return apiFetch<Page<BasecampSearchItem>>('/api/basecamps', {
    query: {
      swLat: query.swLat,
      swLng: query.swLng,
      neLat: query.neLat,
      neLng: query.neLng,
      fromDate: query.fromDate,
      toDate: query.toDate,
      hasVacancy: query.hasVacancy,
      page: query.page,
      size: query.size,
    },
    signal,
  });
}

export function fetchBasecampDetail(basecampId: number, signal?: AbortSignal): Promise<BasecampDetail> {
  return apiFetch<BasecampDetail>(`/api/basecamps/${basecampId}`, { signal });
}

// 베이스캠프 API는 Idempotency-Key를 받지 않는다.
export function openBasecamp(body: BasecampOpenRequest, signal?: AbortSignal): Promise<BasecampOpenResponse> {
  return apiFetch<BasecampOpenResponse>('/api/basecamps', { method: 'POST', body, signal });
}

export function applyToBasecamp(
  basecampId: number,
  message?: string,
  signal?: AbortSignal,
): Promise<BasecampApplyResponse> {
  return apiFetch<BasecampApplyResponse>(`/api/basecamps/${basecampId}/applications`, {
    method: 'POST',
    body: message ? { message } : {},
    signal,
  });
}

// 서버는 본문 없이 204만 준다.
export function cancelMyApplication(basecampId: number, signal?: AbortSignal): Promise<void> {
  return apiFetch<void>(`/api/basecamps/${basecampId}/applications/me`, { method: 'DELETE', signal });
}

export const APPLICATION_PAGE_SIZE = 20;

// 캠프 리더만 받을 수 있다. 대기 중인 신청을 오래된 순서로 준다.
export function fetchBasecampApplications(
  basecampId: number,
  page: number,
  signal?: AbortSignal,
): Promise<Page<BasecampApplicationItem>> {
  return apiFetch<Page<BasecampApplicationItem>>(`/api/basecamps/${basecampId}/applications`, {
    query: { status: 'PENDING', page, size: APPLICATION_PAGE_SIZE },
    signal,
  });
}

export function approveApplication(basecampId: number, applicationId: number): Promise<BasecampApproveResponse> {
  return apiFetch<BasecampApproveResponse>(`/api/basecamps/${basecampId}/applications/${applicationId}/approve`, {
    method: 'POST',
  });
}

export function rejectApplication(basecampId: number, applicationId: number): Promise<BasecampRejectResponse> {
  return apiFetch<BasecampRejectResponse>(`/api/basecamps/${basecampId}/applications/${applicationId}/reject`, {
    method: 'POST',
  });
}

export function kickMember(basecampId: number, memberId: number, reason: KickReason): Promise<void> {
  return apiFetch<void>(`/api/basecamps/${basecampId}/members/${memberId}/kick`, {
    method: 'POST',
    body: { reason },
  });
}

// 멤버가 스스로 나간다. 서버는 본문 없이 204만 준다.
export function leaveBasecamp(basecampId: number): Promise<void> {
  return apiFetch<void>(`/api/basecamps/${basecampId}/members/me`, { method: 'DELETE' });
}

function changeStatus(basecampId: number, action: 'close' | 'reopen' | 'confirm' | 'cancel') {
  return apiFetch<BasecampStatusResponse>(`/api/basecamps/${basecampId}/${action}`, { method: 'POST' });
}

export const closeBasecamp = (basecampId: number) => changeStatus(basecampId, 'close');
export const reopenBasecamp = (basecampId: number) => changeStatus(basecampId, 'reopen');
export const confirmBasecamp = (basecampId: number) => changeStatus(basecampId, 'confirm');
export const cancelBasecamp = (basecampId: number) => changeStatus(basecampId, 'cancel');

export function updateBasecampContact(basecampId: number, contactInfo: string): Promise<BasecampStatusResponse> {
  return apiFetch<BasecampStatusResponse>(`/api/basecamps/${basecampId}/contact`, {
    method: 'PUT',
    body: { contactInfo },
  });
}

export const MY_BASECAMP_PAGE_SIZE = 20;

export function fetchMyBasecamps(query: MyBasecampsQuery, signal?: AbortSignal): Promise<Page<MyBasecampItem>> {
  return apiFetch<Page<MyBasecampItem>>('/api/me/basecamps', {
    query: {
      relation: query.relation,
      status: query.status,
      page: query.page,
      size: query.size ?? MY_BASECAMP_PAGE_SIZE,
    },
    signal,
  });
}
