package com.pitchmap.spot.application;

import com.pitchmap.spot.infra.ProtectedAreaInsertRow;
import com.pitchmap.spot.infra.ProtectedAreaKeyRow;
import com.pitchmap.spot.infra.ProtectedAreaMapper;
import com.pitchmap.spot.infra.ProtectedAreaUpdateRow;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공원·보호지역 경계를 protected_area에 적재한다. 같은 경계는 출처, 구분, 이름으로 찾아서 한 행만 유지한다.
 *
 * <p>서비스는 경계를 추가하고 갱신하기만 하고, 새 묶음에 없는 기존 경계를 지우지 않는다. 장소가 공원 경고의 근거로 경계 ID를 참조하고 있어서, DB는 참조된
 * 경계를 지우지 못하게 막는다. 그리고 경계가 바뀐 뒤 장소의 공원 경고를 다시 판정하는 일은 별도의 재판정 작업이 맡는다. 그래서 서비스는 원천에서 빠진
 * 경계도 그대로 둔다.
 */
@Service
@RequiredArgsConstructor
public class ProtectedAreaLoadService {

    private final ProtectedAreaMapper protectedAreaMapper;
    private final Clock clock;

    /**
     * 호출하면 commands를 한 트랜잭션으로 적재하고 건수를 돌려준다. source 출처에 같은 구분과 이름의 경계가 있으면 그 경계의 도형과 기준일을 바꾸고,
     * 없으면 새로 추가한다. 경계 하나라도 저장하지 못하면 DB가 묶음 전체를 되돌린다.
     *
     * @throws IllegalArgumentException commands에 같은 구분과 이름이 두 번 이상 있을 때
     */
    @Transactional
    public ProtectedAreaUpsertResult upsert(
            ProtectedAreaSource source, LocalDate sourceDate, List<ProtectedAreaCommand> commands) {
        requireUniqueKeys(commands);
        Map<AreaKey, List<Long>> storedIds = findStoredIds(source);
        Instant now = Instant.now(clock);
        int inserted = 0;
        for (ProtectedAreaCommand command : commands) {
            List<Long> ids = storedIds.get(AreaKey.of(command));
            if (ids == null) {
                protectedAreaMapper.insert(toInsertRow(source, sourceDate, command), now);
                inserted++;
                continue;
            }
            updateAll(ids, sourceDate, command, now);
        }
        return new ProtectedAreaUpsertResult(inserted, commands.size() - inserted);
    }

    // 같은 키의 명령이 둘이면 뒤의 명령이 앞의 명령을 말없이 덮어쓴다. 어느 쪽이 맞는지 서비스가 알 수 없으므로 묶음 전체를 거부한다.
    private static void requireUniqueKeys(List<ProtectedAreaCommand> commands) {
        Set<AreaKey> keys = new HashSet<>();
        for (ProtectedAreaCommand command : commands) {
            if (!keys.add(AreaKey.of(command))) {
                throw new IllegalArgumentException(
                        "같은 구분과 이름의 경계가 두 번 있습니다. areaType=" + command.areaType() + ", name=" + command.name());
            }
        }
    }

    // 경계는 출처마다 수십 개라서 서비스는 출처의 키를 한 번에 모두 읽는다. 테이블에 유니크 제약이 없어서 같은 키의 행이 여럿 있을 수 있으므로,
    // 키마다 ID 목록을 두고 갱신할 때 모두 같은 값으로 맞춘다.
    private Map<AreaKey, List<Long>> findStoredIds(ProtectedAreaSource source) {
        Map<AreaKey, List<Long>> storedIds = new HashMap<>();
        for (ProtectedAreaKeyRow row : protectedAreaMapper.selectKeysBySource(source.name())) {
            storedIds
                    .computeIfAbsent(new AreaKey(row.areaType(), row.name()), key -> new ArrayList<>())
                    .add(row.id());
        }
        return storedIds;
    }

    private void updateAll(List<Long> ids, LocalDate sourceDate, ProtectedAreaCommand command, Instant now) {
        for (long id : ids) {
            protectedAreaMapper.updateBoundary(
                    new ProtectedAreaUpdateRow(id, sourceDate, command.multiPolygonWkt()), now);
        }
    }

    private static ProtectedAreaInsertRow toInsertRow(
            ProtectedAreaSource source, LocalDate sourceDate, ProtectedAreaCommand command) {
        return new ProtectedAreaInsertRow(
                source.name(), command.areaType().name(), command.name(), sourceDate, command.multiPolygonWkt());
    }

    private record AreaKey(String areaType, String name) {

        static AreaKey of(ProtectedAreaCommand command) {
            return new AreaKey(command.areaType().name(), command.name());
        }
    }
}
