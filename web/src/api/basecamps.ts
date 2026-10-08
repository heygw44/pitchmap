import { apiFetch } from './client';
import type {
  BasecampApplyResponse,
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
