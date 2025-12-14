package com.music.musicwebapplication.dto;

import lombok.Data;

@Data
public class RoomJoin {
    private String roomName;
    private String roomCode;
    private String passCode;
    private String participantName;
}
