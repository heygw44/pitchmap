package com.pitchmap.basecamp.application;

import com.pitchmap.basecamp.domain.Basecamp;
import com.pitchmap.basecamp.domain.BasecampRepository;
import com.pitchmap.basecamp.infra.BasecampApplicationMapper;
import com.pitchmap.basecamp.infra.BasecampApplicationRow;
import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.trust.application.MemberProfileQueryService;
import com.pitchmap.trust.application.MemberTrustProfile;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캠프 리더가 자기 베이스캠프에 들어온 합류 신청 목록을 읽는다.
 *
 * <p>신청자의 프로필은 신청마다 신뢰 모듈의 서비스를 불러 읽는다. 한 페이지는 최대 50건이다.
 */
@Service
@RequiredArgsConstructor
public class BasecampApplicationListService {

    private final BasecampRepository basecampRepository;
    private final BasecampApplicationMapper basecampApplicationMapper;
    private final MemberProfileQueryService memberProfileQueryService;

    /**
     * 호출하면 leaderId인 캠프 리더가 basecampId인 베이스캠프의 신청 가운데 query.status인 것을 신청이 오래된 순서로 한 페이지 읽는다.
     * 베이스캠프가 없으면 NOT_FOUND, 요청자가 캠프 리더가 아니면 ACCESS_DENIED로 실패한다.
     *
     * <p>다음 페이지가 있는지 알려고 한 건을 더 읽고, 그 건은 결과에서 뺀다. 그래서 전체 개수를 세는 쿼리를 따로 보내지 않는다.
     */
    @Transactional(readOnly = true)
    public BasecampApplicationPage list(long basecampId, long leaderId, BasecampApplicationListQuery query) {
        Basecamp basecamp = basecampRepository
                .findById(basecampId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!basecamp.isLeader(leaderId)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
        long offset = (long) query.page() * query.size();
        List<BasecampApplicationRow> rows =
                basecampApplicationMapper.selectByStatus(basecampId, query.status(), offset, query.size() + 1);
        boolean hasNext = rows.size() > query.size();
        List<BasecampApplicationItem> content =
                rows.stream().limit(query.size()).map(this::toItem).toList();
        return new BasecampApplicationPage(content, query.page(), query.size(), hasNext);
    }

    private BasecampApplicationItem toItem(BasecampApplicationRow row) {
        MemberTrustProfile profile = memberProfileQueryService.find(row.applicantId());
        return new BasecampApplicationItem(
                row.applicationId(),
                row.status().name(),
                row.message(),
                row.appliedAt(),
                new BasecampApplicationItem.Applicant(
                        profile.memberId(),
                        profile.nickname(),
                        profile.ageGroup(),
                        profile.ageGroupVerified(),
                        profile.gender(),
                        profile.genderVerified(),
                        profile.trustLevel(),
                        profile.completedCompanions()));
    }
}
