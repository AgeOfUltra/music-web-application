package com.music.musicwebapplication.exception;

import com.music.musicwebapplication.utils.ErrorResponseUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(SongNotFoundException.class)
    public ResponseEntity<?> songControllerExceptionHandler(SongNotFoundException e){
        ErrorResponseUtil error = new ErrorResponseUtil(LocalDateTime.now(), HttpStatus.BAD_REQUEST.toString(),e.getMessage());
       return ResponseEntity.badRequest().body(error);
    }
}
