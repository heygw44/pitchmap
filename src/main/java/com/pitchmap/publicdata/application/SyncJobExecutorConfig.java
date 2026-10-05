package com.pitchmap.publicdata.application;

import com.pitchmap.publicdata.domain.SyncJobType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 관리자가 실행한 적재·동기화 작업을 요청 스레드 밖에서 돌리는 실행기 설정.
 *
 * <p>같은 종류의 작업은 한 번에 하나만 실행되므로, 동시에 도는 작업은 많아야 작업 종류 수만큼이다. 그래서 스레드를 종류 수만큼 두고 대기열은 두지 않는다.
 * 그 이상 몰리면 실행기가 작업을 거부하고, 실행을 요청한 쪽이 그 실행 기록을 실패로 남긴다.
 *
 * <p>서버가 작업 도중 꺼지면 실행 기록이 RUNNING으로 남는다. 하지만 다음 실행이 오래 갱신되지 않은 기록을 실패로 처리하고 이어서 처리하므로,
 * 실행기는 종료할 때 작업이 끝나기를 기다리지 않는다.
 */
@Configuration(proxyBeanMethods = false)
public class SyncJobExecutorConfig {

    public static final String SYNC_JOB_EXECUTOR = "syncJobExecutor";

    private static final int POOL_SIZE = SyncJobType.values().length;

    @Bean(SYNC_JOB_EXECUTOR)
    ThreadPoolTaskExecutor syncJobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("sync-job-");
        executor.setCorePoolSize(POOL_SIZE);
        executor.setMaxPoolSize(POOL_SIZE);
        executor.setQueueCapacity(0);
        return executor;
    }
}
