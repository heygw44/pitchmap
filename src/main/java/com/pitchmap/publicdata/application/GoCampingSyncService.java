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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 고캠핑 동기화 목록(basedSyncList)을 페이지 단위로 받아 장소로 적재한다.
 *
 * <p>동기화 목록은 원천이 가진 모든 항목을 한 번씩 담고, 항목마다 syncStatus로 추가(A)·수정(U)·삭제(D)를 알려 준다.
 * 삭제 항목도 좌표 같은 필드를 모두 담고 있지만 이 서비스는 삭제 항목을 장소로 적재하지 않는다. 우리 DB에 이미 있는 장소라면 숨기기만 한다.
 * 이 서비스는 목록을 매일 처음부터 끝까지 읽는다. 그래서 같은 삭제 항목이 실행마다 다시 오지만, 장소 모듈은 아직 숨기지 않은 장소만 숨긴다.
 * 따라서 반복해서 받아도 결과는 같고, 관리자가 다시 공개한 장소를 이 서비스가 도로 숨기지 않는다. 삭제됐던 항목이 살아 있는 항목으로 돌아오면 장소 모듈이 다시 공개한다.
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
        return syncJobRunService
                .begin(SyncJobType.GOCAMPING, syncProperties.staleAfter())
                .map(this::run);
    }

    /**
     * 호출하면 이미 시작한 실행 기록으로 동기화를 실행하고 결과를 돌려준다. 실행 기록은 호출하는 쪽이 {@link SyncJobRunService#begin}으로 먼저
     * 만들어 넘긴다. 성공하면 실행 기록을 COMPLETED로, 실패하면 FAILED로 남기고 받은 예외를 그대로 다시 던진다.
     */
    public GoCampingSyncResult run(SyncJobStart start) {
        long runId = start.runId();
        try {
            int firstPage = firstPage(start.resumeCursor());
            log.info("gocamping sync started runId={} firstPage={}", runId, firstPage);
            GoCampingSyncResult result = syncPages(runId, firstPage);
            syncJobRunService.complete(runId);
            logCompleted(result);
            return result;
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
            GoCampingSyncResult afterPage = syncPage(result, page.items());
            syncJobRunService.recordProgress(
                    runId, String.valueOf(pageNo), page.items().size(), afterPage.skipped() - result.skipped());
            result = afterPage;
            if ((long) pageNo * pageSize >= page.totalCount()) {
                return result;
            }
            pageNo++;
        }
    }

    // 삭제된 항목은 적재하지 않고 숨기기만 한다. 그래서 삭제 항목은 건너뛴 항목에 넣지 않는다.
    // 삭제 항목에 ID가 없으면 숨길 장소를 찾을 수 없으므로 건너뛴 항목으로 센다.
    private GoCampingSyncResult syncPage(GoCampingSyncResult result, List<GoCampingItem> items) {
        Map<Boolean, List<GoCampingItem>> byRemoved =
                items.stream().collect(Collectors.partitioningBy(GoCampingItemConverter::isRemoved));
        List<GoCampingItem> liveItems = byRemoved.get(false);
        List<PublicSpotCommand> commands = liveItems.stream()
                .map(GoCampingItemConverter::convert)
                .flatMap(Optional::stream)
                .toList();
        List<String> removedIds = byRemoved.get(true).stream()
                .map(GoCampingItem::contentId)
                .filter(id -> id != null && !id.isBlank())
                .map(String::strip)
                .toList();
        int skipped = liveItems.size() - commands.size() + (byRemoved.get(true).size() - removedIds.size());
        PublicSpotUpsertResult upserted = commands.isEmpty()
                ? NOTHING_UPSERTED
                : publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, commands);
        int hidden =
                removedIds.isEmpty() ? 0 : publicSpotSyncService.markRemoved(PublicSpotSource.GOCAMPING, removedIds);
        return result.addPage(items.size(), skipped, upserted, hidden);
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
                "gocamping sync completed runId={} pages={} processed={} inserted={} updated={} unchanged={} skipped={} removed={}",
                result.runId(),
                result.pages(),
                result.processedCount(),
                result.inserted(),
                result.updated(),
                result.unchanged(),
                result.skipped(),
                result.removed());
    }
}
