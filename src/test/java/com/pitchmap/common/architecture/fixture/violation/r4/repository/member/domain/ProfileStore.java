package com.pitchmap.common.architecture.fixture.violation.r4.repository.member.domain;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/** Spring Data 리포지토리 스캔이 빈으로 등록하지 않도록 {@code @NoRepositoryBean}을 붙인다. R4가 보는 것은 Repository 하위 타입이라는 사실뿐이다. */
@NoRepositoryBean
public interface ProfileStore extends Repository<Object, Long> {}
