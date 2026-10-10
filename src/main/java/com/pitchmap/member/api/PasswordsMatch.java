package com.pitchmap.member.api;

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
 * 비밀번호와 비밀번호 확인 값이 같은지 검사한다. 다르면 확인 필드({@link ConfirmedPassword#confirmationFieldName()})에 오류를 붙인다.
 *
 * <p>둘 중 하나가 null이면 여기서 통과시킨다. 빠진 값은 {@code @NotNull}이 알리므로, 같은 필드에 오류가 두 개 나오지 않게 하려는 것이다.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordsMatch.Validator.class)
@interface PasswordsMatch {

    String message() default "비밀번호 확인이 비밀번호와 다릅니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<PasswordsMatch, ConfirmedPassword> {

        @Override
        public boolean isValid(ConfirmedPassword value, ConstraintValidatorContext context) {
            if (value == null || value.passwordToConfirm() == null || value.passwordConfirmation() == null) {
                return true;
            }
            if (value.passwordToConfirm().equals(value.passwordConfirmation())) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode(value.confirmationFieldName())
                    .addConstraintViolation();
            return false;
        }
    }
}
