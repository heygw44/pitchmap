#!/usr/bin/env bash
# 복구 연습: S3의 백업 하나를 임시 MySQL 컨테이너에 복원해 보고, 단계별 걸린 시간과 주요 테이블 행 수를 출력한다.
# 운영 DB와 운영 컨테이너는 건드리지 않는다. 개발자가 서버에서 root로 직접 실행한다.
#
# 사용법: restore-drill.sh [S3 키]   (키를 주지 않으면 가장 최근 백업)
#
# 임시 컨테이너는 네트워크 없이 띄우고, 끝나면 볼륨과 함께 지운다. 메모리가 2GB인 서버라서 메모리 한도를 512MB로 둔다.
set -euo pipefail
export PATH="$PATH:/snap/bin"

readonly DEPLOY_DIR=/opt/pitchmap
# 환경 파일 경로는 기본값이 운영 값이다. 서버 밖에서 시험할 때만 환경 변수로 바꾼다.
readonly ENV_FILE="${PITCHMAP_ENV_FILE:-$DEPLOY_DIR/.env}"
readonly REGION=ap-northeast-2
readonly S3_PREFIX=mysql
readonly DRILL_CONTAINER=pitchmap-restore-drill
readonly DRILL_DB=restore_drill
readonly MYSQL_IMAGE=mysql:8.4
readonly CHECK_TABLES=(flyway_schema_history member spot public_spot_detail bakji_detail protected_area spot_review identity_verification outbox_event)

log() {
    echo "[restore-drill $(date -u +%H:%M:%S)] $*"
}

read_env() {
    local line
    line="$(grep -m1 "^$1=" "$ENV_FILE" || true)"
    line="${line#*=}"
    line="${line#\'}"
    echo "${line%\'}"
}

latest_key() {
    aws s3api list-objects-v2 --region "$REGION" --bucket "$1" --prefix "$S3_PREFIX/" \
        --query 'sort_by(Contents, &LastModified)[-1].Key' --output text
}

cleanup() {
    docker rm -f -v "$DRILL_CONTAINER" > /dev/null 2>&1 || true
    rm -f "${DUMP_FILE:-}"
}

drill_mysql() {
    docker exec -i -e MYSQL_PWD "$DRILL_CONTAINER" mysql -uroot --batch --skip-column-names "$@"
}

main() {
    local bucket key t0 t_download t_ready t_import root_password
    bucket="$(read_env BACKUP_BUCKET)"
    if [[ -z "$bucket" ]]; then
        log "$ENV_FILE 에 BACKUP_BUCKET이 없다"
        exit 1
    fi
    key="${1:-$(latest_key "$bucket")}"
    if [[ -z "$key" || "$key" == "None" ]]; then
        log "s3://$bucket/$S3_PREFIX/ 에 백업이 없다"
        exit 1
    fi

    trap cleanup EXIT
    cleanup
    DUMP_FILE="$(mktemp /var/tmp/pitchmap-restore.XXXXXX)"
    # 임시 컨테이너에서만 쓰는 일회용 root 비밀번호다. 운영 비밀번호와 관계없다.
    root_password="$(openssl rand -hex 16)"
    export MYSQL_PWD="$root_password"

    t0="$(date +%s)"
    log "1/3 내려받기: s3://$bucket/$key"
    aws s3 cp "s3://$bucket/$key" "$DUMP_FILE" --region "$REGION" --only-show-errors
    t_download="$(date +%s)"

    log "2/3 임시 MySQL 컨테이너 시작"
    docker run -d --name "$DRILL_CONTAINER" --network none --memory 512m \
        -e MYSQL_ROOT_PASSWORD="$root_password" -e MYSQL_DATABASE="$DRILL_DB" \
        "$MYSQL_IMAGE" --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci \
        --default-time-zone=+00:00 > /dev/null
    # 초기화 중인 임시 서버는 소켓만 열고 TCP는 막아 둔다. 그래서 TCP로 접속이 되면 준비가 끝난 것이다.
    until docker exec -e MYSQL_PWD "$DRILL_CONTAINER" mysqladmin ping -h 127.0.0.1 -uroot --silent > /dev/null 2>&1; do
        sleep 2
    done
    t_ready="$(date +%s)"

    log "3/3 복원"
    gzip -dc "$DUMP_FILE" | drill_mysql "$DRILL_DB"
    t_import="$(date +%s)"

    log "주요 테이블 행 수"
    local table
    for table in "${CHECK_TABLES[@]}"; do
        printf '  %-24s %s\n' "$table" "$(drill_mysql "$DRILL_DB" -e "SELECT COUNT(*) FROM \`$table\`" 2> /dev/null || echo '테이블 없음')"
    done
    log "마지막 마이그레이션: $(drill_mysql "$DRILL_DB" -e 'SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success = 1')"

    log "백업 크기: $(stat -c %s "$DUMP_FILE")바이트(압축)"
    log "걸린 시간: 내려받기 $((t_download - t0))초, 컨테이너 준비 $((t_ready - t_download))초, 복원 $((t_import - t_ready))초, 합계 $((t_import - t0))초"
}

main "$@"
