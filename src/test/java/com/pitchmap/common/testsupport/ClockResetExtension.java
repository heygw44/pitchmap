package com.pitchmap.common.testsupport;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/** 컨텍스트가 테스트 사이에 공유되므로, 시간을 옮긴 테스트가 다음 테스트에 영향을 주지 않게 시계를 되돌린다. */
public class ClockResetExtension implements AfterEachCallback {

    @Override
    public void afterEach(ExtensionContext context) {
        SpringExtension.getApplicationContext(context)
                .getBean(MutableClock.class)
                .reset();
    }
}
