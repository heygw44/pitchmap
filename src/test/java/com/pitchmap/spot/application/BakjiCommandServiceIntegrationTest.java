package com.pitchmap.spot.application;

import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.error.ErrorCode;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.common.web.PatchField;
import com.pitchmap.member.infra.MemberJpaRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class BakjiCommandServiceIntegrationTest {

    // 경계 WKT는 (경도 위도) 순서다. 경도 127.0~127.5, 위도 37.0~37.5인 사각형이다.
    private static final String PARK_SQUARE = "MULTIPOLYGON(((127 37, 127.5 37, 127.5 37.5, 127 37.5, 127 37)))";
    private static final double INSIDE_PARK_LAT = 37.25;
    private static final double INSIDE_PARK_LNG = 127.25;
    private static final double OUTSIDE_LAT = 37.25;
    private static final double OUTSIDE_LNG = 127.75;

    // 지구를 반지름 6,370,986m인 구로 볼 때 1m에 해당하는 위도 차이(도)다. 같은 경도에서 위도만 다르면 거리가 이 값에 비례한다.
    private static final double DEGREES_PER_METER = Math.toDegrees(1.0 / 6_370_986.0);

    private static final String WARNED_GUIDE = "공원 안 지정 장소 밖 야영은 과태료 대상입니다. 흔적을 남기지 마세요.";
    private static final String NORMAL_GUIDE = "머문 자리에 흔적을 남기지 마세요. 쓰레기는 모두 되가져가세요.";

    @Autowired
    private BakjiCommandService bakjiCommandService;

    @Autowired
    private SpotDetailQueryService spotDetailQueryService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("[F-07] 박지를 제보하면 ACTIVE 박지와 제보자, 입력한 내용이 저장되고 경고 없는 안내 문구를 돌려준다")
    void reportSavesBakjiWithReporter() {
        // given
        long reporterId = saveMember();

        // when
        BakjiSubmission submission = bakjiCommandService.report(
                reporterId, new BakjiReportCommand("능선 끝 평지", 37.6, 128.7, "바람이 약하다", true, false, "WEAK", "GRASS"));

        // then
        SpotRow spot = spotRow(submission.spotId());
        assertThat(spot.type()).isEqualTo("BAKJI");
        assertThat(spot.status()).isEqualTo("ACTIVE");
        assertThat(spot.name()).isEqualTo("능선 끝 평지");
        assertThat(spot.lat()).isCloseTo(37.6, within(1e-7));
        assertThat(spot.lng()).isCloseTo(128.7, within(1e-7));
        DetailRow detail = detailRow(submission.spotId());
        assertThat(detail.reporterId()).isEqualTo(reporterId);
        assertThat(detail.description()).isEqualTo("바람이 약하다");
        assertThat(detail.hasWater()).isTrue();
        assertThat(detail.hasToilet()).isFalse();
        assertThat(detail.signalLevel()).isEqualTo("WEAK");
        assertThat(detail.groundType()).isEqualTo("GRASS");
        assertThat(submission.warned()).isFalse();
        assertThat(submission.areaName()).isNull();
        assertThat(submission.guide()).isEqualTo(NORMAL_GUIDE);
        assertThat(submission.duplicateCandidates()).isEmpty();
    }

    @Test
    @DisplayName("[F-07] 공원 경계 안 좌표로 제보하면 경고와 공원 이름, 과태료 안내 문구를 돌려주고 판정 결과를 저장한다")
    void reportInsideBoundaryWarnsWithAreaName() {
        // given
        String areaName = TestSequence.unique("설악산");
        long areaId = insertProtectedArea(areaName, PARK_SQUARE);
        long reporterId = saveMember();

        // when
        BakjiSubmission submission =
                bakjiCommandService.report(reporterId, minimalReport(INSIDE_PARK_LAT, INSIDE_PARK_LNG));

        // then
        assertThat(submission.warned()).isTrue();
        assertThat(submission.areaName()).isEqualTo(areaName);
        assertThat(submission.guide()).isEqualTo(WARNED_GUIDE);
        SpotRow spot = spotRow(submission.spotId());
        assertThat(spot.parkWarning()).isTrue();
        assertThat(spot.protectedAreaId()).isEqualTo(areaId);
        assertThat(spot.areaCheckedAt()).isNotNull();
    }

    @Test
    @DisplayName("[F-07] 제보하면 50m 안의 ACTIVE 박지만 가까운 순서로 중복 후보에 담고, 51m 박지와 숨긴 박지, 야영장, 자기 자신은 뺀다")
    void reportListsDuplicateCandidatesWithinFiftyMeters() {
        // given: 기준점에서 같은 경도의 북쪽으로 떨어진 박지들이다.
        double baseLat = 37.6;
        double baseLng = 128.7;
        long reporterId = saveMember();
        long at49 = bakjiCommandService
                .report(reporterId, namedReport("49m 박지", baseLat + 49 * DEGREES_PER_METER, baseLng))
                .spotId();
        long at20 = bakjiCommandService
                .report(reporterId, namedReport("20m 박지", baseLat + 20 * DEGREES_PER_METER, baseLng))
                .spotId();
        bakjiCommandService.report(reporterId, namedReport("51m 박지", baseLat + 51 * DEGREES_PER_METER, baseLng));
        insertSpot("BAKJI", "PENDING_REVIEW", baseLat + 10 * DEGREES_PER_METER, baseLng);
        insertSpot("CAMPSITE", "ACTIVE", baseLat + 5 * DEGREES_PER_METER, baseLng);

        // when
        BakjiSubmission submission = bakjiCommandService.report(reporterId, namedReport("새 박지", baseLat, baseLng));

        // then
        assertThat(submission.duplicateCandidates())
                .containsExactly(
                        new BakjiDuplicateCandidate(at20, "20m 박지", 20),
                        new BakjiDuplicateCandidate(at49, "49m 박지", 49));
    }

    @Test
    @DisplayName("[F-07] 중복 후보는 최대 10개이고, 같은 거리의 박지는 장소 ID 순서로 준다")
    void reportLimitsDuplicateCandidatesToTen() {
        // given: 12곳이 모두 기준점에서 10m 떨어져 있다.
        double baseLat = 37.6;
        double baseLng = 128.7;
        long reporterId = saveMember();
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            ids.add(bakjiCommandService
                    .report(reporterId, namedReport("근처 " + i, baseLat + 10 * DEGREES_PER_METER, baseLng))
                    .spotId());
        }

        // when
        BakjiSubmission submission = bakjiCommandService.report(reporterId, namedReport("새 박지", baseLat, baseLng));

        // then
        assertThat(submission.duplicateCandidates())
                .extracting(BakjiDuplicateCandidate::spotId)
                .containsExactlyElementsOf(ids.subList(0, 10));
    }

    @Test
    @DisplayName("[F-07] 기상청 격자 밖 좌표나 허용하지 않는 값으로 제보하면 INVALID_INPUT으로 거부하고 아무것도 저장하지 않는다")
    void reportRejectsInvalidInput() {
        // given
        long reporterId = saveMember();
        BakjiReportCommand outsideGrid = minimalReport(35.6762, 139.6503);
        BakjiReportCommand badGroundType = new BakjiReportCommand("이름", 37.6, 128.7, null, true, true, null, "MUD");
        BakjiReportCommand badSignalLevel = new BakjiReportCommand("이름", 37.6, 128.7, null, true, true, "STRONG", null);
        BakjiReportCommand longDescription =
                new BakjiReportCommand("이름", 37.6, 128.7, "가".repeat(2001), true, true, null, null);

        // when & then
        for (BakjiReportCommand command : List.of(outsideGrid, badGroundType, badSignalLevel, longDescription)) {
            assertThatThrownBy(() -> bakjiCommandService.report(reporterId, command))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spot", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bakji_detail", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("[F-07] 허용하지 않는 바닥 유형과 통신 상태의 오류 메시지는 올바른 조사로 허용 값을 알려 준다")
    void invalidEnumMessagesUseCorrectParticles() {
        // given
        long reporterId = saveMember();

        // when & then
        assertThatThrownBy(() -> bakjiCommandService.report(
                        reporterId, new BakjiReportCommand("이름", 37.6, 128.7, null, true, true, null, "MUD")))
                .hasMessage("바닥 유형은 SOIL, GRASS, GRAVEL, SAND, ROCK, DECK 중 하나여야 합니다.");
        assertThatThrownBy(() -> bakjiCommandService.report(
                        reporterId, new BakjiReportCommand("이름", 37.6, 128.7, null, true, true, "STRONG", null)))
                .hasMessage("통신 상태는 NONE, WEAK, GOOD 중 하나여야 합니다.");
    }

    @Test
    @DisplayName("[F-07] 제보자가 수정하면 보낸 필드만 바뀌고, null로 보낸 설명·통신 상태·바닥 유형은 지워지며, 안 보낸 필드는 그대로다")
    void reporterUpdatesOnlySentFields() {
        // given
        long reporterId = saveMember();
        long spotId = bakjiCommandService
                .report(
                        reporterId,
                        new BakjiReportCommand("능선 끝 평지", 37.6, 128.7, "바람이 약하다", true, false, "WEAK", "GRASS"))
                .spotId();
        clock.setInstant(MutableClock.DEFAULT_INSTANT.plusSeconds(3600));
        BakjiUpdateCommand command = updateCommand()
                .name(PatchField.of("계곡 옆 평지"))
                .hasToilet(PatchField.of(true))
                .description(PatchField.of(null))
                .signalLevel(PatchField.of(null))
                .build();

        // when
        BakjiSubmission submission = bakjiCommandService.update(reporterId, spotId, command);

        // then
        assertThat(submission.spotId()).isEqualTo(spotId);
        SpotRow spot = spotRow(spotId);
        assertThat(spot.name()).isEqualTo("계곡 옆 평지");
        assertThat(spot.lat()).isCloseTo(37.6, within(1e-7));
        DetailRow detail = detailRow(spotId);
        assertThat(detail.description()).isNull();
        assertThat(detail.hasWater()).isTrue();
        assertThat(detail.hasToilet()).isTrue();
        assertThat(detail.signalLevel()).isNull();
        assertThat(detail.groundType()).isEqualTo("GRASS");
        assertThat(detail.reporterId()).isEqualTo(reporterId);
    }

    @Test
    @DisplayName("[F-07] 다른 회원이 수정하거나 삭제하면 ACCESS_DENIED로 거부하고 박지는 그대로다")
    void otherMemberCannotUpdateOrDelete() {
        // given
        long reporterId = saveMember();
        long strangerId = saveMember();
        long spotId = bakjiCommandService
                .report(reporterId, minimalReport(37.6, 128.7))
                .spotId();

        // when & then
        BakjiUpdateCommand rename = updateCommand().name(PatchField.of("바꾼 이름")).build();
        assertThatThrownBy(() -> bakjiCommandService.update(strangerId, spotId, rename))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.ACCESS_DENIED));
        assertThatThrownBy(() -> bakjiCommandService.delete(strangerId, spotId))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.ACCESS_DENIED));
        SpotRow spot = spotRow(spotId);
        assertThat(spot.name()).isEqualTo("기본 박지");
        assertThat(spot.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("[F-07] 좌표를 경계 안에서 밖으로 옮기면 경고가 꺼지고 경계 ID가 비며, 같은 좌표를 다시 보내면 판정 시각이 그대로다")
    void movingCoordinateRejudgesBoundaryOnlyWhenChanged() {
        // given
        insertProtectedArea(TestSequence.unique("경계"), PARK_SQUARE);
        long reporterId = saveMember();
        BakjiSubmission created =
                bakjiCommandService.report(reporterId, minimalReport(INSIDE_PARK_LAT, INSIDE_PARK_LNG));
        long spotId = created.spotId();
        assertThat(created.warned()).isTrue();
        Instant judgedAt = spotRow(spotId).areaCheckedAt();
        clock.setInstant(judgedAt.plusSeconds(3600));

        // when: 같은 좌표를 다시 보낸다.
        BakjiSubmission same = bakjiCommandService.update(
                reporterId,
                spotId,
                updateCommand()
                        .lat(PatchField.of(INSIDE_PARK_LAT))
                        .lng(PatchField.of(INSIDE_PARK_LNG))
                        .build());

        // then
        assertThat(same.warned()).isTrue();
        assertThat(spotRow(spotId).areaCheckedAt()).isEqualTo(judgedAt);

        // when: 경계 밖으로 옮긴다.
        BakjiSubmission moved = bakjiCommandService.update(
                reporterId,
                spotId,
                updateCommand()
                        .lat(PatchField.of(OUTSIDE_LAT))
                        .lng(PatchField.of(OUTSIDE_LNG))
                        .build());

        // then
        assertThat(moved.warned()).isFalse();
        assertThat(moved.areaName()).isNull();
        assertThat(moved.guide()).isEqualTo(NORMAL_GUIDE);
        SpotRow spot = spotRow(spotId);
        assertThat(spot.parkWarning()).isFalse();
        assertThat(spot.protectedAreaId()).isNull();
        assertThat(spot.areaCheckedAt()).isEqualTo(judgedAt.plusSeconds(3600));
        assertThat(spot.lat()).isCloseTo(OUTSIDE_LAT, within(1e-7));
        assertThat(spot.lng()).isCloseTo(OUTSIDE_LNG, within(1e-7));
    }

    @Test
    @DisplayName("[F-07] 수정하면 중복 후보를 수정한 뒤의 좌표로 계산한다")
    void updateComputesDuplicatesAtNewLocation() {
        // given
        long reporterId = saveMember();
        long neighbor = bakjiCommandService
                .report(reporterId, namedReport("이웃", 36.5, 128.0))
                .spotId();
        long spotId = bakjiCommandService
                .report(reporterId, minimalReport(37.6, 128.7))
                .spotId();

        // when
        BakjiSubmission submission = bakjiCommandService.update(
                reporterId,
                spotId,
                updateCommand()
                        .lat(PatchField.of(36.5 + 30 * DEGREES_PER_METER))
                        .lng(PatchField.of(128.0))
                        .build());

        // then
        assertThat(submission.duplicateCandidates()).containsExactly(new BakjiDuplicateCandidate(neighbor, "이웃", 30));
    }

    @Test
    @DisplayName("[F-07] 값이 규칙을 어기는 수정 요청은 INVALID_INPUT으로 거부하고 박지는 그대로다")
    void updateRejectsInvalidInput() {
        // given
        long reporterId = saveMember();
        long spotId = bakjiCommandService
                .report(reporterId, minimalReport(37.6, 128.7))
                .spotId();
        List<BakjiUpdateCommand> invalidCommands = List.of(
                updateCommand().lat(PatchField.of(37.7)).build(),
                updateCommand().lng(PatchField.of(128.8)).build(),
                updateCommand()
                        .lat(PatchField.of(null))
                        .lng(PatchField.of(null))
                        .build(),
                updateCommand()
                        .lat(PatchField.of(35.6762))
                        .lng(PatchField.of(139.6503))
                        .build(),
                updateCommand()
                        .lat(PatchField.of(91.0))
                        .lng(PatchField.of(128.8))
                        .build(),
                updateCommand().name(PatchField.of(null)).build(),
                updateCommand().name(PatchField.of(" ")).build(),
                updateCommand().name(PatchField.of("가".repeat(101))).build(),
                updateCommand().hasWater(PatchField.of(null)).build(),
                updateCommand().hasToilet(PatchField.of(null)).build(),
                updateCommand().description(PatchField.of("가".repeat(2001))).build(),
                updateCommand().groundType(PatchField.of("MUD")).build());

        // when & then
        for (BakjiUpdateCommand command : invalidCommands) {
            assertThatThrownBy(() -> bakjiCommandService.update(reporterId, spotId, command))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
        }
        SpotRow spot = spotRow(spotId);
        assertThat(spot.name()).isEqualTo("기본 박지");
        assertThat(spot.lat()).isCloseTo(37.6, within(1e-7));
    }

    @Test
    @DisplayName("[F-07] 제보자가 삭제하면 상태가 DELETED가 되어 장소 상세가 404가 되고, 다시 수정하거나 삭제해도 NOT_FOUND다")
    void deleteHidesBakji() {
        // given
        long reporterId = saveMember();
        long spotId = bakjiCommandService
                .report(reporterId, minimalReport(37.6, 128.7))
                .spotId();
        assertThat(spotDetailQueryService.findDetail(spotId).spotId()).isEqualTo(spotId);

        // when
        bakjiCommandService.delete(reporterId, spotId);

        // then
        assertThat(spotRow(spotId).status()).isEqualTo("DELETED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bakji_detail WHERE spot_id = ?", Integer.class, spotId))
                .isEqualTo(1);
        assertNotFound(() -> spotDetailQueryService.findDetail(spotId));
        assertNotFound(() -> bakjiCommandService.delete(reporterId, spotId));
        assertNotFound(() -> bakjiCommandService.update(
                reporterId, spotId, updateCommand().name(PatchField.of("x")).build()));
    }

    @Test
    @DisplayName("[F-07] 없는 장소, 공공데이터 장소, ACTIVE가 아닌 박지는 NOT_FOUND이고, 다른 회원의 비ACTIVE 박지도 403이 아니라 404다")
    void updateAndDeleteOfNonActiveBakjiAreNotFound() {
        // given
        long reporterId = saveMember();
        long strangerId = saveMember();
        long campsiteId = insertSpot("CAMPSITE", "ACTIVE", 37.6, 128.7);
        long hiddenId = bakjiCommandService
                .report(reporterId, minimalReport(37.5, 128.6))
                .spotId();
        jdbc.update("UPDATE spot SET status = 'HIDDEN' WHERE id = ?", hiddenId);
        long pendingId = bakjiCommandService
                .report(reporterId, minimalReport(37.4, 128.5))
                .spotId();
        jdbc.update("UPDATE spot SET status = 'PENDING_REVIEW' WHERE id = ?", pendingId);
        BakjiUpdateCommand rename = updateCommand().name(PatchField.of("x")).build();

        // when & then
        for (long id : List.of(campsiteId, hiddenId, pendingId, 999_999L)) {
            assertNotFound(() -> bakjiCommandService.update(reporterId, id, rename));
            assertNotFound(() -> bakjiCommandService.delete(reporterId, id));
        }
        assertNotFound(() -> bakjiCommandService.update(strangerId, hiddenId, rename));
        assertNotFound(() -> bakjiCommandService.delete(strangerId, hiddenId));
    }

    private void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(BusinessException.class, e -> {
            ErrorCode code = e.getErrorCode();
            assertThat(code).isEqualTo(CommonErrorCode.NOT_FOUND);
        });
    }

    private long saveMember() {
        return memberRepository.save(aMember().build()).getId();
    }

    private static BakjiReportCommand minimalReport(double lat, double lng) {
        return namedReport("기본 박지", lat, lng);
    }

    private static BakjiReportCommand namedReport(String name, double lat, double lng) {
        return new BakjiReportCommand(name, lat, lng, null, false, false, null, null);
    }

    private static UpdateCommandBuilder updateCommand() {
        return new UpdateCommandBuilder();
    }

    // 요청에 없는 필드는 absent로 채운다. 테스트마다 보낼 필드만 고르게 하려는 빌더다.
    private static final class UpdateCommandBuilder {
        private PatchField<String> name = PatchField.absent();
        private PatchField<Double> lat = PatchField.absent();
        private PatchField<Double> lng = PatchField.absent();
        private PatchField<String> description = PatchField.absent();
        private PatchField<Boolean> hasWater = PatchField.absent();
        private PatchField<Boolean> hasToilet = PatchField.absent();
        private PatchField<String> signalLevel = PatchField.absent();
        private PatchField<String> groundType = PatchField.absent();

        UpdateCommandBuilder name(PatchField<String> name) {
            this.name = name;
            return this;
        }

        UpdateCommandBuilder lat(PatchField<Double> lat) {
            this.lat = lat;
            return this;
        }

        UpdateCommandBuilder lng(PatchField<Double> lng) {
            this.lng = lng;
            return this;
        }

        UpdateCommandBuilder description(PatchField<String> description) {
            this.description = description;
            return this;
        }

        UpdateCommandBuilder hasWater(PatchField<Boolean> hasWater) {
            this.hasWater = hasWater;
            return this;
        }

        UpdateCommandBuilder hasToilet(PatchField<Boolean> hasToilet) {
            this.hasToilet = hasToilet;
            return this;
        }

        UpdateCommandBuilder signalLevel(PatchField<String> signalLevel) {
            this.signalLevel = signalLevel;
            return this;
        }

        UpdateCommandBuilder groundType(PatchField<String> groundType) {
            this.groundType = groundType;
            return this;
        }

        BakjiUpdateCommand build() {
            return new BakjiUpdateCommand(name, lat, lng, description, hasWater, hasToilet, signalLevel, groundType);
        }
    }

    private long insertProtectedArea(String name, String longLatWkt) {
        jdbc.update(
                "INSERT INTO protected_area (name, area_type, source, source_date, boundary, created_at, updated_at)"
                        + " VALUES (?, 'NATIONAL_PARK', 'KDPA', '2026-01-01',"
                        + " ST_GeomFromText(?, 4326, 'axis-order=long-lat'), NOW(6), NOW(6))",
                name,
                longLatWkt);
        return jdbc.queryForObject("SELECT id FROM protected_area WHERE name = ?", Long.class, name);
    }

    // 제보 흐름 밖의 장소(숨김 박지, 야영장)를 만든다. 좌표는 SRID 4326 WKT 기본 순서인 (위도 경도)로 넣는다.
    private long insertSpot(String type, String status, double lat, double lng) {
        String name = TestSequence.unique(type);
        Timestamp now = Timestamp.from(MutableClock.DEFAULT_INSTANT);
        jdbc.update(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                        + " VALUES (?, ?, ST_GeomFromText(?, 4326), 60, 127, ?, ?, ?)",
                type,
                name,
                String.format(Locale.ROOT, "POINT(%s %s)", lat, lng),
                status,
                now,
                now);
        return jdbc.queryForObject("SELECT id FROM spot WHERE name = ?", Long.class, name);
    }

    private SpotRow spotRow(long spotId) {
        return jdbc.queryForObject(
                "SELECT type, status, name, ST_Latitude(location) AS lat, ST_Longitude(location) AS lng,"
                        + " park_warning, protected_area_id, area_checked_at FROM spot WHERE id = ?",
                (rs, rowNum) -> {
                    Timestamp checkedAt = rs.getTimestamp("area_checked_at");
                    return new SpotRow(
                            rs.getString("type"),
                            rs.getString("status"),
                            rs.getString("name"),
                            rs.getDouble("lat"),
                            rs.getDouble("lng"),
                            rs.getBoolean("park_warning"),
                            rs.getObject("protected_area_id", Long.class),
                            checkedAt == null ? null : checkedAt.toInstant());
                },
                spotId);
    }

    private DetailRow detailRow(long spotId) {
        return jdbc.queryForObject(
                "SELECT reporter_id, description, has_water, has_toilet, signal_level, ground_type"
                        + " FROM bakji_detail WHERE spot_id = ?",
                (rs, rowNum) -> new DetailRow(
                        rs.getLong("reporter_id"),
                        rs.getString("description"),
                        rs.getBoolean("has_water"),
                        rs.getBoolean("has_toilet"),
                        rs.getString("signal_level"),
                        rs.getString("ground_type")),
                spotId);
    }

    private record SpotRow(
            String type,
            String status,
            String name,
            double lat,
            double lng,
            boolean parkWarning,
            Long protectedAreaId,
            Instant areaCheckedAt) {}

    private record DetailRow(
            long reporterId,
            String description,
            boolean hasWater,
            boolean hasToilet,
            String signalLevel,
            String groundType) {}
}
