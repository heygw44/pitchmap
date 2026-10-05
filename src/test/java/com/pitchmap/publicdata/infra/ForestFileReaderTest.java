package com.pitchmap.publicdata.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// 헤더와 0101(유명산 자연휴양림), 0116(화천숲속 야영장) 행은 실제 파일
// (산림청 국립자연휴양림관리소_국립자연휴양림 예약 정책_20260325.csv)에서 가져왔다.
// 열 순서를 바꾼 헤더, 열이 빠진 헤더, 열 수가 틀린 줄, 따옴표가 든 줄은 형식 검사를 확인하려고 테스트에서 지어낸 것이다.
class ForestFileReaderTest {

    private static final String BOM = "\uFEFF";
    private static final String HEADER = "기관아이디,기관명,정책구분,정책유형,요청일자,사용일자,지역,위도,경도,연락처,홈페이지주소";
    private static final String YUMYEONGSAN_FIRST = "0101,유명산 자연휴양림,선착순 예약정책,선착순 예약정책,2026-03-25~2026-05-05,"
            + "2026-03-25~2026-05-05, 서울/인천/경기,37.593195,127.49145051,031-589-5487,http://www.foresttrip.go.kr/0101";
    private static final String YUMYEONGSAN_SECOND = "0101,유명산 자연휴양림,우선예약정책,2026년 04월 산림복지 바우처 우선예약,"
            + "2026-03-04~2026-03-14,2026-04-01~2026-04-30, 서울/인천/경기,37.593195,127.49145051,031-589-5487,"
            + "http://www.foresttrip.go.kr/0101";
    private static final String HWACHEON = "0116,화천숲속 야영장,선착순 예약정책,선착순 예약정책,2026-03-25~2026-05-05,"
            + "2026-03-25~2026-05-05, 강원,38.01030834,127.79755541,033-441-4466,http://www.foresttrip.go.kr/0116";

    private final ForestFileReader reader = new ForestFileReader();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("[F-06] BOM이 붙은 헤더에서 열 이름으로 위치를 찾고, 빈 줄을 건너뛰며 모든 데이터 행을 문자열 그대로 읽는다")
    void readsAllRowsByColumnNameAfterBom() throws IOException {
        // given: 실제 파일처럼 줄 끝은 CRLF이고, 마지막에 빈 줄이 있다.
        Path file = write(
                "forest_20260325.csv",
                BOM + HEADER + "\r\n" + YUMYEONGSAN_FIRST + "\r\n" + YUMYEONGSAN_SECOND + "\r\n\r\n" + HWACHEON
                        + "\r\n");

        // when
        ForestFile forestFile = reader.read(file);

        // then
        assertThat(forestFile.records())
                .containsExactly(
                        new ForestRecord(
                                "0101",
                                "유명산 자연휴양림",
                                "37.593195",
                                "127.49145051",
                                "031-589-5487",
                                "http://www.foresttrip.go.kr/0101"),
                        new ForestRecord(
                                "0101",
                                "유명산 자연휴양림",
                                "37.593195",
                                "127.49145051",
                                "031-589-5487",
                                "http://www.foresttrip.go.kr/0101"),
                        new ForestRecord(
                                "0116",
                                "화천숲속 야영장",
                                "38.01030834",
                                "127.79755541",
                                "033-441-4466",
                                "http://www.foresttrip.go.kr/0116"));
    }

    @Test
    @DisplayName("[F-06] 헤더의 열 순서가 바뀌어도 열 이름으로 값을 찾는다")
    void findsColumnsByNameWhenOrderChanges() throws IOException {
        // given
        Path file = write(
                "forest_20260325.csv",
                "홈페이지주소,연락처,경도,위도,기관명,기관아이디\nhttp://www.foresttrip.go.kr/0116,033-441-4466,127.79755541,"
                        + "38.01030834,화천숲속 야영장,0116\n");

        // when
        ForestFile forestFile = reader.read(file);

        // then
        assertThat(forestFile.records())
                .containsExactly(new ForestRecord(
                        "0116",
                        "화천숲속 야영장",
                        "38.01030834",
                        "127.79755541",
                        "033-441-4466",
                        "http://www.foresttrip.go.kr/0116"));
    }

    @Test
    @DisplayName("[F-06] 파일 이름 끝의 _YYYYMMDD.csv에서 기준일을 읽는다")
    void readsSourceDateFromFileName() throws IOException {
        // given
        Path file = write("forest_20260901.csv", BOM + HEADER + "\n" + HWACHEON + "\n");

        // when
        ForestFile forestFile = reader.read(file);

        // then
        assertThat(forestFile.sourceDate()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    @DisplayName("[F-06] 한글과 공백이 든 실제 파일 이름에서도 기준일을 읽는다")
    void readsSourceDateFromActualFileName() throws IOException {
        // given
        Path file = write("산림청 국립자연휴양림관리소_국립자연휴양림 예약 정책_20260325.csv", BOM + HEADER + "\n" + HWACHEON + "\n");

        // when
        ForestFile forestFile = reader.read(file);

        // then
        assertThat(forestFile.sourceDate()).isEqualTo(LocalDate.of(2026, 3, 25));
        assertThat(forestFile.records()).hasSize(1);
    }

    @Test
    @DisplayName("[F-06] 파일 이름이 기준일로 끝나지 않으면 파일을 읽지 않고 거부한다")
    void rejectsFileNameWithoutSourceDate() throws IOException {
        // given
        Path file = write("forest.csv", BOM + HEADER + "\n" + HWACHEON + "\n");

        // when, then
        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("forest.csv");
    }

    @Test
    @DisplayName("[F-06] 파일 이름의 기준일이 없는 날짜이면 거부한다")
    void rejectsFileNameWithInvalidDate() throws IOException {
        // given
        Path file = write("forest_20260230.csv", BOM + HEADER + "\n" + HWACHEON + "\n");

        // when, then
        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("forest_20260230.csv");
    }

    @Test
    @DisplayName("[F-06] 헤더에 필요한 열이 없으면 거부한다")
    void rejectsHeaderWithoutRequiredColumn() throws IOException {
        // given: 위도 열이 빠진 헤더다.
        Path file = write(
                "forest_20260325.csv",
                BOM + "기관아이디,기관명,경도,연락처,홈페이지주소\n0116,화천숲속 야영장,127.79755541,033-441-4466,"
                        + "http://www.foresttrip.go.kr/0116\n");

        // when, then
        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("위도");
    }

    @Test
    @DisplayName("[F-06] 열 수가 헤더와 다른 줄이 있으면 일부만 돌려주지 않고 파일 전체를 거부한다")
    void rejectsLineWithDifferentColumnCount() throws IOException {
        // given: 세 번째 줄에 쉼표가 하나 더 있다.
        Path file = write(
                "forest_20260325.csv",
                BOM + HEADER + "\n" + YUMYEONGSAN_FIRST + "\n" + HWACHEON.replace("강원", "강원,춘천") + "\n");

        // when, then
        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("line=3");
    }

    @Test
    @DisplayName("[F-06] 따옴표가 든 줄이 있으면 열 수가 맞아도 파일 전체를 거부한다")
    void rejectsLineWithQuote() throws IOException {
        // given: 열 수는 헤더와 같지만 기관명에 따옴표가 있다.
        Path file =
                write("forest_20260325.csv", BOM + HEADER + "\n" + HWACHEON.replace("화천숲속 야영장", "\"화천숲속 야영장\"") + "\n");

        // when, then
        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("line=2");
    }

    @Test
    @DisplayName("[F-06] 파일이 없으면 UncheckedIOException을 던진다")
    void throwsUncheckedIoExceptionWhenFileIsMissing() {
        // given
        Path file = tempDir.resolve("forest_20260325.csv");

        // when, then
        assertThatThrownBy(() -> reader.read(file)).isInstanceOf(UncheckedIOException.class);
    }

    private Path write(String fileName, String content) throws IOException {
        return Files.writeString(tempDir.resolve(fileName), content, StandardCharsets.UTF_8);
    }
}
