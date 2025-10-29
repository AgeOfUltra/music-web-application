package com.music.musicwebapplication.dto;

import com.music.musicwebapplication.entity.User;
import com.music.musicwebapplication.support.Role;
import com.music.musicwebapplication.utils.validation.UniqueValidator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterUser {

    @NotBlank(message = "username cannot be blank")
    @Size(min = 5,message = "minimum 5 letter username")
    private String username;

    @NotBlank(message = "Password cannot be empty")
    @Size(min = 8, max = 20, message = "Password must be between 8 and 20 characters")
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).*$",
            message = "Password must contain at least one digit, one lowercase letter, one uppercase letter, and one special character")
    private String password;

    @NotBlank(message = "Email cannot be blank")
    @Email(message = "provide an valid email address")
    @UniqueValidator(
            entity = User.class,
            email = "email",
            message = "This email is already registered"
    )
    private String email;

    private Role role;
}
