package com.flicklan.model;

import java.io.Serializable;
import java.time.Instant;

public class Peer implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String ipAddress;
    private int port;
    private long lastSeenEpochMs;
    private boolean online;
    private String clientType; // "NATIVE_EXE", "MOBILE", "BROWSER"

    public Peer() {
    }

    public Peer(String id, String name, String ipAddress, int port) {
        this(id, name, ipAddress, port, "NATIVE_EXE");
    }

    public Peer(String id, String name, String ipAddress, int port, String clientType) {
        this.id = id;
        this.name = name;
        this.ipAddress = ipAddress;
        this.port = port;
        this.lastSeenEpochMs = Instant.now().toEpochMilli();
        this.online = true;
        this.clientType = clientType != null ? clientType : "BROWSER";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public long getLastSeenEpochMs() {
        return lastSeenEpochMs;
    }

    public void setLastSeenEpochMs(long lastSeenEpochMs) {
        this.lastSeenEpochMs = lastSeenEpochMs;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public String getClientType() {
        return clientType;
    }

    public void setClientType(String clientType) {
        this.clientType = clientType;
    }
}
