package com.music.musicwebapplication.dto;

import com.music.musicwebapplication.enums.Status;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResponseSongDto {
    private String songName;

    private String movieName;

    private String singerName;

    private Status status;

    private String note;
}
