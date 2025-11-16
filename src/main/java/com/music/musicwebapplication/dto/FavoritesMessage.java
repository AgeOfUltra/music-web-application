package com.music.musicwebapplication.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FavoritesMessage {
    private String action;          // "ADD", "REMOVE", "CLEAR", "SYNC"
    private List<FavoriteSong> favorites;
    private FavoriteSong song;      // The song that was added/removed (for notifications)
    private String username;        // Who made the change
    private Long timestamp;
}