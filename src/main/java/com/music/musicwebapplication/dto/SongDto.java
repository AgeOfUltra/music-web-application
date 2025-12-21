package com.music.musicwebapplication.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SongDto {
    private String songName;
    private String fileName;
    private String movie;
    private String singer;
    private String songType;
    private String hero;
    private String heroine;
    private String language;
}
