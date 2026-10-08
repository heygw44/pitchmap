package com.pitchmap.basecamp.application;

import com.pitchmap.basecamp.domain.JoinUnmetReason;
import java.util.List;

/** 로그인한 회원이 베이스캠프에 신청할 수 있는지와, 없다면 그 이유다. canApply는 unmetReasons가 비어 있을 때만 true다. */
public record JoinEligibility(boolean canApply, List<JoinUnmetReason> unmetReasons) {

    static JoinEligibility of(List<JoinUnmetReason> unmetReasons) {
        return new JoinEligibility(unmetReasons.isEmpty(), unmetReasons);
    }
}
