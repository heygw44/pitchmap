package com.pitchmap.basecamp.domain;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 같은 날 같은 장소에서 1박 이상 함께 야영하는 모임이다. 합류 신청과 멤버를 함께 묶어 상태 전이와 정원 규칙을 지킨다.
 *
 * <p>인원은 상태가 ACTIVE인 멤버 행의 수이고 캠프 리더도 센다. 신청과 멤버의 상태는 이 클래스의 메서드로만 바꿀 수 있다.
 * 모든 메서드는 현재 시각을 호출하는 쪽에서 받는다. 이 클래스는 같은 베이스캠프를 동시에 바꾸는 요청을 직접 막지 않는다.
 * 그 제어는 이 클래스를 불러 쓰는 서비스가 맡는다.
 */
@Entity
@Table(name = "basecamp")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Basecamp {

    public static final int TITLE_MAX_LENGTH = 100;
    public static final int DESCRIPTION_MAX_LENGTH = 2000;
    public static final int CONTACT_INFO_MAX_LENGTH = 255;

    /** 확정하려면 캠프 리더를 포함해 최소 이 인원이 있어야 한다. */
    public static final int MIN_CONFIRM_HEADCOUNT = 2;

    /** 출발 시각 이 기간 전부터 하는 탈퇴를 임박 탈퇴로 본다. */
    public static final Duration EARLY_LEAVE_WINDOW = Duration.ofHours(48);

    /** 완료된 뒤 이 기간이 지나면 연락 수단을 더 보여 주지 않는다. */
    public static final Duration CONTACT_VISIBLE_AFTER_COMPLETION = Duration.ofDays(7);

    /** 출발일 0시를 출발 시각으로 볼 때 쓰는 시간대다. */
    public static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private static final Set<BasecampStatus> RECRUITING_ONLY = Set.of(BasecampStatus.RECRUITING);
    private static final Set<BasecampStatus> BEFORE_CONFIRM = Set.of(BasecampStatus.RECRUITING, BasecampStatus.CLOSED);
    private static final Set<BasecampStatus> NOT_FINISHED =
            Set.of(BasecampStatus.RECRUITING, BasecampStatus.CLOSED, BasecampStatus.CONFIRMED);
    private static final Set<BasecampStatus> CONFIRMED_ONLY = Set.of(BasecampStatus.CONFIRMED);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "leader_id")
    private long leaderId;

    @Column(name = "spot_id")
    private long spotId;

    private String title;

    private String description;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Embedded
    private Capacity capacity;

    @Enumerated(EnumType.STRING)
    private BasecampStatus status;

    @Column(name = "closed_reason")
    @Enumerated(EnumType.STRING)
    private ClosedReason closedReason;

    @Embedded
    private JoinCondition joinCondition;

    @Column(name = "contact_info")
    private String contactInfo;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "cancel_reason")
    @Enumerated(EnumType.STRING)
    private CancelReason cancelReason;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Getter(AccessLevel.NONE)
    @OneToMany(
            mappedBy = "basecamp",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<BasecampMember> members = new ArrayList<>();

    @Getter(AccessLevel.NONE)
    @OneToMany(
            mappedBy = "basecamp",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<BasecampApplication> applications = new ArrayList<>();

    private Basecamp(long leaderId, long spotId, BasecampDetails details, Instant now) {
        this.leaderId = leaderId;
        this.spotId = spotId;
        this.title = details.title();
        this.description = details.description();
        this.startDate = details.startDate();
        this.endDate = details.endDate();
        this.capacity = details.capacity();
        this.joinCondition = details.joinCondition();
        this.status = BasecampStatus.RECRUITING;
        this.createdAt = now;
        this.updatedAt = now;
        this.members.add(BasecampMember.leader(this, leaderId, now));
    }

    /**
     * 호출하면 leaderId인 회원이 spotId인 장소에 여는 모집 중 베이스캠프를 만들고, 캠프 리더를 첫 멤버로 넣는다.
     *
     * <p>값이 null이거나, 제목·설명이 비었거나 너무 길거나, 종료일이 출발일보다 늦지 않으면 {@link IllegalArgumentException}을 던진다.
     * 출발일 범위와 박 수는 {@link BasecampOpenPolicy}로, 모집 중인 베이스캠프 수 제한은 같은 정책 클래스와 저장소의 개수 조회로 호출하는 쪽이 검사한다.
     * 캠프 리더의 자격과 장소의 경고 여부는 다른 모듈의 정보가 필요해서 역시 호출하는 쪽이 검사한다.
     */
    public static Basecamp open(long leaderId, long spotId, BasecampDetails details, Instant now) {
        if (details == null || now == null) {
            throw new IllegalArgumentException("베이스캠프를 만드는 데 필요한 값이 null입니다.");
        }
        requireValidText(details.title(), details.description());
        requireValidSchedule(details.startDate(), details.endDate());
        if (details.capacity() == null || details.joinCondition() == null) {
            throw new IllegalArgumentException("정원과 합류 조건은 null일 수 없습니다.");
        }
        return new Basecamp(leaderId, spotId, details, now);
    }

    /** 현재 인원이다. 캠프 리더를 포함해 ACTIVE인 멤버를 센다. */
    public int headcount() {
        return (int) members.stream().filter(BasecampMember::isActive).count();
    }

    public boolean isFull() {
        return capacity.isFilledBy(headcount());
    }

    public boolean isLeader(long memberId) {
        return leaderId == memberId;
    }

    public List<BasecampMember> getMembers() {
        return Collections.unmodifiableList(members);
    }

    public List<BasecampApplication> getApplications() {
        return Collections.unmodifiableList(applications);
    }

    /**
     * memberId인 회원이 이 베이스캠프와 맺은 관계를 돌려준다. memberId가 null이면 비로그인 요청자라서 NONE이다.
     *
     * <p>ACTIVE 멤버는 캠프 리더이면 LEADER, 아니면 MEMBER다. 멤버가 아니면서 대기 중인 신청이 있으면 APPLICANT이고, 탈퇴·강퇴·거절·취소·만료처럼
     * 지금은 멤버도 대기 신청자도 아닌 회원은 NONE이다.
     */
    public BasecampRelation relationOf(Long memberId) {
        if (memberId == null) {
            return BasecampRelation.NONE;
        }
        Optional<BasecampMember> activeMember = findMember(memberId).filter(BasecampMember::isActive);
        if (activeMember.isPresent()) {
            return activeMember.get().isLeader() ? BasecampRelation.LEADER : BasecampRelation.MEMBER;
        }
        boolean pending = findApplicationOf(memberId)
                .filter(BasecampApplication::isPending)
                .isPresent();
        return pending ? BasecampRelation.APPLICANT : BasecampRelation.NONE;
    }

    /**
     * memberId인 회원에게 연락 수단을 보여 줘도 되면 true다.
     *
     * <p>확정된 동안은 ACTIVE 멤버에게만 보인다. 완료된 뒤에는 완료 시각부터 {@link #CONTACT_VISIBLE_AFTER_COMPLETION}이 지날 때까지 보인다.
     */
    public boolean canViewContact(long memberId, Instant now) {
        if (!isActiveMember(memberId)) {
            return false;
        }
        return switch (status) {
            case CONFIRMED -> true;
            case COMPLETED -> !now.isAfter(completedAt.plus(CONTACT_VISIBLE_AFTER_COMPLETION));
            default -> false;
        };
    }

    /** 호출하면 모집 중인 베이스캠프를 캠프 리더가 직접 마감한다. 직접 마감한 베이스캠프는 빈자리가 생겨도 저절로 다시 열리지 않는다. */
    public void close(Instant now) {
        requireStatus(RECRUITING_ONLY);
        markClosed(ClosedReason.LEADER, now);
    }

    /** 호출하면 마감된 베이스캠프의 모집을 다시 연다. 정원이 가득 차 있으면 {@link BasecampErrorCode#BASECAMP_FULL}이다. */
    public void reopen(Instant now) {
        requireStatus(Set.of(BasecampStatus.CLOSED));
        if (isFull()) {
            throw new BasecampException(BasecampErrorCode.BASECAMP_FULL);
        }
        markRecruiting(now);
    }

    /** 호출하면 베이스캠프를 확정한다. 인원이 {@value #MIN_CONFIRM_HEADCOUNT}명 미만이면 {@link BasecampErrorCode#BASECAMP_NOT_ENOUGH_MEMBERS}이다. */
    public void confirm(Instant now) {
        requireStatus(BEFORE_CONFIRM);
        if (headcount() < MIN_CONFIRM_HEADCOUNT) {
            throw new BasecampException(BasecampErrorCode.BASECAMP_NOT_ENOUGH_MEMBERS);
        }
        markConfirmed(now);
    }

    /** 호출하면 베이스캠프를 취소한다. 확정된 뒤에도 취소할 수 있다. 결정되지 않은 신청은 모두 만료된다. */
    public void cancel(CancelReason reason, Instant now) {
        if (reason == null) {
            throw new IllegalArgumentException("취소 사유가 null입니다.");
        }
        requireStatus(NOT_FINISHED);
        markCanceled(reason, now);
    }

    /** 출발 전날 0시에 자동으로 부른다. 인원이 충분하면 확정하고, 부족하면 인원 부족으로 취소한다. */
    public void processDayBeforeDeparture(Instant now) {
        requireStatus(BEFORE_CONFIRM);
        if (headcount() < MIN_CONFIRM_HEADCOUNT) {
            markCanceled(CancelReason.NOT_ENOUGH_MEMBERS, now);
            return;
        }
        markConfirmed(now);
    }

    /** 호출하면 확정된 베이스캠프를 완료로 바꾼다. 연락 수단은 이 시각부터 정해진 기간 동안만 보인다. */
    public void complete(Instant now) {
        requireStatus(CONFIRMED_ONLY);
        this.status = BasecampStatus.COMPLETED;
        this.completedAt = now;
        expirePendingApplications(now);
        touch(now);
    }

    /**
     * 호출하면 applicantId인 회원의 합류 신청을 만들어 돌려준다.
     *
     * <p>이미 대기 중이거나 멤버이면 {@link BasecampErrorCode#BASECAMP_ALREADY_APPLIED}이고, 거절·탈퇴·강퇴된 적이 있으면
     * {@link BasecampErrorCode#BASECAMP_REAPPLY_NOT_ALLOWED}이다. 스스로 취소했던 신청은 새 행을 만들지 않고 같은 행을 대기로 되돌린다.
     * 신청 자격, 합류 조건, 날짜 겹침, 대기 신청 수 제한은 다른 모듈의 정보가 필요해서 호출하는 쪽이 검사한다.
     */
    public BasecampApplication apply(long applicantId, String message, Instant now) {
        requireStatus(RECRUITING_ONLY);
        requireNotBlockedByMembership(applicantId);
        Optional<BasecampApplication> existing = findApplicationOf(applicantId);
        if (existing.isPresent()) {
            return resubmit(existing.get(), message, now);
        }
        BasecampApplication application = BasecampApplication.submit(this, applicantId, message, now);
        applications.add(application);
        return application;
    }

    /** 호출하면 applicantId인 신청자가 대기 중인 자기 신청을 취소한다. 모집 중이거나 마감인 때만 할 수 있다. */
    public void cancelApplication(long applicantId, Instant now) {
        requireStatus(BEFORE_CONFIRM);
        BasecampApplication application = findApplicationOf(applicantId).orElseThrow(Basecamp::notFound);
        requirePending(application);
        application.cancel(now);
    }

    /** 호출하면 신청을 승인하고 멤버로 넣는다. 정원이 가득 차 있으면 {@link BasecampErrorCode#BASECAMP_FULL}이고, 승인해서 가득 차면 자동 마감한다. */
    public void approve(long applicationId, Instant now) {
        requireStatus(RECRUITING_ONLY);
        BasecampApplication application = requireApplication(applicationId);
        requirePending(application);
        if (isFull()) {
            throw new BasecampException(BasecampErrorCode.BASECAMP_FULL);
        }
        application.approve(now);
        members.add(BasecampMember.member(this, application.getApplicantId(), now));
        closeIfFull(now);
    }

    /** 호출하면 대기 중인 신청을 거절한다. 거절된 회원은 같은 베이스캠프에 다시 신청할 수 없다. */
    public void reject(long applicationId, Instant now) {
        requireStatus(RECRUITING_ONLY);
        BasecampApplication application = requireApplication(applicationId);
        requirePending(application);
        application.reject(now);
    }

    /**
     * 호출하면 memberId인 멤버가 탈퇴한다. 캠프 리더는 탈퇴할 수 없다.
     *
     * <p>확정된 뒤 출발 시각 {@link #EARLY_LEAVE_WINDOW} 전부터 하는 탈퇴는 임박 탈퇴로 기록한다. 빈자리가 생기면 자동 마감한 베이스캠프는 다시 연다.
     */
    public void leave(long memberId, Instant now) {
        requireStatus(NOT_FINISHED);
        BasecampMember member = requireActiveMember(memberId);
        if (member.isLeader()) {
            throw new BasecampException(BasecampErrorCode.BASECAMP_LEADER_CANNOT_LEAVE);
        }
        member.leave(isEarlyLeave(now), now);
        reopenIfAutoClosed(now);
    }

    /** 호출하면 캠프 리더가 memberId인 멤버를 강퇴한다. 확정 전에만 할 수 있고 사유가 꼭 있어야 한다. 캠프 리더는 강퇴할 수 없다. */
    public void kick(long memberId, KickReason reason, Instant now) {
        if (reason == null) {
            throw new IllegalArgumentException("강퇴 사유가 null입니다.");
        }
        requireStatus(BEFORE_CONFIRM);
        BasecampMember member = requireActiveMember(memberId);
        if (member.isLeader()) {
            throw new BasecampException(BasecampErrorCode.BASECAMP_LEADER_CANNOT_LEAVE);
        }
        member.kick(reason, now);
        reopenIfAutoClosed(now);
    }

    /**
     * 호출하면 제목, 설명, 정원, 합류 조건을 고친다. 출발일과 장소는 바꾸지 않는다.
     *
     * <p>정원을 줄이면 {@link BasecampErrorCode#BASECAMP_CAPACITY_INVALID}이다. 정원을 늘려 자동 마감한 베이스캠프에 빈자리가 생기면 다시 연다.
     */
    public void revise(BasecampRevision revision, Instant now) {
        requireStatus(BEFORE_CONFIRM);
        requireValidText(revision.title(), revision.description());
        if (revision.capacity() == null || revision.joinCondition() == null) {
            throw new IllegalArgumentException("정원과 합류 조건은 null일 수 없습니다.");
        }
        if (revision.capacity().isLessThan(capacity)) {
            throw new BasecampException(BasecampErrorCode.BASECAMP_CAPACITY_INVALID);
        }
        this.title = revision.title();
        this.description = revision.description();
        this.capacity = revision.capacity();
        this.joinCondition = revision.joinCondition();
        touch(now);
        reopenIfAutoClosed(now);
    }

    /** 호출하면 연락 수단을 등록하거나 바꾼다. 완료되거나 취소된 뒤에는 할 수 없다. */
    public void registerContact(String contactInfo, Instant now) {
        if (contactInfo == null || contactInfo.isBlank() || contactInfo.length() > CONTACT_INFO_MAX_LENGTH) {
            throw new IllegalArgumentException("연락 수단은 공백뿐이 아닌 " + CONTACT_INFO_MAX_LENGTH + "자 이하여야 합니다.");
        }
        requireStatus(NOT_FINISHED);
        this.contactInfo = contactInfo;
        touch(now);
    }

    private void markClosed(ClosedReason reason, Instant now) {
        this.status = BasecampStatus.CLOSED;
        this.closedReason = reason;
        touch(now);
    }

    private void markRecruiting(Instant now) {
        this.status = BasecampStatus.RECRUITING;
        this.closedReason = null;
        touch(now);
    }

    private void markConfirmed(Instant now) {
        this.status = BasecampStatus.CONFIRMED;
        this.closedReason = null;
        this.confirmedAt = now;
        expirePendingApplications(now);
        touch(now);
    }

    private void markCanceled(CancelReason reason, Instant now) {
        this.status = BasecampStatus.CANCELED;
        this.closedReason = null;
        this.canceledAt = now;
        this.cancelReason = reason;
        expirePendingApplications(now);
        touch(now);
    }

    private void closeIfFull(Instant now) {
        if (isFull()) {
            markClosed(ClosedReason.AUTO_FULL, now);
            return;
        }
        touch(now);
    }

    // 자동 마감한 경우에만 다시 연다. 캠프 리더가 직접 마감한 베이스캠프는 빈자리가 생겨도 닫아 둔다.
    private void reopenIfAutoClosed(Instant now) {
        boolean autoClosed = status == BasecampStatus.CLOSED && closedReason == ClosedReason.AUTO_FULL;
        if (autoClosed && !isFull()) {
            markRecruiting(now);
            return;
        }
        touch(now);
    }

    private void expirePendingApplications(Instant now) {
        applications.stream().filter(BasecampApplication::isPending).forEach(application -> application.expire(now));
    }

    private BasecampApplication resubmit(BasecampApplication existing, String message, Instant now) {
        switch (existing.getStatus()) {
            case CANCELED -> existing.resubmit(message, now);
            case REJECTED -> throw new BasecampException(BasecampErrorCode.BASECAMP_REAPPLY_NOT_ALLOWED);
            case PENDING, APPROVED -> throw new BasecampException(BasecampErrorCode.BASECAMP_ALREADY_APPLIED);
            case EXPIRED -> throw new BasecampException(BasecampErrorCode.BASECAMP_INVALID_STATE);
        }
        return existing;
    }

    private void requireNotBlockedByMembership(long applicantId) {
        Optional<BasecampMember> member = findMember(applicantId);
        if (member.isEmpty()) {
            return;
        }
        if (member.get().isActive()) {
            throw new BasecampException(BasecampErrorCode.BASECAMP_ALREADY_APPLIED);
        }
        throw new BasecampException(BasecampErrorCode.BASECAMP_REAPPLY_NOT_ALLOWED);
    }

    private boolean isEarlyLeave(Instant now) {
        if (status != BasecampStatus.CONFIRMED) {
            return false;
        }
        Instant departure = startDate.atStartOfDay(KOREA).toInstant();
        return !now.isBefore(departure.minus(EARLY_LEAVE_WINDOW));
    }

    private boolean isActiveMember(long memberId) {
        return findMember(memberId).filter(BasecampMember::isActive).isPresent();
    }

    private Optional<BasecampMember> findMember(long memberId) {
        return members.stream()
                .filter(member -> member.getMemberId() == memberId)
                .findFirst();
    }

    private BasecampMember requireActiveMember(long memberId) {
        return findMember(memberId).filter(BasecampMember::isActive).orElseThrow(Basecamp::notFound);
    }

    private Optional<BasecampApplication> findApplicationOf(long applicantId) {
        return applications.stream()
                .filter(application -> application.isFrom(applicantId))
                .findFirst();
    }

    private BasecampApplication requireApplication(long applicationId) {
        return applications.stream()
                .filter(application -> Objects.equals(application.getId(), applicationId))
                .findFirst()
                .orElseThrow(Basecamp::notFound);
    }

    private static void requirePending(BasecampApplication application) {
        if (!application.isPending()) {
            throw new BasecampException(BasecampErrorCode.BASECAMP_INVALID_STATE);
        }
    }

    private void requireStatus(Set<BasecampStatus> allowed) {
        if (!allowed.contains(status)) {
            throw new BasecampException(BasecampErrorCode.BASECAMP_INVALID_STATE);
        }
    }

    private static BusinessException notFound() {
        return new BusinessException(CommonErrorCode.NOT_FOUND);
    }

    private void touch(Instant now) {
        this.updatedAt = now;
    }

    private static void requireValidText(String title, String description) {
        if (title == null || title.isBlank() || title.length() > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("제목은 공백뿐이 아닌 " + TITLE_MAX_LENGTH + "자 이하여야 합니다.");
        }
        if (description == null || description.isBlank() || description.length() > DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException("설명은 공백뿐이 아닌 " + DESCRIPTION_MAX_LENGTH + "자 이하여야 합니다.");
        }
    }

    private static void requireValidSchedule(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || !endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("종료일은 출발일보다 늦어야 합니다.");
        }
    }
}
