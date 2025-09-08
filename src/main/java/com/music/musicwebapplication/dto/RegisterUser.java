package com.music.musicwebapplication.dto;

import com.music.musicwebapplication.support.Role;
import lombok.Data;

@Data
public class RegisterUser {
    private String username;
    private String password;
    private String email;
    private Role role;
}
