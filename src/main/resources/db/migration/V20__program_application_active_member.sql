-- 행사마다 회원은 활성 신청(결제 대기, 확정)을 한 건만 가진다. 취소하거나 만료된 뒤에는 다시 신청할 수 있어서
-- (program_id, member_id)에 UNIQUE를 걸 수 없다. 대신 활성 신청일 때만 member_id 값을 갖는 생성 컬럼을 만들고 그 컬럼에 UNIQUE를 건다.
-- 취소나 만료로 status가 바뀌면 DB가 이 컬럼을 NULL로 다시 계산한다. MySQL의 UNIQUE는 NULL을 여러 개 허용하므로 지난 신청이 몇 건 남아 있어도
-- 재신청을 막지 않는다. JPA 엔티티는 이 컬럼을 매핑하지 않는다.
ALTER TABLE program_application
    ADD COLUMN active_member_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN status IN ('PENDING_PAYMENT', 'CONFIRMED') THEN member_id END) VIRTUAL,
    ADD CONSTRAINT uk_program_application_active_member UNIQUE (program_id, active_member_id);
