package com.music.musicwebapplication.entity;

import com.music.musicwebapplication.support.Status;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class RequestSong {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private long id;

    private String requestor;

    private String songName;

    private String movieName;

    private String singerName;

    @Enumerated(EnumType.STRING)
    private Status status; // SENT -> DONE || REJECTED

    private String note;
}
