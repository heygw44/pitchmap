package com.pitchmap.publicdata.domain;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공공데이터 적재·동기화 작업을 한 번 실행한 기록.
 *
 * <p>작업은 처리를 마친 위치를 {@code progressCursor}에 남긴다. 그래서 실행이 실패하면 다음 실행이 이 위치 다음부터 이어서 처리한다.
 */
@Entity
@Table(name = "sync_job_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SyncJobRun {

    /** error_message 컬럼 길이와 같다. 이보다 긴 메시지는 잘라서 저장한다. */
    public static final int ERROR_MESSAGE_MAX_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type")
    private SyncJobType jobType;

    @Enumerated(EnumType.STRING)
    private SyncJobStatus status;

    @Column(name = "progress_cursor")
    private String progressCursor;

    @Column(name = "processed_count")
    private int processedCount;

    @Column(name = "skipped_count")
    private int skippedCount;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private SyncJobRun(SyncJobType jobType, String startCursor, Instant now) {
        this.jobType = jobType;
        this.status = SyncJobStatus.RUNNING;
        this.progressCursor = startCursor;
        this.processedCount = 0;
        this.skippedCount = 0;
        this.startedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * RUNNING 상태의 실행 기록을 만든다. 이전 실행이 실패한 곳부터 이어서 처리할 때는 그 실행의 진행 위치를 {@code startCursor}로 넘기고,
     * 처음부터 처리할 때는 null을 넘긴다.
     */
    public static SyncJobRun start(SyncJobType jobType, String startCursor, Instant now) {
        if (jobType == null || now == null) {
            throw new IllegalArgumentException("실행 기록을 만드는 데 필요한 값이 null입니다.");
        }
        return new SyncJobRun(jobType, startCursor, now);
    }

    /**
     * 호출하면 진행 위치를 바꾸고 처리 건수에 {@code processedDelta}를, 건너뛴 건수에 {@code skippedDelta}를 더한다.
     * 수정 시각도 바꾸므로 오래된 실행인지 판단할 때 이 시각을 쓴다.
     */
    public void recordProgress(String cursor, int processedDelta, int skippedDelta, Instant now) {
        requireRunning();
        requireNow(now);
        if (processedDelta < 0) {
            throw new IllegalArgumentException("처리 건수 증가분은 0 이상이어야 합니다.");
        }
        if (skippedDelta < 0) {
            throw new IllegalArgumentException("건너뛴 건수 증가분은 0 이상이어야 합니다.");
        }
        this.progressCursor = cursor;
        this.processedCount += processedDelta;
        this.skippedCount += skippedDelta;
        this.updatedAt = now;
    }

    public void complete(Instant now) {
        requireRunning();
        requireNow(now);
        this.status = SyncJobStatus.COMPLETED;
        this.finishedAt = now;
        this.updatedAt = now;
    }

    /** 호출하면 실행을 FAILED로 끝낸다. 진행 위치는 그대로 두어 다음 실행이 이어서 처리할 수 있게 한다. */
    public void fail(String message, Instant now) {
        requireRunning();
        requireNow(now);
        this.status = SyncJobStatus.FAILED;
        this.errorMessage = truncate(message);
        this.finishedAt = now;
        this.updatedAt = now;
    }

    /**
     * 실행 중인 기록이 {@code staleAfter}보다 오래 갱신되지 않았는지 본다. 작업은 페이지를 끝낼 때마다 기록을 갱신한다.
     * 그래서 이렇게 오래 멈춘 기록은 서버가 작업 도중 꺼져서 남은 것으로 보고, 다음 실행이 실패로 처리한 뒤 이어서 처리한다.
     */
    public boolean isStale(Instant now, Duration staleAfter) {
        return isRunning() && updatedAt.plus(staleAfter).isBefore(now);
    }

    public boolean isRunning() {
        return status == SyncJobStatus.RUNNING;
    }

    public boolean isFailed() {
        return status == SyncJobStatus.FAILED;
    }

    private void requireRunning() {
        if (!isRunning()) {
            throw new IllegalStateException("실행 중인 기록만 바꿀 수 있습니다. status=" + status);
        }
    }

    private static void requireNow(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("시각이 null입니다.");
        }
    }

    // 자를 위치가 서로게이트 쌍의 가운데이면 깨진 글자가 남으므로 한 칸 앞에서 자른다.
    private static String truncate(String message) {
        if (message == null || message.length() <= ERROR_MESSAGE_MAX_LENGTH) {
            return message;
        }
        int end = ERROR_MESSAGE_MAX_LENGTH;
        if (Character.isHighSurrogate(message.charAt(end - 1))) {
            end--;
        }
        return message.substring(0, end);
    }
}
