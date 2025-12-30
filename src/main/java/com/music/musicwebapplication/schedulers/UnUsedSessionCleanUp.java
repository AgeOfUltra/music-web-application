package com.music.musicwebapplication.schedulers;

import com.music.musicwebapplication.entity.UserSession;
import com.music.musicwebapplication.service.PublicLoginService;
import com.music.musicwebapplication.service.UserSessionService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.ModelAndView;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class UnUsedSessionCleanUp {

    private final UserSessionService sessionService;
    private final PublicLoginService loginService;

    public UnUsedSessionCleanUp(UserSessionService sessionService, PublicLoginService loginService) {
        this.sessionService = sessionService;
        this.loginService = loginService;
    }

    @Scheduled(initialDelay = 60*3*1000, fixedRate = 60*2*1000)
    public void cleanUpUnUsedSession(){

        log.info("Session cleaning process started");
        Optional<List<UserSession>> unUsedSession= sessionService.getUserSessionByEmptyRoom() ;

        if(unUsedSession.isEmpty()){
            log.info("No Un Used Session to CleanUp & Cleaning process completed!");
            return;
        }

        unUsedSession.get().stream()
                .filter(session ->
                        Duration.between(session.getLastAccessedAt(), LocalDateTime.now())
                                .toMinutes() > 40
                )
                .forEach(session -> {
                    log.info("Clearing session from Db and Cache for Username {}",session.getUsername());
                    sessionService.deleteUserSession(session.getUsername());
                });

        log.info("Cleaning process completed!");
    }

//    public void cleanUpExpiredSession(ModelAndView view){
//        log.info("Process started for cleaning inactive accounts");
//
//        Optional<List<UserSession>> inactiveSession = Optional.of(sessionService.getInactiveUsers());
//        if(inactiveSession.isEmpty()){
//            log.info("No inactive sessions");
//            return;
//        }
//
//        inactiveSession.get().forEach(loginService::logout);
//
//    }


}
