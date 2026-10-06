plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.10.3"
}

group = "com.pitchmap"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-mysql")
    implementation("org.hibernate.orm:hibernate-spatial")
    implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:4.1.0")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-session-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-mail")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    runtimeOnly("com.mysql:mysql-connector-j")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-mysql")
    testImplementation("com.tngtech.archunit:archunit:1.5.1")
    testImplementation("org.wiremock:wiremock-standalone:3.13.2")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

val integrationTest by tasks.registering(Test::class) {
    description = "Testcontainers가 필요한 통합 테스트를 실행한다. Docker가 필요하다."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("integration")
    }
    // 우리는 UTC가 아닌 JVM에서도 시각이 UTC로 저장되는지 확인하려고, 일부러 한국 표준시(KST)로 JVM을 띄운다.
    jvmArgs("-Duser.timezone=Asia/Seoul")
    shouldRunAfter(tasks.test)
}

// 운영 이미지(Dockerfile)는 build/libs에 있는 jar 하나를 가져간다. 그래서 실행할 수 없는 plain jar는 만들지 않는다.
tasks.jar {
    enabled = false
}

jacoco {
    // JaCoCo는 Java 25 클래스 파일을 0.8.14부터 정식 지원한다.
    toolVersion = "0.8.15"
}

tasks.jacocoTestReport {
    // 단위 테스트와 통합 테스트의 실행 정보를 한 리포트로 합친다. 참고용이라 우리는 기준선을 두지 않는다.
    dependsOn(tasks.test, integrationTest)
    executionData(tasks.test.get(), integrationTest.get())
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.check {
    dependsOn(integrationTest, tasks.jacocoTestReport)
}

spotless {
    java {
        target("src/**/*.java")
        palantirJavaFormat("2.101.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
