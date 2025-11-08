package com.music.musicwebapplication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MusicWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(MusicWebApplication.class, args);
    }

}
