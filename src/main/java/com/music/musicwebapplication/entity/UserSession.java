package com.music.musicwebapplication.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private long id;

    private String username;

    @Column(unique = true)
    private String tokenId;

    private boolean visitedDashBoard;
}
