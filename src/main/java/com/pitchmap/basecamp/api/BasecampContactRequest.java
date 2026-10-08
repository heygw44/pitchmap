package com.pitchmap.basecamp.api;

import com.pitchmap.basecamp.domain.Basecamp;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

// @URL은 null과 빈 문자열을 통과시켜서 @NotBlank를 함께 단다.
public record BasecampContactRequest(
        @Schema(description = "오픈채팅 링크 같은 연락 수단. https URL만 받는다.", example = "https://open.kakao.com/o/example")
        @NotBlank(message = "연락 수단을 입력해야 합니다.")
        @Size(max = Basecamp.CONTACT_INFO_MAX_LENGTH, message = "연락 수단은 255자 이하여야 합니다.")
        @URL(protocol = "https", message = "연락 수단은 https로 시작하는 URL이어야 합니다.")
        String contactInfo) {}
