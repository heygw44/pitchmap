package com.pitchmap.common.testsupport;

import javax.sql.DataSource;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * JUnit이 테스트가 끝날 때마다 이 확장을 실행해서 DB를 비운다.
 * 테스트가 실패해도 실행되므로, 다음 테스트는 항상 깨끗한 DB에서 시작한다.
 */
public class DatabaseCleanupExtension implements AfterEachCallback {

    @Override
    public void afterEach(ExtensionContext context) {
        DataSource dataSource = SpringExtension.getApplicationContext(context).getBean(DataSource.class);
        new DatabaseCleaner(dataSource).clean();
    }
}
