package com.music.musicwebapplication.service;

import com.music.musicwebapplication.entity.UserSession;
import com.music.musicwebapplication.repo.UserSessionRepo;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.Optional;


@Slf4j
@Service
public class UserSessionService {
    private final UserSessionRepo repo;

    @Autowired
    public UserSessionService(UserSessionRepo repo) {
        this.repo = repo;
    }

    public boolean saveSession(String token, String username) {
        if (repo.existsByUsername(username)) {
            return false;
        }

        UserSession session = new UserSession();
        session.setToken(token);
        session.setUsername(username);
        session.setRoomName(null);
        repo.save(session);

        return true;
    }
    @Transactional
    public void updateRoomName(String username, String roomName) {
        repo.updateRoomName(username, roomName);
    }
    @Transactional
    public void clearRoomForUser(String username) {
        repo.updateRoomName(username, null);
    }


    public Optional<String> getRoomName(String username) {
        return repo.findByUsername(username).map(UserSession::getRoomName);
    }
    public Optional<String> getToken(String username) {
        return repo.findByUsername(username).map(UserSession::getToken);
    }
    @Transactional
    public void deleteUserSession(String username) {
        repo.deleteByUsername(username);
    }
    @PreDestroy
    private void clearUserSessions() {
        log.info("All rooms deletion started...");
        repo.deleteAll();
        log.info("All rooms deletion completed...");
    }
}

