import { ApiError, apiFetch } from './client';
import type {
  CommunityComment,
  CommunityCommentCreateRequest,
  CommunityImageUpload,
  CommunityImageUploadRequest,
  CommunityLikeResponse,
  CommunityPostCreateRequest,
  CommunityPostDetail,
  CommunityPostSummary,
  CommunityPostUpdateRequest,
  CommunityReportRequest,
  Page,
} from './types';

// 서버가 받는 목록 크기는 1~50이다.
export const COMMUNITY_POST_PAGE_SIZE = 20;
export const COMMUNITY_COMMENT_PAGE_SIZE = 20;

export interface CommunityPostQuery {
  spotId?: number;
}

// 최신 글부터 준다.
export function listPosts(
  filter: CommunityPostQuery,
  page: number,
  signal?: AbortSignal,
): Promise<Page<CommunityPostSummary>> {
  return apiFetch<Page<CommunityPostSummary>>('/api/community/posts', {
    query: { spotId: filter.spotId, page, size: COMMUNITY_POST_PAGE_SIZE },
    signal,
  });
}

export function getPost(postId: number, signal?: AbortSignal): Promise<CommunityPostDetail> {
  return apiFetch<CommunityPostDetail>(`/api/community/posts/${postId}`, { signal });
}

export function createPost(req: CommunityPostCreateRequest): Promise<{ postId: number }> {
  return apiFetch<{ postId: number }>('/api/community/posts', { method: 'POST', body: req });
}

export function updatePost(postId: number, req: CommunityPostUpdateRequest): Promise<CommunityPostDetail> {
  return apiFetch<CommunityPostDetail>(`/api/community/posts/${postId}`, { method: 'PATCH', body: req });
}

export function deletePost(postId: number): Promise<void> {
  return apiFetch<void>(`/api/community/posts/${postId}`, { method: 'DELETE' });
}

// 페이지는 답글이 아닌 댓글 단위로 센다. 댓글은 오래된 순이다.
export function listComments(
  postId: number,
  page: number,
  signal?: AbortSignal,
): Promise<Page<CommunityComment>> {
  return apiFetch<Page<CommunityComment>>(`/api/community/posts/${postId}/comments`, {
    query: { page, size: COMMUNITY_COMMENT_PAGE_SIZE },
    signal,
  });
}

export function createComment(postId: number, req: CommunityCommentCreateRequest): Promise<{ commentId: number }> {
  return apiFetch<{ commentId: number }>(`/api/community/posts/${postId}/comments`, { method: 'POST', body: req });
}

export function updateComment(commentId: number, req: { content: string }): Promise<CommunityComment> {
  return apiFetch<CommunityComment>(`/api/community/comments/${commentId}`, { method: 'PATCH', body: req });
}

export function deleteComment(commentId: number): Promise<void> {
  return apiFetch<void>(`/api/community/comments/${commentId}`, { method: 'DELETE' });
}

// 이미 누른 글에 다시 눌러도 서버는 같은 응답을 준다.
export function likePost(postId: number): Promise<CommunityLikeResponse> {
  return apiFetch<CommunityLikeResponse>(`/api/community/posts/${postId}/like`, { method: 'PUT' });
}

export function unlikePost(postId: number): Promise<CommunityLikeResponse> {
  return apiFetch<CommunityLikeResponse>(`/api/community/posts/${postId}/like`, { method: 'DELETE' });
}

export function reportPost(postId: number, req: CommunityReportRequest): Promise<void> {
  return apiFetch<void>(`/api/community/posts/${postId}/reports`, { method: 'POST', body: req });
}

export function reportComment(commentId: number, req: CommunityReportRequest): Promise<void> {
  return apiFetch<void>(`/api/community/comments/${commentId}/reports`, { method: 'POST', body: req });
}

// 파일은 서버를 거치지 않고 저장소에 직접 올린다. 올린 뒤 글 작성·수정 요청의 imageIds에 imageId를 넣는다.
export function requestImageUpload(req: CommunityImageUploadRequest): Promise<CommunityImageUpload> {
  return apiFetch<CommunityImageUpload>('/api/community/images', { method: 'POST', body: req });
}

// 사전 서명 URL로 직접 PUT한다. 우리 서버가 아니라서 apiFetch(JSON 본문, CSRF 헤더, 세션 쿠키)를 쓰지 않는다.
// 서명에 형식이 들어 있으므로 Content-Type은 파일의 형식과 같아야 하고, Content-Length는 브라우저가 붙인다.
export async function putImage(upload: CommunityImageUpload, file: File, signal?: AbortSignal): Promise<void> {
  let response: Response;
  try {
    response = await fetch(upload.uploadUrl, {
      method: 'PUT',
      headers: { 'Content-Type': file.type },
      body: file,
      credentials: 'omit',
      signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    throw new ApiError(0, 'IMAGE_UPLOAD_FAILED', null);
  }
  if (!response.ok) {
    throw new ApiError(response.status, 'IMAGE_UPLOAD_FAILED', null);
  }
}
