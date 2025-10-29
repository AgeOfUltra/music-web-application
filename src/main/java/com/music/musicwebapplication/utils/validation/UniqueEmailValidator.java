package com.music.musicwebapplication.utils.validation;

import com.music.musicwebapplication.entity.User;
import com.music.musicwebapplication.repo.UserRepo;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UniqueEmailValidator implements ConstraintValidator<UniqueValidator,String> {
    @Autowired
    private UserRepo repo;
    private Class<User> userClass;
    private String email;
    @Override
    public boolean isValid(String s, ConstraintValidatorContext context) {
        if(s==null){
            return false;
        }


        return !repo.existsByEmail(email,s);
    }
}
