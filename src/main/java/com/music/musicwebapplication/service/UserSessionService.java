package com.music.musicwebapplication.service;

import com.music.musicwebapplication.entity.UserSession;
import com.music.musicwebapplication.repo.UserSessionRepo;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class UserSessionService {
    private final UserSessionRepo repo;

    public UserSessionService(UserSessionRepo repo) {
        this.repo = repo;
    }

    public boolean saveSession(String token, String username) {
        UserSession session = new UserSession();
        session.setTokenId(token);
        session.setUsername(username);
        session.setVisitedDashBoard(false);
        boolean b = (long) repo.findAllByUsername(username).size() == 0;

        UserSession savedSession = null;

        if (b) {
            savedSession = repo.save(session);
            return savedSession.getId() >= 0;
        }
        return false;
    }

    public Optional<Map<String, ?>> updateDashBoardEntry(String username) {
        UserSession currentSession = repo.getUserSessionByUsername(username);

        Map<String, Object> reponseDataHandlerMap = new HashMap<>();

        if (currentSession == null) {
            reponseDataHandlerMap.put("ERROR", "User not found");
            return Optional.of(reponseDataHandlerMap);
        }

        if (currentSession.isVisitedDashBoard()) {
            Object obj = "user already visited";
            reponseDataHandlerMap.put("ALREADY_VISITED", obj);
            return Optional.of(reponseDataHandlerMap);
        }

        currentSession.setVisitedDashBoard(true);
        repo.save(currentSession);

        reponseDataHandlerMap.put("UPDATED", true);

        return Optional.of(reponseDataHandlerMap);
    }

    @PreDestroy
    private void clearUserSessions() {
        log.info("All rooms deletion started...");
        repo.deleteAll();
        log.info("All rooms deletion completed...");
    }
}
