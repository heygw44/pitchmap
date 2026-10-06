package com.pitchmap.common.architecture.fixture.violation.r4.repository.member.domain;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * Spring Data 리포지토리 스캔이 이 인터페이스를 빈으로 등록하지 않도록 {@code @NoRepositoryBean}을 붙인다.
 * 규칙 R4는 Repository의 하위 타입이라는 사실만 검사한다.
 */
@NoRepositoryBean
public interface ProfileStore extends Repository<Object, Long> {}
