package com.pitchmap.notification.infra;

import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 받는 사람의 주소와 회원 상태는 member 테이블에 있고, notification 모듈은 member 모듈의 Java 클래스를 쓸 수 없다.
// 그래서 알림, 이메일 수신 설정, 회원을 한 번에 조인하는 SQL로 보낼 대상을 고른다.
@Mapper
public interface NotificationMailMapper {

    /**
     * 호출하면 dedupKeys에 해당하는 알림 가운데 메일을 보낼 것을 알림 ID 순으로 읽는다. 아직 메일을 보내지 않았고,
     * 받는 회원이 그 종류의 이메일을 받기로 했으며(설정이 없으면 defaultEmailEnabled), 상태가 ACTIVE나 SUSPENDED이고 이메일이 있는 알림이다.
     * 이메일 인증을 마치지 않은 회원(UNVERIFIED, 인증 전에 정지된 회원 포함)은 주소를 확인하지 않았으므로 보내지 않고, 탈퇴 회원은 이메일이 지워져 있다.
     * dedupKeys가 비어 있으면 호출하지 않는다. 읽기만 하므로 트랜잭션 없이 불러도 된다.
     */
    List<NotificationMailRow> selectMailTargets(
            @Param("dedupKeys") Collection<String> dedupKeys,
            @Param("defaultEmailEnabled") boolean defaultEmailEnabled);
}
