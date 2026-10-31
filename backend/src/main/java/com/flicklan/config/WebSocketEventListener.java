package com.flicklan.config;

import com.flicklan.service.LanDiscoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketEventListener {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);

    @Autowired
    private LanDiscoveryService lanDiscoveryService;

    // Maps STOMP Session ID -> User ID
    private final Map<String, String> sessionUserMap = new ConcurrentHashMap<>();

    public void registerSession(String sessionId, String userId) {
        if (sessionId != null && userId != null) {
            sessionUserMap.put(sessionId, userId);
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        if (sessionId != null) {
            String userId = sessionUserMap.remove(sessionId);
            if (userId != null) {
                logger.info("WebSocket session ended for user: {}", userId);
            }
        }
    }
}

