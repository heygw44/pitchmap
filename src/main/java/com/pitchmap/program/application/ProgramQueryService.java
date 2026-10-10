package com.pitchmap.program.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.program.domain.ProgramPhase;
import com.pitchmap.program.infra.MyApplicationRow;
import com.pitchmap.program.infra.MyProgramApplicationRow;
import com.pitchmap.program.infra.ProgramDetailRow;
import com.pitchmap.program.infra.ProgramListRow;
import com.pitchmap.program.infra.ProgramQueryMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 행사 목록과 상세를 읽는다. 로그인하지 않아도 부를 수 있다.
 *
 * <p>진행 단계(UPCOMING, OPEN, CLOSED, CANCELED)는 저장하지 않고, 읽을 때마다 현재 시각과 신청 시작·마감 시각으로 계산한다.
 */
@Service
@RequiredArgsConstructor
public class ProgramQueryService {

    private final ProgramQueryMapper programQueryMapper;
    private final Clock clock;

    /**
     * 호출하면 취소되지 않은 행사를 query.phase로 거르고 행사 시작 시각이 이른 순서로 한 페이지 읽는다.
     * 다음 페이지가 있는지 알려고 한 건을 더 읽고, 그 건은 결과에서 뺀다. 그래서 전체 개수를 세는 쿼리를 따로 보내지 않는다.
     */
    @Transactional(readOnly = true)
    public ProgramPage list(ProgramListQuery query) {
        Instant now = clock.instant();
        String phase = query.phase() == null ? null : query.phase().name();
        long offset = (long) query.page() * query.size();
        List<ProgramListRow> rows = programQueryMapper.selectList(phase, now, offset, query.size() + 1);
        boolean hasNext = rows.size() > query.size();
        List<ProgramSummary> content = rows.stream()
                .limit(query.size())
                .map(row -> toSummary(row, now))
                .toList();
        return new ProgramPage(content, query.page(), query.size(), hasNext);
    }

    /**
     * 호출하면 programId인 행사의 상세를 읽는다. 취소된 행사도 status가 CANCELED인 상세로 돌려준다. 신청했던 회원이 취소 사실을 확인해야 하기 때문이다.
     * viewerId가 null이 아니고 그 회원의 신청이 있으면 가장 최근 신청을 myApplication에 담는다. 행사가 없으면 NOT_FOUND로 실패한다.
     */
    @Transactional(readOnly = true)
    public ProgramDetail detail(long programId, Long viewerId) {
        ProgramDetailRow row = programQueryMapper.selectDetail(programId);
        if (row == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return toDetail(row, clock.instant(), findMyApplication(programId, viewerId));
    }

    /**
     * 호출하면 memberId인 회원의 신청을 query.status로 거르고 최근 신청부터 한 페이지 읽는다. 취소된 행사의 신청도 포함한다.
     * 다음 페이지가 있는지 알려고 한 건을 더 읽고, 그 건은 결과에서 뺀다.
     */
    @Transactional(readOnly = true)
    public MyProgramApplicationPage listMine(long memberId, MyProgramApplicationQuery query) {
        Instant now = clock.instant();
        long offset = (long) query.page() * query.size();
        List<MyProgramApplicationRow> rows =
                programQueryMapper.selectMyApplications(memberId, query.status(), offset, query.size() + 1);
        boolean hasNext = rows.size() > query.size();
        List<MyProgramApplicationItem> content =
                rows.stream().limit(query.size()).map(row -> toMyItem(row, now)).toList();
        return new MyProgramApplicationPage(content, query.page(), query.size(), hasNext);
    }

    private ProgramDetail.MyApplication findMyApplication(long programId, Long viewerId) {
        if (viewerId == null) {
            return null;
        }
        MyApplicationRow row = programQueryMapper.selectLatestApplication(programId, viewerId);
        if (row == null) {
            return null;
        }
        return new ProgramDetail.MyApplication(row.applicationId(), row.status().name(), row.paymentDueAt());
    }

    private static ProgramSummary toSummary(ProgramListRow row, Instant now) {
        ProgramPhase phase = ProgramPhase.of(row.status(), row.applyOpenAt(), row.applyCloseAt(), now);
        return new ProgramSummary(
                row.programId(),
                row.title(),
                row.locationText(),
                row.spotId(),
                row.startAt(),
                row.endAt(),
                row.applyOpenAt(),
                row.applyCloseAt(),
                row.capacity(),
                row.remainingSeats(),
                row.fee(),
                row.overnight(),
                phase.name());
    }

    private static MyProgramApplicationItem toMyItem(MyProgramApplicationRow row, Instant now) {
        ProgramPhase phase = ProgramPhase.of(row.programStatus(), row.applyOpenAt(), row.applyCloseAt(), now);
        return new MyProgramApplicationItem(
                row.applicationId(),
                row.status().name(),
                row.paymentDueAt(),
                row.confirmedAt(),
                row.canceledAt(),
                row.cancelReason() == null ? null : row.cancelReason().name(),
                row.createdAt(),
                new MyProgramApplicationItem.Program(
                        row.programId(),
                        row.title(),
                        row.locationText(),
                        row.startAt(),
                        row.endAt(),
                        row.fee(),
                        phase.name()));
    }

    private static ProgramDetail toDetail(ProgramDetailRow row, Instant now, ProgramDetail.MyApplication mine) {
        ProgramPhase phase = ProgramPhase.of(row.status(), row.applyOpenAt(), row.applyCloseAt(), now);
        return new ProgramDetail(
                row.programId(),
                row.title(),
                row.description(),
                row.locationText(),
                row.spotId(),
                row.startAt(),
                row.endAt(),
                row.applyOpenAt(),
                row.applyCloseAt(),
                row.capacity(),
                row.remainingSeats(),
                row.fee(),
                row.paymentDeadlineMinutes(),
                row.overnight(),
                phase.name(),
                mine);
    }
}
