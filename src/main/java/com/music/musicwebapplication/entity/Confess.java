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
@Setter
@Getter
@NoArgsConstructor(force = true)
public class Confess {
    @Id
    private long id;

    private final String initiatedBy;

    private final String senderOriginalName;

    private final String senderEmail;

    private final String receiverAlias;

    private final String confessType;

    private final String email;

    @Column(unique = true)
    private final String passcode;

    private final String songName;

    private final String singerName;

    private final String Message;

    private final Duration activeTime;

    private final Timestamp createdAt;

    @Enumerated(EnumType.STRING)
    private final Status status;

    private final String roomName;

    @Column(unique = true)
    private final String roomHash;

    @Enumerated(EnumType.STRING)
    private Role role;

}
