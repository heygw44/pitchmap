package com.pitchmap.trust.application;

import org.springframework.stereotype.Service;

@Service
public class TrustSummaryService {

    private static final TrustSummary NOT_VERIFIED_LEVEL_ZERO = new TrustSummary(false, 0);

    // 본인확인 기록을 저장하는 기능이 아직 없어서 모든 회원을 본인확인 전, 신뢰 단계 0으로 계산한다.
    // 본인확인과 신뢰 단계 계산을 만들면 이 메서드가 회원별 기록을 읽도록 바꾼다. DB를 읽지 않으므로 지금은 트랜잭션을 열지 않는다.
    public TrustSummary summarize(long memberId) {
        return NOT_VERIFIED_LEVEL_ZERO;
    }
}
