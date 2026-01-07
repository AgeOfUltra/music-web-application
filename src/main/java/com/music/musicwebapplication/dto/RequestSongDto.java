package com.music.musicwebapplication.dto;

import com.music.musicwebapplication.enums.Status;
import com.music.musicwebapplication.utils.validation.UniqueValidator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RequestSongDto {

    private String requestor;

    private String email;

    @NotBlank(message = "song name cannot be blank")
    @Size(min = 5,message = "song should be at-least 3 characters")
    @UniqueValidator(
            fieldName = "songName",
            message = "This song name already exist"
    )
    private String songName;

    private String movieName;

    @NotBlank(message = "song name cannot be blank")
    private String singerName;

//    this both are for response.
    private Status status;

    private String note;
}
