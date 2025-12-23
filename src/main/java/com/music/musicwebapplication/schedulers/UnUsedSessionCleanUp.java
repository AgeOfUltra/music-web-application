package com.music.musicwebapplication.schedulers;

import com.music.musicwebapplication.entity.UserSession;
import com.music.musicwebapplication.repo.UserSessionRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class UnUsedSessionCleanUp {

    private final UserSessionRepo sessionData;

    public UnUsedSessionCleanUp(UserSessionRepo sessionData) {
        this.sessionData = sessionData;
    }

    @Scheduled(initialDelay = 60*3*1000, fixedRate = 60*2*1000)
    public void cleanUpUnUsedSession(){

        log.info("Session cleaning process started");
        Optional<List<UserSession>> unUsedSession= sessionData.getUserSessionByRoomNameEmpty();

        if(unUsedSession.isEmpty()){
            log.info("No Un Used Session to CleanUp & Cleaning process completed!");
            return;
        }

        unUsedSession.get().stream()
                .filter(session ->
                        Duration.between(session.getLastAccessedAt(), LocalDateTime.now())
                                .toMinutes() > 3
                )
                .forEach(session -> {
                    log.info("Clearing session for Username {}",session.getUsername());
                    sessionData.delete(session);

                });

        log.info("Cleaning process completed!");
    }


}
