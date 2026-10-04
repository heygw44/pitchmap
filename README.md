# 피치맵 (pitchmap)

백패커가 합법적인 박지를 찾고 믿을 수 있는 동행과 함께 가는 서비스.

## 로컬 개발 환경

### 준비물

- Docker (Docker Desktop, OrbStack 등)
- JDK는 설치하지 않아도 된다. Gradle 툴체인이 JDK 25를 내려받는다.

### 실행 순서

1. `cp .env.example .env` 후 `DB_PASSWORD`와 `DB_ROOT_PASSWORD`를 채운다. `.env`는 커밋되지 않는다.
2. `docker compose up -d` 후 `docker compose ps`에서 mysql이 healthy가 될 때까지 기다린다.
3. `./gradlew bootRun`. 기본 프로필이 `local`이라 같은 `.env`를 읽어 localhost의 MySQL에 연결한다.
4. 확인
   - `curl http://localhost:8080/actuator/health` 응답의 `status`가 `UP`이다(DB 연결 포함). 예: `{"groups":["liveness","readiness"],"status":"UP"}`. MySQL을 멈추면 503 `DOWN`이 된다.
   - Swagger UI: http://localhost:8080/swagger-ui/index.html
   - 앱이 시작될 때 Flyway가 `src/main/resources/db/migration`의 마이그레이션을 적용한다. 테이블은 `docker compose exec mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" pitchmap -e "SHOW TABLES"'`로 확인한다.

### 종료

`docker compose down`. 데이터까지 지우려면 내린 뒤 `mysql-data/` 디렉터리를 삭제한다.

### 프로필

| 프로필 | 용도 | DB |
|---|---|---|
| `local` (기본) | 로컬 실행 | `.env` 값으로 localhost의 compose MySQL에 연결 |
| `test` | 자동 테스트 | 테스트 컨테이너가 DB를 제공 |
| `prod` | 운영 | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 환경 변수 필수 |

### 자동 테스트

- `./gradlew test`: 단위·웹 계층·아키텍처(ArchUnit) 테스트. Docker가 필요 없다.
- `./gradlew integrationTest`: JUnit 태그 `integration` 테스트. Testcontainers가 MySQL 8.4 컨테이너를 실행당 하나만 띄워 모든 테스트가 나눠 쓰고, 테스트 하나가 끝날 때마다 테이블을 비운다. Docker가 필요하다. 테스트는 로컬 `.env`와 compose DB를 쓰지 않는다.
- `./gradlew check`: 위 둘과 Spotless 검사, JaCoCo 리포트.

`integrationTest`와 `check`를 실행하기 전에 Docker가 켜져 있어야 한다.

실행 후 리포트는 `build/reports/` 아래에 생긴다.

- 테스트 결과: `tests/test/index.html`, `tests/integrationTest/index.html`
- 커버리지(JaCoCo): `jacoco/test/html/index.html`. `test`와 `integrationTest`를 합친 참고용 리포트이고 기준선은 없다.

### CI

PR과 `develop`·`main` 푸시마다 GitHub Actions(`.github/workflows/ci.yml`, 작업 이름 `check`)가 `./gradlew check`를 실행한다. 단위·아키텍처·통합 테스트와 Spotless, JaCoCo가 모두 들어 있다. 실행 화면의 Artifacts에서 `test-reports`(테스트 HTML 리포트와 JUnit XML)와 `jacoco-report`를 내려받을 수 있고, 테스트가 실패해도 올라간다.

### 문제 해결

- 3306 포트가 이미 쓰이면 `.env`의 `DB_PORT`를 바꾼다.
- 비밀번호를 바꿨다면 `mysql-data/`를 지우고 다시 띄운다. MySQL 초기 비밀번호는 데이터 디렉터리를 처음 만들 때만 적용된다.
- 앱이 `Access denied`로 시작하지 못하면 `.env`의 `DB_PASSWORD`가 비어 있거나 `mysql-data/`를 처음 만들 때 쓴 값과 다른지 확인한다.
