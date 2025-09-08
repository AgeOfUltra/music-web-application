package com.music.musicwebapplication.support;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;


import java.util.Set;

@Getter
@RequiredArgsConstructor
public enum Role {
    ADMIN(Set.of(Permissions.MUSIC_READ,Permissions.MUSIC_WRITE)),
    LISTENER(Set.of(Permissions.MUSIC_READ));

    private final Set<Permissions> permissions;

}
