package com.pitchmap.weather.infra;

import com.pitchmap.weather.domain.MidTermRegion;
import com.pitchmap.weather.domain.MidTermRegions;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * 클래스패스의 {@code weather/mid-term-regions.csv}를 읽어 중기예보 구역 목록을 빈으로 만든다. 파일 형식이 틀리면 앱이 뜨지 않는다.
 */
@Configuration(proxyBeanMethods = false)
public class MidTermRegionTable {

    private static final String RESOURCE = "weather/mid-term-regions.csv";
    private static final int COLUMN_COUNT = 5;

    @Bean
    MidTermRegions midTermRegions() {
        return new MidTermRegions(load());
    }

    public static List<MidTermRegion> load() {
        List<MidTermRegion> regions = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource(RESOURCE).getInputStream(), StandardCharsets.UTF_8))) {
            reader.readLine();
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (!line.isBlank()) {
                    regions.add(parse(line));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(RESOURCE + "를 읽지 못했다", e);
        }
        return regions;
    }

    private static MidTermRegion parse(String line) {
        String[] columns = line.split(",");
        if (columns.length != COLUMN_COUNT) {
            throw new IllegalStateException(RESOURCE + " 행의 열이 " + COLUMN_COUNT + "개가 아니다: " + line);
        }
        return new MidTermRegion(
                columns[0], columns[1], columns[2], Double.parseDouble(columns[3]), Double.parseDouble(columns[4]));
    }
}
