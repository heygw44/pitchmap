package com.pitchmap.trust.infra;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 지울 행 수를 제한하는 삭제 문장이라 JPA 대신 MyBatis를 쓴다.
@Mapper
public interface CiRetentionCleanupMapper {

    /**
     * 호출하면 CI 보관 기한이 now와 같거나 지난 본인확인 행을 최대 limit개 지우고, 지운 행 수를 돌려준다.
     * 보관 기한이 없는(NULL) 행은 탈퇴하지 않은 회원의 것이라 지우지 않는다.
     */
    int deleteRetentionExpired(@Param("now") Instant now, @Param("limit") int limit);
}
