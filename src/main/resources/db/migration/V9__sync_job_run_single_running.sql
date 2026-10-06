-- 같은 종류의 작업은 한 번에 하나만 실행한다. 이 규칙을 지키려고 DB가 종류마다 RUNNING 실행 기록을 하나만 받게 한다.
-- running_job_type은 RUNNING인 행에서만 job_type 값을 갖고 나머지 행에서는 NULL이다. MySQL은 유니크 인덱스에 NULL이 여러 개
-- 있어도 허용하므로, 끝난 실행 기록은 몇 개든 남고 RUNNING만 종류마다 하나로 제한된다.
-- 두 요청이 동시에 같은 종류를 시작하면 늦게 넣은 쪽이 이 유니크 제약 위반으로 실패하고, 서버는 그 요청을 "이미 실행 중"으로 처리한다.
-- 직전 기록을 잠금 읽기(SELECT ... FOR UPDATE)로 읽던 방식은 실행 기록이 없을 때 두 트랜잭션이 같은 간격(gap)을 잠근 뒤
-- 서로의 INSERT를 기다려 교착 상태가 날 수 있다. 그래서 서버는 잠금 읽기를 쓰지 않고 이 제약에 맡긴다.
ALTER TABLE sync_job_run
    ADD COLUMN running_job_type VARCHAR(30)
        GENERATED ALWAYS AS (IF(status = 'RUNNING', job_type, NULL)) STORED AFTER status,
    ADD CONSTRAINT uk_sync_job_run_running UNIQUE (running_job_type);
