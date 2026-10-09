package com.pitchmap.program.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 관리자가 등록하는 공식 행사다. 정원, 참가비, 신청 기간, 결제 기한을 가진다.
 *
 * <p>이 클래스는 값의 범위와 시각 순서(신청 시작 &lt; 신청 마감 &le; 행사 시작 &lt; 행사 종료)를 검사한다.
 * 신청을 몇 건 받았는지는 알지 못하고, 같은 행사를 동시에 바꾸는 요청도 직접 막지 않는다. 그 제어는 호출하는 서비스가 맡는다.
 * 모든 메서드는 현재 시각을 호출하는 쪽에서 받는다.
 */
@Entity
@Table(name = "program")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Program {

    public static final int TITLE_MAX_LENGTH = 100;
    public static final int DESCRIPTION_MAX_LENGTH = 5000;
    public static final int LOCATION_TEXT_MAX_LENGTH = 255;
    public static final int CAPACITY_MIN = 1;
    public static final int CAPACITY_MAX = 1000;
    public static final int FEE_MIN = 0;
    public static final int FEE_MAX = 1_000_000;
    public static final int PAYMENT_DEADLINE_MINUTES_MIN = 1;
    public static final int PAYMENT_DEADLINE_MINUTES_MAX = 1440;
    public static final int DEFAULT_PAYMENT_DEADLINE_MINUTES = 15;
    public static final int MIN_TRUST_LEVEL_FOR_OVERNIGHT = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    // MySQL TEXT 컬럼이다. 길이는 DESCRIPTION_MAX_LENGTH로 앱이 검사한다.
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String description;

    @Column(name = "spot_id")
    private Long spotId;

    @Column(name = "location_text")
    private String locationText;

    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    private int capacity;

    private int fee;

    @Column(name = "apply_open_at")
    private Instant applyOpenAt;

    @Column(name = "apply_close_at")
    private Instant applyCloseAt;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "payment_deadline_minutes")
    private int paymentDeadlineMinutes;

    private boolean overnight;

    @Enumerated(EnumType.STRING)
    private ProgramStatus status;

    @Column(name = "created_by")
    private long createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private Program(long createdBy, ProgramDetails details, Instant now) {
        this.createdBy = createdBy;
        this.title = details.title();
        this.description = details.description();
        this.spotId = details.spotId();
        this.locationText = details.locationText();
        this.startAt = details.startAt();
        this.endAt = details.endAt();
        this.capacity = details.capacity();
        this.fee = details.fee();
        this.applyOpenAt = details.applyOpenAt();
        this.applyCloseAt = details.applyCloseAt();
        this.paymentDeadlineMinutes = details.paymentDeadlineMinutes();
        this.overnight = details.overnight();
        this.status = ProgramStatus.SCHEDULED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 호출하면 관리자 createdBy가 details대로 등록하는 행사를 만든다.
     *
     * <p>값이 null이거나 범위를 벗어나거나, 시각 순서가 맞지 않거나, 행사 시작이 now 이전이면 {@link IllegalArgumentException}을 던진다.
     * 신청 시작 시각은 now 이후일 필요가 없다. 이미 신청을 받는 중인 행사도 등록할 수 있다.
     * 장소가 지도에 보이는 장소인지는 다른 모듈의 정보가 필요해서 호출하는 쪽이 검사한다.
     */
    public static Program create(long createdBy, ProgramDetails details, Instant now) {
        if (details == null || now == null) {
            throw new IllegalArgumentException("행사를 만드는 데 필요한 값이 null입니다.");
        }
        requireValid(
                details.title(),
                details.description(),
                details.locationText(),
                details.capacity(),
                details.fee(),
                details.paymentDeadlineMinutes(),
                new Schedule(details.applyOpenAt(), details.applyCloseAt(), details.startAt(), details.endAt()));
        if (!details.startAt().isAfter(now)) {
            throw new IllegalArgumentException("행사 시작 시각은 현재보다 늦어야 합니다.");
        }
        return new Program(createdBy, details, now);
    }

    /**
     * 호출하면 revision에서 null이 아닌 필드로 행사를 고치고, 바뀐 내용을 돌려준다. 고친 뒤의 값으로 범위와 시각 순서를 다시 검사한다.
     *
     * <p>취소된 행사이면 PROGRAM_INVALID_STATE를 던진다. 신청 시작 시각이 now 이전인데 정원을 줄이면 PROGRAM_CAPACITY_DECREASE를 던진다.
     * 신청자가 이미 들어왔을 수 있어서 줄일 수 없고, 늘리는 것은 허용한다. 이 비교는 수정 전의 신청 시작 시각으로 한다.
     * 값이 범위를 벗어나거나 시각 순서가 맞지 않으면 {@link IllegalArgumentException}을 던진다. 이 경우 행사는 바뀌지 않는다.
     */
    public ProgramChange revise(ProgramRevision revision, Instant now) {
        if (revision == null || now == null) {
            throw new IllegalArgumentException("행사를 고치는 데 필요한 값이 null입니다.");
        }
        if (status == ProgramStatus.CANCELED) {
            throw new ProgramException(ProgramErrorCode.PROGRAM_INVALID_STATE);
        }
        int newCapacity = revision.capacity() == null ? capacity : revision.capacity();
        if (!now.isBefore(applyOpenAt) && newCapacity < capacity) {
            throw new ProgramException(ProgramErrorCode.PROGRAM_CAPACITY_DECREASE);
        }
        Program merged = merge(revision);
        requireValid(
                merged.title,
                merged.description,
                merged.locationText,
                merged.capacity,
                merged.fee,
                merged.paymentDeadlineMinutes,
                new Schedule(merged.applyOpenAt, merged.applyCloseAt, merged.startAt, merged.endAt));
        List<String> changedFields = changedFields(merged);
        int capacityBefore = capacity;
        apply(merged, now);
        return new ProgramChange(changedFields, capacityBefore, capacity);
    }

    /**
     * 호출하면 행사를 CANCELED로 바꾼다. 이미 취소된 행사이면 PROGRAM_INVALID_STATE를 던진다.
     * 이 행사의 신청과 결제를 함께 취소하는 일은 호출하는 서비스가 맡는다.
     */
    public void cancel(Instant now) {
        if (status == ProgramStatus.CANCELED) {
            throw new ProgramException(ProgramErrorCode.PROGRAM_INVALID_STATE);
        }
        this.status = ProgramStatus.CANCELED;
        this.updatedAt = now;
    }

    /** 호출하면 now 시점의 공개 진행 단계를 돌려준다. */
    public ProgramPhase phaseAt(Instant now) {
        return ProgramPhase.of(status, applyOpenAt, applyCloseAt, now);
    }

    /**
     * 호출하면 now에 신청을 받는 행사인지 검사한다. 취소된 행사이면 PROGRAM_INVALID_STATE, 신청 시작 전이거나 마감 뒤이면
     * PROGRAM_NOT_IN_APPLY_PERIOD를 던진다.
     */
    public void requireApplicableAt(Instant now) {
        switch (phaseAt(now)) {
            case CANCELED -> throw new ProgramException(ProgramErrorCode.PROGRAM_INVALID_STATE);
            case UPCOMING, CLOSED -> throw new ProgramException(ProgramErrorCode.PROGRAM_NOT_IN_APPLY_PERIOD);
            case OPEN -> {}
        }
    }

    /** 호출하면 신뢰 단계가 trustLevel인 회원이 이 행사에 신청할 자격이 있는지 돌려준다. 숙박 행사만 단계 1 이상을 요구한다. */
    public boolean isTrustLevelSufficient(int trustLevel) {
        return !overnight || trustLevel >= MIN_TRUST_LEVEL_FOR_OVERNIGHT;
    }

    /** 호출하면 now에 신청한 회원의 결제 기한을 돌려준다. */
    public Instant paymentDueAt(Instant now) {
        return now.plus(Duration.ofMinutes(paymentDeadlineMinutes));
    }

    // 고친 뒤의 값을 영속 상태가 아닌 임시 객체에 모아서, 검사에 실패하면 이 행사를 건드리지 않게 한다.
    private Program merge(ProgramRevision revision) {
        Program merged = new Program();
        merged.title = revision.title() == null ? title : revision.title();
        merged.description = revision.description() == null ? description : revision.description();
        merged.spotId = revision.spotId().present() ? revision.spotId().value() : spotId;
        merged.locationText = revision.locationText() == null ? locationText : revision.locationText();
        merged.startAt = revision.startAt() == null ? startAt : revision.startAt();
        merged.endAt = revision.endAt() == null ? endAt : revision.endAt();
        merged.capacity = revision.capacity() == null ? capacity : revision.capacity();
        merged.fee = revision.fee() == null ? fee : revision.fee();
        merged.applyOpenAt = revision.applyOpenAt() == null ? applyOpenAt : revision.applyOpenAt();
        merged.applyCloseAt = revision.applyCloseAt() == null ? applyCloseAt : revision.applyCloseAt();
        merged.paymentDeadlineMinutes =
                revision.paymentDeadlineMinutes() == null ? paymentDeadlineMinutes : revision.paymentDeadlineMinutes();
        merged.overnight = revision.overnight() == null ? overnight : revision.overnight();
        return merged;
    }

    private List<String> changedFields(Program merged) {
        List<String> names = new ArrayList<>();
        addIfChanged(names, "title", title, merged.title);
        addIfChanged(names, "description", description, merged.description);
        addIfChanged(names, "spotId", spotId, merged.spotId);
        addIfChanged(names, "locationText", locationText, merged.locationText);
        addIfChanged(names, "startAt", startAt, merged.startAt);
        addIfChanged(names, "endAt", endAt, merged.endAt);
        addIfChanged(names, "capacity", capacity, merged.capacity);
        addIfChanged(names, "fee", fee, merged.fee);
        addIfChanged(names, "applyOpenAt", applyOpenAt, merged.applyOpenAt);
        addIfChanged(names, "applyCloseAt", applyCloseAt, merged.applyCloseAt);
        addIfChanged(names, "paymentDeadlineMinutes", paymentDeadlineMinutes, merged.paymentDeadlineMinutes);
        addIfChanged(names, "overnight", overnight, merged.overnight);
        return List.copyOf(names);
    }

    private static void addIfChanged(List<String> names, String name, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            names.add(name);
        }
    }

    private void apply(Program merged, Instant now) {
        this.title = merged.title;
        this.description = merged.description;
        this.spotId = merged.spotId;
        this.locationText = merged.locationText;
        this.startAt = merged.startAt;
        this.endAt = merged.endAt;
        this.capacity = merged.capacity;
        this.fee = merged.fee;
        this.applyOpenAt = merged.applyOpenAt;
        this.applyCloseAt = merged.applyCloseAt;
        this.paymentDeadlineMinutes = merged.paymentDeadlineMinutes;
        this.overnight = merged.overnight;
        this.updatedAt = now;
    }

    private record Schedule(Instant applyOpenAt, Instant applyCloseAt, Instant startAt, Instant endAt) {}

    private static void requireValid(
            String title,
            String description,
            String locationText,
            int capacity,
            int fee,
            int paymentDeadlineMinutes,
            Schedule schedule) {
        requireText(title, TITLE_MAX_LENGTH, "제목");
        requireText(description, DESCRIPTION_MAX_LENGTH, "설명");
        requireText(locationText, LOCATION_TEXT_MAX_LENGTH, "장소 설명");
        requireRange(capacity, CAPACITY_MIN, CAPACITY_MAX, "정원");
        requireRange(fee, FEE_MIN, FEE_MAX, "참가비");
        requireRange(paymentDeadlineMinutes, PAYMENT_DEADLINE_MINUTES_MIN, PAYMENT_DEADLINE_MINUTES_MAX, "결제 기한(분)");
        requireSchedule(schedule);
    }

    private static void requireText(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "은(는) 비워 둘 수 없습니다.");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(label + "은(는) " + maxLength + "자 이하여야 합니다.");
        }
    }

    private static void requireRange(int value, int min, int max, String label) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(label + "은(는) " + min + "~" + max + " 범위여야 합니다.");
        }
    }

    private static void requireSchedule(Schedule schedule) {
        if (schedule.applyOpenAt() == null
                || schedule.applyCloseAt() == null
                || schedule.startAt() == null
                || schedule.endAt() == null) {
            throw new IllegalArgumentException("신청 시작·마감 시각과 행사 시작·종료 시각은 null일 수 없습니다.");
        }
        if (!schedule.applyOpenAt().isBefore(schedule.applyCloseAt())) {
            throw new IllegalArgumentException("신청 마감 시각은 신청 시작 시각보다 늦어야 합니다.");
        }
        if (schedule.applyCloseAt().isAfter(schedule.startAt())) {
            throw new IllegalArgumentException("신청 마감 시각은 행사 시작 시각보다 늦을 수 없습니다.");
        }
        if (!schedule.startAt().isBefore(schedule.endAt())) {
            throw new IllegalArgumentException("행사 종료 시각은 행사 시작 시각보다 늦어야 합니다.");
        }
    }
}
