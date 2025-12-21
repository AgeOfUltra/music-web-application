package com.music.musicwebapplication.dto;

import com.music.musicwebapplication.enums.Status;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ConfessContainerRequest {

    private String initiatedBy;

    @NotBlank(message = "Please enter you name to identification to recipient")
    private String senderOriginalName;

    private String senderEmail;

    @NotBlank(message = "Please enter room name")
    private String roomName;

    @NotBlank(message = "Please enter alias name")
    private String receiverAlias;

    private String confessType;

    @NotBlank(message = "Email cannot be blank")
    @Email(message = "provide an valid fieldName address")
    private String email;

    @NotBlank(message = "Please enter your favourite song")
    private String songName;

    private String singerName;

    @NotBlank(message = "Please enter singer name to identify the song")
    @Size(min= 20,message = "Provide at-least 20 letters to express")
    private String message;

    private Status status;
//to handle while repose return not for the request
    private String roomHash;
}
