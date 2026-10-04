package com.pitchmap.common.scheduling;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// 아웃박스 발행기처럼 주기적으로 도는 작업이 @Scheduled를 쓰려면 스케줄링을 켜야 한다.
// 이 설정을 한 곳에 두어, 스케줄링을 쓰는 모듈이 각자 켜지 않게 한다.
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {}
