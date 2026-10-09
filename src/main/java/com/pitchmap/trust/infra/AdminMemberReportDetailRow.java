package com.pitchmap.trust.infra;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 관리자 신고 상세 한 건. 후기 열(reviewId, reviewComment, reviewRejoinWanted, reviewHidden)은 후기 신고일 때만 값이 있다.
 * handledBy, handledAt, resultNote는 처리 전이면 null이다.
 * 처리가 끝난 지 1년이 지나면 정리 작업이 content와 resultNote, 제재 이력의 reason을 지우므로 이 값들도 null일 수 있다.
 */
public record AdminMemberReportDetailRow(
        long reportId,
        String kind,
        String type,
        boolean urgent,
        String status,
        long reporterId,
        String reporterNickname,
        long targetId,
        String targetNickname,
        long basecampId,
        Instant createdAt,
        String content,
        String resultNote,
        Long handledBy,
        Instant handledAt,
        String basecampTitle,
        String basecampStatus,
        LocalDate basecampStartDate,
        Long reviewId,
        String reviewComment,
        Boolean reviewRejoinWanted,
        Boolean reviewHidden) {}
