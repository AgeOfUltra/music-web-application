    package com.music.musicwebapplication.dto;

    import lombok.AllArgsConstructor;
    import lombok.Builder;
    import lombok.Data;
    import lombok.NoArgsConstructor;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public class ChatMessage {
        private String sender;
        private String content;
        private String type;            // "CHAT", "JOIN", "LEAVE"
        private Long timestamp;
    }
