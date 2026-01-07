package com.music.musicwebapplication.repo;

import com.music.musicwebapplication.entity.RequestSong;
import com.music.musicwebapplication.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SongRequestRepo extends JpaRepository<RequestSong,Long> {

    Optional<List<RequestSong>> findRequestSongByStatus(Status status);

    Optional<RequestSong> findRequestSongBySongName(String songName);

    @Query("Select s from RequestSong s where s.status = 'UPLOADED' or s.status = 'REJECTED'")
    Optional<List<RequestSong>> findRequestSongByUploadedAndRejected();
}
