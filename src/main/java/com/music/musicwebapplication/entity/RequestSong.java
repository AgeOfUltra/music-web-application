package com.music.musicwebapplication.entity;

import com.music.musicwebapplication.enums.Status;
import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Data
@EntityListeners(AuditingEntityListener.class)
public class RequestSong {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String requestor;

    private String email;

    private String songName;

    private String movieName;

    private String singerName;

    @Enumerated(EnumType.STRING)
    private Status status; // SENT -> UPLOADED || REJECTED -> DONE

    private String note;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime lastAccessedAt;
}
