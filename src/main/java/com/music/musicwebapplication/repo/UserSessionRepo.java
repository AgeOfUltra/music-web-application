package com.music.musicwebapplication.repo;

import com.music.musicwebapplication.entity.UserSession;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserSessionRepo extends JpaRepository<UserSession,Long> {
    boolean existsByUsername(String username);

    boolean existsByToken(String token);

    Optional<UserSession> findByUsername(String username);


    void deleteByUsername(String username);


    @Query("SELECT s from UserSession s where s.username = :username and s.roomName = :roomName")
    UserSession findByUsernameAndRoomName(String username, String roomName);
}
