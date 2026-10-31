package com.flicklan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flicklan.model.Message;
import com.flicklan.model.Peer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class P2PClientService {
    private static final Logger logger = LoggerFactory.getLogger(P2PClientService.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Sends a 1-to-1 direct message directly to a target peer's IP & port
     */
    public boolean sendDirectMessage(Peer targetPeer, Message message) {
        if (targetPeer == null || targetPeer.getIpAddress() == null) {
            logger.warn("Cannot send direct message: target peer or IP is null");
            return false;
        }

        String ip = targetPeer.getIpAddress();
        if (ip.contains(":") && !ip.startsWith("[")) {
            ip = "[" + ip + "]";
        }

        String targetUrl = String.format("http://%s:%d/api/p2p/direct-message",
                ip, targetPeer.getPort());

        try {
            String jsonPayload = objectMapper.writeValueAsString(message);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(4))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                logger.debug("Successfully dispatched direct message to {}", targetPeer.getName());
                return true;
            } else {
                logger.warn("Peer {} returned HTTP status {}", targetPeer.getName(), response.statusCode());
                return false;
            }
        } catch (Exception e) {
            logger.warn("Failed to deliver direct message to {} ({}:{}): {}",
                    targetPeer.getName(), targetPeer.getIpAddress(), targetPeer.getPort(), e.getMessage());
            return false;
        }
    }

    /**
     * Broadcasts a channel message across all currently known LAN peers in parallel
     */
    public void broadcastChannelMessage(List<Peer> peers, Message message) {
        if (peers == null || peers.isEmpty()) {
            return;
        }

        try {
            String jsonPayload = objectMapper.writeValueAsString(message);

            for (Peer peer : peers) {
                if (!peer.isOnline() || peer.getIpAddress() == null) continue;

                CompletableFuture.runAsync(() -> {
                    String ip = peer.getIpAddress();
                    if (ip.contains(":") && !ip.startsWith("[")) {
                        ip = "[" + ip + "]";
                    }
                    String targetUrl = String.format("http://%s:%d/api/p2p/channel-message",
                            ip, peer.getPort());
                    try {
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(targetUrl))
                                .header("Content-Type", "application/json")
                                .timeout(Duration.ofSeconds(4))
                                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                                .build();

                        httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                    } catch (Exception e) {
                        logger.debug("Could not deliver channel message to peer {}: {}", peer.getName(), e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            logger.error("Error serializing channel message for P2P dispatch", e);
        }
    }
}

