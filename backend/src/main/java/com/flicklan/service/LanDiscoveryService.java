package com.flicklan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flicklan.model.Peer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
public class LanDiscoveryService {
    private static final Logger logger = LoggerFactory.getLogger(LanDiscoveryService.class);

    @Value("${server.port:65005}")
    private int serverPort;

    @Value("${app.discovery.udp-port:8888}")
    private int udpPort;

    @Value("${app.discovery.interval-ms:3000}")
    private long beaconIntervalMs;

    private final String localInstanceId = UUID.randomUUID().toString();
    private String localHostName;
    private String localLanIp;

    private final Map<String, Peer> activePeers = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private DatagramSocket udpSocket;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private volatile boolean running = true;

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    @PostConstruct
    public void start() {
        resolveLocalNetworkInfo();
        startReceiver();
        startBeaconSender();
        startPeerPruner();
    }

    private void resolveLocalNetworkInfo() {
        try {
            localHostName = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            localHostName = "Node-" + localInstanceId.substring(0, 4);
        }

        try {
            List<NetworkInterface> candidates = new ArrayList<>();
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp() || isVirtualOrInternalAdapter(ni)) continue;
                candidates.add(ni);
            }

            // Loophole Fix: Prefer physical Wi-Fi/Ethernet adapters over virtual adapters (WSL, Hyper-V, VirtualBox, VMware)
            for (NetworkInterface ni : candidates) {
                if (isVirtualOrInternalAdapter(ni)) continue;

                String ip = extractValidIpv4(ni);
                if (ip != null) {
                    localLanIp = ip;
                    break;
                }
            }

            // Fallback: If no non-virtual adapter found, take any valid candidate
            if (localLanIp == null) {
                for (NetworkInterface ni : candidates) {
                    String ip = extractValidIpv4(ni);
                    if (ip != null) {
                        localLanIp = ip;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Could not determine local LAN IP: {}", e.getMessage());
        }

        if (localLanIp == null) {
            localLanIp = "127.0.0.1";
        }
        logger.info("FlickLAN P2P Node started. NodeID={}, Name={}, LAN IP={}, Port={}",
                localInstanceId, localHostName, localLanIp, serverPort);
    }

    private boolean isVirtualOrInternalAdapter(NetworkInterface ni) {
        String name = (ni.getName() + " " + ni.getDisplayName()).toLowerCase();
        return name.contains("virtual") || name.contains("vbox") || name.contains("vmware")
                || name.contains("wsl") || name.contains("hyper-v") || name.contains("vethernet")
                || name.contains("docker") || name.contains("tap");
    }

    private String extractValidIpv4(NetworkInterface ni) {
        Enumeration<InetAddress> addresses = ni.getInetAddresses();
        while (addresses.hasMoreElements()) {
            InetAddress addr = addresses.nextElement();
            if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                return addr.getHostAddress();
            }
        }
        return null;
    }

    private void startReceiver() {
        new Thread(() -> {
            try {
                udpSocket = new DatagramSocket(null);
                udpSocket.setReuseAddress(true);
                udpSocket.bind(new InetSocketAddress(udpPort));
                byte[] buffer = new byte[2048];

                while (running) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        udpSocket.receive(packet);

                        String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                        handleIncomingBeacon(message, packet.getAddress().getHostAddress());
                    } catch (SocketException se) {
                        if (!running) break;
                    } catch (Exception ex) {
                        logger.debug("Error processing UDP packet: {}", ex.getMessage());
                    }
                }
            } catch (Exception e) {
                logger.warn("Could not bind UDP receiver on port {}: {}", udpPort, e.getMessage());
            }
        }, "FlickLAN-P2P-Discovery").start();
    }

    private void handleIncomingBeacon(String json, String senderIp) {
        try {
            Map<?, ?> data = objectMapper.readValue(json, Map.class);
            String id = (String) data.get("id");
            String name = (String) data.get("name");
            Integer port = (Integer) data.get("port");

            // Ignore our own beacon
            if (id == null || id.equals(localInstanceId)) {
                return;
            }

            Peer peer = activePeers.get(id);
            boolean isNew = (peer == null);

            if (isNew) {
                peer = new Peer(id, name, senderIp, port != null ? port : 8080, "PEER_NODE");
                activePeers.put(id, peer);
                logger.info("Discovered new LAN peer: {} at {}:{}", name, senderIp, port);
                notifyPeerChange();
            } else {
                peer.setLastSeenEpochMs(Instant.now().toEpochMilli());
                peer.setName(name);
                peer.setIpAddress(senderIp);
                if (port != null) {
                    peer.setPort(port);
                }
                if (!peer.isOnline()) {
                    peer.setOnline(true);
                    notifyPeerChange();
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void startBeaconSender() {
        scheduler.scheduleAtFixedRate(() -> {
            if (!running) return;
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("app", "FlickLAN");
                payload.put("id", localInstanceId);
                payload.put("name", localHostName);
                payload.put("port", serverPort);
                byte[] data = objectMapper.writeValueAsBytes(payload);

                List<InetAddress> broadcastTargets = getBroadcastAddresses();
                try (DatagramSocket senderSocket = new DatagramSocket()) {
                    senderSocket.setBroadcast(true);
                    for (InetAddress target : broadcastTargets) {
                        try {
                            DatagramPacket packet = new DatagramPacket(data, data.length, target, udpPort);
                            senderSocket.send(packet);
                        } catch (Exception ignored) {
                        }
                    }
                }
            } catch (Exception e) {
                logger.debug("Error sending UDP beacon: {}", e.getMessage());
            }
        }, 1, beaconIntervalMs, TimeUnit.MILLISECONDS);
    }

    private List<InetAddress> getBroadcastAddresses() {
        List<InetAddress> list = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp() || isVirtualOrInternalAdapter(ni)) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress broadcast = ia.getBroadcast();
                    if (broadcast != null) {
                        list.add(broadcast);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (list.isEmpty()) {
            try {
                list.add(InetAddress.getByName("255.255.255.255"));
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    private void startPeerPruner() {
        scheduler.scheduleAtFixedRate(() -> {
            long now = Instant.now().toEpochMilli();
            boolean changed = false;

            for (Peer peer : activePeers.values()) {
                // If no heartbeat in 12 seconds, mark as offline
                if (peer.isOnline() && (now - peer.getLastSeenEpochMs() > 12000)) {
                    peer.setOnline(false);
                    changed = true;
                    logger.info("Peer {} is now offline", peer.getName());
                }
            }
            if (changed) {
                notifyPeerChange();
            }
        }, 4, 4, TimeUnit.SECONDS);
    }

    public void notifyPeerChange() {
        if (messagingTemplate != null) {
            try {
                messagingTemplate.convertAndSend("/topic/peers", getPeers());
            } catch (Exception e) {
                logger.debug("Could not push peer list update: {}", e.getMessage());
            }
        }
    }

    /**
     * Returns all discovered peers on the LAN (excluding this node itself)
     */
    public List<Peer> getPeers() {
        return new ArrayList<>(activePeers.values());
    }

    public Peer getPeerById(String peerId) {
        if (peerId == null) return null;
        return activePeers.get(peerId);
    }

    public String getLocalInstanceId() {
        return localInstanceId;
    }

    public String getLocalHostName() {
        return localHostName;
    }

    public String getLocalLanIp() {
        return localLanIp;
    }

    public int getServerPort() {
        return serverPort;
    }

    @PreDestroy
    public void stop() {
        running = false;
        scheduler.shutdownNow();
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
    }
}
