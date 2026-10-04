package com.pitchmap.member.infra;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 공개 목록 수천 건을 한 번에 넣는 벌크 적재라서, 우리는 JPA 대신 MyBatis를 쓴다.
@Mapper
public interface DisposableEmailDomainMapper {

    /** 호출하면 이미 있는 도메인(관리자가 등록한 행 포함)은 건드리지 않고 건너뛰며, 새로 들어간 행 수를 돌려준다. */
    int insertPublicIfAbsent(@Param("domains") List<String> domains, @Param("createdAt") Instant createdAt);
}
