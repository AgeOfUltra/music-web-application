package com.music.musicwebapplication.chatDto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaybackState {

    // Song information
    private String songFileName;
    private String songName;
    private String hero;
    private String heroine;
    private String singer;
    private String movie;
    private String language;

    // Playback control
    private String organizer;          // Username of the organizer controlling playback
    private long timestamp;            // Playback position in milliseconds
    private long serverTime;

    @JsonProperty("isPlaying")
    private boolean isPlaying;         // Is any song currently playing

    @JsonProperty("isPaused")
    private boolean isPaused;          // Is the playback currently paused

    /**
     * Calculate the current playback position accounting for elapsed time
     * @return Current timestamp in milliseconds
     */
    public long getCurrentTimestamp() {
        if (isPaused) {
            return timestamp;
        }

        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - serverTime;
        return timestamp + elapsed;
    }

    /**
     * Check if the playback state is valid and can be synced
     */
    public boolean isValid() {
        return isPlaying &&
                songFileName != null &&
                !songFileName.isEmpty() &&
                songName != null &&
                !songName.isEmpty();
    }
}

