package dev.gavinfecko.triagedesk.identity.application;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** The password must not be on the list of commonly breached passwords. */
@Documented
@Constraint(validatedBy = BreachedPasswords.Validator.class)
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface NotBreached {

    String message() default "is a commonly breached password; choose another";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
