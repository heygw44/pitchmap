package com.pitchmap.community.application;

import java.util.List;

/**
 * 댓글 목록의 한 페이지. 전체 개수는 세지 않고, 다음 페이지가 있는지만 {@code hasNext}로 알려 준다.
 *
 * @param content 오래된 댓글부터 정렬한 최상위 댓글. 답글은 각 댓글의 replies에 담긴다.
 * @param page 0부터 센 페이지 번호
 * @param size 요청한 페이지 크기. 답글이 아닌 댓글의 수로 센다.
 */
public record CommunityCommentPage(List<CommunityCommentItem> content, int page, int size, boolean hasNext) {}
