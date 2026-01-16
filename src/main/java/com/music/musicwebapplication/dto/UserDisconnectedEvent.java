package com.music.musicwebapplication.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserDisconnectedEvent {

    private final String username;
    private final String roomName;
    private final String sessionId;


}

