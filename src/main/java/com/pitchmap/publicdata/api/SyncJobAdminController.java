package com.pitchmap.publicdata.api;

import com.pitchmap.common.security.LoginMember;
import com.pitchmap.publicdata.application.SyncJobLauncher;
import com.pitchmap.publicdata.application.SyncJobRunQueryService;
import com.pitchmap.publicdata.domain.SyncJobType;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 관리자 기능이지만 관리자 모듈에 두지 않는다. 관리자 모듈은 공공데이터 모듈을 부를 수 없게 모듈 의존 방향이 정해져 있다.
// 그래서 공공데이터 모듈이 자기 관리자 엔드포인트를 직접 둔다. /api/admin/** 경로는 보안 설정이 ADMIN 역할에만 연다.
@RestController
@RequestMapping("/api/admin/sync-jobs")
class SyncJobAdminController {

    private static final int MAX_PAGE_SIZE = 50;

    private final SyncJobLauncher syncJobLauncher;
    private final SyncJobRunQueryService syncJobRunQueryService;

    SyncJobAdminController(SyncJobLauncher syncJobLauncher, SyncJobRunQueryService syncJobRunQueryService) {
        this.syncJobLauncher = syncJobLauncher;
        this.syncJobRunQueryService = syncJobRunQueryService;
    }

    @Operation(
            summary = "공공데이터 적재·동기화 실행",
            description = "jobType 작업(GOCAMPING, FOREST, PARK_BOUNDARY, BAKJI_REJUDGE)의 실행 기록을 만들고 작업을 백그라운드에서 시작한다. "
                    + "작업이 끝나기를 기다리지 않고 202와 실행 기록 ID를 돌려주므로, 결과는 실행 기록 목록에서 확인한다. "
                    + "같은 종류의 작업이 실행 중이면 409 SYNC_JOB_ALREADY_RUNNING으로 응답한다. "
                    + "관리자가 요청한 실행은 감사 로그에 한 줄 남는다. "
                    + "PARK_BOUNDARY 적재가 성공하면 서버가 바로 이어서 박지 재판정(BAKJI_REJUDGE)을 별도의 실행 기록으로 시작한다.")
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    SyncJobLaunchResponse launch(
            @AuthenticationPrincipal LoginMember admin, @Valid @RequestBody SyncJobLaunchRequest request) {
        return new SyncJobLaunchResponse(syncJobLauncher.launch(request.jobType(), admin.memberId()));
    }

    @Operation(
            summary = "공공데이터 적재·동기화 실행 기록 조회",
            description = "실행 기록을 최근 것부터 돌려준다. jobType을 주면 그 종류만 돌려준다. " + "page는 0부터 세고, size는 기본 20, 최대 50이다.")
    @GetMapping
    SyncJobRunPageResponse findRuns(
            @RequestParam(required = false) SyncJobType jobType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return SyncJobRunPageResponse.from(syncJobRunQueryService.findRuns(jobType, page, size));
    }
}
