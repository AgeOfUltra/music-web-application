package com.music.musicwebapplication.repo;

import com.music.musicwebapplication.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ParticipantRepo extends JpaRepository<Participant,Long> {

    long countParticipantByRoomId(long roomId);
}
