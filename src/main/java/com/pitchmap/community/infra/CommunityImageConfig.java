package com.pitchmap.community.infra;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CommunityImageProperties.class)
public class CommunityImageConfig {}
