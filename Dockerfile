# 운영용 앱 이미지다. jar는 CI가 ./gradlew bootJar로 먼저 만들고, 이 파일은 그 jar를 받아 이미지로 묶기만 한다.
# 이미지 안에서 Gradle로 빌드하지 않는 이유: CI의 Gradle 캐시를 그대로 쓸 수 있고, 이미지에 JDK가 들어가지 않는다.

FROM eclipse-temurin:25-jre AS extract
WORKDIR /work
COPY build/libs/*.jar app.jar
# Spring Boot 도구 모드로 jar를 의존성, 로더, 스냅숏 의존성, 애플리케이션 층으로 나눈다.
# 코드만 바뀐 배포에서는 서버가 맨 아래 애플리케이션 층만 새로 받는다.
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

FROM eclipse-temurin:25-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=extract /work/extracted/dependencies/ ./
COPY --from=extract /work/extracted/spring-boot-loader/ ./
COPY --from=extract /work/extracted/snapshot-dependencies/ ./
COPY --from=extract /work/extracted/application/ ./
USER app
EXPOSE 8080
# 컨테이너 메모리 한도의 75%까지만 힙으로 쓴다. 나머지는 메타스페이스, 스레드 스택, 네이티브 메모리 몫이다.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
