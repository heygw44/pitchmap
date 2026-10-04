package com.pitchmap.member.domain;

import java.util.Optional;

public interface MemberRepository {

    Member save(Member member);

    Optional<Member> findById(Long id);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);
}
