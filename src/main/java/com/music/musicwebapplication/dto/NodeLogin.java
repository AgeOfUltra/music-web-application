package com.music.musicwebapplication.dto;

import lombok.Data;

@Data
public class NodeLogin {
    private String roomId;
    private String sender;
    private String passcode;
}
