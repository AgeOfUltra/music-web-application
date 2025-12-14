package com.music.musicwebapplication.repo;

import com.music.musicwebapplication.entity.RequestSong;
import com.music.musicwebapplication.entity.Song;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SongRepo extends JpaRepository<Song,Long> {
    Optional<Song> findSongBySongName(String songName);

    Optional<Song> findSongByFileName(String objectKey);

//    @Query("SELECT s from Song s where lower(s.songName) = :query or lower(s.movie) = :query")
@Query("SELECT s FROM Song s WHERE LOWER(s.songName) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(s.movie) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Song> findBySongNameContainingIgnoreCase(@Param("query") String query);

    boolean existsByFileName(String fileName);

    boolean existsBySongName(String songName);

    @Query("SELECT s.fileName from Song s")
    List<String> findAllSongBySongName();

    @Query("SELECT s from RequestSong s where s.requestor = :requestor")
    Optional<List<RequestSong>> findSongsByRequestor(@Param("requestor") String requestor);
}
