package com.flicklan.controller;

import com.flicklan.model.Message;
import com.flicklan.service.DatabaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/p2p")
@Tag(name = "P2P Mesh Receiver", description = "Inbound endpoints for receiving direct messages and channel broadcasts from other LAN nodes")
public class P2PController {
    private static final Logger logger = LoggerFactory.getLogger(P2PController.class);

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @PostMapping("/direct-message")
    @Operation(summary = "Receive Inbound Direct Message", description = "Called by a remote peer node when sending a 1-to-1 message to this machine")
    public ResponseEntity<?> receiveDirectMessage(@RequestBody Message message) {
        if (message == null || message.getId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid message payload"));
        }

        logger.info("Received P2P direct message from {} (ID: {})", message.getSenderName(), message.getSenderId());

        // 1. Persist to this node's local SQLite database
        databaseService.saveMessage(message);

        // 2. Push in real-time to this node's local React UI (Push to recipient's local channel and sender's channel)
        if (message.getRecipientId() != null) {
            messagingTemplate.convertAndSend("/topic/dm/" + message.getRecipientId(), message);
        }
        messagingTemplate.convertAndSend("/topic/dm/" + message.getSenderId(), message);

        return ResponseEntity.ok(Map.of("status", "received", "id", message.getId()));
    }

    @PostMapping("/channel-message")
    @Operation(summary = "Receive Inbound Channel Message", description = "Called by a remote peer node when broadcasting a room message (e.g. #general) to this machine")
    public ResponseEntity<?> receiveChannelMessage(@RequestBody Message message) {
        if (message == null || message.getId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid message payload"));
        }

        // 1. Persist to this node's local SQLite database
        databaseService.saveMessage(message);

        // 2. Push in real-time to this node's local React UI
        messagingTemplate.convertAndSend("/topic/channel/" + message.getChannel(), message);

        return ResponseEntity.ok(Map.of("status", "received", "id", message.getId()));
    }
}

