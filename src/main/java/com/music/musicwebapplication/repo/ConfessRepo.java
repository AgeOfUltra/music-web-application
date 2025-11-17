package com.music.musicwebapplication.repo;

import com.music.musicwebapplication.entity.Confess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfessRepo extends JpaRepository<Confess,Long> {

}
