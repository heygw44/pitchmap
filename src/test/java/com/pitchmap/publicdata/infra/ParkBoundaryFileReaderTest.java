package com.pitchmap.publicdata.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

// 공원 이름은 실제 공원이지만, 경계는 형식 검사를 확인하려고 테스트에서 지어낸 작은 사각형이다.
class ParkBoundaryFileReaderTest {

    private static final String BOM = "\uFEFF";
    private static final String HEADER = "name\tarea_type\twkt";
    private static final String BUKHANSAN_WKT = "MULTIPOLYGON(((126.9 37.6,127 37.6,127 37.7,126.9 37.7,126.9 37.6)))";
    private static final String DAEDUNSAN_POLYGON_WKT =
            "POLYGON((127.3 36.1,127.35 36.1,127.35 36.15,127.3 36.15,127.3 36.1))";
    private static final String BUKHANSAN = "북한산\tNATIONAL_PARK\t" + BUKHANSAN_WKT;
    private static final String DAEDUNSAN = "대둔산\tPROVINCIAL_PARK\t" + DAEDUNSAN_POLYGON_WKT;

    private final ParkBoundaryFileReader reader = new ParkBoundaryFileReader();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("[F-06] 파일 이름에서 기준일을 읽고, 빈 줄을 건너뛰며 공원마다 이름, 구분, MULTIPOLYGON WKT를 그대로 읽는다")
    void readsRecordsAndSourceDate() throws IOException {
        // given: 마지막에 빈 줄이 있다.
        String county = "강천산\tCOUNTY_PARK\tMULTIPOLYGON(((127 35.4,127.05 35.4,127.05 35.45,127 35.45,127 35.4)))";
        Path file = write("kdpa-park-boundaries_20251231.tsv", HEADER + "\n" + BUKHANSAN + "\n" + county + "\n\n");

        // when
        ParkBoundaryFile boundaryFile = reader.read(file);

        // then
        assertThat(boundaryFile.sourceDate()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(boundaryFile.records())
                .containsExactly(
                        new ParkBoundaryRecord("북한산", "NATIONAL_PARK", BUKHANSAN_WKT),
                        new ParkBoundaryRecord(
                                "강천산",
                                "COUNTY_PARK",
                                "MULTIPOLYGON(((127 35.4,127.05 35.4,127.05 35.45,127 35.45,127 35.4)))"));
    }

    @Test
    @DisplayName("[F-06] POLYGON 경계는 같은 좌표의 조각 하나짜리 MULTIPOLYGON WKT로 바꾼다")
    void wrapsPolygonIntoMultiPolygon() throws IOException, ParseException {
        // given
        Path file = write("parks_20251231.tsv", HEADER + "\n" + DAEDUNSAN + "\n");

        // when
        ParkBoundaryRecord record = reader.read(file).records().getFirst();

        // then
        Geometry stored = new WKTReader().read(record.multiPolygonWkt());
        Geometry original = new WKTReader().read(DAEDUNSAN_POLYGON_WKT);
        assertThat(stored).isInstanceOf(MultiPolygon.class);
        assertThat(stored.getNumGeometries()).isEqualTo(1);
        assertThat(stored.getGeometryN(0).equalsExact(original)).isTrue();
    }

    @Test
    @DisplayName("[F-06] 첫 줄 앞에 BOM이 있어도 헤더로 읽는다")
    void stripsBomBeforeHeader() throws IOException {
        // given
        Path file = write("parks_20251231.tsv", BOM + HEADER + "\n" + BUKHANSAN + "\n");

        // when
        ParkBoundaryFile boundaryFile = reader.read(file);

        // then
        assertThat(boundaryFile.records()).extracting(ParkBoundaryRecord::name).containsExactly("북한산");
    }

    @Test
    @DisplayName("[F-06] 공원 이름의 앞뒤 공백을 지우고, 이름이 같아도 구분이 다르면 다른 공원으로 읽는다")
    void stripsNameAndAllowsSameNameWithDifferentAreaType() throws IOException {
        // given
        Path file = write(
                "parks_20251231.tsv",
                HEADER + "\n 북한산 \tNATIONAL_PARK\t" + BUKHANSAN_WKT + "\n북한산\tCOUNTY_PARK\t" + BUKHANSAN_WKT + "\n");

        // when
        ParkBoundaryFile boundaryFile = reader.read(file);

        // then
        assertThat(boundaryFile.records())
                .extracting(ParkBoundaryRecord::areaType, ParkBoundaryRecord::name)
                .containsExactly(tuple("NATIONAL_PARK", "북한산"), tuple("COUNTY_PARK", "북한산"));
    }

    @Test
    @DisplayName("[F-06] 파일 이름이 _YYYYMMDD.tsv로 끝나지 않으면 파일을 읽지 않고 거부한다")
    void rejectsFileNameWithoutSourceDate() throws IOException {
        // given
        Path withoutDate = write("parks.tsv", HEADER + "\n" + BUKHANSAN + "\n");
        Path csv = write("parks_20251231.csv", HEADER + "\n" + BUKHANSAN + "\n");

        // when, then
        assertThatThrownBy(() -> reader.read(withoutDate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("parks.tsv");
        assertThatThrownBy(() -> reader.read(csv))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("parks_20251231.csv");
    }

    @Test
    @DisplayName("[F-06] 파일 이름의 기준일이 없는 날짜이면 거부한다")
    void rejectsFileNameWithInvalidDate() throws IOException {
        // given
        Path file = write("parks_20251232.tsv", HEADER + "\n" + BUKHANSAN + "\n");

        // when, then
        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("parks_20251232.tsv");
    }

    @Test
    @DisplayName("[F-06] 헤더가 name, area_type, wkt를 탭으로 나눈 줄이 아니면 거부한다")
    void rejectsUnexpectedHeader() throws IOException {
        // given: 열 순서가 다르다.
        Path file = write("parks_20251231.tsv", "area_type\tname\twkt\nNATIONAL_PARK\t북한산\t" + BUKHANSAN_WKT + "\n");

        // when, then
        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("file=parks_20251231.tsv")
                .hasMessageContaining("line=1");
    }

    @Test
    @DisplayName("[F-06] 빈 파일은 거부한다")
    void rejectsEmptyFile() throws IOException {
        // given
        Path file = write("parks_20251231.tsv", "");

        // when, then
        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("parks_20251231.tsv");
    }

    @Test
    @DisplayName("[F-06] 열이 세 개가 아닌 줄이 있으면 일부만 돌려주지 않고 파일 전체를 거부한다")
    void rejectsLineWithWrongColumnCount() throws IOException {
        // given: 세 번째 줄에 탭이 하나 더 있다.
        Path file = write("parks_20251231.tsv", HEADER + "\n" + BUKHANSAN + "\n" + DAEDUNSAN + "\t추가\n");

        // when, then
        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("file=parks_20251231.tsv")
                .hasMessageContaining("line=3");
    }

    @Test
    @DisplayName("[F-06] 구분이 국립·도립·군립공원이 아니면 파일 전체를 거부한다")
    void rejectsUnknownAreaType() throws IOException {
        // given: 도시자연공원구역은 적재 대상이 아니고, OTHER도 파일에서는 받지 않는다.
        Path urban =
                write("parks_20251231.tsv", HEADER + "\n" + BUKHANSAN.replace("NATIONAL_PARK", "URBAN_PARK") + "\n");
        Path other = write("others_20251231.tsv", HEADER + "\n" + BUKHANSAN.replace("NATIONAL_PARK", "OTHER") + "\n");

        // when, then
        assertThatThrownBy(() -> reader.read(urban))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("URBAN_PARK")
                .hasMessageContaining("line=2");
        assertThatThrownBy(() -> reader.read(other))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("line=2");
    }

    @Test
    @DisplayName("[F-06] 같은 구분과 이름이 두 번 나오면 파일 전체를 거부한다")
    void rejectsDuplicateAreaTypeAndName() throws IOException {
        // given
        Path file = write("parks_20251231.tsv", HEADER + "\n" + BUKHANSAN + "\n" + DAEDUNSAN + "\n" + BUKHANSAN + "\n");

        // when, then
        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("북한산")
                .hasMessageContaining("line=4");
    }

    @Test
    @DisplayName("[F-06] 공원 이름이 비었거나 100자를 넘으면 파일 전체를 거부한다")
    void rejectsBlankOrTooLongName() throws IOException {
        // given
        Path blank = write("blank_20251231.tsv", HEADER + "\n \tNATIONAL_PARK\t" + BUKHANSAN_WKT + "\n");
        Path tooLong = write(
                "long_20251231.tsv", HEADER + "\n" + "가".repeat(101) + "\tNATIONAL_PARK\t" + BUKHANSAN_WKT + "\n");

        // when, then
        assertThatThrownBy(() -> reader.read(blank))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("line=2");
        assertThatThrownBy(() -> reader.read(tooLong))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("line=2");
    }

    @Test
    @DisplayName("[F-06] 이름이 정확히 100자이면 읽는다")
    void acceptsNameOfMaxLength() throws IOException {
        // given
        String name = "가".repeat(100);
        Path file = write("parks_20251231.tsv", HEADER + "\n" + name + "\tNATIONAL_PARK\t" + BUKHANSAN_WKT + "\n");

        // when
        ParkBoundaryFile boundaryFile = reader.read(file);

        // then
        assertThat(boundaryFile.records()).extracting(ParkBoundaryRecord::name).containsExactly(name);
    }

    @Test
    @DisplayName("[F-06] 경계 WKT를 읽지 못하면 파일 전체를 거부한다")
    void rejectsUnparsableWkt() throws IOException {
        // given
        Path file = write("parks_20251231.tsv", HEADER + "\n북한산\tNATIONAL_PARK\tMULTIPOLYGON(((126.9 37.6,abc\n");

        // when, then
        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("line=2");
    }

    @Test
    @DisplayName("[F-06] 경계가 MULTIPOLYGON이나 POLYGON이 아니거나 비어 있으면 파일 전체를 거부한다")
    void rejectsNonPolygonOrEmptyGeometry() throws IOException {
        // given
        Path point = write("point_20251231.tsv", HEADER + "\n북한산\tNATIONAL_PARK\tPOINT(126.95 37.65)\n");
        Path empty = write("empty_20251231.tsv", HEADER + "\n북한산\tNATIONAL_PARK\tMULTIPOLYGON EMPTY\n");

        // when, then
        assertThatThrownBy(() -> reader.read(point))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Point")
                .hasMessageContaining("line=2");
        assertThatThrownBy(() -> reader.read(empty))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("line=2");
    }

    @Test
    @DisplayName("[F-06] 위도와 경도를 바꿔 써서 좌표가 경위도 범위 밖이면 파일 전체를 거부한다")
    void rejectsCoordinatesOutOfRange() throws IOException {
        // given: 북한산 경계를 (위도 경도) 순서로 잘못 썼다. 그래서 위도 자리에 127이 들어간다.
        Path file = write(
                "parks_20251231.tsv",
                HEADER + "\n" + BUKHANSAN + "\n대둔산\tPROVINCIAL_PARK\t"
                        + "MULTIPOLYGON(((37.6 126.9,37.6 127,37.7 127,37.7 126.9,37.6 126.9)))\n");

        // when, then
        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("line=3");
    }

    @Test
    @DisplayName("[F-06] 파일이 없으면 UncheckedIOException을 던진다")
    void throwsUncheckedIoExceptionWhenFileIsMissing() {
        // given
        Path file = tempDir.resolve("parks_20251231.tsv");

        // when, then
        assertThatThrownBy(() -> reader.read(file)).isInstanceOf(UncheckedIOException.class);
    }

    private Path write(String fileName, String content) throws IOException {
        return Files.writeString(tempDir.resolve(fileName), content, StandardCharsets.UTF_8);
    }
}
