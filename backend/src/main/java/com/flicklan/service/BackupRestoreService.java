package com.flicklan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flicklan.model.BackupData;
import com.flicklan.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class BackupRestoreService {
    private static final Logger logger = LoggerFactory.getLogger(BackupRestoreService.class);

    @Value("${app.version:1.0.0}")
    private String appVersion;

    @Value("${app.data.dir:./data}")
    private String dataDir;

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private LanDiscoveryService lanDiscoveryService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public BackupData generateBackupData() {
        List<Message> allMessages = databaseService.getAllMessages();

        BackupData backup = new BackupData();
        backup.setSchemaVersion(1);
        backup.setAppVersion(appVersion);
        backup.setExportDate(Instant.now().toString());
        backup.setExportedBy(lanDiscoveryService.getLocalHostName());
        backup.setMessages(allMessages);

        try {
            byte[] jsonBytes = objectMapper.writeValueAsBytes(allMessages);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(jsonBytes);
            backup.setChecksum(HexFormat.of().formatHex(hash));
        } catch (Exception e) {
            backup.setChecksum("none");
        }

        return backup;
    }

    public byte[] exportJson() throws IOException {
        BackupData backup = generateBackupData();
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(backup);
    }

    public byte[] exportZip(boolean includeMedia) throws IOException {
        BackupData backup = generateBackupData();
        byte[] jsonBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(backup);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Write manifest / backup.json
            ZipEntry jsonEntry = new ZipEntry("flicklan_backup.json");
            zos.putNextEntry(jsonEntry);
            zos.write(jsonBytes);
            zos.closeEntry();

            // Include media attachments if requested
            if (includeMedia) {
                File uploadsFolder = new File(dataDir, "uploads");
                if (uploadsFolder.exists() && uploadsFolder.isDirectory()) {
                    File[] files = uploadsFolder.listFiles();
                    if (files != null) {
                        for (File file : files) {
                            if (file.isFile()) {
                                ZipEntry fileEntry = new ZipEntry("attachments/" + file.getName());
                                zos.putNextEntry(fileEntry);
                                try (FileInputStream fis = new FileInputStream(file)) {
                                    fis.transferTo(zos);
                                }
                                zos.closeEntry();
                            }
                        }
                    }
                }
            }
        }
        return baos.toByteArray();
    }

    public BackupData inspectBackup(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename != null && filename.toLowerCase().endsWith(".zip")) {
            return inspectZip(file.getInputStream());
        } else {
            return inspectJson(file.getInputStream());
        }
    }

    private BackupData inspectJson(InputStream is) throws IOException {
        return objectMapper.readValue(is, BackupData.class);
    }

    private BackupData inspectZip(InputStream is) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("flicklan_backup.json".equalsIgnoreCase(entry.getName())) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    zis.transferTo(buffer);
                    return objectMapper.readValue(buffer.toByteArray(), BackupData.class);
                }
            }
        }
        throw new IllegalArgumentException("Invalid backup ZIP: flicklan_backup.json not found in archive.");
    }

    public RestoreResult restoreBackup(MultipartFile file, String mode) throws IOException {
        BackupData backup;
        boolean isZip = false;

        String filename = file.getOriginalFilename();
        if (filename != null && filename.toLowerCase().endsWith(".zip")) {
            isZip = true;
            backup = inspectZip(file.getInputStream());
        } else {
            backup = inspectJson(file.getInputStream());
        }

        if (backup == null || backup.getMessages() == null) {
            throw new IllegalArgumentException("Backup contains no message data.");
        }

        // Restore media if it was a ZIP
        if (isZip) {
            extractZipAttachments(file.getInputStream());
        }

        int affectedCount;
        if ("OVERWRITE".equalsIgnoreCase(mode)) {
            affectedCount = databaseService.replaceAllMessages(backup.getMessages());
        } else {
            // Default to MERGE
            affectedCount = databaseService.mergeMessages(backup.getMessages());
        }

        return new RestoreResult(true, affectedCount, backup.getTotalMessages(), mode);
    }

    private void extractZipAttachments(InputStream is) {
        try (ZipInputStream zis = new ZipInputStream(is)) {
            File uploadsFolder = new File(dataDir, "uploads");
            if (!uploadsFolder.exists()) {
                uploadsFolder.mkdirs();
            }

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().startsWith("attachments/") && !entry.isDirectory()) {
                    String baseName = new File(entry.getName()).getName();
                    File destFile = new File(uploadsFolder, baseName);
                    try (FileOutputStream fos = new FileOutputStream(destFile)) {
                        zis.transferTo(fos);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Could not extract some attachments during restore: {}", e.getMessage());
        }
    }

    public static class RestoreResult {
        private final boolean success;
        private final int importedCount;
        private final int totalInBackup;
        private final String mode;

        public RestoreResult(boolean success, int importedCount, int totalInBackup, String mode) {
            this.success = success;
            this.importedCount = importedCount;
            this.totalInBackup = totalInBackup;
            this.mode = mode;
        }

        public boolean isSuccess() {
            return success;
        }

        public int getImportedCount() {
            return importedCount;
        }

        public int getTotalInBackup() {
            return totalInBackup;
        }

        public String getMode() {
            return mode;
        }
    }
}

