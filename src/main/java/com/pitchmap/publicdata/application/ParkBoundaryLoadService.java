package com.pitchmap.publicdata.application;

import com.pitchmap.publicdata.application.SyncJobRunService.SyncJobStart;
import com.pitchmap.publicdata.domain.SyncJobType;
import com.pitchmap.publicdata.infra.ParkBoundaryFile;
import com.pitchmap.publicdata.infra.ParkBoundaryFileReader;
import com.pitchmap.publicdata.infra.ParkBoundaryRecord;
import com.pitchmap.spot.application.ProtectedAreaCommand;
import com.pitchmap.spot.application.ProtectedAreaLoadService;
import com.pitchmap.spot.application.ProtectedAreaSource;
import com.pitchmap.spot.application.ProtectedAreaType;
import com.pitchmap.spot.application.ProtectedAreaUpsertResult;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 공원 경계 파일을 읽어 국립·도립·군립공원 경계를 장소 모듈의 공원 경계로 적재한다.
 *
 * <p>원천은 KDPA(한국보호지역 통합DB관리시스템)의 보호지역 SHP다. 개발자가 {@code scripts/publicdata/extract-park-boundaries.py}로 그 SHP에서
 * 자연공원 세 종류만 골라, 공원마다 이름, 구분, 경계 WKT를 한 줄씩 담은 TSV 파일을 만든다. 서버는 이 파일만 읽고 SHP는 읽지 않는다.
 *
 * <p>서비스는 출처, 구분, 이름이 같은 공원을 같은 공원으로 보고 경계와 기준일을 덮어쓴다. 새 파일에 없는 기존 공원은 지우지 않는다. 장소가 공원 경고의
 * 근거로 그 경계를 참조하고 있을 수 있어서다. 경계를 바꾼 뒤 장소의 공원 경고를 다시 판정하는 일은 이 서비스가 하지 않는다. 관리자 실행 요청으로 적재가
 * 성공하면 {@link SyncJobLauncher}가 이어서 박지 재판정을 시작한다.
 *
 * <p>서비스는 파일 전체를 장소 모듈의 트랜잭션 하나로 적재한다. 그래서 적재 도중 실패하면 DB가 그 트랜잭션을 모두 되돌리고, 다음 실행이 이어서 처리할
 * 위치도 없다. 따라서 서비스는 실행 기록에 진행 위치를 쓰지 않고, 직전 실행이 실패했어도 파일을 처음부터 다시 적재한다.
 *
 * <p>이 서비스는 트랜잭션을 열지 않는다. 실행 기록과 적재는 각 서비스의 트랜잭션에서 따로 커밋한다. 그래서 적재가 실패해도 실패한 실행 기록은 남는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParkBoundaryLoadService {

    private final SyncJobRunService syncJobRunService;
    private final ParkBoundaryFileReader parkBoundaryFileReader;
    private final ProtectedAreaLoadService protectedAreaLoadService;
    private final ParkBoundaryLoadProperties properties;

    /**
     * 호출하면 실행 기록을 시작하고, 설정한 경로의 공원 경계 파일을 한 번 적재한 결과를 돌려준다. 다른 적재가 진행 중이라 시작하지 않았으면 빈 값을
     * 돌려준다. 실패하면 실행 기록을 FAILED로 남기고 받은 예외를 그대로 다시 던진다.
     */
    public Optional<ParkBoundaryLoadResult> load() {
        return load(properties.file());
    }

    Optional<ParkBoundaryLoadResult> load(Path file) {
        return syncJobRunService
                .begin(SyncJobType.PARK_BOUNDARY, properties.staleAfter())
                .map(start -> run(start, file));
    }

    /**
     * 호출하면 이미 시작한 실행 기록 start로 설정한 경로의 공원 경계 파일을 적재하고, 성공하면 실행 기록을 COMPLETED로 바꾼다. 실행 기록을 새로 시작하지
     * 않으므로, 호출하는 쪽이 {@link SyncJobRunService#begin}으로 PARK_BOUNDARY 실행을 먼저 시작해 둬야 한다. 실패하면 실행 기록을 FAILED로 남기고
     * 받은 예외를 그대로 다시 던진다.
     */
    public ParkBoundaryLoadResult run(SyncJobStart start) {
        return run(start, properties.file());
    }

    ParkBoundaryLoadResult run(SyncJobStart start, Path file) {
        long runId = start.runId();
        try {
            ParkBoundaryLoadResult result = loadFile(runId, requireConfigured(file));
            syncJobRunService.complete(runId);
            logCompleted(runId, result);
            return result;
        } catch (RuntimeException e) {
            recordFailure(runId, e);
            throw e;
        }
    }

    // 경로가 비었는지는 실행을 시작한 뒤에 확인한다. 그래야 설정을 빠뜨린 실행도 실패 기록으로 남는다.
    private static Path requireConfigured(Path file) {
        if (file == null) {
            throw new IllegalStateException(
                    "공원 경계 파일 경로가 설정되지 않았습니다. " + ParkBoundaryLoadProperties.FILE_PROPERTY + " 값을 확인하세요.");
        }
        return file;
    }

    private ParkBoundaryLoadResult loadFile(long runId, Path file) {
        log.info("park boundary load started runId={} file={}", runId, file.getFileName());
        ParkBoundaryFile boundaryFile = parkBoundaryFileReader.read(file);
        List<ProtectedAreaCommand> commands = toCommands(boundaryFile.records());
        ProtectedAreaUpsertResult upserted =
                protectedAreaLoadService.upsert(ProtectedAreaSource.KDPA, boundaryFile.sourceDate(), commands);
        ParkBoundaryLoadResult result = ParkBoundaryLoadResult.of(boundaryFile.sourceDate(), commands.size(), upserted);
        syncJobRunService.recordProgress(runId, null, result.processedCount(), 0);
        return result;
    }

    // 리더가 구분 값을 NATIONAL_PARK, PROVINCIAL_PARK, COUNTY_PARK 중 하나로 검사했으므로, 이름 그대로 enum으로 바꾼다.
    private static List<ProtectedAreaCommand> toCommands(List<ParkBoundaryRecord> records) {
        return records.stream()
                .map(record -> new ProtectedAreaCommand(
                        record.name(), ProtectedAreaType.valueOf(record.areaType()), record.multiPolygonWkt()))
                .toList();
    }

    // 예외 메시지에는 파일 이름, 줄 번호, 공원 이름 같은 값만 들어가고 비밀값과 개인정보는 없다. 그래서 메시지를 그대로 실행 기록에 남긴다.
    // 실패 기록마저 실패하면 원래 예외가 더 중요하므로, 기록 실패는 원래 예외에 덧붙여 함께 던진다.
    private void recordFailure(long runId, RuntimeException cause) {
        log.warn(
                "park boundary load failed runId={} error={}",
                runId,
                cause.getClass().getSimpleName());
        try {
            syncJobRunService.fail(runId, cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } catch (RuntimeException failure) {
            cause.addSuppressed(failure);
        }
    }

    private static void logCompleted(long runId, ParkBoundaryLoadResult result) {
        log.info(
                "park boundary load completed runId={} sourceDate={} processed={} inserted={} updated={}",
                runId,
                result.sourceDate(),
                result.processedCount(),
                result.inserted(),
                result.updated());
    }
}
