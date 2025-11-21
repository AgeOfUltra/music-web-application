package com.music.musicwebapplication.entity;

import com.music.musicwebapplication.support.Role;
import com.music.musicwebapplication.support.Status;
import jakarta.persistence.*;
import lombok.*;

import java.sql.Timestamp;
import java.time.Duration;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
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

    private String Message;

    private Duration activeTime;

    private Timestamp createdAt;

    @Enumerated(EnumType.STRING)
    private Status status;

    private String roomName;

    @Column(unique = true)
    private String roomHash;

    @Enumerated(EnumType.STRING)
    private Role role;

}
