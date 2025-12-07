package com.music.musicwebapplication.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RequestSong {

    private String songName;

    private String movieName;

    private String singerName;
}
