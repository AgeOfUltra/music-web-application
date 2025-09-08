package com.music.musicwebapplication.service;

import com.music.musicwebapplication.dto.RegisterUser;
import com.music.musicwebapplication.entity.User;
import com.music.musicwebapplication.repo.UserRepo;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class RegisterUserService {

    private final UserRepo repo;

    private final ModelMapper mapper;
    private final PasswordEncoder encoder;

    @Autowired
    RegisterUserService(UserRepo repo, ModelMapper mapper,PasswordEncoder encoder){
        this.repo = repo;
        this.mapper= mapper;
        this.encoder= encoder;
    }

    public String registerUser(RegisterUser newUser){

        Optional<User> existing = repo.findByUsername(newUser.getUsername());

        if(existing.isEmpty()){
           User user = mapper.map(newUser, User.class);
           user.setPassword(encoder.encode(user.getPassword()));
           repo.save(user);
           return "Registration Successfully";
        }else{
            return "Please try new User Details. User Already exist";
        }
    }

}
