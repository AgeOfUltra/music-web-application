package com.music.musicwebapplication.entity;

import com.music.musicwebapplication.support.Status;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class RequestSong {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private long id;

    private String songName;

    private String movieName;

    private String singerName;

    private Status status;

    private String note;
}
