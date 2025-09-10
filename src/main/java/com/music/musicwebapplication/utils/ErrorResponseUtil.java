package com.music.musicwebapplication.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponseUtil {
    LocalDateTime dateTime;
    String errorCode;
    String errorDetails;
}
