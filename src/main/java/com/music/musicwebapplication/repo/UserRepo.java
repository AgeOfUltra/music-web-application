package com.music.musicwebapplication.repo;

import com.music.musicwebapplication.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepo extends JpaRepository<User,Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    User findByEmail(String email);

    @Query("SELECT u from User u where u.createdAt < :cutoffTime and u.isVerified=false and u.isEmailSent= true")
    Optional<List<User>> findRecordsOlderThan5Minutes(@Param("cutoffTime") LocalDateTime cutoffTime);
}
