package com.music.musicwebapplication.service;

import com.music.musicwebapplication.entity.Confess;
import com.music.musicwebapplication.entity.RequestSong;
import com.music.musicwebapplication.entity.UserSession;
import com.music.musicwebapplication.repo.ConfessRepo;
import com.music.musicwebapplication.repo.SongRequestRepo;
import com.music.musicwebapplication.repo.UserSessionRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UtilityService {
    private final UserSessionRepo repo;
    private final SongRequestRepo repoSong;
    private final ConfessRepo repoConfess;

    @Value("${app.music.inactive.session-time:40}")
    private int minutes;

    public UtilityService(UserSessionRepo repo, SongRequestRepo repoSong, ConfessRepo repoConfess) {
        this.repo = repo;
        this.repoSong = repoSong;
        this.repoConfess = repoConfess;
    }


    public List<UserSession> getInactiveUsers(){

        LocalDateTime threshold = LocalDateTime.now().minusMinutes(minutes);

        List<UserSession> inActiveUsers = repo.getUserSessionByRoomNameEmptyAndLastAccessedAt(threshold);

        if(inActiveUsers.isEmpty()){
            log.info("All are active user only.");
            return inActiveUsers;
        }

        inActiveUsers =inActiveUsers.stream().filter(u->
            !checkIsUserSentConfess(u.getUsername(),threshold) && !checkIsUserSentRequest(u.getUsername(),threshold)
        ).collect(Collectors.toList());

        // !false && !true
//        true && false
//

        log.info("Inactive user are   : {}",inActiveUsers.isEmpty() ? "null" : inActiveUsers);
        return inActiveUsers;
    }

    private boolean checkIsUserSentRequest(String user,LocalDateTime threshold){
//        if created true else false

        Optional<RequestSong> request = repoSong.getRequestSongByCreatedAtAfterAndRequestor(user,threshold);
        log.info("Requested Song : {}",request.isEmpty() ? "null" : request.get());
        return request.isPresent(); // true

    }
    private boolean checkIsUserSentConfess(String user,LocalDateTime threshold){
//        if created true else false

        Optional<Confess> request = repoConfess.getConfessByCreatedAtAfterAndInitiatedBy(user ,threshold);
        log.info("RequestedConfess : {}",request.isEmpty() ? "null" : request.get());
        return request.isPresent(); //false

    }
}
