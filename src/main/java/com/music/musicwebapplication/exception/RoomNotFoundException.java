package com.music.musicwebapplication.exception;

public class RoomNotFoundException extends RuntimeException{
    public RoomNotFoundException(String message) {
        super(message);
    }
}
