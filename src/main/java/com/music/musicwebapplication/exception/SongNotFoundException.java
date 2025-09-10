package com.music.musicwebapplication.exception;

public class SongNotFoundException extends RuntimeException{
    public SongNotFoundException(String message) {
        super(message);
    }
}
