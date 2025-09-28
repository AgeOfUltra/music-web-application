package com.music.musicwebapplication.repo;

import com.music.musicwebapplication.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface RoomRepo extends JpaRepository<Room,Long> {

    Optional<Room> findRoomByRoomName(String roomName);
}
