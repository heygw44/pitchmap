package com.pitchmap.member.infra;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 미인증 회원을 인증 회원으로 바꾸는 일은 조건부 상태 변경이다. 같은 회원의 인증 요청이 동시에 와도 한 번만 성공해야 해서,
// 회원 엔티티의 상태를 바꿔 저장하지 않고 status 조건을 건 UPDATE의 영향받은 행 수로 성공 여부를 판단한다.
// 이 매퍼의 모든 메서드는 호출하는 쪽(EmailVerificationService)이 연 트랜잭션 안에서 실행해야 한다.
@Mapper
public interface MemberActivationMapper {

    /**
     * 호출하면 UNVERIFIED인 회원만 ACTIVE로 바꾸고 이메일 인증 시각을 기록한 뒤 1을 돌려준다.
     * 이미 다른 상태이면 0을 돌려준다. 바뀐 행의 회원 엔티티는 같은 트랜잭션에서 다시 쓰지 않는다.
     */
    int activate(@Param("memberId") long memberId, @Param("now") Instant now);
}
