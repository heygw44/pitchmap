package com.pitchmap.common.testsupport;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TestCodeConventionTest {

    private static final String SPRING_SECURITY_TEST_POST_PROCESSORS =
            "org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors";

    @Test
    @DisplayName("테스트 코드는 spring-security-test의 csrf()를 부르지 않고 TestCsrf.csrf()를 쓴다")
    void testsUseTestCsrfInsteadOfSpringSecurityCsrf() {
        JavaClasses testClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.OnlyIncludeTests())
                .importPackages("com.pitchmap");

        noClasses()
                .should()
                .callMethodWhere(describe(
                        "spring-security-test의 csrf()",
                        (JavaCall<?> call) ->
                                call.getTargetOwner().getName().equals(SPRING_SECURITY_TEST_POST_PROCESSORS)
                                        && call.getTarget().getName().equals("csrf")))
                .because("csrf()는 공유 CsrfFilter의 토큰 저장소를 바꿔 다른 테스트의 XSRF-TOKEN 쿠키를 없앤다. TestCsrf.csrf()를 쓴다")
                .check(testClasses);
    }
}
