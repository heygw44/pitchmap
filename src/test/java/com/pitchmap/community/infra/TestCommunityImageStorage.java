package com.pitchmap.community.infra;

import com.pitchmap.community.application.CommunityImageStorage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 통합 테스트가 실제 S3 대신 쓰는 저장소. 테스트가 {@link #markUploaded}로 올라온 객체를 정하고, 삭제한 키를 기록하며, 키별로 삭제를 실패시킬 수 있다.
 *
 * <p>정리 작업 스레드와 테스트 스레드가 함께 쓸 수 있도록 스레드 안전하게 만든다. 통합 테스트는 Spring 컨텍스트를 공유하므로,
 * {@link #reset()}을 테스트가 끝날 때마다 불러 이전 테스트의 상태를 지운다.
 */
public class TestCommunityImageStorage implements CommunityImageStorage {

    public static final String DELETE_FAILURE_MESSAGE = "simulated storage delete failure";

    private final Map<String, Long> objects = new ConcurrentHashMap<>();
    private final Set<String> failingDeletes = ConcurrentHashMap.newKeySet();
    private final List<String> deletedKeys = new CopyOnWriteArrayList<>();

    @Override
    public PresignedUpload presignUpload(String key, String contentType, long sizeBytes) {
        return new PresignedUpload(
                "https://upload.test.invalid/" + key + "?signed=1",
                Map.of("Content-Type", contentType, "Content-Length", String.valueOf(sizeBytes)),
                Instant.parse("2026-11-20T10:10:00Z"));
    }

    @Override
    public Optional<Long> findObjectSize(String key) {
        return Optional.ofNullable(objects.get(key));
    }

    @Override
    public String presignView(String key) {
        return "https://view.test.invalid/" + key + "?signed=1";
    }

    @Override
    public void delete(String key) {
        if (failingDeletes.contains(key)) {
            throw new IllegalStateException(DELETE_FAILURE_MESSAGE);
        }
        objects.remove(key);
        deletedKeys.add(key);
    }

    /** 호출하면 key 위치에 sizeBytes 크기의 객체가 올라온 것으로 한다. */
    public void markUploaded(String key, long sizeBytes) {
        objects.put(key, sizeBytes);
    }

    /** 호출하면 key의 삭제가 예외로 실패한다. */
    public void failDeleteOf(String key) {
        failingDeletes.add(key);
    }

    /** 성공한 삭제만 지운 순서대로 돌려준다. 실패시킨 삭제는 포함하지 않는다. */
    public List<String> deletedKeys() {
        return List.copyOf(deletedKeys);
    }

    public boolean hasObject(String key) {
        return objects.containsKey(key);
    }

    public void reset() {
        objects.clear();
        failingDeletes.clear();
        deletedKeys.clear();
    }
}
