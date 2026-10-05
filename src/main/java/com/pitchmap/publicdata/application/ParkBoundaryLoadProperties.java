package com.pitchmap.publicdata.application;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 공원 경계 파일 적재 설정.
 *
 * @param file 적재할 공원 경계 TSV 파일 경로. 파일 이름은 기준일({@code _YYYYMMDD.tsv})로 끝나야 한다. 비어 있어도 앱은 뜨지만, 이 경로로 적재를
 *     실행하면 실패로 기록된다.
 * @param staleAfter 실행 중인 기록이 이 시간보다 오래 갱신되지 않으면, 다음 실행은 서버가 작업 도중 꺼진 것으로 보고 그 기록을 실패로 처리한다.
 *     적재는 파일 전체를 한 번에 처리한 뒤에야 기록을 갱신하므로, 이 값은 파일 하나를 적재하는 시간보다 충분히 길어야 한다.
 */
@ConfigurationProperties("pitchmap.publicdata.park-boundary")
public record ParkBoundaryLoadProperties(
        Path file, @DefaultValue("30m") Duration staleAfter) {

    static final String FILE_PROPERTY = "pitchmap.publicdata.park-boundary.file";

    public ParkBoundaryLoadProperties {
        if (staleAfter.isZero() || staleAfter.isNegative()) {
            throw new IllegalArgumentException("pitchmap.publicdata.park-boundary.staleAfter must be positive");
        }
    }
}
