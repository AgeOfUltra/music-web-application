package com.music.musicwebapplication.dto;

import com.music.musicwebapplication.entity.Song;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SkipMessage {
    private String action; // "NEXT" or "PREVIOUS"
    private Song song;
    private int index;
    private String controller;
    private long timestamp;

    // Getters and setters
}
