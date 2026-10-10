package com.pitchmap.community.application;

import java.util.List;

/**
 * 글 목록의 한 페이지. 화면이 페이지 번호를 그릴 수 있도록 조건에 맞는 전체 글 수와 페이지 수를 함께 담는다.
 *
 * @param content 최신 글부터 정렬한 글 목록. text에는 본문 앞 100자가 담긴다.
 * @param page 0부터 센 페이지 번호
 * @param size 요청한 페이지 크기
 * @param hasNext 이 페이지 뒤에 페이지가 더 있는지
 * @param totalElements 조건에 맞는 전체 글 수
 * @param totalPages 전체 페이지 수. 글이 없으면 0이다.
 */
public record CommunityPostPage(
        List<CommunityPostItem> content, int page, int size, boolean hasNext, long totalElements, int totalPages) {

    /** 호출하면 전체 글 수와 페이지 크기로 페이지 수와 다음 페이지가 있는지를 계산해 페이지를 만든다. */
    public static CommunityPostPage of(List<CommunityPostItem> content, int page, int size, long totalElements) {
        int totalPages = (int) ((totalElements + size - 1) / size);
        return new CommunityPostPage(content, page, size, page + 1 < totalPages, totalElements, totalPages);
    }
}
