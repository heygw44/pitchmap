package com.pitchmap.trust.application;

import com.pitchmap.member.application.MemberSessions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 회원이나 동행 후기를 신고한다. 신고 내용은 로그에 남기지 않는다.
 *
 * <p>이 클래스에는 {@code @Transactional}을 두지 않는다. DB 변경은 {@link MemberReportApplier}의 트랜잭션이 맡고,
 * 세션 삭제는 그 트랜잭션이 커밋된 뒤에 한다. Spring Session JDBC는 세션 작업마다 새 트랜잭션(두 번째 커넥션)을 열어서,
 * 트랜잭션 안에서 세션을 지우면 동시 요청이 커넥션 풀 크기에 닿을 때 서로 커넥션을 기다리며 멈춘다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberReportService {

    private final MemberReportApplier memberReportApplier;
    private final MemberSessions memberSessions;

    /**
     * 호출하면 reporterId인 회원의 신고를 접수하고 신고 ID를 돌려준다. 성희롱·위협 신고이면 대상 회원을 72시간 임시 정지하고,
     * 트랜잭션이 커밋된 뒤에 그 회원의 세션을 모두 지운다. 커밋 뒤 세션 삭제가 실패하면 정지는 이미 기록된 상태이고 예전 세션이 남을 수 있다.
     * 검사 순서와 오류는 {@link MemberReportApplier#apply}를 따른다.
     */
    public long report(long reporterId, MemberReportCommand command) {
        MemberReportResult result = memberReportApplier.apply(reporterId, command);
        if (result.isTargetSuspended()) {
            memberSessions.invalidateAll(result.targetMemberId());
            log.info(
                    "member temporarily suspended memberId={} reportId={}", result.targetMemberId(), result.reportId());
        }
        return result.reportId();
    }
}
