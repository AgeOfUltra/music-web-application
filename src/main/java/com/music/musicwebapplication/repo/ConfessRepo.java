package com.music.musicwebapplication.repo;

import com.music.musicwebapplication.entity.Confess;
import com.music.musicwebapplication.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConfessRepo extends JpaRepository<Confess,Long> {

    @Query("SELECT c from Confess c where c.roomHash = :roomHash")
    Optional<Confess> findByRoomHash(@Param("roomHash")String roomHash);

    @Query("SELECT c from Confess c where c.status = :status")
    Optional<List<Confess>> findByStatus(@Param("status") Status status);

    @Query("SELECT c from Confess c where c.initiatedBy = :initiatedBy")
    Optional<List<Confess>> findByInitiatedBy(@Param("initiatedBy") String initiatedBy);

}
