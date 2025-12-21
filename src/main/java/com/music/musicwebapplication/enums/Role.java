package com.music.musicwebapplication.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;


import java.util.Set;

@Getter
@RequiredArgsConstructor
public enum Role {
    ADMIN(Set.of(Permissions.MUSIC_READ,Permissions.MUSIC_WRITE)),
    LISTENER(Set.of(Permissions.MUSIC_READ)),
    GUEST(Set.of(Permissions.MUSIC_READ));

    private final Set<Permissions> permissions;

}
