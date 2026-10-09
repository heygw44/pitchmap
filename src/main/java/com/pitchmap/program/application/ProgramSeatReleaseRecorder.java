package com.pitchmap.program.application;

import com.pitchmap.common.outbox.OutboxEventRecorder;
import com.pitchmap.program.domain.Program;
import com.pitchmap.program.domain.ProgramPhase;
import com.pitchmap.program.domain.ProgramVacancyAlertRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 본인 취소나 결제 만료로 행사에 자리가 돌아왔을 때, 빈자리 알림 신청자에게 알릴 이벤트를 기록한다.
 * 호출하는 쪽의 트랜잭션 안에서 불러야 한다. 그래야 자리를 돌려주는 변경이 롤백되면 이벤트도 함께 사라진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProgramSeatReleaseRecorder {

    private final ProgramVacancyAlertRepository vacancyAlertRepository;
    private final OutboxEventRecorder outboxEventRecorder;

    /**
     * 호출하면 알림 대상 회원이 있을 때 알림 시각을 now로 적고 이벤트를 기록한다. 대상은 알림 신청자 중 결제 대기·확정 신청이 없는 회원이다.
     * 취소됐거나 신청 마감 시각이 지난 행사는 자리가 돌아와도 새로 신청받지 않으므로 아무것도 하지 않는다. 대상이 없을 때도 마찬가지다.
     */
    public void record(Program program, Instant now) {
        ProgramPhase phase = program.phaseAt(now);
        if (phase == ProgramPhase.CANCELED || phase == ProgramPhase.CLOSED) {
            return;
        }
        long programId = program.getId();
        List<Long> targets = vacancyAlertRepository.findAlertTargets(programId);
        if (targets.isEmpty()) {
            return;
        }
        vacancyAlertRepository.markNotified(programId, targets, now);
        outboxEventRecorder.record(
                ProgramSeatEvents.SEAT_RELEASED_EVENT_TYPE,
                ProgramSeatEvents.AGGREGATE_TYPE,
                programId,
                new ProgramSeatEvents.SeatReleasedPayload(programId, targets));
        log.info("program seat released programId={} targetCount={}", programId, targets.size());
    }
}
