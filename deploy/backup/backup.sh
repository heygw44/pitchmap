#!/usr/bin/env bash
# 운영 MySQL을 덤프해 압축한 뒤 S3 백업 버킷에 올린다. systemd 타이머(pitchmap-backup.timer)가 매일 새벽 root로 실행한다.
#
# - 앱 계정으로 --single-transaction 덤프를 뜬다. InnoDB 테이블을 잠그지 않고 덤프를 시작한 시점의 일관된 상태를 읽는다.
# - 세션 테이블(SPRING_SESSION*)은 뺀다. 로그인 정보가 들어 있고, 복원한 뒤에는 회원이 다시 로그인하면 되기 때문이다.
# - 성공하면 마지막 성공 시각·크기·걸린 시간을 지표 파일로 남긴다. 수집 에이전트가 이 파일을 읽고,
#   Grafana 알림이 마지막 성공 시각이 26시간을 넘기면 알린다. 그래서 실패를 따로 알리지 않아도 백업이 멈추면 알림이 온다.
# - S3 버킷의 수명 주기 규칙이 7일 지난 백업을 지운다. 서버의 인스턴스 역할에는 삭제 권한을 주지 않는다.
set -euo pipefail

# snap으로 설치한 AWS CLI는 /snap/bin에 있다. systemd가 여는 환경의 PATH에는 이 경로가 없다.
export PATH="$PATH:/snap/bin"

readonly DEPLOY_DIR=/opt/pitchmap
# 환경 파일 경로와 Compose 프로젝트 이름은 기본값이 운영 값이다. 서버 밖에서 시험할 때만 환경 변수로 바꾼다.
readonly ENV_FILE="${PITCHMAP_ENV_FILE:-$DEPLOY_DIR/.env}"
readonly COMPOSE_PROJECT="${COMPOSE_PROJECT_NAME:-pitchmap}"
readonly METRICS_DIR="$DEPLOY_DIR/metrics"
readonly METRICS_FILE="$METRICS_DIR/backup.prom"
readonly REGION=ap-northeast-2
readonly S3_PREFIX=mysql

log() {
    echo "[backup $(date -u +%H:%M:%S)] $*"
}

# deploy.sh가 쓴 .env에서 값 하나를 읽는다. 값은 작은따옴표로 감싸여 있다. 파일을 source하지 않고 필요한 값만 꺼낸다.
read_env() {
    local key="$1" line
    line="$(grep -m1 "^${key}=" "$ENV_FILE" || true)"
    if [[ -z "$line" ]]; then
        log "$ENV_FILE 에 ${key}가 없다"
        exit 1
    fi
    line="${line#*=}"
    line="${line#\'}"
    echo "${line%\'}"
}

mysql_container() {
    docker ps -q \
        --filter "label=com.docker.compose.project=$COMPOSE_PROJECT" \
        --filter label=com.docker.compose.service=mysql
}

write_metrics() {
    local finished="$1" size="$2" duration="$3" tmp
    mkdir -p "$METRICS_DIR"
    tmp="$(mktemp "$METRICS_DIR/.backup.prom.XXXXXX")"
    cat > "$tmp" <<EOF
# HELP pitchmap_backup_last_success_timestamp_seconds 마지막으로 성공한 DB 백업의 완료 시각(유닉스 초)
# TYPE pitchmap_backup_last_success_timestamp_seconds gauge
pitchmap_backup_last_success_timestamp_seconds $finished
# HELP pitchmap_backup_last_size_bytes 마지막으로 성공한 DB 백업 파일(압축)의 크기
# TYPE pitchmap_backup_last_size_bytes gauge
pitchmap_backup_last_size_bytes $size
# HELP pitchmap_backup_last_duration_seconds 마지막으로 성공한 DB 백업에 걸린 시간
# TYPE pitchmap_backup_last_duration_seconds gauge
pitchmap_backup_last_duration_seconds $duration
EOF
    chmod 644 "$tmp"
    mv "$tmp" "$METRICS_FILE"
}

main() {
    local started db_name db_user db_password bucket container key size finished
    started="$(date -u +%s)"
    db_name="$(read_env DB_NAME)"
    db_user="$(read_env DB_USERNAME)"
    db_password="$(read_env DB_PASSWORD)"
    bucket="$(read_env BACKUP_BUCKET)"

    container="$(mysql_container)"
    if [[ -z "$container" ]]; then
        log "실행 중인 MySQL 컨테이너가 없다"
        exit 1
    fi

    # 임시 파일 경로는 전역 변수에 둔다. EXIT trap은 main이 끝난 뒤 실행되므로, 지역 변수를 쓰면 그때는 이미 사라져 있다.
    DUMP_FILE="$(mktemp /var/tmp/pitchmap-backup.XXXXXX)"
    trap 'rm -f "$DUMP_FILE"' EXIT
    key="$S3_PREFIX/${db_name}-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"

    log "덤프 시작: $db_name"
    # 비밀번호는 명령 인자가 아니라 MYSQL_PWD 환경 변수로 넘겨 프로세스 목록에 남지 않게 한다.
    MYSQL_PWD="$db_password" docker exec -e MYSQL_PWD "$container" \
        mysqldump -u"$db_user" --single-transaction --quick --no-tablespaces --set-gtid-purged=OFF \
        --ignore-table="$db_name.SPRING_SESSION" --ignore-table="$db_name.SPRING_SESSION_ATTRIBUTES" \
        "$db_name" | gzip -9 > "$DUMP_FILE"

    # 덤프가 중간에 끊기면 마지막 줄의 완료 표시가 없다. 압축 파일이 깨졌거나 표시가 없으면 올리지 않는다.
    gzip -t "$DUMP_FILE"
    if ! gzip -dc "$DUMP_FILE" | tail -n 1 | grep -q "^-- Dump completed"; then
        log "덤프가 끝까지 쓰이지 않았다"
        exit 1
    fi

    size="$(stat -c %s "$DUMP_FILE")"
    aws s3 cp "$DUMP_FILE" "s3://$bucket/$key" --region "$REGION" --only-show-errors
    finished="$(date -u +%s)"
    write_metrics "$finished" "$size" "$((finished - started))"
    log "백업 성공: s3://$bucket/$key (${size}바이트, $((finished - started))초)"
}

main "$@"
