#!/usr/bin/env bash
# Grafana Cloud에 피치맵 대시보드, 알림 연락처, 알림 정책, 알림 규칙을 올린다. 개발자가 내 컴퓨터에서 실행한다.
# 같은 uid로 덮어쓰므로 여러 번 실행해도 결과가 같다.
#
# 필요한 환경 변수
#   GRAFANA_URL       스택 주소. 예: https://pitchmap.grafana.net
#   GRAFANA_SA_TOKEN  Grafana 서비스 계정 토큰(Editor 이상). 저장소나 셸 기록에 남기지 않는다
#   ALERT_EMAIL       알림을 받을 이메일 주소
# 선택: PROM_UID, LOKI_UID  데이터 소스 uid. 비우면 이름이 -prom, -logs로 끝나는 Grafana Cloud 기본 데이터 소스를 찾는다
#
# 필요한 도구: curl, jq
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly SCRIPT_DIR
readonly FOLDER_UID=pitchmap
readonly FOLDER_TITLE="피치맵"
readonly CONTACT_POINT_UID=pitchmap-email
readonly CONTACT_POINT_NAME=pitchmap-email
readonly RULE_GROUP=pitchmap

: "${GRAFANA_URL:?GRAFANA_URL이 필요하다}"
: "${GRAFANA_SA_TOKEN:?GRAFANA_SA_TOKEN이 필요하다}"
: "${ALERT_EMAIL:?ALERT_EMAIL이 필요하다}"

log() {
    echo "[provision] $*"
}

RESPONSE_FILE="$(mktemp)"
readonly RESPONSE_FILE
trap 'rm -f "$RESPONSE_FILE"' EXIT

# 토큰이 명령 인자(프로세스 목록)에 드러나지 않게, 인증 헤더는 curl 설정으로 넘긴다.
curl_auth() {
    curl -K <(printf 'header = "Authorization: Bearer %s"\n' "$GRAFANA_SA_TOKEN") "$@"
}

# 호출하면 HTTP 상태 코드를 출력하고, 응답 본문은 RESPONSE_FILE에 둔다.
# X-Disable-Provenance를 붙여서, 이 스크립트로 올린 알림 설정도 Grafana 화면에서 고칠 수 있게 한다.
api() {
    local method="$1" path="$2" body="${3:-}"
    local args=(-sS -o "$RESPONSE_FILE" -w '%{http_code}' -X "$method" "${GRAFANA_URL%/}$path"
        -H "Content-Type: application/json" -H "X-Disable-Provenance: true")
    if [[ -n "$body" ]]; then
        args+=(--data-binary "$body")
    fi
    curl_auth "${args[@]}"
}

# 응답 본문이 필요한 조회용. 실패하면 멈춘다.
api_get() {
    curl_auth -fsS "${GRAFANA_URL%/}$1"
}

require_ok() {
    local code="$1" what="$2"
    if [[ ! "$code" =~ ^2 ]]; then
        log "$what 실패: HTTP $code $(head -c 500 "$RESPONSE_FILE")"
        exit 1
    fi
    log "$what 완료"
}

find_datasource_uid() {
    local type="$1" suffix="$2"
    api_get /api/datasources | jq -r --arg type "$type" --arg suffix "$suffix" \
        '[.[] | select(.type == $type and (.name | endswith($suffix)))][0].uid // empty'
}

PROM_UID="${PROM_UID:-$(find_datasource_uid prometheus -prom)}"
LOKI_UID="${LOKI_UID:-$(find_datasource_uid loki -logs)}"
if [[ -z "$PROM_UID" || -z "$LOKI_UID" ]]; then
    log "Prometheus나 Loki 데이터 소스를 찾지 못했다. PROM_UID, LOKI_UID를 직접 넣는다"
    exit 1
fi
log "데이터 소스: prometheus=$PROM_UID loki=$LOKI_UID"

# 1. 폴더
if api_get "/api/folders/$FOLDER_UID" > /dev/null 2>&1; then
    log "폴더 $FOLDER_TITLE 있음"
else
    require_ok "$(api POST /api/folders "$(jq -n --arg uid "$FOLDER_UID" --arg title "$FOLDER_TITLE" '{uid: $uid, title: $title}')")" "폴더 만들기"
fi

# 2. 대시보드
dashboard="$(jq --arg prom "$PROM_UID" --arg loki "$LOKI_UID" \
    'walk(if . == "__PROM_UID__" then $prom elif . == "__LOKI_UID__" then $loki else . end)' \
    "$SCRIPT_DIR/dashboard-pitchmap.json")"
require_ok "$(api POST /api/dashboards/db \
    "$(jq -n --argjson dashboard "$dashboard" --arg folder "$FOLDER_UID" '{dashboard: $dashboard, folderUid: $folder, overwrite: true}')")" \
    "대시보드 올리기"

# 3. 알림 연락처(이메일). 있으면 고치고, 없으면 만든다.
contact="$(jq -n --arg uid "$CONTACT_POINT_UID" --arg name "$CONTACT_POINT_NAME" --arg email "$ALERT_EMAIL" \
    '{uid: $uid, name: $name, type: "email", settings: {addresses: $email}, disableResolveMessage: false}')"
code="$(api PUT "/api/v1/provisioning/contact-points/$CONTACT_POINT_UID" "$contact")"
if [[ ! "$code" =~ ^2 ]]; then
    code="$(api POST /api/v1/provisioning/contact-points "$contact")"
fi
require_ok "$code" "알림 연락처 설정"

# 4. 알림 정책: 기본 수신처를 위 연락처로 바꾼다. 하위 경로는 그대로 둔다.
policies="$(api_get /api/v1/provisioning/policies | jq --arg receiver "$CONTACT_POINT_NAME" \
    '.receiver = $receiver | .group_by = ["grafana_folder", "alertname"]')"
require_ok "$(api PUT /api/v1/provisioning/policies "$policies")" "알림 정책 설정"

# 5. 알림 규칙 그룹. 그룹 전체를 같은 내용으로 덮어쓴다.
rules="$(jq --arg prom "$PROM_UID" --arg folder "$FOLDER_UID" --arg group "$RULE_GROUP" \
    'walk(if . == "__PROM_UID__" then $prom else . end)
     | .rules |= map(. + {folderUID: $folder, ruleGroup: $group, orgID: 1})' \
    "$SCRIPT_DIR/alert-rules.json")"
require_ok "$(api PUT "/api/v1/provisioning/folder/$FOLDER_UID/rule-groups/$RULE_GROUP" "$rules")" "알림 규칙 올리기"

log "끝. Grafana 화면의 Alerting > Contact points에서 ${CONTACT_POINT_NAME}의 Test를 눌러 테스트 메일을 받는다"
