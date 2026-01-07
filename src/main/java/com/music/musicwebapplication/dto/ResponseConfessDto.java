package com.music.musicwebapplication.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResponseConfessDto {

    private String receiverAlias;

    private String confessType;

    private String roomName;

    private String note;
}
