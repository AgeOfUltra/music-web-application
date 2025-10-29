
package com.music.musicwebapplication.utils.validation;

import com.music.musicwebapplication.entity.User;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueEmailValidator.class)
@Documented
public @interface UniqueValidator {
    String message() default "This element already exists";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    Class<User> entity();

    String email();
}