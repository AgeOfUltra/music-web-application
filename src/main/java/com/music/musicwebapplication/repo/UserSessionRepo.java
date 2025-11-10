package com.music.musicwebapplication.repo;

import com.music.musicwebapplication.entity.UserSession;
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

    @Modifying
    @Query("UPDATE UserSession u SET u.roomName = :roomName WHERE u.username = :username")
    void updateRoomName(@Param("username") String username,
                        @Param("roomName") String roomName);

    void deleteByUsername(String username);
}
