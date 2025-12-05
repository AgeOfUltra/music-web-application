package com.music.musicwebapplication.chatDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaybackSyncRequest {

    private String requester;      // Username requesting sync
    private long timestamp;        // Client timestamp when request was made

    /**
     * Check if the sync request is valid
     */
    public boolean isValid() {
        return requester != null &&
                !requester.isEmpty() &&
                timestamp > 0;
    }
}