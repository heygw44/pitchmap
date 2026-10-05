package com.pitchmap.member.api;

import com.pitchmap.member.domain.Member;
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
 * 닉네임 규칙({@link Member#isValidNickname})을 지키는지 검사한다. null은 통과시키므로 필수 여부는 {@code @NotBlank}가 가린다.
 * {@code @Size}를 쓰지 않는 이유: 그것은 UTF-16 문자 수로 세서, 이모지처럼 문자 두 개로 이뤄진 글자의 길이를 도메인 규칙과 다르게 센다.
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidNickname.Validator.class)
@interface ValidNickname {

    String message() default Member.NICKNAME_RULE_MESSAGE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidNickname, String> {

        @Override
        public boolean isValid(String nickname, ConstraintValidatorContext context) {
            return nickname == null || Member.isValidNickname(nickname);
        }
    }
}
