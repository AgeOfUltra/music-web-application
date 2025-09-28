package com.music.musicwebapplication.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Set;

@Entity
@Data
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long roomId;
    private String roomName;
    private int maxCount;
    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL,orphanRemoval = true)
    private Set<Participant> participant;
}
