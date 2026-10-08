package com.pitchmap.trust.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.member.application.MemberSessions;
import com.pitchmap.trust.domain.SanctionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 관리자가 신고를 조치한다. 제재 사유와 처리 메모는 로그에 남기지 않는다.
 *
 * <p>이 클래스에는 {@code @Transactional}을 두지 않는다. DB 변경은 {@link MemberReportActionApplier}의 트랜잭션이 맡고,
 * 세션 삭제는 그 트랜잭션이 커밋된 뒤에 한다. Spring Session JDBC는 세션 작업마다 새 트랜잭션(두 번째 커넥션)을 열어서,
 * 트랜잭션 안에서 세션을 지우면 동시 요청이 커넥션 풀 크기에 닿을 때 서로 커넥션을 기다리며 멈춘다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberReportActionService {

    private final MemberReportActionApplier memberReportActionApplier;
    private final MemberSessions memberSessions;

    /**
     * 호출하면 신고를 조치 완료로 바꾸고 결과를 돌려준다. 이용을 막는 정지를 내렸으면 트랜잭션이 커밋된 뒤에 대상 회원의 세션을 모두 지운다.
     * 경고나 후기 숨김만 했으면 세션을 지우지 않는다. 커밋 뒤 세션 삭제가 실패하면 조치는 이미 기록된 상태이고 예전 세션이 남을 수 있다.
     * 제재 종류 이름이 올바르지 않으면 DB를 읽기 전에 INVALID_INPUT으로 거부하고, 그 밖의 거부는 {@link MemberReportActionApplier#apply}를 따른다.
     */
    public MemberReportActionResult act(MemberReportActionCommand command) {
        MemberReportActionResult result = memberReportActionApplier.apply(command, parseSanctionType(command));
        if (result.suspended()) {
            memberSessions.invalidateAll(result.targetMemberId());
        }
        log.info(
                "member report actioned reportId={} adminId={} sanctionId={}",
                result.reportId(),
                command.adminId(),
                result.sanctionId());
        return result;
    }

    private static SanctionType parseSanctionType(MemberReportActionCommand command) {
        if (command.sanctionType() == null) {
            return null;
        }
        try {
            SanctionType type = SanctionType.valueOf(command.sanctionType());
            if (type != SanctionType.TEMPORARY_72H) {
                return type;
            }
        } catch (IllegalArgumentException e) {
            // 알 수 없는 이름과 임시 정지는 같은 입력 오류로 거부한다.
        }
        throw new BusinessException(CommonErrorCode.INVALID_INPUT, "관리자가 내릴 수 없는 제재 종류입니다.");
    }
}
