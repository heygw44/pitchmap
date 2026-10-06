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
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 공공데이터포털에서 받은 국립자연휴양림 예약 정책 CSV 파일을 읽는다.
 *
 * <p>파일은 UTF-8이고 첫 줄 앞에 BOM이 붙어 있다. 리더는 헤더의 열 이름으로 필요한 열의 위치를 찾는다. 그래서 원천이 열 순서를 바꿔도 같은
 * 이름이면 그대로 읽는다.
 *
 * <p>파일 기준일은 파일 안에 없고, 공공데이터포털이 붙인 파일 이름 끝의 {@code _YYYYMMDD.csv}에만 있다. 그래서 리더는 이름에서 기준일을 읽고,
 * 이름이 이 형식이 아니면 파일을 읽지 않는다.
 *
 * <p>이 파일은 사람이 내려받아 넣는 정적 파일이다. 한 행이라도 형식이 깨졌으면 파일 전체가 잘못 받아졌을 수 있으므로, 리더는 일부 행만 돌려주지
 * 않고 파일 전체를 거부한다.
 */
@Component
public class ForestFileReader {

    private static final Pattern SOURCE_DATE_SUFFIX = Pattern.compile("_(\\d{8})\\.csv$");
    private static final String BOM = "\uFEFF";
    private static final String DELIMITER = ",";
    private static final String QUOTE = "\"";

    private static final String INSTITUTION_ID_COLUMN = "기관아이디";
    private static final String NAME_COLUMN = "기관명";
    private static final String LATITUDE_COLUMN = "위도";
    private static final String LONGITUDE_COLUMN = "경도";
    private static final String PHONE_COLUMN = "연락처";
    private static final String HOMEPAGE_COLUMN = "홈페이지주소";

    /**
     * 호출하면 파일 이름에서 기준일을 읽고, 파일의 데이터 행을 모두 읽어 돌려준다. 빈 줄은 건너뛴다.
     *
     * @throws IllegalStateException 파일 이름에 기준일이 없거나, 헤더에 필요한 열이 없거나, 데이터 행의 형식이 깨졌을 때
     * @throws UncheckedIOException 파일이 없거나 읽지 못했을 때
     */
    public ForestFile read(Path file) {
        LocalDate sourceDate = parseSourceDate(file);
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return new ForestFile(sourceDate, readRecords(reader, fileName(file)));
        } catch (IOException e) {
            throw new UncheckedIOException("휴양림 파일을 읽지 못했습니다. file=" + file, e);
        }
    }

    private static LocalDate parseSourceDate(Path file) {
        String fileName = fileName(file);
        Matcher matcher = SOURCE_DATE_SUFFIX.matcher(fileName);
        if (!matcher.find()) {
            throw new IllegalStateException("휴양림 파일 이름이 기준일(_YYYYMMDD.csv)로 끝나지 않습니다. file=" + fileName);
        }
        try {
            return LocalDate.parse(matcher.group(1), DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalStateException("휴양림 파일 이름의 기준일이 올바른 날짜가 아닙니다. file=" + fileName, e);
        }
    }

    private static String fileName(Path file) {
        Path fileName = file.getFileName();
        return fileName == null ? "" : fileName.toString();
    }

    private static List<ForestRecord> readRecords(BufferedReader reader, String fileName) throws IOException {
        String headerLine = reader.readLine();
        if (headerLine == null) {
            throw new IllegalStateException("휴양림 파일이 비어 있습니다. file=" + fileName);
        }
        Header header = Header.parse(stripBom(headerLine), fileName);
        List<ForestRecord> records = new ArrayList<>();
        int lineNumber = 1;
        String line;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (!line.isBlank()) {
                records.add(header.toRecord(splitDataLine(line, header, fileName, lineNumber)));
            }
        }
        return records;
    }

    private static String stripBom(String line) {
        return line.startsWith(BOM) ? line.substring(BOM.length()) : line;
    }

    // 리더는 쉼표로만 열을 나눈다. 따옴표로 감싼 값 안에 쉼표가 있으면 열이 밀린 채로 읽히는데, 열 수가 우연히 맞으면 알아챌 수 없다.
    // 지금 받은 파일에는 따옴표가 없으므로, 따옴표가 나오면 리더는 파일 형식이 바뀐 것으로 보고 거부한다.
    private static String[] splitDataLine(String line, Header header, String fileName, int lineNumber) {
        if (line.contains(QUOTE)) {
            throw new IllegalStateException("휴양림 파일에 따옴표가 든 줄이 있어 읽지 않습니다. file=" + fileName + ", line=" + lineNumber);
        }
        String[] values = line.split(DELIMITER, -1);
        if (values.length != header.columnCount()) {
            throw new IllegalStateException("휴양림 파일 줄의 열 수가 헤더와 다릅니다. file=" + fileName + ", line=" + lineNumber
                    + ", expected=" + header.columnCount() + ", actual=" + values.length);
        }
        return values;
    }

    /** 헤더의 열 수와, 리더가 읽는 열의 위치. */
    private record Header(
            int columnCount, int institutionId, int name, int latitude, int longitude, int phone, int homepage) {

        static Header parse(String headerLine, String fileName) {
            List<String> columns = Arrays.stream(headerLine.split(DELIMITER, -1))
                    .map(String::strip)
                    .toList();
            return new Header(
                    columns.size(),
                    indexOf(columns, INSTITUTION_ID_COLUMN, fileName),
                    indexOf(columns, NAME_COLUMN, fileName),
                    indexOf(columns, LATITUDE_COLUMN, fileName),
                    indexOf(columns, LONGITUDE_COLUMN, fileName),
                    indexOf(columns, PHONE_COLUMN, fileName),
                    indexOf(columns, HOMEPAGE_COLUMN, fileName));
        }

        private static int indexOf(List<String> columns, String column, String fileName) {
            int index = columns.indexOf(column);
            if (index < 0) {
                throw new IllegalStateException("휴양림 파일 헤더에 필요한 열이 없습니다. file=" + fileName + ", column=" + column);
            }
            return index;
        }

        ForestRecord toRecord(String[] values) {
            return new ForestRecord(
                    values[institutionId],
                    values[name],
                    values[latitude],
                    values[longitude],
                    values[phone],
                    values[homepage]);
        }
    }
}
