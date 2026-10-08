package com.pitchmap.trust.domain;

import com.pitchmap.common.error.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 베이스캠프에서 함께했던 회원이 다른 회원, 또는 자신이 받은 동행 후기를 신고한 기록.
 *
 * <p>신고자와 대상의 자격, 중복 여부는 서비스가 검사하고, 이 엔티티는 신고 종류와 유형이 맞는지 같은 값의 형식만 지킨다.
 * 회원과 베이스캠프는 다른 모듈의 엔티티라서 연관관계로 걸지 않고 ID로만 가리킨다.
 */
@Entity
@Table(name = "member_report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberReport {

    /** 신고 내용의 최대 길이다. DB 컬럼 크기와 같다. */
    public static final int CONTENT_MAX_LENGTH = 1000;

    /** 처리 메모의 최대 길이다. DB 컬럼 크기와 같다. */
    public static final int RESULT_NOTE_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_id")
    private long reporterId;

    @Column(name = "target_member_id")
    private long targetMemberId;

    @Column(name = "basecamp_id")
    private long basecampId;

    @Enumerated(EnumType.STRING)
    private ReportKind kind;

    @Column(name = "companion_review_id")
    private Long companionReviewId;

    @Enumerated(EnumType.STRING)
    private ReportType type;

    private String content;

    private boolean urgent;

    @Enumerated(EnumType.STRING)
    private ReportStatus status;

    @Column(name = "handled_by")
    private Long handledBy;

    @Column(name = "handled_at")
    private Instant handledAt;

    @Column(name = "result_note")
    private String resultNote;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private MemberReport(
            long reporterId,
            long targetMemberId,
            long basecampId,
            Long companionReviewId,
            ReportType type,
            String content,
            Instant now) {
        this.reporterId = reporterId;
        this.targetMemberId = targetMemberId;
        this.basecampId = basecampId;
        this.kind = type.kind();
        this.companionReviewId = companionReviewId;
        this.type = type;
        this.content = content;
        this.urgent = type.isUrgent();
        this.status = ReportStatus.RECEIVED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 호출하면 reporterId인 회원이 basecampId인 베이스캠프에서 targetMemberId인 회원을 신고하는 접수 상태의 신고를 만든다.
     * 신고 종류는 유형에서 정해지고, 긴급 여부도 유형에서 계산한다.
     *
     * <p>후기 신고 유형이면 companionReviewId가 있어야 하고, 회원 신고 유형이면 없어야 한다. 신고자와 대상이 같거나,
     * 내용이 비었거나 {@value #CONTENT_MAX_LENGTH}자를 넘거나, 필수 값이 null이면 {@link IllegalArgumentException}을 던진다.
     */
    public static MemberReport receive(
            long reporterId,
            long targetMemberId,
            long basecampId,
            Long companionReviewId,
            ReportType type,
            String content,
            Instant now) {
        if (type == null || now == null) {
            throw new IllegalArgumentException("신고 유형이나 접수 시각이 null입니다.");
        }
        if (reporterId == targetMemberId) {
            throw new IllegalArgumentException("자기 자신은 신고할 수 없습니다.");
        }
        if ((type.kind() == ReportKind.REVIEW) != (companionReviewId != null)) {
            throw new IllegalArgumentException("후기 신고에만 동행 후기 ID가 필요합니다.");
        }
        if (content == null || content.isBlank() || content.length() > CONTENT_MAX_LENGTH) {
            throw new IllegalArgumentException("신고 내용은 1~" + CONTENT_MAX_LENGTH + "자여야 합니다.");
        }
        return new MemberReport(reporterId, targetMemberId, basecampId, companionReviewId, type, content, now);
    }

    /**
     * 호출하면 접수 상태(RECEIVED)인 신고를 검토 중(IN_REVIEW)으로 바꾸고 검토를 시작한 관리자와 시각을 남긴다.
     * 접수 상태가 아니면 REPORT_INVALID_STATE 예외를 던진다.
     */
    public void startReview(long adminId, Instant now) {
        requireNow(now);
        requireStatus(ReportStatus.RECEIVED);
        this.status = ReportStatus.IN_REVIEW;
        this.handledBy = adminId;
        this.updatedAt = now;
    }

    /**
     * 호출하면 검토 중인 신고를 조치 완료(ACTIONED)로 바꾸고 처리한 관리자, 처리 시각, 메모를 남긴다.
     * 검토 중이 아니면 REPORT_INVALID_STATE 예외를 던진다. 공백뿐인 메모는 없는 것으로 저장한다.
     */
    public void action(long adminId, String note, Instant now) {
        resolve(ReportStatus.ACTIONED, adminId, note, now);
    }

    /**
     * 호출하면 검토 중인 신고를 기각(DISMISSED)으로 바꾸고 처리한 관리자, 처리 시각, 메모를 남긴다.
     * 검토 중이 아니면 REPORT_INVALID_STATE 예외를 던진다. 공백뿐인 메모는 없는 것으로 저장한다.
     */
    public void dismiss(long adminId, String note, Instant now) {
        resolve(ReportStatus.DISMISSED, adminId, note, now);
    }

    /** 호출하면 신고가 검토 중(IN_REVIEW)인지 확인한다. 아니면 REPORT_INVALID_STATE 예외를 던진다. */
    public void requireInReview() {
        requireStatus(ReportStatus.IN_REVIEW);
    }

    private void resolve(ReportStatus result, long adminId, String note, Instant now) {
        requireNow(now);
        if (note != null && note.length() > RESULT_NOTE_MAX_LENGTH) {
            throw new IllegalArgumentException("처리 메모는 " + RESULT_NOTE_MAX_LENGTH + "자 이하여야 합니다.");
        }
        requireStatus(ReportStatus.IN_REVIEW);
        this.status = result;
        this.handledBy = adminId;
        this.handledAt = now;
        this.resultNote = note == null || note.isBlank() ? null : note;
        this.updatedAt = now;
    }

    private void requireStatus(ReportStatus expected) {
        if (status != expected) {
            throw new BusinessException(TrustErrorCode.REPORT_INVALID_STATE);
        }
    }

    private static void requireNow(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("처리 시각이 null입니다.");
        }
    }
}
