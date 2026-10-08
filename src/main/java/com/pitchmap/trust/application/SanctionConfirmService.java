package com.pitchmap.trust.application;

import com.pitchmap.member.application.MemberSessions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 관리자가 확정한 제재를 기록하고, 정지이면 그 회원의 세션을 지운다. 제재 사유는 로그에 남기지 않는다.
 *
 * <p>이 클래스에는 {@code @Transactional}을 두지 않는다. DB 변경은 {@link SanctionConfirmApplier}의 트랜잭션이 맡고,
 * 세션 삭제는 그 트랜잭션이 커밋된 뒤에 한다. Spring Session JDBC는 세션 작업마다 새 트랜잭션(두 번째 커넥션)을 열어서,
 * 트랜잭션 안에서 세션을 지우면 동시 요청이 커넥션 풀 크기에 닿을 때 서로 커넥션을 기다리며 멈춘다.
 * 진행 중인 베이스캠프 정리는 커밋된 이벤트를 발행기가 처리기에 넘겨서 하므로, 이 클래스에서 기다리지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SanctionConfirmService {

    private final SanctionConfirmApplier sanctionConfirmApplier;
    private final MemberSessions memberSessions;

    /**
     * 호출하면 command대로 제재를 확정하고 결과를 돌려준다. 정지이면 트랜잭션이 커밋된 뒤에 그 회원의 세션을 모두 지운다.
     * 경고는 이용을 막지 않으므로 세션을 지우지 않는다. 커밋 뒤 세션 삭제가 실패하면 제재는 이미 기록된 상태이고 예전 세션이 남을 수 있다.
     * 거부하는 경우는 {@link SanctionConfirmApplier#apply}를 따른다.
     */
    public SanctionConfirmResult confirm(SanctionConfirmCommand command) {
        SanctionConfirmResult result = sanctionConfirmApplier.apply(command);
        if (result.suspended()) {
            memberSessions.invalidateAll(result.memberId());
        }
        log.info(
                "sanction confirmed memberId={} sanctionId={} type={}",
                result.memberId(),
                result.sanctionId(),
                command.type());
        return result;
    }
}
