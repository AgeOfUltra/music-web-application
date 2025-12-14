package com.music.musicwebapplication.entity;

import com.music.musicwebapplication.support.Role;
import com.music.musicwebapplication.support.Status;
import jakarta.persistence.*;
import lombok.*;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Confess {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private long id;

    private String initiatedBy;

    private String senderOriginalName;

    private String senderEmail;

    private String receiverAlias;

    private String confessType;

    private String email;

    @Column(unique = true)
    private String passcode;

    private String songName;

    private String singerName;

    @Column(columnDefinition = "TEXT")
    private String message;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(insertable = false)
    private LocalDateTime modifiedAt;

    @Enumerated(EnumType.STRING)
    private Status status;

    private String roomName;

    @Column(unique = true)
    private String roomHash;

    @Enumerated(EnumType.STRING)
    private Role role;

}
