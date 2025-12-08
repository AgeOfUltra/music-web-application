package com.music.musicwebapplication.repo;

import com.music.musicwebapplication.dto.RequestSongDto;
import com.music.musicwebapplication.entity.RequestSong;
import com.music.musicwebapplication.support.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SongRequestRepo extends JpaRepository<RequestSong,Long> {

    Optional<List<RequestSongDto>> findRequestSongByStatus(Status status);

    Optional<RequestSong> findRequestSongBySongName(String songName);
}
