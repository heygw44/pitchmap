import { apiFetch } from './client';
import type { Page, SpotReview, SpotReviewCreateRequest, SpotReviewUpdateRequest } from './types';

// 서버가 받는 목록 크기 최댓값은 50이다. 장소 상세에서는 한 번에 이만큼씩 더 불러온다.
export const SPOT_REVIEW_PAGE_SIZE = 10;

// 작성 시각이 늦은 순서다. 장소가 없거나 지도에 보이지 않으면 서버가 404로 답한다.
export function fetchSpotReviews(spotId: number, page: number, signal?: AbortSignal): Promise<Page<SpotReview>> {
  return apiFetch<Page<SpotReview>>(`/api/spots/${spotId}/reviews`, {
    query: { page, size: SPOT_REVIEW_PAGE_SIZE },
    signal,
  });
}

export function createSpotReview(spotId: number, req: SpotReviewCreateRequest): Promise<{ reviewId: number }> {
  return apiFetch<{ reviewId: number }>(`/api/spots/${spotId}/reviews`, { method: 'POST', body: req });
}

// 방문일은 바꿀 수 없어서 평점과 내용만 보낸다. 서버는 고친 후기를 목록 항목과 같은 모양으로 돌려준다.
export function updateSpotReview(reviewId: number, req: SpotReviewUpdateRequest): Promise<SpotReview> {
  return apiFetch<SpotReview>(`/api/reviews/${reviewId}`, { method: 'PATCH', body: req });
}

export function deleteSpotReview(reviewId: number): Promise<void> {
  return apiFetch<void>(`/api/reviews/${reviewId}`, { method: 'DELETE' });
}
