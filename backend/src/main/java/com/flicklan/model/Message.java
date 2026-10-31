package com.flicklan.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type {
        CHAT,
        FILE,
        SYSTEM,
        JOIN,
        LEAVE
    }

    private String id;
    private Type type;
    private String senderId;
    private String senderName;
    private String recipientId; // null or empty for public/channel messages
    private String channel;     // default "general"
    private String content;
    private String fileName;
    private Long fileSize;
    private String fileDownloadUrl;
    private String timestamp;

    public Message() {
        this.id = UUID.randomUUID().toString();
        this.type = Type.CHAT;
        this.channel = "general";
        this.timestamp = Instant.now().toString();
    }

    public Message(Type type, String senderId, String senderName, String content) {
        this();
        this.type = type;
        this.senderId = senderId;
        this.senderName = senderName;
        this.content = content;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileDownloadUrl() {
        return fileDownloadUrl;
    }

    public void setFileDownloadUrl(String fileDownloadUrl) {
        this.fileDownloadUrl = fileDownloadUrl;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}

