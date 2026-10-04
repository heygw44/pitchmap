package com.pitchmap.member.application;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// 트랜잭션 프록시가 적용되도록 우리는 리스너를 서비스와 다른 빈으로 분리했다.
// 이 리스너는 적재에 실패해도 예외를 삼키지 않고 기동을 중단한다.
@Component
@RequiredArgsConstructor
public class DisposableEmailDomainSeedRunner {

    private final DisposableEmailDomainSeedService seedService;

    @EventListener(ApplicationReadyEvent.class)
    public void seedOnStartup() {
        seedService.seedPublicDomains();
    }
}
