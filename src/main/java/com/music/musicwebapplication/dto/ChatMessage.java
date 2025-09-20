    package com.music.musicwebapplication.dto;

    import com.music.musicwebapplication.support.MessageType;
    import lombok.Builder;
    import lombok.Data;

    @Data
    @Builder
    public class ChatMessage {
        private MessageType type;
        private String content;
        private String sender;
        private String roomId;
    }
