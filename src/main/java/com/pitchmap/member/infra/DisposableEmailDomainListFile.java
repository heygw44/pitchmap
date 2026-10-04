package com.pitchmap.member.infra;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class DisposableEmailDomainListFile {

    private static final String RESOURCE_PATH = "disposable-email-domains.txt";
    private static final String COMMENT_PREFIX = "#";
    // disposable_email_domain.domain 컬럼 길이
    private static final int MAX_DOMAIN_LENGTH = 253;

    public List<String> readDomains() {
        Resource resource = new ClassPathResource(RESOURCE_PATH);
        Set<String> domains = new LinkedHashSet<>();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String domain = line.trim().toLowerCase(Locale.ROOT);
                if (domain.isEmpty() || domain.startsWith(COMMENT_PREFIX) || domain.length() > MAX_DOMAIN_LENGTH) {
                    continue;
                }
                domains.add(domain);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("일회용 이메일 도메인 목록을 읽지 못했습니다: " + RESOURCE_PATH, e);
        }
        return new ArrayList<>(domains);
    }
}
