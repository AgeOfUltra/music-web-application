package com.music.musicwebapplication.service;

import com.music.musicwebapplication.entity.User;
import com.music.musicwebapplication.repo.UserRepo;
import com.music.musicwebapplication.support.Role;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class InitiateAdmin {
    @Bean
    public CommandLineRunner init(UserRepo userRepo, PasswordEncoder passwordEncoder){
        return args -> {
            if(userRepo.findByUsername("admin").isEmpty()){
                User admin = new User();
                admin.setUsername("admin");
                admin.setEmail("admin@musicChat.com");
                admin.setPassword(passwordEncoder.encode("passis@123"));
                admin.setRole(Role.ADMIN);
                userRepo.save(admin);

                System.out.println("Admin users is created.");
            }
        };
    }
}
