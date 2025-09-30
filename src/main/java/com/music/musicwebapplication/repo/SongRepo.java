package com.music.musicwebapplication.repo;

import com.music.musicwebapplication.entity.Song;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SongRepo extends JpaRepository<Song,Long> {
    Optional<Song> findSongBySongName(String songName);

    Optional<Song> findSongByFileName(String objectKey);

    @Query("select songName from Song")
    Optional<List<String>> findAllBySongName();
}
