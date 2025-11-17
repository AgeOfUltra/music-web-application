package com.music.musicwebapplication.dto;

import com.music.musicwebapplication.support.Status;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor(force = true)
public class ConfessDto {

    private final String initiatedBy;

    private final String senderOriginalName;

    private final String senderEmail;

    @NotBlank(message = "Please enter room name")
    private final String roomName;

    @NotBlank(message = "Please enter alias name")
    private final String receiverAlias;

    private final String confessType;

    @NotBlank(message = "Email cannot be blank")
    @Email(message = "provide an valid fieldName address")
    private final String email;

    @NotBlank(message = "Please enter your favourite song")
    private final String songName;

    @NotBlank(message = "Please enter singer name to identify the above song")
    private final String singerName;

    @NotBlank(message = "Please enter singer name to identify the song")
    @Size(min= 10,message = "Provide atleast 10 letters")
    private final String Message;

    private final Status status;
}
