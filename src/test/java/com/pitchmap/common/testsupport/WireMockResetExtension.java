package com.pitchmap.common.testsupport;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * JUnit이 테스트가 끝날 때마다 이 확장을 실행해서 공유 WireMock 서버의 스텁, 요청 기록, 시나리오 상태를 지운다.
 * 테스트가 실패해도 실행되므로, 다음 테스트는 항상 스텁이 없는 서버에서 시작한다.
 */
public class WireMockResetExtension implements AfterEachCallback {

    @Override
    public void afterEach(ExtensionContext context) {
        SpringExtension.getApplicationContext(context)
                .getBean(WireMockServer.class)
                .resetAll();
    }
}
