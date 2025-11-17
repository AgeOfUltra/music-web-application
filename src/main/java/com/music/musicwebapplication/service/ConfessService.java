package com.music.musicwebapplication.service;

import com.music.musicwebapplication.repo.ConfessRepo;
import org.springframework.stereotype.Service;

@Service
public class ConfessService {

    private final ConfessRepo repo;

    public ConfessService(ConfessRepo repo) {
        this.repo = repo;
    }


}
