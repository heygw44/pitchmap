import { apiFetch } from './client';
import type { SpotAreaQuery, SpotAreaResponse, SpotDetail } from './types';

// 지도 화면 영역 안의 장소를 받는다. 장소가 너무 많으면 서버가 markers를 비우고 clusters로 묶어서 준다.
export function fetchSpotsInArea(query: SpotAreaQuery, signal?: AbortSignal): Promise<SpotAreaResponse> {
  return apiFetch<SpotAreaResponse>('/api/spots', {
    query: {
      swLat: query.swLat,
      swLng: query.swLng,
      neLat: query.neLat,
      neLng: query.neLng,
      zoom: query.zoom,
      types: query.types,
      hasWater: query.hasWater,
      hasToilet: query.hasToilet,
      excludeWarning: query.excludeWarning,
    },
    signal,
  });
}

export function fetchSpotDetail(spotId: number, signal?: AbortSignal): Promise<SpotDetail> {
  return apiFetch<SpotDetail>(`/api/spots/${spotId}`, { signal });
}
