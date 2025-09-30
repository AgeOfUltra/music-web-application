package com.music.musicwebapplication.dto;

import lombok.Data;

@Data
public class ParticipantDto {
    private long id;
    private String userName;
    private boolean isOrganizer;

}
