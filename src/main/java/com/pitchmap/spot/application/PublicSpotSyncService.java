package com.pitchmap.spot.application;

import com.pitchmap.spot.infra.PublicSpotColumns;
import com.pitchmap.spot.infra.PublicSpotInsertRow;
import com.pitchmap.spot.infra.PublicSpotMapper;
import com.pitchmap.spot.infra.PublicSpotRow;
import com.pitchmap.spot.infra.PublicSpotUpdateRow;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 공공데이터 원천의 장소 목록을 spot과 public_spot_detail에 적재한다. 같은 원천 행은 출처와 외부 ID로 찾아서 한 행만 유지한다.
 *
 * <p>서버는 바뀐 행만 갱신한다. 원천 데이터는 대부분 날마다 그대로라서, 모든 행을 다시 쓰면 updated_at과 synced_at이 실제 변경과 상관없이
 * 매일 바뀐다. 그래서 서버는 묶음의 기존 행을 먼저 읽어 Java에서 비교하고, 새 행은 추가하고, 값이 다른 행만 갱신하고, 같은 행에는 SQL을
 * 보내지 않는다.
 *
 * <p>원천이 삭제로 알려 준 장소는 {@link #markRemoved}가 숨기고, 숨긴 시각을 public_spot_detail에 남긴다. 그 장소가 원천에 다시 나타나면
 * {@link #upsert}가 그 표시를 보고 되돌린다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicSpotSyncService {

    private static final TypeReference<Map<String, String>> FACILITIES_TYPE = new TypeReference<>() {};

    private final PublicSpotMapper publicSpotMapper;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    /**
     * 호출하면 commands를 한 트랜잭션으로 적재하고 건수를 돌려준다.
     *
     * <p>외부 ID나 이름이 비었거나 좌표가 기상청 격자 밖인 명령은 저장하지 않고 WARN 로그만 남긴다. 한 묶음에 같은 외부 ID가 여러 번 있으면 마지막
     * 명령만 저장한다. 이미 있는 장소를 갱신해도 상태와 공원 경고는 그대로 둔다. 그래서 관리자가 숨긴 장소는 동기화 뒤에도 숨겨진 채로 남는다.
     *
     * <p>예외는 {@link #markRemoved}가 원천 삭제로 숨긴 장소다. 그 장소가 다시 오면 서버는 HIDDEN이던 상태를 ACTIVE로 되돌리고 삭제 표시를
     * 지운다. 삭제 표시가 있지만 이미 ACTIVE인 장소(관리자가 다시 공개한 장소)는 표시만 지운다. 되돌린 장소는 다른 값이 바뀌었는지와 상관없이
     * 갱신한 것으로 한 번 센다.
     */
    @Transactional
    public PublicSpotUpsertResult upsert(PublicSpotSource source, List<PublicSpotCommand> commands) {
        Map<String, NormalizedPublicSpot> spots = normalize(source, commands);
        int skipped = commands.size() - spots.size();
        if (spots.isEmpty()) {
            return new PublicSpotUpsertResult(0, 0, 0, skipped);
        }
        Map<String, PublicSpotRow> storedById = findStored(source, spots);
        UpsertPlan plan = new UpsertPlan();
        spots.values().forEach(spot -> plan.add(spot, storedById.get(spot.externalId())));
        Instant now = Instant.now(clock);
        insert(source, plan.inserts, now);
        update(plan, now);
        return new PublicSpotUpsertResult(plan.inserts.size(), plan.detailUpdates.size(), plan.unchanged, skipped);
    }

    /**
     * 호출하면 source 출처에서 externalIds에 해당하는 장소 중, 지금 ACTIVE이고 원천 삭제로 숨긴 적이 없는 장소를 HIDDEN으로 바꾸고 숨긴 시각을
     * 남긴다. 실제로 숨긴 장소 수를 돌려준다.
     *
     * <p>관리자가 이미 숨긴 장소는 삭제 표시가 없어서 건드리지도 표시하지도 않는다. 그래서 나중에 원천에 다시 나타나도 서버가 그 장소를 공개하지
     * 않는다. 삭제 표시가 이미 있는 장소도 건드리지 않는다. 같은 삭제 항목이 날마다 와도 한 번만 숨기고, 관리자가 다시 공개한 장소를 동기화가 또
     * 숨기지 않기 위해서다. DB에 없는 외부 ID, 비어 있거나 열 크기를 넘는 외부 ID는 무시한다.
     */
    @Transactional
    public int markRemoved(PublicSpotSource source, Collection<String> externalIds) {
        List<String> ids = externalIds.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(id -> !id.isEmpty() && lengthOf(id) <= NormalizedPublicSpot.MAX_EXTERNAL_ID_LENGTH)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return 0;
        }
        // 잠그지 않는 읽기로 후보를 먼저 찾는다. 없는 외부 ID를 곧바로 잠금 읽기에 넘기면 InnoDB가 없는 키 주변의 틈까지 잠가서
        // 같은 범위에 새 장소를 넣는 트랜잭션을 막는다. 후보가 있을 때만 기본 키로 잠가 읽는다.
        List<Long> candidateIds = publicSpotMapper.selectRemovalCandidateIds(source.name(), ids);
        if (candidateIds.isEmpty()) {
            return 0;
        }
        // 관리자가 같은 장소의 상태를 동시에 바꿔도 spot과 public_spot_detail이 서로 어긋나지 않게 두 테이블의 행을 먼저 잠근다.
        // 잠근 뒤에도 ACTIVE이고 표시가 없는 행만 돌려받으므로, 그 사이 숨겨진 행은 빠진다. 두 테이블을 한 UPDATE로 바꾸면 영향받은 행 수가
        // 테이블별 합이라 숨긴 수를 알 수 없다. 그래서 spot을 먼저 바꿔 그 행 수를 숨긴 수로 쓰고, 이어서 표시를 남긴다.
        // 조건부 상태 변경이라 JPA 엔티티 대신 MyBatis로 처리한다.
        List<Long> lockedIds = publicSpotMapper.lockRemovalCandidates(candidateIds);
        if (lockedIds.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now(clock);
        int hidden = publicSpotMapper.hideSpots(lockedIds, now);
        publicSpotMapper.markSourceRemoved(lockedIds, now);
        if (hidden > 0) {
            log.info("public spot hidden by source removal source={} count={}", source, hidden);
        }
        return hidden;
    }

    // 외부 ID를 키로 모으므로, 같은 외부 ID가 다시 나오면 뒤의 명령이 앞의 명령을 덮어쓴다.
    // 그래야 한 INSERT 문에 같은 (출처, 외부 ID)가 두 번 들어가 유니크 제약을 어기는 일이 없다.
    private Map<String, NormalizedPublicSpot> normalize(PublicSpotSource source, List<PublicSpotCommand> commands) {
        Map<String, NormalizedPublicSpot> spots = new LinkedHashMap<>();
        for (PublicSpotCommand command : commands) {
            normalizeOrSkip(source, command).ifPresent(spot -> keepLast(source, spots, spot));
        }
        return spots;
    }

    // 원천 데이터 한 건이 잘못됐다고 묶음 전체를 실패시키면, 다음 동기화도 같은 페이지에서 계속 멈춘다. 그래서 그 건만 건너뛴다.
    private Optional<NormalizedPublicSpot> normalizeOrSkip(PublicSpotSource source, PublicSpotCommand command) {
        try {
            return Optional.of(NormalizedPublicSpot.from(command));
        } catch (IllegalArgumentException e) {
            log.warn(
                    "public spot skipped source={} externalId={} reason={}",
                    source,
                    command.externalId(),
                    e.getMessage());
            return Optional.empty();
        }
    }

    private void keepLast(PublicSpotSource source, Map<String, NormalizedPublicSpot> spots, NormalizedPublicSpot spot) {
        if (spots.put(spot.externalId(), spot) != null) {
            log.warn(
                    "public spot duplicated in batch, keeping last source={} externalId={}", source, spot.externalId());
        }
    }

    private Map<String, PublicSpotRow> findStored(PublicSpotSource source, Map<String, NormalizedPublicSpot> spots) {
        return publicSpotMapper.selectBySourceAndExternalIds(source.name(), spots.keySet()).stream()
                .collect(Collectors.toMap(PublicSpotRow::externalId, Function.identity()));
    }

    private void insert(PublicSpotSource source, List<PublicSpotInsertRow> inserts, Instant now) {
        if (inserts.isEmpty()) {
            return;
        }
        publicSpotMapper.insertSpots(source.spotType(), inserts, now);
        publicSpotMapper.insertDetails(source.name(), inserts, now);
    }

    private void update(UpsertPlan plan, Instant now) {
        if (!plan.spotUpdates.isEmpty()) {
            publicSpotMapper.updateSpots(plan.spotUpdates, now);
        }
        if (!plan.detailUpdates.isEmpty()) {
            publicSpotMapper.updateDetails(plan.detailUpdates, now);
        }
        if (!plan.restoredSpotIds.isEmpty()) {
            // 순서가 중요하다. 상태를 되돌리는 문장이 삭제 표시를 보고 대상을 고르므로, 표시를 지우기 전에 실행한다.
            publicSpotMapper.restoreSpots(plan.restoredSpotIds, now);
            publicSpotMapper.clearSourceRemoved(plan.restoredSpotIds, now);
        }
    }

    private static int lengthOf(String value) {
        return value.codePointCount(0, value.length());
    }

    private PublicSpotColumns toColumns(NormalizedPublicSpot spot) {
        if (spot.facilities() == null) {
            return spot.toColumns(null);
        }
        return spot.toColumns(jsonMapper.writeValueAsString(spot.facilities()));
    }

    // MySQL은 JSON을 저장할 때 키 순서와 공백을 정규화한다. 그래서 저장된 문자열은 넘긴 문자열과 다를 수 있으므로 맵으로 바꿔서 비교한다.
    private Map<String, String> parseFacilities(String storedJson) {
        if (storedJson == null) {
            return null;
        }
        return jsonMapper.readValue(storedJson, FACILITIES_TYPE);
    }

    /**
     * 묶음의 장소를 추가할 것, 갱신할 것, 그대로 둘 것으로 나눈 결과.
     *
     * <p>spot 열이 바뀐 장소만 spotUpdates에 넣는다. 하지만 바뀐 장소는 모두 detailUpdates에 넣는다. synced_at은 동기화가 그 장소를 마지막으로 쓴
     * 시각이라서, 이름이나 좌표만 바뀌어도 public_spot_detail의 synced_at을 옮겨야 하기 때문이다.
     *
     * <p>원천 삭제 표시가 있는 장소가 다시 오면 다른 값이 그대로여도 detailUpdates와 restoredSpotIds에 넣는다. 그래서 되돌린 장소는 갱신 건수에
     * 한 번만 들어간다.
     */
    private final class UpsertPlan {

        private final List<PublicSpotInsertRow> inserts = new ArrayList<>();
        private final List<PublicSpotUpdateRow> spotUpdates = new ArrayList<>();
        private final List<PublicSpotUpdateRow> detailUpdates = new ArrayList<>();
        private final List<Long> restoredSpotIds = new ArrayList<>();
        private int unchanged;

        void add(NormalizedPublicSpot spot, PublicSpotRow stored) {
            if (stored == null) {
                inserts.add(new PublicSpotInsertRow(toColumns(spot)));
                return;
            }
            boolean spotChanged = spot.differsInSpotColumns(stored);
            boolean detailChanged = spot.differsInDetailColumns(stored, parseFacilities(stored.facilities()));
            if (!spotChanged && !detailChanged && !stored.sourceRemoved()) {
                unchanged++;
                return;
            }
            PublicSpotUpdateRow row = new PublicSpotUpdateRow(stored.spotId(), toColumns(spot));
            if (spotChanged) {
                spotUpdates.add(row);
            }
            detailUpdates.add(row);
            if (stored.sourceRemoved()) {
                restoredSpotIds.add(stored.spotId());
            }
        }
    }
}
