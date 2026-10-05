package com.pitchmap.publicdata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.testsupport.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SyncJobRunTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    private static final Duration STALE_AFTER = Duration.ofMinutes(30);

    @Test
    @DisplayName("[F-06] 실행 기록은 넘겨받은 시작 위치를 가진 RUNNING 상태로 만들어진다")
    void startCreatesRunningRunWithStartCursor() {
        // when
        SyncJobRun run = SyncJobRun.start(SyncJobType.GOCAMPING, "1", NOW);

        // then
        assertThat(run.getStatus()).isEqualTo(SyncJobStatus.RUNNING);
        assertThat(run.getJobType()).isEqualTo(SyncJobType.GOCAMPING);
        assertThat(run.getProgressCursor()).isEqualTo("1");
        assertThat(run.getProcessedCount()).isZero();
        assertThat(run.getSkippedCount()).isZero();
        assertThat(run.getStartedAt()).isEqualTo(NOW);
        assertThat(run.getUpdatedAt()).isEqualTo(NOW);
        assertThat(run.getFinishedAt()).isNull();
    }

    @Test
    @DisplayName("[F-06] 진행을 기록하면 위치를 바꾸고 처리 건수와 건너뛴 건수를 더하고 수정 시각을 바꾼다")
    void recordProgressMovesCursorAndAddsCounts() {
        // given
        SyncJobRun run = SyncJobRun.start(SyncJobType.GOCAMPING, null, NOW);

        // when
        run.recordProgress("1", 100, 3, NOW.plusSeconds(10));
        run.recordProgress("2", 15, 0, NOW.plusSeconds(20));

        // then
        assertThat(run.getProgressCursor()).isEqualTo("2");
        assertThat(run.getProcessedCount()).isEqualTo(115);
        assertThat(run.getSkippedCount()).isEqualTo(3);
        assertThat(run.getUpdatedAt()).isEqualTo(NOW.plusSeconds(20));
    }

    @Test
    @DisplayName("[F-06] 완료하면 COMPLETED가 되고 끝난 시각이 남는다")
    void completeMarksRunCompleted() {
        // given
        SyncJobRun run = SyncJobRun.start(SyncJobType.GOCAMPING, null, NOW);

        // when
        run.complete(NOW.plusSeconds(60));

        // then
        assertThat(run.getStatus()).isEqualTo(SyncJobStatus.COMPLETED);
        assertThat(run.getFinishedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    @DisplayName("[F-06] 실패하면 FAILED가 되고 진행 위치와 오류 메시지가 남는다")
    void failKeepsCursorAndStoresMessage() {
        // given
        SyncJobRun run = SyncJobRun.start(SyncJobType.GOCAMPING, null, NOW);
        run.recordProgress("3", 300, 0, NOW.plusSeconds(10));

        // when
        run.fail("GoCampingApiException: 서버 오류", NOW.plusSeconds(20));

        // then
        assertThat(run.getStatus()).isEqualTo(SyncJobStatus.FAILED);
        assertThat(run.getProgressCursor()).isEqualTo("3");
        assertThat(run.getErrorMessage()).isEqualTo("GoCampingApiException: 서버 오류");
        assertThat(run.getFinishedAt()).isEqualTo(NOW.plusSeconds(20));
    }

    @Test
    @DisplayName("[F-06] 2000자보다 긴 오류 메시지는 2000자로 잘라 저장한다")
    void failTruncatesLongMessage() {
        // given
        SyncJobRun run = SyncJobRun.start(SyncJobType.GOCAMPING, null, NOW);

        // when
        run.fail("가".repeat(2001), NOW);

        // then
        assertThat(run.getErrorMessage()).hasSize(SyncJobRun.ERROR_MESSAGE_MAX_LENGTH);
    }

    @Test
    @DisplayName("[F-06] 자를 위치가 이모지 가운데이면 이모지를 통째로 빼고 자른다")
    void failDoesNotSplitSurrogatePair() {
        // given
        SyncJobRun run = SyncJobRun.start(SyncJobType.GOCAMPING, null, NOW);
        String message = "a".repeat(1999) + "😀" + "b";

        // when
        run.fail(message, NOW);

        // then
        assertThat(run.getErrorMessage()).isEqualTo("a".repeat(1999));
    }

    @Test
    @DisplayName("[F-06] 실행 중인 기록이 기준 시간보다 오래 갱신되지 않았을 때만 오래된 기록으로 본다")
    void isStaleOnlyWhenRunningRunWasNotUpdatedLongerThanThreshold() {
        // given
        SyncJobRun run = SyncJobRun.start(SyncJobType.GOCAMPING, null, NOW);

        // when, then
        assertThat(run.isStale(NOW.plus(STALE_AFTER), STALE_AFTER)).isFalse();
        assertThat(run.isStale(NOW.plus(STALE_AFTER).plusNanos(1000), STALE_AFTER))
                .isTrue();
    }

    @Test
    @DisplayName("[F-06] 끝난 기록은 오래돼도 오래된 실행 기록으로 보지 않는다")
    void finishedRunIsNeverStale() {
        // given
        SyncJobRun run = SyncJobRun.start(SyncJobType.GOCAMPING, null, NOW);
        run.complete(NOW);

        // when, then
        assertThat(run.isStale(NOW.plus(Duration.ofDays(1)), STALE_AFTER)).isFalse();
    }

    @Test
    @DisplayName("[F-06] 끝난 기록은 진행 기록, 완료, 실패로 바꿀 수 없다")
    void finishedRunRejectsTransitions() {
        // given
        SyncJobRun completed = SyncJobRun.start(SyncJobType.GOCAMPING, null, NOW);
        completed.complete(NOW);
        SyncJobRun failed = SyncJobRun.start(SyncJobType.GOCAMPING, null, NOW);
        failed.fail("오류", NOW);

        // when, then
        assertThatThrownBy(() -> completed.recordProgress("1", 1, 0, NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> completed.complete(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> completed.fail("오류", NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> failed.complete(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> failed.fail("오류", NOW)).isInstanceOf(IllegalStateException.class);
        assertThat(failed.getStatus()).isEqualTo(SyncJobStatus.FAILED);
    }

    @Test
    @DisplayName("[F-06] 처리 건수 증가분이 음수이면 진행 기록을 거부한다")
    void recordProgressRejectsNegativeDelta() {
        // given
        SyncJobRun run = SyncJobRun.start(SyncJobType.GOCAMPING, null, NOW);

        // when, then
        assertThatThrownBy(() -> run.recordProgress("1", -1, 0, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-06] 건너뛴 건수 증가분이 음수이면 진행 기록을 거부하고 기록을 바꾸지 않는다")
    void recordProgressRejectsNegativeSkippedDelta() {
        // given
        SyncJobRun run = SyncJobRun.start(SyncJobType.GOCAMPING, null, NOW);
        run.recordProgress("1", 10, 2, NOW.plusSeconds(10));

        // when, then
        assertThatThrownBy(() -> run.recordProgress("2", 10, -1, NOW.plusSeconds(20)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(run.getProgressCursor()).isEqualTo("1");
        assertThat(run.getProcessedCount()).isEqualTo(10);
        assertThat(run.getSkippedCount()).isEqualTo(2);
    }
}
