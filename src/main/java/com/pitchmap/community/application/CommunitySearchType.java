package com.pitchmap.community.application;

/** 글 검색 대상. 댓글은 검색하지 않는다. */
public enum CommunitySearchType {
    /** 제목이나 본문에 검색어가 들어간 글 */
    TITLE_CONTENT,
    /** 제목에 검색어가 들어간 글 */
    TITLE,
    /** 본문에 검색어가 들어간 글 */
    CONTENT,
    /** 작성자 닉네임에 검색어가 들어간 글. 탈퇴한 작성자는 익명화된 닉네임으로 찾는다. */
    AUTHOR
}
