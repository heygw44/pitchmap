package com.pitchmap.member.api;

import com.pitchmap.member.domain.Email;
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
 * 이메일 도메인이 인증 메일을 받을 수 있는 모양인지({@link Email#isMailableDomain}) 검사한다. 점이 없는 도메인(localhost)과
 * IP 주소 도메인을 거른다.
 *
 * <p>이메일 형식 자체는 {@code @Email}이 검사한다. 형식이 틀린 값에 이 제약까지 오류를 내면 같은 필드에 오류가 두 개 나오므로,
 * '@' 앞이나 뒤가 비어서 도메인을 가릴 수 없는 값과 null은 여기서 통과시키고 {@code @Email}과 {@code @NotBlank}에 맡긴다.
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MailableEmail.Validator.class)
@interface MailableEmail {

    String message() default "이메일 도메인에 점이 있어야 하고 IP 주소는 쓸 수 없습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<MailableEmail, String> {

        @Override
        public boolean isValid(String email, ConstraintValidatorContext context) {
            return email == null
                    || Email.domainOf(email).map(Email::isMailableDomain).orElse(true);
        }
    }
}
