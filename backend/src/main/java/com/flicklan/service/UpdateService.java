package com.flicklan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flicklan.model.UpdateInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class UpdateService {
    private static final Logger logger = LoggerFactory.getLogger(UpdateService.class);

    @Value("${app.version:1.0.0}")
    private String currentVersion;

    @Value("${app.update.check-url:https://api.github.com/repos/flicklan/flicklan/releases/latest}")
    private String updateCheckUrl;

    @Value("${app.data.dir:./data}")
    private String dataDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public UpdateInfo checkForUpdates() {
        UpdateInfo info = new UpdateInfo();
        info.setCurrentVersion(currentVersion);
        info.setUpdateAvailable(false);

        try {
            URI uri = URI.create(updateCheckUrl);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestProperty("User-Agent", "FlickLAN-Client/" + currentVersion);
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int status = conn.getResponseCode();
            if (status == 200) {
                try (InputStream is = conn.getInputStream()) {
                    JsonNode root = objectMapper.readTree(is);
                    String tagName = root.path("tag_name").asText();
                    String latestVersion = tagName.replaceFirst("^[vV]", "");
                    info.setLatestVersion(latestVersion);
                    info.setReleaseNotes(root.path("body").asText());
                    info.setPublishedAt(root.path("published_at").asText());

                    // Check if latestVersion > currentVersion
                    boolean newer = isVersionNewer(latestVersion, currentVersion);
                    info.setUpdateAvailable(newer);

                    // Find Windows .exe asset
                    JsonNode assets = root.path("assets");
                    if (assets.isArray()) {
                        for (JsonNode asset : assets) {
                            String name = asset.path("name").asText();
                            if (name.endsWith(".exe") || name.endsWith(".zip")) {
                                info.setDownloadUrl(asset.path("browser_download_url").asText());
                                break;
                            }
                        }
                    }
                }
            } else {
                logger.info("Update check responded with HTTP status: {}", status);
            }
        } catch (Exception e) {
            logger.warn("Could not check for updates (offline or server unreachable): {}", e.getMessage());
        }

        return info;
    }

    public boolean applyUpdate(String downloadUrl, String expectedSha256) {
        try {
            File updateDir = new File(dataDir, "updates");
            if (!updateDir.exists()) {
                updateDir.mkdirs();
            }

            File targetFile = new File(updateDir, "FlickLAN-update.exe");
            logger.info("Downloading update from {} to {}", downloadUrl, targetFile.getAbsolutePath());

            // 1. Download binary
            URI uri = URI.create(downloadUrl);
            try (InputStream in = uri.toURL().openStream()) {
                Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            // 2. Verify SHA-256 Checksum if provided
            if (expectedSha256 != null && !expectedSha256.isBlank()) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream fis = new FileInputStream(targetFile)) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = fis.read(buffer)) != -1) {
                        digest.update(buffer, 0, n);
                    }
                }
                String actualHash = HexFormat.of().formatHex(digest.digest());
                if (!actualHash.equalsIgnoreCase(expectedSha256.trim())) {
                    logger.error("Update checksum mismatch! Expected: {}, Actual: {}", expectedSha256, actualHash);
                    targetFile.delete();
                    return false;
                }
            }

            // 3. Trigger updater script
            spawnWindowsUpdateRunner(targetFile);
            return true;
        } catch (Exception e) {
            logger.error("Failed to download and apply update", e);
            return false;
        }
    }

    private void spawnWindowsUpdateRunner(File newExeFile) {
        try {
            // Locate update-runner.bat
            File script = new File("scripts/update-runner.bat");
            if (!script.exists()) {
                // Look relative to root
                script = new File("../scripts/update-runner.bat");
            }

            // Current running exe name
            String currentPid = String.valueOf(ProcessHandle.current().pid());

            if (script.exists()) {
                ProcessBuilder pb = new ProcessBuilder(
                        "cmd.exe", "/c", "start", "",
                        script.getAbsolutePath(),
                        currentPid,
                        newExeFile.getAbsolutePath()
                );
                pb.start();
                logger.info("Spawned update runner. Shutting down application for update.");
                new Thread(() -> {
                    try {
                        Thread.sleep(1000);
                        System.exit(0);
                    } catch (Exception ignored) {
                    }
                }).start();
            } else {
                logger.warn("Update runner script not found at expected location: {}", script.getAbsolutePath());
            }
        } catch (Exception e) {
            logger.error("Could not spawn update runner script", e);
        }
    }

    private boolean isVersionNewer(String latest, String current) {
        if (latest == null || current == null) return false;
        String[] lParts = latest.split("\\.");
        String[] cParts = current.split("\\.");
        int length = Math.max(lParts.length, cParts.length);

        for (int i = 0; i < length; i++) {
            int lVal = i < lParts.length ? parseSafeInt(lParts[i]) : 0;
            int cVal = i < cParts.length ? parseSafeInt(cParts[i]) : 0;
            if (lVal > cVal) return true;
            if (lVal < cVal) return false;
        }
        return false;
    }

    private int parseSafeInt(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D+", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    public String getCurrentVersion() {
        return currentVersion;
    }
}

