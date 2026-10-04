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

### 종료

`docker compose down`. 데이터까지 지우려면 내린 뒤 `mysql-data/` 디렉터리를 삭제한다.

### 프로필

| 프로필 | 용도 | DB |
|---|---|---|
| `local` (기본) | 로컬 실행 | `.env` 값으로 localhost의 compose MySQL에 연결 |
| `test` | 자동 테스트 | 테스트 컨테이너가 DB를 제공 |
| `prod` | 운영 | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 환경 변수 필수 |

### 문제 해결

- 3306 포트가 이미 쓰이면 `.env`의 `DB_PORT`를 바꾼다.
- 비밀번호를 바꿨다면 `mysql-data/`를 지우고 다시 띄운다. MySQL 초기 비밀번호는 데이터 디렉터리를 처음 만들 때만 적용된다.
- 앱은 뜨는데 health가 `DOWN`이면 `.env`의 `DB_PASSWORD`가 비어 있거나 `mysql-data/`를 처음 만들 때 쓴 값과 다른지 확인한다.
