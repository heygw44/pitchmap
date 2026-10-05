package com.pitchmap.publicdata.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 기본값은 하루 한 번, 새벽 3시(한국 시간)다. 고캠핑 API는 하루 호출 한도가 1,000건이고, 페이지 크기 100이면 전체 목록을 받는 데
// 약 32건을 쓴다. 그래서 하루 한 번이면 한도 안에서 충분히 최신 상태를 유지하고, 실패해도 다음 날 다시 받을 여유가 남는다.
// 또 사용자가 가장 적은 새벽에 돌려서, 동기화가 지도 조회와 같은 시간에 DB를 쓰는 일을 줄인다.
// cron을 UTC가 아닌 한국 시간으로 해석해야 하므로 zone을 명시한다.
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "pitchmap.publicdata.gocamping.sync",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class GoCampingSyncScheduler {

    private final GoCampingSyncService goCampingSyncService;

    @Scheduled(cron = "${pitchmap.publicdata.gocamping.sync.cron:0 0 3 * * *}", zone = "Asia/Seoul")
    public void sync() {
        try {
            goCampingSyncService.sync();
        } catch (Exception e) {
            // 어떤 예외든 이번 실행의 실패 하나로 끝내고 다음 주기에 다시 실행한다. 실패한 위치는 실행 기록에 남아 있어서
            // 다음 실행이 그 페이지부터 이어서 처리한다. 예외를 스케줄러 기본 처리기에 맡기면 어느 작업이 실패했는지 로그만 봐서는
            // 알기 어렵다. 그래서 작업 이름이 드러나는 ERROR 로그를 직접 남긴다. 예외를 삼키는 곳은 호출 경로의 맨 끝인 여기뿐이다.
            log.error("gocamping sync failed", e);
        }
    }
}
