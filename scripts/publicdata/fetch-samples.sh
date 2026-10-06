#!/usr/bin/env bash
# 이 스크립트는 공공데이터 API 응답 샘플을 받아 테스트 픽스처로 저장한다.
# 사용법: ./scripts/publicdata/fetch-samples.sh  (실행하면 공공데이터포털 Decoding 키를 묻는다)
# 스크립트는 키를 화면·셸 기록·저장 파일에 남기지 않는다. 또한 저장한 뒤 파일에 키가 들어 있는지 검사한다.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
FIXTURES="$ROOT/src/test/resources/fixtures"
BASE="https://apis.data.go.kr"

# 서울시청 근처. 기상청 격자(60, 127)와 같은 지역이다.
LAT=37.5665
LNG=126.9780
NX=60
NY=127

read -r -s -p "공공데이터포털 인증키(Decoding): " RAW_KEY
echo
# 터미널이 붙여넣기 때 섞는 제어 문자(NUL, ESC 등)를 지운다.
KEY=$(printf %s "$RAW_KEY" | LC_ALL=C tr -cd '[:alnum:]+/=')
unset RAW_KEY
if [ -z "$KEY" ]; then
  echo "키가 비어 있습니다." >&2
  exit 1
fi

kst() { TZ=Asia/Seoul date "$@"; }
kst_yesterday() {
  TZ=Asia/Seoul date -v-1d "$@" 2>/dev/null || TZ=Asia/Seoul date -d yesterday "$@"
}

TODAY=$(kst +%Y%m%d)
HOUR=$(kst +%H)

# 단기예보는 02·05·08·11·14·17·20·23시에 발표하고 10분쯤 뒤부터 조회된다.
# 그래서 우리는 06시 이후에는 오늘 05시 발표를, 그 전에는 전날 23시 발표를 쓴다.
if [ "$HOUR" -ge 6 ]; then
  VILAGE_DATE=$TODAY; VILAGE_TIME=0500
else
  VILAGE_DATE=$(kst_yesterday +%Y%m%d); VILAGE_TIME=2300
fi

# 중기예보는 06·18시에 발표한다.
if [ "$HOUR" -ge 18 ]; then
  MID_TMFC=${TODAY}1800
elif [ "$HOUR" -ge 6 ]; then
  MID_TMFC=${TODAY}0600
else
  MID_TMFC=$(kst_yesterday +%Y%m%d)1800
fi

failed=0
saved=()

# fetch <저장 경로> <형식 json|xml> <URL> [-d 파라미터 ...]
fetch() {
  local out="$1" format="$2" url="$3"
  shift 3
  local tmp
  tmp=$(mktemp)
  if ! curl -sSG --connect-timeout 5 --max-time 30 "$url" --data-urlencode "serviceKey=$KEY" "$@" -o "$tmp"; then
    echo "실패(연결): ${out#"$ROOT"/}" >&2
    failed=1; rm -f "$tmp"; return
  fi
  # 성공은 resultCode 00(기상청·천문연) 또는 0000(고캠핑)이다. 반면 인증 오류는 resultCode 없이 errMsg로 온다.
  if ! grep -q -E '"resultCode" *: *"(00|0000)"|<resultCode>(00|0000)</resultCode>' "$tmp"; then
    echo "실패(오류 응답): ${out#"$ROOT"/}" >&2
    head -c 300 "$tmp" >&2; echo >&2
    failed=1; rm -f "$tmp"; return
  fi
  mkdir -p "$(dirname "$out")"
  if [ "$format" = json ]; then
    python3 -c 'import json,sys; json.dump(json.load(open(sys.argv[1], encoding="utf-8")), open(sys.argv[2], "w", encoding="utf-8"), ensure_ascii=False, indent=2)' "$tmp" "$out"
  else
    cp "$tmp" "$out"
  fi
  rm -f "$tmp"
  saved+=("$out")
  echo "저장: ${out#"$ROOT"/}"
}

GOCAMPING=(-d MobileOS=ETC -d MobileApp=pitchmap -d _type=json)

fetch "$FIXTURES/publicdata/gocamping/based-list.json" json \
  "$BASE/B551011/GoCamping/basedList" "${GOCAMPING[@]}" -d numOfRows=3 -d pageNo=1

fetch "$FIXTURES/publicdata/gocamping/location-based-list.json" json \
  "$BASE/B551011/GoCamping/locationBasedList" "${GOCAMPING[@]}" -d numOfRows=3 -d pageNo=1 \
  -d mapX="$LNG" -d mapY="$LAT" -d radius=20000

# 동기화 목록은 최근에 수정한 순서로 온다. 그래서 한 페이지(1,000건)를 받은 다음 추가(A)·수정(U)·삭제(D)를 한 건씩 골라 3건으로 줄인다.
# 수정과 삭제는 휴장 기간이 있는 항목을 고른다. 이 조건에 맞는 항목이 그 페이지에 없으면 저장하지 않고 실패로 센다.
fetch_sync_list_sample() {
  local out="$FIXTURES/publicdata/gocamping/based-sync-list.json" tmp
  tmp=$(mktemp)
  if ! curl -sSG --connect-timeout 5 --max-time 60 "$BASE/B551011/GoCamping/basedSyncList" --data-urlencode "serviceKey=$KEY" \
    "${GOCAMPING[@]}" -d numOfRows=1000 -d pageNo=1 -o "$tmp"; then
    echo "실패(연결): ${out#"$ROOT"/}" >&2
    failed=1; rm -f "$tmp"; return
  fi
  mkdir -p "$(dirname "$out")"
  python3 - "$tmp" "$out" <<'PY' || { echo "실패(오류 응답이거나 고를 항목이 없음): ${out#"$ROOT"/}" >&2; failed=1; rm -f "$tmp"; return; }
import json, sys

source, target = sys.argv[1], sys.argv[2]
data = json.load(open(source, encoding="utf-8"))
if data["response"]["header"]["resultCode"] != "0000":
    sys.exit(1)
body = data["response"]["body"]
items = body["items"]["item"]


def first(status, needs_closed_period):
    for item in items:
        if item["syncStatus"] != status:
            continue
        if needs_closed_period and not (item["hvofBgnde"] and item["hvofEnddle"]):
            continue
        return item
    return None


picked = [first("A", False), first("U", True), first("D", True)]
if None in picked:
    sys.exit(1)
body["items"]["item"] = picked
body["numOfRows"] = len(picked)
json.dump(data, open(target, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
PY
  rm -f "$tmp"
  saved+=("$out")
  echo "저장: ${out#"$ROOT"/}"
}

fetch_sync_list_sample

fetch "$FIXTURES/weather/kma/vilage-fcst.json" json \
  "$BASE/1360000/VilageFcstInfoService_2.0/getVilageFcst" -d dataType=JSON -d numOfRows=1000 -d pageNo=1 \
  -d base_date="$VILAGE_DATE" -d base_time="$VILAGE_TIME" -d nx="$NX" -d ny="$NY"

fetch "$FIXTURES/weather/kma/mid-land-fcst.json" json \
  "$BASE/1360000/MidFcstInfoService/getMidLandFcst" -d dataType=JSON -d numOfRows=10 -d pageNo=1 \
  -d regId=11B00000 -d tmFc="$MID_TMFC"

fetch "$FIXTURES/weather/kma/mid-ta.json" json \
  "$BASE/1360000/MidFcstInfoService/getMidTa" -d dataType=JSON -d numOfRows=10 -d pageNo=1 \
  -d regId=11B10101 -d tmFc="$MID_TMFC"

fetch "$FIXTURES/weather/kasi/rise-set.xml" xml \
  "$BASE/B090041/openapi/service/RiseSetInfoService/getLCRiseSetInfo" \
  -d locdate="$TODAY" -d longitude="$LNG" -d latitude="$LAT" -d dnYn=Y

# 저장한 파일에 키(원문·URL 인코딩)가 들어 있으면 지운다. 왜냐하면 이 파일은 공개 저장소에 올라가기 때문이다.
ENCODED_KEY=$(python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$KEY")
for f in ${saved[@]+"${saved[@]}"}; do
  if grep -q -F -e "$KEY" -e "$ENCODED_KEY" "$f"; then
    echo "키가 들어 있어 삭제함: $f" >&2
    rm -f "$f"; failed=1
  fi
done
unset KEY ENCODED_KEY

if [ "${#saved[@]}" -eq 0 ]; then
  echo "저장한 샘플이 없습니다. 위 오류를 확인하세요." >&2
  exit 1
fi

cat > "$FIXTURES/publicdata-samples.txt" <<EOF
공공데이터 API 응답 샘플 (scripts/publicdata/fetch-samples.sh로 받음)
받은 시각(KST): $(kst '+%Y-%m-%d %H:%M')
위치: 위도 $LAT, 경도 $LNG, 기상청 격자 ($NX, $NY)
단기예보 발표: $VILAGE_DATE $VILAGE_TIME / 중기예보 발표: $MID_TMFC
고캠핑: 3건만 받음 (numOfRows=3), 위치 기반은 반경 20km
고캠핑 동기화 목록: 한 페이지(1,000건)에서 추가·수정·삭제를 한 건씩 골라 3건으로 줄임
EOF

if [ "$failed" -ne 0 ]; then
  echo "일부 샘플을 받지 못했습니다. 위 오류를 확인하세요." >&2
  exit 1
fi
echo "완료: ${#saved[@]}개 저장"
