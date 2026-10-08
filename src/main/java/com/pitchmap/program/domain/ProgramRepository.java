package com.pitchmap.program.domain;

import java.util.Optional;

public interface ProgramRepository {

    Program saveAndFlush(Program program);

    Optional<Program> findById(Long id);

    /**
     * 호출하면 id인 행사를 쓰기 잠금으로 읽는다. 같은 행사를 바꾸려는 다른 트랜잭션은 이 트랜잭션이 끝날 때까지 기다린다.
     * 호출하는 트랜잭션 안에서 첫 조회여야 잠금을 얻은 뒤 다른 트랜잭션이 커밋한 내용이 보인다.
     */
    Optional<Program> findByIdForUpdate(Long id);
}
