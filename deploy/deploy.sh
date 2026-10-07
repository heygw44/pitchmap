#!/usr/bin/env bash
# 운영 서버에서 이미지 태그 하나를 배포한다. 배포 워크플로가 SSM Run Command로 root 권한으로 실행한다.
#
# 사용법: deploy.sh <이미지 태그(커밋 SHA)>
# 종료 코드: 0 성공 / 1 실패(되돌릴 태그가 없거나 되돌리기도 실패) / 2 실패했지만 직전에 성공한 태그로 되돌림
#
# 이 스크립트의 출력은 GitHub Actions 로그에 그대로 남고, 공개 저장소라 누구나 볼 수 있다.
# 그래서 비밀값을 출력하지 않고, 실패해도 앱 로그를 출력하지 않는다. Spring은 설정 바인딩에 실패하면
# 오류 메시지에 설정 값을 그대로 찍을 수 있다. 앱 로그는 서버에서 docker compose logs app으로 본다.
set -euo pipefail

# snap으로 설치한 AWS CLI는 /snap/bin에 있다. SSM이 여는 셸의 PATH에는 이 경로가 없을 수 있다.
export PATH="$PATH:/snap/bin"

readonly DEPLOY_DIR=/opt/pitchmap
readonly COMPOSE_FILE="$DEPLOY_DIR/docker-compose.prod.yml"
readonly ENV_FILE="$DEPLOY_DIR/.env"
readonly SERVER_ENV_FILE="$DEPLOY_DIR/server.env"
readonly LAST_TAG_FILE="$DEPLOY_DIR/last-successful-tag"
readonly REGION=ap-northeast-2
readonly PARAM_PREFIX=/pitchmap/prod
readonly APP_REPO=ghcr.io/heygw44/pitchmap-app
readonly CADDY_REPO=ghcr.io/heygw44/pitchmap-caddy
readonly HEALTH_URL=http://127.0.0.1:8080/actuator/health
# 앱은 시작할 때 Flyway 마이그레이션을 실행하므로, 처음 배포하거나 마이그레이션이 많으면 시간이 걸린다.
readonly HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-240}"

# Parameter Store 이름(접두사 뒤) → .env 변수 이름
declare -rA SECRET_PARAMS=(
    [db/password]=DB_PASSWORD
    [db/root-password]=DB_ROOT_PASSWORD
    [publicdata/service-key]=PUBLICDATA_SERVICE_KEY
    [mail/app-password]=MAIL_APP_PASSWORD
    [identity/ci-hmac-key]=CI_HMAC_KEY
    [db/exporter-password]=MYSQL_EXPORTER_PASSWORD
    [grafana/token]=GRAFANA_CLOUD_TOKEN
)
readonly EXPORTER_USER=pitchmap_exporter
readonly BACKUP_DIR="$DEPLOY_DIR/backup"
readonly BACKUP_METRICS_FILE="$DEPLOY_DIR/metrics/backup.prom"
readonly SYSTEMD_DIR=/etc/systemd/system

declare -A secrets=()
declare -A server_env=()

log() {
    echo "[deploy $(date -u +%H:%M:%S)] $*"
}

compose() {
    docker compose --project-directory "$DEPLOY_DIR" -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"
}

# .env는 값을 작은따옴표로 감싸 쓴다. Compose는 작은따옴표 안의 $를 변수로 해석하지 않는다.
# 대신 값에 작은따옴표나 줄바꿈이 있으면 파일이 깨지므로 거부한다.
require_safe_value() {
    local name="$1" value="$2"
    if [[ "$value" == *"'"* || "$value" == *$'\n'* ]]; then
        log "$name 값에 작은따옴표나 줄바꿈이 있어 .env에 쓸 수 없다"
        exit 1
    fi
}

check_architecture() {
    local arch
    arch="$(uname -m)"
    if [[ "$arch" != "aarch64" ]]; then
        log "서버 아키텍처가 ${arch}다. 이미지는 arm64로만 빌드한다"
        exit 1
    fi
}

load_server_env() {
    if [[ ! -f "$SERVER_ENV_FILE" ]]; then
        log "$SERVER_ENV_FILE 이 없다. deploy/server.env.example을 보고 만든다"
        exit 1
    fi
    local line key value
    while IFS= read -r line || [[ -n "$line" ]]; do
        [[ -z "${line//[[:space:]]/}" || "$line" == \#* ]] && continue
        key="${line%%=*}"
        value="${line#*=}"
        if [[ ! "$key" =~ ^[A-Z_][A-Z0-9_]*$ || "$line" != *=* ]]; then
            log "server.env에 형식이 맞지 않는 줄이 있다: 변수 이름=값 형식으로 쓴다"
            exit 1
        fi
        require_safe_value "$key" "$value"
        server_env["$key"]="$value"
    done < "$SERVER_ENV_FILE"
    if [[ -z "${server_env[SITE_ADDRESS]:-}" ]]; then
        log "server.env에 SITE_ADDRESS가 없다"
        exit 1
    fi
}

load_secrets() {
    local param name value
    for param in "${!SECRET_PARAMS[@]}"; do
        name="${SECRET_PARAMS[$param]}"
        if ! value="$(aws ssm get-parameter --region "$REGION" --name "$PARAM_PREFIX/$param" \
            --with-decryption --query Parameter.Value --output text)"; then
            log "Parameter Store에서 $PARAM_PREFIX/$param 을 읽지 못했다"
            exit 1
        fi
        require_safe_value "$name" "$value"
        secrets["$name"]="$value"
    done
}

# 태그에 맞는 이미지 이름과 비밀값, 서버 설정을 합쳐 .env를 새로 쓴다.
# 임시 파일에 다 쓴 뒤 이름을 바꿔서, 쓰다가 멈춰도 반쯤 쓴 .env가 남지 않게 한다.
write_env() {
    local tag="$1" tmp key
    tmp="$(umask 077 && mktemp "$DEPLOY_DIR/.env.XXXXXX")"
    {
        echo "# deploy.sh가 배포할 때마다 새로 쓴다. 직접 고치지 않는다."
        echo "APP_IMAGE='$APP_REPO:$tag'"
        echo "CADDY_IMAGE='$CADDY_REPO:$tag'"
        for key in "${!server_env[@]}"; do
            printf "%s='%s'\n" "$key" "${server_env[$key]}"
        done
        for key in "${!secrets[@]}"; do
            printf "%s='%s'\n" "$key" "${secrets[$key]}"
        done
    } > "$tmp"
    chmod 600 "$tmp"
    mv "$tmp" "$ENV_FILE"
}

check_image_architecture() {
    local tag="$1" image arch
    for image in "$APP_REPO:$tag" "$CADDY_REPO:$tag"; do
        if ! arch="$(docker image inspect --format '{{.Architecture}}' "$image" 2>/dev/null)"; then
            log "$image 이미지가 서버에 없다"
            return 1
        fi
        if [[ "$arch" != "arm64" ]]; then
            log "$image 의 아키텍처가 ${arch}다"
            return 1
        fi
    done
}

# 앱의 상태 확인 주소가 200을 줄 때까지 기다린 뒤, caddy가 HTTP 요청에 응답하는지 본다.
# caddy는 80번 요청에 HTTPS로 넘기는 응답(308)을 준다. 인증서 발급은 Let's Encrypt 상황에 따라 늦어질 수 있어서 HTTPS는 확인하지 않는다.
wait_healthy() {
    local deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS)) code
    until curl -fsS -o /dev/null --max-time 5 "$HEALTH_URL" 2>/dev/null; do
        if ((SECONDS >= deadline)); then
            log "앱이 ${HEALTH_TIMEOUT_SECONDS}초 안에 정상 상태가 되지 않았다"
            return 1
        fi
        sleep 5
    done
    log "앱 상태 확인 통과"
    until code="$(curl -s -o /dev/null --max-time 5 -w '%{http_code}' \
        -H "Host: ${server_env[SITE_ADDRESS]}" http://127.0.0.1/)" && [[ "$code" =~ ^[23] ]]; do
        if ((SECONDS >= deadline)); then
            log "caddy가 응답하지 않는다"
            return 1
        fi
        sleep 2
    done
    log "caddy 응답 확인 통과"
}

# if 조건 안에서 부르면 set -e가 꺼지므로, 단계마다 &&로 이어 앞 단계가 실패하면 바로 실패를 돌려준다.
start() {
    local tag="$1"
    write_env "$tag" && compose up -d --remove-orphans && wait_healthy
}

# Alloy의 MySQL 수집기가 쓰는 계정을 만들고 비밀번호를 Parameter Store 값에 맞춘다. 배포할 때마다 실행해도 결과가 같다.
# 권한은 서버 상태(PROCESS, REPLICATION CLIENT)와 performance_schema 읽기뿐이라 앱 데이터는 읽지 못한다.
# root 비밀번호는 명령 인자가 아니라 MYSQL_PWD 환경 변수로, SQL은 표준 입력으로 넘겨서 프로세스 목록에 남지 않게 한다.
# 실패해도 서비스는 정상이고 MySQL 지표만 빠지므로, 배포를 실패로 만들지 않고 로그만 남긴다.
ensure_exporter_user() {
    if MYSQL_PWD="${secrets[DB_ROOT_PASSWORD]}" compose exec -T -e MYSQL_PWD mysql mysql -uroot --batch > /dev/null <<SQL
CREATE USER IF NOT EXISTS '$EXPORTER_USER'@'%' IDENTIFIED BY '${secrets[MYSQL_EXPORTER_PASSWORD]}' WITH MAX_USER_CONNECTIONS 3;
ALTER USER '$EXPORTER_USER'@'%' IDENTIFIED BY '${secrets[MYSQL_EXPORTER_PASSWORD]}' WITH MAX_USER_CONNECTIONS 3;
GRANT PROCESS, REPLICATION CLIENT ON *.* TO '$EXPORTER_USER'@'%';
GRANT SELECT ON performance_schema.* TO '$EXPORTER_USER'@'%';
SQL
    then
        log "MySQL 수집용 계정 확인 완료"
    else
        log "MySQL 수집용 계정을 맞추지 못했다. 서비스는 정상이고 MySQL 지표만 빠진다"
    fi
}

# 매일 DB를 백업하는 systemd 타이머를 설치하거나 갱신한다. 파일은 배포 워크플로가 이 커밋의 deploy/backup/에서 받아 둔다.
# 백업 결과 지표가 아직 없으면(처음 설치) 백업을 바로 한 번 실행해서, 다음 날 새벽까지 '백업 없음' 알림이 울리지 않게 한다.
# 실패해도 서비스는 정상이므로 배포를 실패로 만들지 않고 로그만 남긴다. 백업이 멈추면 Grafana 알림이 따로 알린다.
install_backup_timer() {
    if [[ -z "${server_env[BACKUP_BUCKET]:-}" ]]; then
        log "server.env에 BACKUP_BUCKET이 없어 백업 타이머를 설치하지 않는다"
        return
    fi
    if ! install -m 644 "$BACKUP_DIR/pitchmap-backup.service" "$BACKUP_DIR/pitchmap-backup.timer" "$SYSTEMD_DIR/" \
        || ! systemctl daemon-reload \
        || ! systemctl enable --now pitchmap-backup.timer > /dev/null 2>&1; then
        log "백업 타이머를 설치하지 못했다"
        return
    fi
    log "백업 타이머 확인 완료: $(systemctl show pitchmap-backup.timer -p NextElapseUSecRealtime --value)"
    if [[ ! -f "$BACKUP_METRICS_FILE" ]]; then
        systemctl start --no-block pitchmap-backup.service
        log "첫 백업을 시작했다. 결과는 journalctl -u pitchmap-backup 으로 본다"
    fi
}

main() {
    local tag="${1:-}"
    if [[ ! "$tag" =~ ^[A-Za-z0-9._-]{1,128}$ ]]; then
        log "사용법: deploy.sh <이미지 태그>"
        exit 1
    fi

    check_architecture
    load_server_env
    load_secrets

    local previous=""
    [[ -f "$LAST_TAG_FILE" ]] && previous="$(<"$LAST_TAG_FILE")"
    log "배포 시작: $tag (직전에 성공한 태그: ${previous:-없음})"

    write_env "$tag"
    if ! compose pull --quiet || ! check_image_architecture "$tag"; then
        log "이미지를 받지 못했거나 아키텍처가 맞지 않는다. 실행 중인 컨테이너는 그대로 둔다"
        [[ -n "$previous" ]] && write_env "$previous"
        exit 1
    fi

    if start "$tag"; then
        echo "$tag" > "$LAST_TAG_FILE"
        ensure_exporter_user
        install_backup_timer
        # 사흘 넘게 쓰지 않은 이미지를 지워 디스크를 확보한다. 되돌릴 이미지가 지워져도 GHCR에서 다시 받는다.
        docker image prune -af --filter "until=72h" > /dev/null
        log "배포 성공: $tag"
        exit 0
    fi

    compose ps
    if [[ -z "$previous" || "$previous" == "$tag" ]]; then
        log "배포 실패. 되돌릴 태그가 없다"
        exit 1
    fi

    log "배포 실패. 직전에 성공한 태그 $previous 로 되돌린다"
    if start "$previous"; then
        log "되돌리기 성공: $previous"
        exit 2
    fi
    compose ps
    log "되돌리기도 실패했다. 서버에서 docker compose logs로 원인을 확인한다"
    exit 1
}

main "$@"
