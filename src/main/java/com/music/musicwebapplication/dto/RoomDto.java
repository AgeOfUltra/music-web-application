package com.music.musicwebapplication.dto;


import lombok.Data;

import java.util.Set;


@Data
public class RoomDto {
    private long roomId;
    private String roomName;
    private int maxCount;
    private Set<ParticipantDto> participantDto;
}
