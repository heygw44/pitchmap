-- 비밀번호를 바꾼 시각이다. 로그인 잠금 계산은 이 시각 이전의 실패를 세지 않는다.
-- 재설정으로 비밀번호를 바꾼 사람이 이전 비밀번호로 쌓인 실패 때문에 잠긴 채 남지 않게 하되,
-- 접속 기록(login_history)은 보관 대상이라 지우거나 고치지 않고 이 시각으로만 가른다.
-- 비밀번호를 바꾼 적이 없는 회원은 NULL이다.
ALTER TABLE member ADD COLUMN password_changed_at DATETIME(6) NULL AFTER password_hash;

-- 비밀번호 재설정 요청 횟수를 키 하나(이메일 또는 IP)마다 한 행으로 센다.
-- throttle_key는 원문 이메일·IP가 아니라 접두사를 붙여 SHA-256으로 해시한 값이다.
-- 접두사가 다르면 같은 문자열도 다른 키가 되고, 이 테이블이 새어도 이메일과 IP를 알 수 없다.
-- 가입 여부와 상관없이 입력한 이메일마다 행이 생겨야 하므로 member와 FK를 걸지 않는다.
--
-- window_start는 첫 요청 시각이고 구간은 거기서부터 고정 길이다. 구간이 끝난 뒤의 첫 요청이 새 구간을 연다.
-- last_request_at은 요청 사이 최소 간격을 판정하는 기준이고, 아직 요청이 없는 행은 NULL이다.
-- 앱은 이 행을 잠그고(FOR UPDATE) 판정하므로 같은 키의 동시 요청이 한 번씩 순서대로 처리된다.
--
-- 마지막 요청이 오래된 행은 쓸모가 없어서 정리 작업이 지운다. 그 작업이 이 칼럼으로 지울 행을 찾는다.
CREATE TABLE password_reset_throttle (
    throttle_key CHAR(64) NOT NULL,
    window_start DATETIME(6) NOT NULL,
    request_count INT NOT NULL,
    last_request_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (throttle_key),
    INDEX idx_password_reset_throttle_last_request_at (last_request_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
