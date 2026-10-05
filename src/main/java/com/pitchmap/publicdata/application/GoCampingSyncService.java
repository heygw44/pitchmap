package com.pitchmap.publicdata.application;

import com.pitchmap.publicdata.application.SyncJobRunService.SyncJobStart;
import com.pitchmap.publicdata.domain.SyncJobType;
import com.pitchmap.publicdata.infra.GoCampingClient;
import com.pitchmap.publicdata.infra.GoCampingItem;
import com.pitchmap.publicdata.infra.GoCampingPage;
import com.pitchmap.publicdata.infra.GoCampingProperties;
import com.pitchmap.spot.application.PublicSpotCommand;
import com.pitchmap.spot.application.PublicSpotSource;
import com.pitchmap.spot.application.PublicSpotSyncService;
import com.pitchmap.spot.application.PublicSpotUpsertResult;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 고캠핑 야영장 목록을 페이지 단위로 받아 장소로 적재한다.
 *
 * <p>이 서비스는 트랜잭션을 열지 않는다. 외부 API를 부르는 동안 DB 커넥션을 붙잡지 않으려고, 적재와 실행 기록은 페이지마다 각 서비스의 트랜잭션에서 따로 커밋한다.
 * 그래서 중간 페이지에서 실패해도 앞 페이지의 적재 결과와 진행 위치가 남고, 다음 실행은 그다음 페이지부터 이어서 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoCampingSyncService {

    private static final PublicSpotUpsertResult NOTHING_UPSERTED = new PublicSpotUpsertResult(0, 0, 0, 0);

    private final SyncJobRunService syncJobRunService;
    private final GoCampingClient goCampingClient;
    private final PublicSpotSyncService publicSpotSyncService;
    private final GoCampingProperties goCampingProperties;
    private final GoCampingSyncProperties syncProperties;

    /**
     * 호출하면 동기화를 한 번 실행하고 결과를 돌려준다. 다른 실행이 진행 중이라 시작하지 않았으면 빈 값을 돌려준다.
     * 실패하면 실행 기록을 FAILED로 남기고 받은 예외를 그대로 다시 던진다.
     */
    public Optional<GoCampingSyncResult> sync() {
        Optional<SyncJobStart> start = syncJobRunService.begin(SyncJobType.GOCAMPING, syncProperties.staleAfter());
        if (start.isEmpty()) {
            return Optional.empty();
        }
        long runId = start.get().runId();
        try {
            int firstPage = firstPage(start.get().resumeCursor());
            log.info("gocamping sync started runId={} firstPage={}", runId, firstPage);
            GoCampingSyncResult result = syncPages(runId, firstPage);
            syncJobRunService.complete(runId);
            logCompleted(result);
            return Optional.of(result);
        } catch (RuntimeException e) {
            recordFailure(runId, e);
            throw e;
        }
    }

    // 진행 위치에는 처리를 마친 마지막 페이지 번호가 들어 있다.
    private static int firstPage(String resumeCursor) {
        if (resumeCursor == null) {
            return 1;
        }
        return Integer.parseInt(resumeCursor) + 1;
    }

    private GoCampingSyncResult syncPages(long runId, int firstPage) {
        int pageSize = goCampingProperties.pageSize();
        GoCampingSyncResult result = GoCampingSyncResult.empty(runId);
        int pageNo = firstPage;
        while (true) {
            GoCampingPage page = goCampingClient.fetchPage(pageNo, pageSize);
            if (page.items().isEmpty()) {
                return result;
            }
            result = upsertPage(result, page.items());
            syncJobRunService.recordProgress(
                    runId, String.valueOf(pageNo), page.items().size());
            if ((long) pageNo * pageSize >= page.totalCount()) {
                return result;
            }
            pageNo++;
        }
    }

    private GoCampingSyncResult upsertPage(GoCampingSyncResult result, List<GoCampingItem> items) {
        List<PublicSpotCommand> commands = items.stream()
                .map(GoCampingItemConverter::convert)
                .flatMap(Optional::stream)
                .toList();
        PublicSpotUpsertResult upserted = commands.isEmpty()
                ? NOTHING_UPSERTED
                : publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, commands);
        return result.addPage(items.size(), items.size() - commands.size(), upserted);
    }

    // 고캠핑 클라이언트는 예외 메시지에 인증키를 넣지 않는다. 그래서 메시지를 그대로 실행 기록에 남겨도 키가 DB에 들어가지 않는다.
    // 실패 기록마저 실패하면 원래 예외가 더 중요하므로, 기록 실패는 원래 예외에 덧붙여 함께 던진다.
    private void recordFailure(long runId, RuntimeException cause) {
        log.warn(
                "gocamping sync failed runId={} error={}",
                runId,
                cause.getClass().getSimpleName());
        try {
            syncJobRunService.fail(runId, cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } catch (RuntimeException failure) {
            cause.addSuppressed(failure);
        }
    }

    private static void logCompleted(GoCampingSyncResult result) {
        log.info(
                "gocamping sync completed runId={} pages={} processed={} inserted={} updated={} unchanged={} skipped={}",
                result.runId(),
                result.pages(),
                result.processedCount(),
                result.inserted(),
                result.updated(),
                result.unchanged(),
                result.skipped());
    }
}
