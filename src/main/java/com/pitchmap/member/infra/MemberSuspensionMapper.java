package com.pitchmap.member.infra;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 기간이 끝난 정지를 풀 회원은 한꺼번에 많을 수 있고, 같은 회원이 로그인으로 먼저 풀리는 경우와 겹쳐도 한 번만 풀려야 한다.
// 그래서 엔티티를 하나씩 읽어 고치지 않고, 상태와 종료 시각 조건을 건 UPDATE의 영향받은 행 수로 처리한다.
// 이 매퍼의 메서드는 호출하는 쪽(MemberSuspensionService)이 연 트랜잭션 안에서 실행해야 한다.
@Mapper
public interface MemberSuspensionMapper {

    /**
     * 호출하면 정지 종료 시각이 now와 같거나 지난 SUSPENDED 회원의 정지를 풀고 바뀐 행 수를 돌려준다.
     * 이메일 인증을 마친 회원은 ACTIVE로, 마치지 못한 회원은 UNVERIFIED로 돌아간다. 종료 시각이 없는 영구 정지는 풀지 않는다.
     */
    int releaseExpired(@Param("now") Instant now);
}
