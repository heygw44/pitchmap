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
}
