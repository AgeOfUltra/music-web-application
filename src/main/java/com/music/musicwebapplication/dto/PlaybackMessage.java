package com.music.musicwebapplication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.music.musicwebapplication.support.PlayBackAction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlaybackMessage {
    // Action types
    private String action;          // PLAY, PAUSE, RESUME, STOP, SEEK, SYNC, ERROR

    // Song information
    private String songFileName;    // File name for streaming
    private String songName;        // Display name
    private String hero;            // Actor/Artist
    private String heroine;         // Co-star
    private String language;        // Language

    // Control information
    private String sender;          // Who sent this message
    private String controller;      // Who is controlling playback
    private long timestamp;         // Current playback time in ms

    // Error information
    private String content;         // Content (for error messages)
}


