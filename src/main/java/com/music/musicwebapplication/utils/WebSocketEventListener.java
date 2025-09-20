package com.music.musicwebapplication.utils;

import com.music.musicwebapplication.dto.ChatMessage;
import com.music.musicwebapplication.support.MessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;


@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final SimpMessagingTemplate messageTemplate;

    @EventListener
    public void handleWebSocketDisconnect(SessionDisconnectEvent event){
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String username = (String) headerAccessor.getSessionAttributes().get("username");
        String roomId = (String) headerAccessor.getSessionAttributes().get("roomId");
        if(username!=null){
            log.info("{} Disconnected",username);
            var messageContext = ChatMessage.builder()
                    .type(MessageType.LEAVE)
                    .sender(username)
                    .roomId(roomId)
                    .build();

            messageTemplate.convertAndSend("/topic/chat/"+roomId,messageContext);
        }
    }
}
