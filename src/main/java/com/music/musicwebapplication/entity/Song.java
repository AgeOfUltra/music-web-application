package com.music.musicwebapplication.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Song {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    @Column(unique = true)
    private String songName;
    @Column(unique = true)
    private String fileName;
    private String movie;
    private String singer;
    private String songType;
    private String hero;
    private String heroine;
    private String language;
    private String url;
}
