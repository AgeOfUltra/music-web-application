package com.music.musicwebapplication.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class SongContainer {
    @NotNull(message = "Please select a file")
    private MultipartFile file;

    @NotBlank(message = "Song name is required")
    private String songName;

    @NotBlank(message = "File name is required")
    private String fileName;

//    @NotBlank(message = "Movie name is required")
    private String movie;

    @NotBlank(message = "Singer name is required")
    private String singer;

    @NotBlank(message = "Song type is required")
    private String songType;

    @NotBlank(message = "Language is required")
    private String language;

    @NotBlank(message = "Hero name required")
    private String hero;

    private String heroine;
}
