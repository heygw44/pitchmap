package com.pitchmap.publicdata.application;

import com.pitchmap.publicdata.application.SyncJobRunService.SyncJobStart;
import com.pitchmap.publicdata.domain.SyncJobType;
import com.pitchmap.publicdata.infra.ForestFile;
import com.pitchmap.publicdata.infra.ForestFileReader;
import com.pitchmap.publicdata.infra.ForestRecord;
import com.pitchmap.spot.application.PublicSpotCommand;
import com.pitchmap.spot.application.PublicSpotSource;
import com.pitchmap.spot.application.PublicSpotSyncService;
import com.pitchmap.spot.application.PublicSpotUpsertResult;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 국립자연휴양림 예약 정책 파일을 읽어 휴양림을 장소로 적재하고, 파일 기준일을 장소 상세에 남긴다.
 *
 * <p>이 파일은 휴양림 목록이 아니라 예약 정책 목록이다. 예약 정책마다 한 행이라 같은 휴양림이 여러 행에 반복되고, 이름·좌표·연락처·홈페이지는 모든
 * 행에서 같다. 그래서 서비스는 기관아이디마다 처음 나온 행 하나만 적재한다.
 *
 * <p>서비스는 휴양림을 추가하고 갱신하기만 하고, 새 파일에 없는 기존 휴양림을 숨기지 않는다. 휴양림은 예약 정책이 없는 기간에 파일에서 빠질 수 있다.
 * 그래서 파일에 없다는 이유만으로 숨기면 운영 중인 휴양림을 잘못 숨기게 된다. 실제로 문을 닫은 휴양림은 관리자가 직접 숨긴다.
 *
 * <p>서비스는 파일 전체를 장소 모듈의 트랜잭션 하나로 적재한다. 그래서 적재 도중 실패하면 DB가 그 트랜잭션을 모두 되돌리고, 다음 실행이 이어서
 * 처리할 위치도 없다. 따라서 서비스는 실행 기록에 진행 위치를 쓰지 않고, 직전 실행이 실패했어도 파일을 처음부터 다시 적재한다.
 *
 * <p>이 서비스는 트랜잭션을 열지 않는다. 실행 기록과 적재는 각 서비스의 트랜잭션에서 따로 커밋한다. 그래서 적재가 실패해도 실패한 실행 기록은 남는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForestLoadService {

    private final SyncJobRunService syncJobRunService;
    private final ForestFileReader forestFileReader;
    private final PublicSpotSyncService publicSpotSyncService;
    private final ForestLoadProperties properties;

    /**
     * 호출하면 설정한 경로의 휴양림 파일을 한 번 적재하고 결과를 돌려준다. 다른 적재가 진행 중이라 시작하지 않았으면 빈 값을 돌려준다.
     * 실패하면 실행 기록을 FAILED로 남기고 받은 예외를 그대로 다시 던진다.
     */
    public Optional<ForestLoadResult> load() {
        return load(properties.file());
    }

    Optional<ForestLoadResult> load(Path file) {
        Optional<SyncJobStart> start = syncJobRunService.begin(SyncJobType.FOREST, properties.staleAfter());
        if (start.isEmpty()) {
            return Optional.empty();
        }
        long runId = start.get().runId();
        try {
            ForestLoadResult result = loadFile(runId, requireConfigured(file));
            syncJobRunService.complete(runId);
            logCompleted(runId, result);
            return Optional.of(result);
        } catch (RuntimeException e) {
            recordFailure(runId, e);
            throw e;
        }
    }

    // 경로가 비었는지는 실행을 시작한 뒤에 확인한다. 그래야 설정을 빠뜨린 실행도 실패 기록으로 남는다.
    private static Path requireConfigured(Path file) {
        if (file == null) {
            throw new IllegalStateException(
                    "휴양림 파일 경로가 설정되지 않았습니다. " + ForestLoadProperties.FILE_PROPERTY + " 값을 확인하세요.");
        }
        return file;
    }

    private ForestLoadResult loadFile(long runId, Path file) {
        log.info("forest load started runId={} file={}", runId, file.getFileName());
        ForestFile forestFile = forestFileReader.read(file);
        Collection<ForestRecord> forests = firstRowPerInstitution(forestFile.records());
        List<PublicSpotCommand> commands = toCommands(forests, forestFile.sourceDate());
        PublicSpotUpsertResult upserted = publicSpotSyncService.upsert(PublicSpotSource.FOREST, commands);
        ForestLoadResult result = ForestLoadResult.of(
                forestFile.sourceDate(), forests.size(), forests.size() - commands.size(), upserted);
        syncJobRunService.recordProgress(runId, null, result.processedCount(), result.skipped());
        return result;
    }

    private static Collection<ForestRecord> firstRowPerInstitution(List<ForestRecord> records) {
        Map<String, ForestRecord> byInstitutionId = new LinkedHashMap<>();
        for (ForestRecord record : records) {
            byInstitutionId.putIfAbsent(record.institutionId().strip(), record);
        }
        return byInstitutionId.values();
    }

    private static List<PublicSpotCommand> toCommands(Collection<ForestRecord> forests, LocalDate sourceDate) {
        List<PublicSpotCommand> commands = new ArrayList<>();
        for (ForestRecord forest : forests) {
            toCommand(forest, sourceDate).ifPresent(commands::add);
        }
        return commands;
    }

    // 좌표를 읽지 못한 휴양림 하나 때문에 나머지 휴양림까지 적재하지 않을 이유는 없다. 그래서 서비스는 그 휴양림만 건너뛴다.
    private static Optional<PublicSpotCommand> toCommand(ForestRecord forest, LocalDate sourceDate) {
        try {
            return Optional.of(new PublicSpotCommand(
                    forest.institutionId(),
                    forest.name(),
                    Double.parseDouble(forest.latitude().strip()),
                    Double.parseDouble(forest.longitude().strip()),
                    null,
                    null,
                    null,
                    forest.phone(),
                    forest.homepage(),
                    null,
                    null,
                    null,
                    sourceDate));
        } catch (NumberFormatException e) {
            log.warn(
                    "forest skipped externalId={} reason=invalid coordinate: {}",
                    forest.institutionId(),
                    e.getMessage());
            return Optional.empty();
        }
    }

    // 예외 메시지에는 파일 경로와 줄 번호 같은 값만 들어가고 비밀값은 없다. 그래서 메시지를 그대로 실행 기록에 남긴다.
    // 실패 기록마저 실패하면 원래 예외가 더 중요하므로, 기록 실패는 원래 예외에 덧붙여 함께 던진다.
    private void recordFailure(long runId, RuntimeException cause) {
        log.warn("forest load failed runId={} error={}", runId, cause.getClass().getSimpleName());
        try {
            syncJobRunService.fail(runId, cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } catch (RuntimeException failure) {
            cause.addSuppressed(failure);
        }
    }

    private static void logCompleted(long runId, ForestLoadResult result) {
        log.info(
                "forest load completed runId={} sourceDate={} processed={} inserted={} updated={} unchanged={} skipped={}",
                runId,
                result.sourceDate(),
                result.processedCount(),
                result.inserted(),
                result.updated(),
                result.unchanged(),
                result.skipped());
    }
}
