package com.music.musicwebapplication.dto;

import com.music.musicwebapplication.support.PlayBackAction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlaybackMessage {
    private PlayBackAction action;
    private String songFileName;
    private String songName;
    private Long timestamp; // For seeking
    private String controller; // Who initiated the action
}


