package com.music.musicwebapplication.repo;

import com.music.musicwebapplication.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserSessionRepo extends JpaRepository<UserSession,Long> {
    UserSession getUserSessionByUsername(String username);

    List<UserSession> findAllByUsername(String username);
}
