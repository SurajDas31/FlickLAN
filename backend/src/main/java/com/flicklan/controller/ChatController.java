package com.flicklan.controller;

import com.flicklan.model.Message;
import com.flicklan.model.Peer;
import com.flicklan.service.DatabaseService;
import com.flicklan.service.LanDiscoveryService;
import com.flicklan.service.P2PClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@Tag(name = "Local Node & Chat APIs", description = "Endpoints for the local UI to interact with its embedded backend engine")
public class ChatController {
    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private LanDiscoveryService lanDiscoveryService;

    @Autowired
    private P2PClientService p2pClientService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // WebSocket STOMP Mappings from the Local UI
    @MessageMapping("/chat.sendMessage")
    public void handleChannelMessage(@Payload Message message) {
        if (message.getId() == null || message.getId().isBlank()) {
            message.setId(UUID.randomUUID().toString());
        }

        // 1. Save to local SQLite database
        databaseService.saveMessage(message);

        // 2. Reflect on local screen
        messagingTemplate.convertAndSend("/topic/channel/" + message.getChannel(), message);

        // 3. Dispatch directly to all online peers across LAN
        p2pClientService.broadcastChannelMessage(lanDiscoveryService.getPeers(), message);
    }

    @MessageMapping("/chat.privateMessage")
    public void handleDirectMessage(@Payload Message message) {
        if (message.getId() == null || message.getId().isBlank()) {
            message.setId(UUID.randomUUID().toString());
        }

        // 1. Save to sender's local SQLite database
        databaseService.saveMessage(message);

        // 2. Reflect on sender's local screen
        messagingTemplate.convertAndSend("/topic/dm/" + message.getRecipientId(), message);

        // 3. Dispatch directly to recipient's IP & Port
        Peer targetPeer = lanDiscoveryService.getPeerById(message.getRecipientId());
        if (targetPeer != null) {
            boolean delivered = p2pClientService.sendDirectMessage(targetPeer, message);
            if (!delivered) {
                logger.warn("Could not deliver direct message to peer {}", targetPeer.getName());
            }
        } else {
            logger.warn("Peer with ID {} not found in discovered peers", message.getRecipientId());
        }
    }

    @MessageMapping("/chat.typing")
    public void handleTypingNotification(@Payload Map<String, Object> payload) {
        String target = (String) payload.get("target");
        if (target != null) {
            messagingTemplate.convertAndSend("/topic/typing/" + target, payload);
        }
    }

    // REST Endpoints consumed by the local React UI
    @GetMapping("/api/messages/channel/{channel}")
    @Operation(summary = "Get Channel Message History", description = "Retrieves stored messages for a public channel from local SQLite DB")
    public List<Message> getChannelHistory(
            @Parameter(description = "Channel name, e.g. general", example = "general")
            @PathVariable("channel") String channel,
            @Parameter(description = "Maximum number of messages to return")
            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        return databaseService.getChannelMessages(channel, limit);
    }

    @GetMapping("/api/messages/direct/{peerId}")
    @Operation(summary = "Get Direct Message History", description = "Retrieves 1-to-1 conversation history between this node and target peer from local SQLite DB")
    public List<Message> getDirectHistory(
            @Parameter(description = "Target peer unique identifier")
            @PathVariable("peerId") String peerId,
            @Parameter(description = "Current user identifier")
            @RequestParam("currentUserId") String currentUserId,
            @Parameter(description = "Maximum number of messages to return")
            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        return databaseService.getDirectMessages(currentUserId, peerId, limit);
    }

    @DeleteMapping("/api/messages/direct/{peerId}")
    @Operation(summary = "Delete Direct Message History", description = "Deletes 1-to-1 conversation messages with target peer from local SQLite DB")
    public ResponseEntity<?> deleteDirectHistory(
            @Parameter(description = "Target peer unique identifier")
            @PathVariable("peerId") String peerId,
            @Parameter(description = "Current user identifier")
            @RequestParam("currentUserId") String currentUserId) {
        int deleted = databaseService.deleteDirectMessages(currentUserId, peerId);
        return ResponseEntity.ok(Map.of("success", true, "deletedCount", deleted));
    }

    @GetMapping("/api/peers")
    @Operation(summary = "List Discovered LAN Peers", description = "Returns all other computers/nodes currently discovered via UDP beacons on the LAN")
    public List<Peer> getDiscoveredPeers() {
        return lanDiscoveryService.getPeers();
    }

    @GetMapping("/api/info")
    @Operation(summary = "Get Local Node Info", description = "Returns this computer's Node ID, Host Name, and LAN IP")
    public Map<String, Object> getLocalNodeInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("instanceId", lanDiscoveryService.getLocalInstanceId());
        info.put("hostName", lanDiscoveryService.getLocalHostName());
        info.put("lanIp", lanDiscoveryService.getLocalLanIp());
        info.put("port", lanDiscoveryService.getServerPort());
        info.put("totalMessages", databaseService.getMessageCount());
        return info;
    }
}
