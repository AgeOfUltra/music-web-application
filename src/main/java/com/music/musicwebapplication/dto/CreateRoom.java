package com.music.musicwebapplication.dto;

import com.music.musicwebapplication.utils.validation.UniqueValidator;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CreateRoom {
    @NotBlank(message = "Room name is required")
    @UniqueValidator(
            fieldName = "roomName",
            message = "Room name already exist"
    )
    private String roomName;

    @NotNull(message = "invalid count of participants")
    @Min(value = 2, message = "Minimum participants should be 2")
    @Max(value = 6, message = "Maximum participants should be 6")
    private int maxCount;

    private String createdBy;
}
