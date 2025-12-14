package com.music.musicwebapplication.repo;

import com.music.musicwebapplication.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface RoomRepo extends JpaRepository<Room,Long> {
    Optional<Room> findRoomByRoomName(@Param("roomName") String roomName);

    boolean existsByRoomName(String roomName);

    @Query("SELECT r FROM Room r LEFT JOIN FETCH r.participant WHERE r.roomName = :roomName")
    Optional<Room> findRoomWithParticipantsByRoomName(@Param("roomName") String roomName);

    @Query("SELECT r FROM Room r LEFT JOIN FETCH r.participant WHERE r.roomHash = :hash")
    Optional<Room> findRoomWithParticipantsByRoomHash(@Param("hash") String hash);

}
