package com.pitchmap.spot.api;

import com.pitchmap.spot.domain.GeoPoint;
import com.pitchmap.spot.domain.WeatherGrid;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 박지 제보 요청의 좌표가 기상청 격자 범위 안인지 검사한다. 범위 밖 좌표는 날씨를 조회할 수 없어서 박지로 받지 않는다.
 *
 * <p>위도와 경도 중 어느 쪽 때문에 범위를 벗어났는지 가릴 수 없으므로, 검사가 실패하면 {@code lat}와 {@code lng} 두 필드에 오류를 붙인다.
 * 좌표가 비었거나 위도·경도 범위를 벗어났으면 필드마다 건 제약이 이미 오류를 내므로 이 검사는 통과시킨다.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = InWeatherGrid.Validator.class)
@interface InWeatherGrid {

    String message() default "서비스 지역 밖 좌표입니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<InWeatherGrid, BakjiCreateRequest> {

        private static final double MAX_ABS_LATITUDE = 90;
        private static final double MAX_ABS_LONGITUDE = 180;

        @Override
        public boolean isValid(BakjiCreateRequest request, ConstraintValidatorContext context) {
            if (request == null || request.lat() == null || request.lng() == null) {
                return true;
            }
            if (Math.abs(request.lat()) > MAX_ABS_LATITUDE || Math.abs(request.lng()) > MAX_ABS_LONGITUDE) {
                return true;
            }
            if (WeatherGrid.covers(new GeoPoint(request.lat(), request.lng()))) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("lat")
                    .addConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("lng")
                    .addConstraintViolation();
            return false;
        }
    }
}
