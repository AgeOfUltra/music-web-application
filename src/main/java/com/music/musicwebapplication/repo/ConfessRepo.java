package com.music.musicwebapplication.repo;

import com.music.musicwebapplication.entity.Confess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConfessRepo extends JpaRepository<Confess,Long> {

    @Query("SELECT c from Confess c where c.roomHash = :roomHash")
    Optional<List<Confess>> findByRoomHash(@Param("roomHash")String roomHash);
}
