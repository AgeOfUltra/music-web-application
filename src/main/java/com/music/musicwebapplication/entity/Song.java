package com.music.musicwebapplication.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class Song {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;
    private String songName;
    private String fileName;
    private String movie;
    private String singer;
    private String songType;
    private String hero;
    private String heroine;
    private String language;
    private String url;
}
