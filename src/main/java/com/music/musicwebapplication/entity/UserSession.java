package com.music.musicwebapplication.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class UserSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String token;
    private String roomName;
}
