package com.pitchmap.program.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.web.PatchField;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProgramTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    private static final long ADMIN_ID = 1L;

    @Test
    @DisplayName("[F-17] 값이 올바르면 SCHEDULED 상태로 행사를 만든다")
    void createsScheduledProgram() {
        // when
        Program program = Program.create(ADMIN_ID, details(), NOW);

        // then
        assertThat(program.getStatus()).isEqualTo(ProgramStatus.SCHEDULED);
        assertThat(program.getCreatedBy()).isEqualTo(ADMIN_ID);
        assertThat(program.getCapacity()).isEqualTo(20);
        assertThat(program.getPaymentDeadlineMinutes()).isEqualTo(15);
        assertThat(program.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("[F-17] 시각 순서(신청 시작 < 신청 마감 <= 행사 시작 < 행사 종료)가 틀리면 만들 수 없다")
    void rejectsWrongScheduleOrder() {
        Instant start = NOW.plus(Duration.ofDays(14));

        assertThatThrownBy(() -> create(withTimes(NOW.plus(Duration.ofDays(5)), NOW.plus(Duration.ofDays(5)), start)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> create(withTimes(NOW.plus(Duration.ofDays(5)), start.plusSeconds(1), start)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> create(withEnd(start))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-17] 신청 마감 시각이 행사 시작 시각과 같은 것은 허용한다")
    void allowsApplyCloseAtStart() {
        Instant start = NOW.plus(Duration.ofDays(14));

        Program program = create(withTimes(NOW.plus(Duration.ofDays(1)), start, start));

        assertThat(program.getApplyCloseAt()).isEqualTo(program.getStartAt());
    }

    @Test
    @DisplayName("[F-17] 행사 시작이 현재 이전이면 만들 수 없고, 신청 시작이 현재 이전인 것은 허용한다")
    void requiresFutureStartButAllowsPastApplyOpen() {
        assertThatThrownBy(() -> create(withTimes(NOW.minus(Duration.ofDays(3)), NOW.minus(Duration.ofDays(2)), NOW)))
                .isInstanceOf(IllegalArgumentException.class);

        Program program = create(
                withTimes(NOW.minus(Duration.ofDays(1)), NOW.plus(Duration.ofDays(5)), NOW.plus(Duration.ofDays(14))));

        assertThat(program.phaseAt(NOW)).isEqualTo(ProgramPhase.OPEN);
    }

    @Test
    @DisplayName("[F-17] 제목·설명·장소 설명이 비었거나 길고, 정원·참가비·결제 기한이 범위 밖이면 만들 수 없다")
    void rejectsOutOfRangeValues() {
        String longTitle = "가".repeat(Program.TITLE_MAX_LENGTH + 1);
        String longDescription = "가".repeat(Program.DESCRIPTION_MAX_LENGTH + 1);
        String longLocation = "가".repeat(Program.LOCATION_TEXT_MAX_LENGTH + 1);

        assertThatThrownBy(() -> create(detail("제목", "설명", " ", 20, 0, 15)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> create(detail(longTitle, "설명", "장소", 20, 0, 15)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> create(detail("제목", longDescription, "장소", 20, 0, 15)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> create(detail("제목", "설명", longLocation, 20, 0, 15)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> create(detail("제목", "설명", "장소", 0, 0, 15)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> create(detail("제목", "설명", "장소", Program.CAPACITY_MAX + 1, 0, 15)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> create(detail("제목", "설명", "장소", 20, -1, 15)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> create(detail("제목", "설명", "장소", 20, Program.FEE_MAX + 1, 15)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> create(detail("제목", "설명", "장소", 20, 0, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> create(detail("제목", "설명", "장소", 20, 0, Program.PAYMENT_DEADLINE_MINUTES_MAX + 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-17] 범위의 경계값(정원 1·1000, 참가비 0·1,000,000, 결제 기한 1·1440)은 허용한다")
    void allowsBoundaryValues() {
        assertThat(create(detail("제목", "설명", "장소", 1, 0, 1)).getCapacity()).isEqualTo(1);
        assertThat(create(detail("제목", "설명", "장소", 1000, 1_000_000, 1440)).getFee())
                .isEqualTo(1_000_000);
    }

    @Test
    @DisplayName("[F-17] 신청 시작 전에는 정원을 줄일 수 있다")
    void allowsCapacityDecreaseBeforeApplyOpen() {
        Program program = create(details());

        ProgramChange change = program.revise(revision().capacity(10).build(), NOW);

        assertThat(program.getCapacity()).isEqualTo(10);
        assertThat(change.changedFields()).containsExactly("capacity");
        assertThat(change.capacityBefore()).isEqualTo(20);
        assertThat(change.capacityAfter()).isEqualTo(10);
        assertThat(change.isCapacityChanged()).isTrue();
    }

    @Test
    @DisplayName("[F-17][PG-01] 신청 시작 시각이 지난 뒤에는 정원을 줄일 수 없고 PROGRAM_CAPACITY_DECREASE로 거부한다")
    void rejectsCapacityDecreaseAfterApplyOpen() {
        Program program = create(details());
        Instant afterOpen = program.getApplyOpenAt();

        assertThatThrownBy(() -> program.revise(revision().capacity(19).build(), afterOpen))
                .isInstanceOfSatisfying(
                        ProgramException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ProgramErrorCode.PROGRAM_CAPACITY_DECREASE));
        assertThat(program.getCapacity()).isEqualTo(20);
    }

    @Test
    @DisplayName("[F-17] 신청 시작 시각이 지난 뒤에도 정원을 늘리거나 그대로 두는 것은 허용한다")
    void allowsCapacityIncreaseAfterApplyOpen() {
        Program program = create(details());
        Instant afterOpen = program.getApplyOpenAt().plusSeconds(1);

        ProgramChange increase = program.revise(revision().capacity(30).build(), afterOpen);
        ProgramChange same = program.revise(revision().capacity(30).fee(5000).build(), afterOpen);

        assertThat(program.getCapacity()).isEqualTo(30);
        assertThat(increase.capacityAfter()).isEqualTo(30);
        assertThat(same.isCapacityChanged()).isFalse();
        assertThat(same.changedFields()).containsExactly("fee");
    }

    @Test
    @DisplayName("[F-17] 신청 시작 시각을 늦춰도 정원 축소 기준은 수정 전의 신청 시작 시각이다")
    void usesApplyOpenAtBeforeRevision() {
        Program program = create(details());
        Instant afterOpen = program.getApplyOpenAt().plusSeconds(1);
        ProgramRevision postponeAndShrink = revision()
                .applyOpenAt(afterOpen.plus(Duration.ofDays(1)))
                .capacity(10)
                .build();

        assertThatThrownBy(() -> program.revise(postponeAndShrink, afterOpen))
                .isInstanceOfSatisfying(
                        ProgramException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ProgramErrorCode.PROGRAM_CAPACITY_DECREASE));
    }

    @Test
    @DisplayName("[F-17] 수정은 보내지 않은 필드를 그대로 두고, 바뀐 필드 이름만 알려 준다")
    void reviseKeepsUntouchedFields() {
        Program program = create(details());

        ProgramChange change = program.revise(revision().title("새 제목").fee(0).build(), NOW);

        assertThat(program.getTitle()).isEqualTo("새 제목");
        assertThat(program.getFee()).isZero();
        assertThat(program.getDescription()).isEqualTo("설명");
        assertThat(program.getUpdatedAt()).isEqualTo(NOW);
        assertThat(change.changedFields()).containsExactly("title", "fee");
    }

    @Test
    @DisplayName("[F-17] 장소를 null로 보내면 연결을 끊고, 보내지 않으면 그대로 둔다")
    void reviseHandlesSpotPatchField() {
        Program program = create(detailWithSpot(7L));

        program.revise(revision().title("바뀐 제목").build(), NOW);
        assertThat(program.getSpotId()).isEqualTo(7L);

        ProgramChange change =
                program.revise(revision().spotId(PatchField.of(null)).build(), NOW);

        assertThat(program.getSpotId()).isNull();
        assertThat(change.changedFields()).containsExactly("spotId");
    }

    @Test
    @DisplayName("[F-17] 고친 뒤의 값으로 시각 순서를 다시 검사하고, 틀리면 행사를 바꾸지 않는다")
    void reviseRevalidatesMergedValues() {
        Program program = create(details());
        Instant originalClose = program.getApplyCloseAt();

        assertThatThrownBy(() -> program.revise(
                        revision()
                                .applyCloseAt(program.getStartAt().plusSeconds(1))
                                .build(),
                        NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> program.revise(
                        revision().endAt(program.getStartAt().minusSeconds(1)).build(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        program.revise(revision().capacity(0).title("바뀌면 안 됨").build(), NOW))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(program.getApplyCloseAt()).isEqualTo(originalClose);
        assertThat(program.getTitle()).isEqualTo("제목");
    }

    @Test
    @DisplayName("[F-17] 취소된 행사는 수정할 수 없고 PROGRAM_INVALID_STATE로 거부한다")
    void rejectsReviseOfCanceledProgram() {
        Program program = create(details());
        program.cancel(NOW);

        assertThatThrownBy(() -> program.revise(revision().title("새 제목").build(), NOW))
                .isInstanceOfSatisfying(
                        ProgramException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ProgramErrorCode.PROGRAM_INVALID_STATE));
    }

    @Test
    @DisplayName("[F-17] 취소하면 CANCELED가 되고, 이미 취소된 행사를 다시 취소하면 PROGRAM_INVALID_STATE로 거부한다")
    void cancelsOnlyOnce() {
        Program program = create(details());

        program.cancel(NOW);

        assertThat(program.getStatus()).isEqualTo(ProgramStatus.CANCELED);
        assertThatThrownBy(() -> program.cancel(NOW))
                .isInstanceOfSatisfying(
                        ProgramException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ProgramErrorCode.PROGRAM_INVALID_STATE));
    }

    @Test
    @DisplayName("[F-17] 진행 단계는 신청 시작 시각 이상부터 OPEN, 마감 시각 이상부터 CLOSED이고 취소하면 CANCELED이다")
    void phaseChangesAtBoundaries() {
        Program program = create(details());
        Instant open = program.getApplyOpenAt();
        Instant close = program.getApplyCloseAt();

        assertThat(program.phaseAt(open.minusNanos(1000))).isEqualTo(ProgramPhase.UPCOMING);
        assertThat(program.phaseAt(open)).isEqualTo(ProgramPhase.OPEN);
        assertThat(program.phaseAt(close.minusNanos(1000))).isEqualTo(ProgramPhase.OPEN);
        assertThat(program.phaseAt(close)).isEqualTo(ProgramPhase.CLOSED);

        program.cancel(NOW);

        assertThat(program.phaseAt(open)).isEqualTo(ProgramPhase.CANCELED);
    }

    private static Program create(ProgramDetails details) {
        return Program.create(ADMIN_ID, details, NOW);
    }

    private static ProgramDetails details() {
        return detail("제목", "설명", "장소", 20, 30000, 15);
    }

    private static ProgramDetails detailWithSpot(Long spotId) {
        return new ProgramDetails(
                "제목",
                "설명",
                spotId,
                "장소",
                NOW.plus(Duration.ofDays(14)),
                NOW.plus(Duration.ofDays(15)),
                20,
                30000,
                NOW.plus(Duration.ofDays(1)),
                NOW.plus(Duration.ofDays(10)),
                15,
                true);
    }

    private static ProgramDetails detail(
            String title, String description, String location, int capacity, int fee, int deadlineMinutes) {
        return new ProgramDetails(
                title,
                description,
                null,
                location,
                NOW.plus(Duration.ofDays(14)),
                NOW.plus(Duration.ofDays(15)),
                capacity,
                fee,
                NOW.plus(Duration.ofDays(1)),
                NOW.plus(Duration.ofDays(10)),
                deadlineMinutes,
                true);
    }

    private static ProgramDetails withTimes(Instant applyOpenAt, Instant applyCloseAt, Instant startAt) {
        return new ProgramDetails(
                "제목",
                "설명",
                null,
                "장소",
                startAt,
                startAt.plus(Duration.ofDays(1)),
                20,
                30000,
                applyOpenAt,
                applyCloseAt,
                15,
                true);
    }

    private static ProgramDetails withEnd(Instant endAt) {
        return new ProgramDetails(
                "제목",
                "설명",
                null,
                "장소",
                endAt,
                endAt,
                20,
                30000,
                NOW.plus(Duration.ofDays(1)),
                NOW.plus(Duration.ofDays(10)),
                15,
                true);
    }

    private static RevisionBuilder revision() {
        return new RevisionBuilder();
    }

    private static final class RevisionBuilder {
        private String title;
        private PatchField<Long> spotId = PatchField.absent();
        private Instant applyOpenAt;
        private Instant applyCloseAt;
        private Instant endAt;
        private Integer capacity;
        private Integer fee;

        RevisionBuilder title(String title) {
            this.title = title;
            return this;
        }

        RevisionBuilder spotId(PatchField<Long> spotId) {
            this.spotId = spotId;
            return this;
        }

        RevisionBuilder applyOpenAt(Instant applyOpenAt) {
            this.applyOpenAt = applyOpenAt;
            return this;
        }

        RevisionBuilder applyCloseAt(Instant applyCloseAt) {
            this.applyCloseAt = applyCloseAt;
            return this;
        }

        RevisionBuilder endAt(Instant endAt) {
            this.endAt = endAt;
            return this;
        }

        RevisionBuilder capacity(Integer capacity) {
            this.capacity = capacity;
            return this;
        }

        RevisionBuilder fee(Integer fee) {
            this.fee = fee;
            return this;
        }

        ProgramRevision build() {
            return new ProgramRevision(
                    title, null, spotId, null, null, endAt, capacity, fee, applyOpenAt, applyCloseAt, null, null);
        }
    }
}
