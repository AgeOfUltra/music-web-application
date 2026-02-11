package com.music.musicwebapplication.dto;

import com.music.musicwebapplication.enums.Status;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConfessDto {

    private String initiatedBy;

    @NotBlank(message = "Please enter you name to identification to recipient")
    private String senderOriginalName;

    private String senderEmail;

    @NotBlank(message = "Please enter room name")
    @Pattern(regexp = "^[a-zA-Z0-9]{3,20}$", message = "Must be 3-20 alphanumeric characters")
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
    @Size(min= 650,message = "Provide at-least 100 words to express you feelings")
    private String message;


//    for back-end response
    private Status status;


    private String roomHash;

    // if status is rejected admin should provide reason
    private String note;


}
