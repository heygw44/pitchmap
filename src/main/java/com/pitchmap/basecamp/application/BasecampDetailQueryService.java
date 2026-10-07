package com.pitchmap.basecamp.application;

import com.pitchmap.basecamp.domain.Basecamp;
import com.pitchmap.basecamp.domain.BasecampMember;
import com.pitchmap.basecamp.domain.BasecampMemberRole;
import com.pitchmap.basecamp.domain.BasecampRelation;
import com.pitchmap.basecamp.domain.BasecampRepository;
import com.pitchmap.basecamp.domain.JoinCondition;
import com.pitchmap.basecamp.infra.BasecampSearchMapper;
import com.pitchmap.basecamp.infra.SpotSummaryRow;
import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.trust.application.MemberProfileQueryService;
import com.pitchmap.trust.application.MemberTrustProfile;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 베이스캠프 한 건의 상세를 읽는다. 상태와 상관없이 읽을 수 있고, 요청자와의 관계에 따라 연락 수단 공개 여부만 달라진다.
 *
 * <p>멤버의 프로필은 멤버마다 신뢰 모듈의 서비스를 불러 읽는다. 정원이 최대 6명이라 쿼리가 많지 않다.
 */
@Service
@RequiredArgsConstructor
public class BasecampDetailQueryService {

    private final BasecampRepository basecampRepository;
    private final BasecampSearchMapper basecampSearchMapper;
    private final MemberProfileQueryService memberProfileQueryService;
    private final Clock clock;

    /**
     * 호출하면 basecampId인 베이스캠프의 상세를 돌려준다. viewerId가 null이면 비로그인 요청자라서 관계는 NONE이고 연락 수단은 없다.
     * 베이스캠프가 없으면 NOT_FOUND로 실패한다.
     *
     * <p>연락 수단은 확정된 뒤부터 완료 후 일주일까지 ACTIVE 멤버(캠프 리더 포함)에게만 채우고, 등록된 값이 없으면 그때도 null이다.
     */
    @Transactional(readOnly = true)
    public BasecampDetail find(Long viewerId, long basecampId) {
        Basecamp basecamp = basecampRepository
                .findById(basecampId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        SpotSummaryRow spot = basecampSearchMapper.selectSpotSummary(basecamp.getSpotId());
        if (spot == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        List<BasecampDetail.DetailMember> members = readMembers(basecamp);
        BasecampDetail.DetailMember leader = findLeader(basecamp, members);
        BasecampRelation relation = basecamp.relationOf(viewerId);
        return new BasecampDetail(
                basecamp.getId(),
                basecamp.getTitle(),
                basecamp.getDescription(),
                new BasecampDetail.SpotSummary(spot.spotId(), spot.name(), spot.type()),
                basecamp.getStartDate(),
                basecamp.getEndDate(),
                basecamp.getCapacity().getValue(),
                members.size(),
                basecamp.getStatus().name(),
                toSummary(basecamp.getJoinCondition()),
                leader,
                members,
                relation,
                visibleContactInfo(basecamp, viewerId));
    }

    // 캠프 리더가 탈퇴하거나 제재로 빠진 뒤 취소된 베이스캠프는 ACTIVE 멤버 목록에 리더가 없다.
    // 그래도 상세는 리더를 보여 줘야 하므로, 목록에 없으면 리더의 프로필을 따로 읽는다.
    private BasecampDetail.DetailMember findLeader(Basecamp basecamp, List<BasecampDetail.DetailMember> members) {
        return members.stream()
                .filter(member -> member.memberId() == basecamp.getLeaderId())
                .findFirst()
                .orElseGet(() -> toDetailMember(
                        memberProfileQueryService.find(basecamp.getLeaderId()), BasecampMemberRole.LEADER));
    }

    private List<BasecampDetail.DetailMember> readMembers(Basecamp basecamp) {
        return basecamp.getMembers().stream()
                .filter(BasecampMember::isActive)
                .sorted(Comparator.comparing((BasecampMember member) -> !member.isLeader())
                        .thenComparing(BasecampMember::getJoinedAt)
                        .thenComparing(BasecampMember::getId))
                .map(this::toDetailMember)
                .toList();
    }

    private BasecampDetail.DetailMember toDetailMember(BasecampMember member) {
        return toDetailMember(memberProfileQueryService.find(member.getMemberId()), member.getRole());
    }

    private static BasecampDetail.DetailMember toDetailMember(MemberTrustProfile profile, BasecampMemberRole role) {
        return new BasecampDetail.DetailMember(
                profile.memberId(),
                profile.nickname(),
                role.name(),
                profile.ageGroup(),
                profile.ageGroupVerified(),
                profile.gender(),
                profile.genderVerified(),
                profile.trustLevel(),
                profile.completedCompanions());
    }

    private String visibleContactInfo(Basecamp basecamp, Long viewerId) {
        if (viewerId == null || !basecamp.canViewContact(viewerId, clock.instant())) {
            return null;
        }
        return basecamp.getContactInfo();
    }

    private static JoinConditionSummary toSummary(JoinCondition condition) {
        return new JoinConditionSummary(
                condition.getMinTrustLevel(),
                condition.getAgeGroupMin(),
                condition.getAgeGroupMax(),
                condition.isSameGenderOnly());
    }
}
