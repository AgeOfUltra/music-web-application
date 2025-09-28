package com.music.musicwebapplication.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Participant {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;
    private String userName;
    private boolean isOrganizer;

    @ManyToOne
    @JoinColumn(name = "roomId")
    private Room room;
}
