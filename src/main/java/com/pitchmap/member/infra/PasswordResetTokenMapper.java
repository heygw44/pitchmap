package com.pitchmap.member.infra;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 두 메서드 모두 조건부 UPDATE다. 같은 링크를 동시에 쓰는 요청 중 하나만 성공하려면,
// 읽은 값을 검사한 뒤 JPA로 저장하지 말고 DB가 조건을 검사하면서 한 번에 바꿔야 한다.
// 그래서 성공 여부를 영향받은 행 수로 판단하는 MyBatis를 쓴다.
// 이 매퍼의 모든 메서드는 호출하는 쪽(PasswordResetConfirmService)이 연 트랜잭션 안에서 실행해야 한다.
@Mapper
public interface PasswordResetTokenMapper {

    /**
     * 호출하면 아직 쓰지 않았고 {@code now}에 만료 전인 행에 사용 시각을 기록하고 1을 돌려준다.
     * 동시에 같은 링크를 쓴 다른 요청이 먼저 이겼거나 만료됐으면 0을 돌려준다.
     */
    int markUsed(@Param("id") long id, @Param("now") Instant now);

    /**
     * 호출하면 그 회원의 아직 쓰지 않은 모든 행에 사용 시각을 기록하고 바꾼 행 수를 돌려준다.
     * 만료된 행도 포함한다. 어차피 쓸 수 없는 행이라 해롭지 않고, 쓰지 않은 행이 남지 않아 테이블이 일관된다.
     */
    int invalidateUnusedTokens(@Param("memberId") long memberId, @Param("now") Instant now);
}
