package com.pitchmap.member.domain;

import java.util.Optional;

public interface MemberRepository {

    Member save(Member member);

    Optional<Member> findById(Long id);

    /** 호출하면 그 회원의 행을 쓰기 잠금으로 읽는다. 잠금은 호출한 트랜잭션이 끝날 때까지 유지되어 같은 회원의 동시 요청을 한 줄로 세운다. */
    Optional<Member> findByIdForUpdate(long id);

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    /** 호출하면 그 회원을 빼고 같은 닉네임을 쓰는 회원이 있는지 본다. 비교는 DB 정렬 규칙을 따르므로 대소문자를 가리지 않는다. */
    boolean existsByNicknameAndIdNot(String nickname, Long id);

    /** 호출하면 지금까지 바꾼 엔티티 내용을 바로 DB에 쓴다. 그래서 유니크 제약 위반이 커밋 전에 이 호출에서 드러난다. */
    void flush();
}
