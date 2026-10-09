package com.pitchmap.program.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.program.domain.Program;
import com.pitchmap.program.domain.ProgramErrorCode;
import com.pitchmap.program.domain.ProgramException;
import com.pitchmap.program.domain.ProgramPhase;
import com.pitchmap.program.domain.ProgramRepository;
import com.pitchmap.program.domain.ProgramVacancyAlertRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원이 행사의 빈자리 알림을 신청하거나 해제한다.
 *
 * <p>신청할 때 남은 자리를 확인하지 않는다. 화면이 본 남은 자리와 요청이 도착한 때의 남은 자리가 달라질 수 있어서, 자리가 있어도 신청을 저장한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramVacancyAlertService {

    private final ProgramRepository programRepository;
    private final ProgramVacancyAlertRepository vacancyAlertRepository;
    private final Clock clock;

    /**
     * 호출하면 memberId인 회원의 알림 신청을 저장하고, 새로 만들었으면 true, 이미 신청한 상태였으면 false를 돌려준다.
     * 없는 행사이면 NOT_FOUND, 취소된 행사이거나 신청 마감 시각 이후이면 PROGRAM_INVALID_STATE를 던진다.
     */
    @Transactional
    public boolean subscribe(long memberId, long programId) {
        Program program = findProgram(programId);
        Instant now = clock.instant();
        ProgramPhase phase = program.phaseAt(now);
        if (phase == ProgramPhase.CANCELED || phase == ProgramPhase.CLOSED) {
            throw new ProgramException(ProgramErrorCode.PROGRAM_INVALID_STATE);
        }
        boolean created = vacancyAlertRepository.subscribe(programId, memberId, now) == 1;
        log.info("program vacancy alert subscribed programId={} memberId={} created={}", programId, memberId, created);
        return created;
    }

    /** 호출하면 memberId인 회원의 알림 신청을 지운다. 신청한 적이 없거나 행사가 취소·마감됐어도 지운다. 없는 행사이면 NOT_FOUND를 던진다. */
    @Transactional
    public void unsubscribe(long memberId, long programId) {
        findProgram(programId);
        vacancyAlertRepository.unsubscribe(programId, memberId);
        log.info("program vacancy alert unsubscribed programId={} memberId={}", programId, memberId);
    }

    private Program findProgram(long programId) {
        return programRepository
                .findById(programId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
