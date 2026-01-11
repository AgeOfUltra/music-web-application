package com.music.musicwebapplication.service;

import com.music.musicwebapplication.dto.RegisterUser;
import com.music.musicwebapplication.entity.User;
import com.music.musicwebapplication.repo.UserRepo;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.Optional;

@Service
@Slf4j
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

    public boolean registerUser(RegisterUser newUser){

        Optional<User> existing = repo.findByUsername(newUser.getUsername());

        if(existing.isEmpty()){
           User user = mapper.map(newUser, User.class);
           user.setPassword(encoder.encode(user.getPassword()));
           try{
               repo.save(user);
               return true;
           }catch (Exception e){
               log.error("Registration failed ! {}",e.getMessage());

               return false;
           }

        }else{
            return false;
        }
    }

    public String getUserEmail(String username){
        Optional<User> user = repo.findByUsername(username);
        if(user.isEmpty()){
            return "";
        }
        return user.get().getEmail();
    }

}
