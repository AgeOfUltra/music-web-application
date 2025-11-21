package com.music.musicwebapplication.dto;

import lombok.Data;

@Data
public class GuestLogin {
    private String roomId;
    private String sender;
    private String passcode;
}
