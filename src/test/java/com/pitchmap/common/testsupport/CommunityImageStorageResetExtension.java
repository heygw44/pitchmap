package com.pitchmap.common.testsupport;

import com.pitchmap.community.infra.TestCommunityImageStorage;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/** 테스트가 끝날 때마다 테스트용 이미지 저장소를 처음 상태로 되돌린다. 컨텍스트를 테스트끼리 공유하기 때문이다. */
public class CommunityImageStorageResetExtension implements AfterEachCallback {

    @Override
    public void afterEach(ExtensionContext context) {
        SpringExtension.getApplicationContext(context)
                .getBean(TestCommunityImageStorage.class)
                .reset();
    }
}
