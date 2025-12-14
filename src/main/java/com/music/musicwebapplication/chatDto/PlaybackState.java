package com.music.musicwebapplication.chatDto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
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
    public static PlaybackState fromMap(Map<String, Object> msg) {
        PlaybackState state = new PlaybackState();

        state.setSongFileName((String) msg.get("songFileName"));
        state.setSongName((String) msg.get("songName"));
        state.setHero((String) msg.get("hero"));
        state.setHeroine((String) msg.get("heroine"));
        state.setMovie((String) msg.get("movie"));
        state.setSinger((String) msg.get("singer"));
        state.setLanguage((String) msg.get("language"));

        String action = (String) msg.get("action");
        state.setPlaying("PLAY".equals(action) || "RESUME".equals(action));
        state.setPaused("PAUSE".equals(action));

        Object ts = msg.get("timestamp");
        state.setTimestamp(ts instanceof Number ? ((Number) ts).longValue() : 0L);

        state.setOrganizer((String) msg.get("controller"));

        // ✅ Set serverTime to current time when converting from map
        state.setServerTime(System.currentTimeMillis());

        return state;
    }

}

