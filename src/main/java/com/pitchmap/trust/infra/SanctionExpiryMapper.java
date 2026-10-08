package com.pitchmap.trust.infra;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 종료 시각이 지난 제재는 한꺼번에 많을 수 있고, 만료 작업이 여러 번 겹쳐 돌아도 한 번만 바뀌어야 한다.
// 그래서 엔티티를 하나씩 읽어 고치지 않고, 상태와 종료 시각 조건을 건 UPDATE의 영향받은 행 수로 처리한다.
// UPDATE 한 문장이라 트랜잭션 없이 불러도 그 문장이 원자적으로 커밋된다.
@Mapper
public interface SanctionExpiryMapper {

    /**
     * 호출하면 종료 시각이 now와 같거나 지난 적용 중 제재를 만료로 바꾸고 바뀐 행 수를 돌려준다.
     * 종료 시각이 없는 경고와 영구 정지는 바꾸지 않는다.
     */
    int expireEnded(@Param("now") Instant now);
}
