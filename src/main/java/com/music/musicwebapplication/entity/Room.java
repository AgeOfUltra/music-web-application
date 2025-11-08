package com.music.musicwebapplication.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Entity
@Data
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;
    @Column(unique = true)
    private String roomName;
    private int maxCount;
    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL,orphanRemoval = true)
    private List<Participant> participant = new ArrayList<>();
}
