package com.music.musicwebapplication.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Participant {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;
    private String userName;
    private boolean organizer;

    @ManyToOne
    @JoinColumn(name = "roomId",nullable = false)
    @JsonIgnore
    private Room room;
}
