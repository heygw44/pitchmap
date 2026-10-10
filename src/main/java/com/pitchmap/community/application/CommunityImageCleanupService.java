package com.pitchmap.community.application;

import com.pitchmap.community.infra.CommunityImageMapper;
import com.pitchmap.community.infra.CommunityImageRow;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 만든 지 24시간이 지나도록 글에 붙지 않은 이미지를 행과 저장소 객체까지 지운다. 글에서 뗀 이미지도 같은 대상이다.
 *
 * <p>이 서비스는 트랜잭션을 열지 않는다. 저장소 삭제는 느릴 수 있어서 DB 트랜잭션과 행 잠금을 쥔 채 기다리면 안 되기 때문이다. 이미지마다 행을
 * 먼저 조건부로 지우고({@code post_id}가 아직 NULL일 때만), 지웠을 때만 저장소 객체를 지운다. 읽은 뒤 지우기 전에 누군가 그 이미지를 글에
 * 붙였다면 행 삭제가 0건이라서 행과 객체가 모두 남는다. 반대로 객체를 먼저 지우면 글에 붙은 이미지의 파일이 사라질 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityImageCleanupService {

    /** 글에 붙지 않은 이미지를 남겨 두는 시간이다. 만든 지 정확히 이 시간이 된 이미지는 남기고, 그보다 오래되면 지운다. */
    static final Duration RETENTION = Duration.ofHours(24);

    // 한 번에 읽는 이미지 수의 상한이다. 정리할 이미지가 많이 쌓였을 때 한꺼번에 읽지 않고 나눈다.
    static final int BATCH_SIZE = 100;

    private final CommunityImageMapper communityImageMapper;
    private final CommunityImageStorage communityImageStorage;
    private final Clock clock;

    /**
     * 호출하면 정리할 이미지를 BATCH_SIZE개씩 읽어 지우기를, 한 묶음을 가득 읽었고 그중 하나라도 지웠을 동안 되풀이한다.
     * 저장소 객체 삭제가 실패하면 경고만 남기고 다음 이미지로 넘어간다. 이때 행은 이미 지워진 상태다.
     */
    public Result deleteUnattached() {
        Instant createdBefore = clock.instant().minus(RETENTION);
        int deletedRows = 0;
        int storageFailures = 0;
        int batchDeleted;
        int batchRead;
        do {
            List<CommunityImageRow> batch =
                    communityImageMapper.selectUnattachedCreatedBefore(createdBefore, BATCH_SIZE);
            batchRead = batch.size();
            batchDeleted = 0;
            for (CommunityImageRow row : batch) {
                if (communityImageMapper.deleteIfUnattached(row.imageId()) == 0) {
                    continue;
                }
                batchDeleted++;
                if (!deleteObject(row)) {
                    storageFailures++;
                }
            }
            deletedRows += batchDeleted;
        } while (batchRead == BATCH_SIZE && batchDeleted > 0);
        log.info("community image cleanup deletedRows={} storageFailures={}", deletedRows, storageFailures);
        return new Result(deletedRows, storageFailures);
    }

    // 키와 URL에는 회원 ID와 서명이 들어 있어서 로그에 남기지 않고 이미지 ID만 남긴다.
    private boolean deleteObject(CommunityImageRow row) {
        try {
            communityImageStorage.delete(row.objectKey());
            return true;
        } catch (RuntimeException e) {
            log.warn(
                    "community image object delete failed imageId={} cause={}",
                    row.imageId(),
                    e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * 정리 결과.
     *
     * @param deletedRows 지운 이미지 행 수
     * @param storageFailures 행은 지웠지만 저장소 객체를 지우지 못한 수
     */
    public record Result(int deletedRows, int storageFailures) {}
}
