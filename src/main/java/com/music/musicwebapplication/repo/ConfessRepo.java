package com.music.musicwebapplication.repo;

import com.music.musicwebapplication.entity.Confess;
import com.music.musicwebapplication.entity.RequestSong;
import com.music.musicwebapplication.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConfessRepo extends JpaRepository<Confess,Long> {

    @Query("SELECT c from Confess c where c.roomHash = :roomHash")
    Optional<Confess> findByRoomHash(@Param("roomHash")String roomHash);

    @Query("SELECT c from Confess c where c.status = :status")
    List<Confess> findByStatus(@Param("status") Status status);

    @Query("SELECT c from Confess c where c.initiatedBy = :initiatedBy")
    List<Confess> findByInitiatedBy(@Param("initiatedBy") String initiatedBy);

    @Query("Select c from Confess c where c.createdAt > :time and c.initiatedBy= :username")
    List<Confess> findRecordsWithIn24Hours(@Param("time") LocalDateTime time ,@Param("username") String username);

//    Optional<RequestSong> getConfessByCreatedAtBefore(LocalDateTime createdAtBefore);


    @Query("select s from Confess s where s.initiatedBy = :user and s.createdAt >= :threshold")
    Optional<Confess> getConfessByCreatedAtAfterAndInitiatedBy(@Param("user") String user, @Param("threshold") LocalDateTime threshold);
}
