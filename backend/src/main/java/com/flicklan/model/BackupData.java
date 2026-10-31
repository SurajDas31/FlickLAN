package com.flicklan.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class BackupData implements Serializable {
    private static final long serialVersionUID = 1L;

    private int schemaVersion = 1;
    private String exportDate;
    private String appVersion;
    private String exportedBy;
    private int totalMessages;
    private String checksum;
    private List<Message> messages = new ArrayList<>();

    public BackupData() {
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getExportDate() {
        return exportDate;
    }

    public void setExportDate(String exportDate) {
        this.exportDate = exportDate;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public String getExportedBy() {
        return exportedBy;
    }

    public void setExportedBy(String exportedBy) {
        this.exportedBy = exportedBy;
    }

    public int getTotalMessages() {
        return totalMessages;
    }

    public void setTotalMessages(int totalMessages) {
        this.totalMessages = totalMessages;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
        this.totalMessages = messages != null ? messages.size() : 0;
    }
}

