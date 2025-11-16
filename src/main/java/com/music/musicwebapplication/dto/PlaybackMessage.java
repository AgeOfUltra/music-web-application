package com.music.musicwebapplication.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlaybackMessage {
    private String action;          // "PLAY", "PAUSE", "RESUME", "ERROR"
    private String songFileName;
    private String songName;
    private String hero;
    private String heroine;
    private String language;
    private String movie;
    private String singer;
    private String sender;
    private String controller;
    private Integer timestamp;      // Playback position in milliseconds
    private String content;         // Error message content (when action = "ERROR")
}

