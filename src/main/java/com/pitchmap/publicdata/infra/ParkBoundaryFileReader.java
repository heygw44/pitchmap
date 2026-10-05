package com.pitchmap.publicdata.infra;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 공원 경계 TSV 파일을 읽는다. 이 파일은 개발자가 KDPA(한국보호지역 통합DB관리시스템) 보호지역 SHP에서 국립·도립·군립공원만 골라 전처리 스크립트로
 * 만든다.
 *
 * <p>파일은 BOM 없는 UTF-8이고 열은 탭으로 나눈다. 첫 줄은 {@code name}, {@code area_type}, {@code wkt} 세 열 이름이고, 그 뒤로 공원마다 한
 * 줄이다. 리더는 BOM이 붙은 파일도 받는다. 경계 WKT의 좌표는 (경도 위도) 순서다. 공원 하나의 WKT가 수 MB인 줄도 있어서, 리더는 줄 단위로 읽고 정규식
 * 대신 탭 문자 하나로 열을 나눈다.
 *
 * <p>파일 기준일은 파일 안에 없고 파일 이름 끝의 {@code _YYYYMMDD.tsv}에만 있다. 그래서 리더는 이름에서 기준일을 읽고, 이름이 이 형식이 아니면 파일을
 * 읽지 않는다.
 *
 * <p>한 줄이라도 형식이 깨졌으면 전처리가 잘못됐을 수 있다. 그래서 리더는 일부 줄만 돌려주지 않고 파일 전체를 거부한다.
 */
@Component
public class ParkBoundaryFileReader {

    private static final Pattern SOURCE_DATE_SUFFIX = Pattern.compile("_(\\d{8})\\.tsv$");
    private static final String BOM = "\uFEFF";
    private static final String HEADER = "name\tarea_type\twkt";
    private static final String DELIMITER = "\t";
    private static final int COLUMN_COUNT = 3;
    private static final int MAX_NAME_LENGTH = 100;
    private static final Set<String> AREA_TYPES = Set.of("NATIONAL_PARK", "PROVINCIAL_PARK", "COUNTY_PARK");

    /**
     * 호출하면 파일 이름에서 기준일을 읽고, 파일의 데이터 줄을 모두 읽어 돌려준다. 빈 줄은 건너뛴다.
     *
     * @throws IllegalStateException 파일 이름에 기준일이 없거나, 헤더가 다르거나, 데이터 줄의 형식이 깨졌을 때. 메시지에 파일 이름과 줄 번호가 들어간다.
     * @throws UncheckedIOException 파일이 없거나, 읽지 못했거나, UTF-8이 아닐 때
     */
    public ParkBoundaryFile read(Path file) {
        LocalDate sourceDate = parseSourceDate(file);
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return new ParkBoundaryFile(sourceDate, readRecords(reader, fileName(file)));
        } catch (IOException e) {
            throw new UncheckedIOException("공원 경계 파일을 읽지 못했습니다. file=" + file, e);
        }
    }

    private static LocalDate parseSourceDate(Path file) {
        String fileName = fileName(file);
        Matcher matcher = SOURCE_DATE_SUFFIX.matcher(fileName);
        if (!matcher.find()) {
            throw new IllegalStateException("공원 경계 파일 이름이 기준일(_YYYYMMDD.tsv)로 끝나지 않습니다. file=" + fileName);
        }
        try {
            return LocalDate.parse(matcher.group(1), DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalStateException("공원 경계 파일 이름의 기준일이 올바른 날짜가 아닙니다. file=" + fileName, e);
        }
    }

    private static String fileName(Path file) {
        Path fileName = file.getFileName();
        return fileName == null ? "" : fileName.toString();
    }

    private static List<ParkBoundaryRecord> readRecords(BufferedReader reader, String fileName) throws IOException {
        requireHeader(reader.readLine(), fileName);
        LineParser parser = new LineParser(fileName);
        List<ParkBoundaryRecord> records = new ArrayList<>();
        int lineNumber = 1;
        String line;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (!line.isBlank()) {
                records.add(parser.parse(line, lineNumber));
            }
        }
        return records;
    }

    private static void requireHeader(String headerLine, String fileName) {
        if (headerLine == null) {
            throw new IllegalStateException("공원 경계 파일이 비어 있습니다. file=" + fileName);
        }
        String header = headerLine.startsWith(BOM) ? headerLine.substring(BOM.length()) : headerLine;
        if (!HEADER.equals(header)) {
            throw new IllegalStateException(
                    "공원 경계 파일의 헤더가 name, area_type, wkt 세 열을 탭으로 나눈 줄이 아닙니다. file=" + fileName + ", line=1");
        }
    }

    /** 데이터 줄을 하나씩 검사해서 행으로 바꾼다. 앞에서 나온 (구분, 이름)을 기억해야 해서 파일마다 새로 만든다. */
    private static final class LineParser {

        private final String fileName;
        private final ParkBoundaryWkt boundaryWkt = new ParkBoundaryWkt();
        private final Set<AreaKey> seenKeys = new HashSet<>();

        LineParser(String fileName) {
            this.fileName = fileName;
        }

        // 구분자가 정규식 특수 문자가 아닌 한 글자라서, String.split은 정규식 엔진을 쓰지 않고 indexOf로 나눈다.
        ParkBoundaryRecord parse(String line, int lineNumber) {
            String[] values = line.split(DELIMITER, -1);
            if (values.length != COLUMN_COUNT) {
                throw reject(lineNumber, "열 수가 " + COLUMN_COUNT + "개가 아닙니다. actual=" + values.length);
            }
            String name = requireName(values[0], lineNumber);
            String areaType = requireAreaType(values[1], lineNumber);
            requireFirstOccurrence(new AreaKey(areaType, name), lineNumber);
            return new ParkBoundaryRecord(name, areaType, toMultiPolygonWkt(values[2], lineNumber));
        }

        private String requireName(String value, int lineNumber) {
            String name = value.strip();
            if (name.isEmpty()) {
                throw reject(lineNumber, "공원 이름이 비어 있습니다.");
            }
            if (name.codePointCount(0, name.length()) > MAX_NAME_LENGTH) {
                throw reject(lineNumber, "공원 이름이 " + MAX_NAME_LENGTH + "자를 넘습니다.");
            }
            return name;
        }

        private String requireAreaType(String value, int lineNumber) {
            if (!AREA_TYPES.contains(value)) {
                throw reject(
                        lineNumber,
                        "area_type은 NATIONAL_PARK, PROVINCIAL_PARK, COUNTY_PARK 중 하나여야 합니다. actual=" + value);
            }
            return value;
        }

        // 같은 구분에 같은 이름이 두 번 나오면 DB에서 두 줄이 같은 공원 하나를 서로 덮어쓴다. 그래서 리더는 어느 쪽이 맞는지 고르지 않고 파일을 거부한다.
        private void requireFirstOccurrence(AreaKey key, int lineNumber) {
            if (!seenKeys.add(key)) {
                throw reject(
                        lineNumber, "같은 area_type과 이름이 앞에서 나왔습니다. areaType=" + key.areaType() + ", name=" + key.name());
            }
        }

        private String toMultiPolygonWkt(String wkt, int lineNumber) {
            try {
                return boundaryWkt.toMultiPolygonWkt(wkt);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(message(lineNumber, "경계 WKT가 잘못됐습니다. " + e.getMessage()), e);
            }
        }

        private IllegalStateException reject(int lineNumber, String reason) {
            return new IllegalStateException(message(lineNumber, reason));
        }

        private String message(int lineNumber, String reason) {
            return "공원 경계 파일 줄의 형식이 잘못됐습니다. " + reason + " file=" + fileName + ", line=" + lineNumber;
        }
    }

    private record AreaKey(String areaType, String name) {}
}
