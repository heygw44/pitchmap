package com.pitchmap.community.infra;

import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 관리자 검토 목록은 글이나 댓글, 작성자 닉네임, 신고 사유별 집계, 대상마다 최근 신고 5건을 한 화면에 모은다. JPA 엔티티로는 이 조인과 집계,
// 대상별 상위 N건(윈도 함수)을 한 문장에 담기 어렵다. 그래서 이 매퍼가 SQL로 직접 읽고, 결과는 조회 전용 record로만 받는다.
@Mapper
public interface AdminCommunityMapper {

    /**
     * 호출하면 status인 글을 금전 요구·사기 신고가 있는 글부터, 같은 묶음에서는 상태가 바뀐 시각이 이른 순서(같으면 ID 순서)로 offset건
     * 건너뛰고 최대 limit건 읽는다.
     */
    List<AdminCommunityPostRow> selectPosts(
            @Param("status") String status, @Param("offset") long offset, @Param("limit") int limit);

    /** 호출하면 status인 댓글을 {@link #selectPosts}와 같은 순서로 읽는다. */
    List<AdminCommunityCommentRow> selectComments(
            @Param("status") String status, @Param("offset") long offset, @Param("limit") int limit);

    /**
     * 호출하면 targetType(POST 또는 COMMENT)인 대상 targetIds마다 검토 전 신고를 최신순으로 최대 5건씩 읽는다. targetIds가 비어 있으면
     * 호출하지 않는다.
     */
    List<AdminCommunityRecentReportRow> selectRecentReports(
            @Param("targetType") String targetType, @Param("targetIds") Collection<Long> targetIds);
}
