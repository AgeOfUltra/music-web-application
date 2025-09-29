package com.music.musicwebapplication.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Data
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    private String roomName;
    private int maxCount;
    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL,orphanRemoval = true)
    private List<Participant> participant = new ArrayList<>();
}
