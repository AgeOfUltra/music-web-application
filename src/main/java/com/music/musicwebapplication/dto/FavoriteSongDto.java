package com.music.musicwebapplication.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FavoriteSongDto {
    private String fileName;
    private String songName;
    private String hero;
    private String heroine;
    private String singer;
    private String movie;
    private String language;
    private String requestedBy;     // Username who added this favorite
    private Long requestedAt;       // Timestamp when added


}