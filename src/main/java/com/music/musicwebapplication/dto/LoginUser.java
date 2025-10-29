package com.music.musicwebapplication.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginUser {
    @NotBlank(message = "username cannot be blank")
    @Size(min = 5,message = "minimum 5 letter username")
    private String username;

    @NotBlank(message = "Password cannot be empty")
    @Size(min = 8, max = 20, message = "Password must be between 8 and 20 characters")
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).*$",
            message = "Password must contain at least one digit, one lowercase letter, one uppercase letter, and one special character")
    private String password;
}
