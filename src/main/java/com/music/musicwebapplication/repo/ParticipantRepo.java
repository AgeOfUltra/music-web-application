package com.music.musicwebapplication.repo;

import com.music.musicwebapplication.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipantRepo extends JpaRepository<Participant,Long> {
}
