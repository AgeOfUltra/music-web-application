package com.music.musicwebapplication.utils.validation;

import com.music.musicwebapplication.repo.UserRepo;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UniqueElementValidator implements ConstraintValidator<UniqueValidator,String> {
    @Autowired
    private UserRepo userRepository;

    private String fieldName;

    @Override
    public void initialize(UniqueValidator annotation) {
        this.fieldName = annotation.fieldName();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        try {
            boolean exists = switch(fieldName) {
                case "username" -> userRepository.existsByUsername(value);
                case "email" -> userRepository.existsByEmail(value);
                default -> false;
            };

            return !exists;
        } catch (Exception e) {
            return true;
        }
    }
}
