package com.pitchmap.community.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.community.domain.CommunityPostStatus;
import com.pitchmap.community.domain.CommunityReportTargetType;
import com.pitchmap.community.infra.AdminCommunityCommentRow;
import com.pitchmap.community.infra.AdminCommunityMapper;
import com.pitchmap.community.infra.AdminCommunityPostRow;
import com.pitchmap.community.infra.AdminCommunityRecentReportRow;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자가 검토 대기 또는 숨긴 글과 댓글의 목록을 읽는다. 신고 내용이 들어 있으므로 호출하는 쪽이 관리자 권한을 확인해야 한다. */
@Service
@RequiredArgsConstructor
public class AdminCommunityQueryService {

    private final AdminCommunityMapper adminCommunityMapper;

    /**
     * 호출하면 status인 글을 금전 요구·사기 신고가 있는 글부터, 같은 묶음에서는 상태가 바뀐 시각이 이른 순서로 한 페이지 돌려준다. status가
     * null이면 검토 대기(PENDING_REVIEW)를 읽는다. 검토 대기와 숨김(HIDDEN) 말고 다른 값이면 INVALID_INPUT으로 거부한다. 각 항목에는 검토 전
     * 신고의 수, 사유별 수, 최근 5건을 담는다.
     */
    @Transactional(readOnly = true)
    public AdminCommunityPage<AdminCommunityPostSummary> listPosts(String status, int page, int size) {
        CommunityPostStatus postStatus = parseStatus(status);
        List<AdminCommunityPostRow> rows =
                adminCommunityMapper.selectPosts(postStatus.name(), (long) page * size, size + 1);
        boolean hasNext = rows.size() > size;
        List<AdminCommunityPostRow> pageRows = rows.stream().limit(size).toList();
        Map<Long, List<AdminCommunityRecentReport>> recent = readRecentReports(
                CommunityReportTargetType.POST,
                pageRows.stream().map(AdminCommunityPostRow::postId).toList());
        List<AdminCommunityPostSummary> content = pageRows.stream()
                .map(row -> new AdminCommunityPostSummary(
                        row.postId(),
                        row.title(),
                        row.content(),
                        new AdminCommunityAuthor(row.authorId(), row.authorNickname()),
                        row.status(),
                        row.reportCount(),
                        new AdminCommunityReasonCounts(
                                row.spamCount(),
                                row.abuseCount(),
                                row.illegalCampingCount(),
                                row.privacyCount(),
                                row.moneyScamCount(),
                                row.otherCount()),
                        recent.getOrDefault(row.postId(), List.of()),
                        row.statusChangedAt()))
                .toList();
        return new AdminCommunityPage<>(content, page, size, hasNext);
    }

    /** 호출하면 status인 댓글을 {@link #listPosts}와 같은 순서와 규칙으로 한 페이지 돌려준다. */
    @Transactional(readOnly = true)
    public AdminCommunityPage<AdminCommunityCommentSummary> listComments(String status, int page, int size) {
        CommunityPostStatus commentStatus = parseStatus(status);
        List<AdminCommunityCommentRow> rows =
                adminCommunityMapper.selectComments(commentStatus.name(), (long) page * size, size + 1);
        boolean hasNext = rows.size() > size;
        List<AdminCommunityCommentRow> pageRows = rows.stream().limit(size).toList();
        Map<Long, List<AdminCommunityRecentReport>> recent = readRecentReports(
                CommunityReportTargetType.COMMENT,
                pageRows.stream().map(AdminCommunityCommentRow::commentId).toList());
        List<AdminCommunityCommentSummary> content = pageRows.stream()
                .map(row -> new AdminCommunityCommentSummary(
                        row.commentId(),
                        row.postId(),
                        row.content(),
                        new AdminCommunityAuthor(row.authorId(), row.authorNickname()),
                        row.status(),
                        row.reportCount(),
                        new AdminCommunityReasonCounts(
                                row.spamCount(),
                                row.abuseCount(),
                                row.illegalCampingCount(),
                                row.privacyCount(),
                                row.moneyScamCount(),
                                row.otherCount()),
                        recent.getOrDefault(row.commentId(), List.of()),
                        row.statusChangedAt()))
                .toList();
        return new AdminCommunityPage<>(content, page, size, hasNext);
    }

    private Map<Long, List<AdminCommunityRecentReport>> readRecentReports(
            CommunityReportTargetType targetType, List<Long> targetIds) {
        if (targetIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return adminCommunityMapper.selectRecentReports(targetType.name(), targetIds).stream()
                .collect(Collectors.groupingBy(
                        AdminCommunityRecentReportRow::targetId,
                        Collectors.mapping(
                                row -> new AdminCommunityRecentReport(row.reason(), row.content(), row.createdAt()),
                                Collectors.toList())));
    }

    // 글과 댓글의 상태 값이 같으므로 글의 상태 열거형으로 검증한다.
    private static CommunityPostStatus parseStatus(String status) {
        if (status == null) {
            return CommunityPostStatus.PENDING_REVIEW;
        }
        for (CommunityPostStatus candidate : List.of(CommunityPostStatus.PENDING_REVIEW, CommunityPostStatus.HIDDEN)) {
            if (candidate.name().equals(status)) {
                return candidate;
            }
        }
        throw new BusinessException(CommonErrorCode.INVALID_INPUT, "status는 PENDING_REVIEW 또는 HIDDEN이어야 합니다.");
    }
}
