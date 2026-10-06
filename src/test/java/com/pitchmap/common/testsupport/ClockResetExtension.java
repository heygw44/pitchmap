package com.pitchmap.common.testsupport;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * JUnit이 테스트가 끝날 때마다 이 확장을 실행해 시계를 되돌린다.
 * 컨텍스트를 테스트끼리 공유하므로, 시간을 옮긴 테스트가 다음 테스트에 영향을 주면 안 되기 때문이다.
 */
public class ClockResetExtension implements AfterEachCallback {

    @Override
    public void afterEach(ExtensionContext context) {
        SpringExtension.getApplicationContext(context)
                .getBean(MutableClock.class)
                .reset();
    }
}
