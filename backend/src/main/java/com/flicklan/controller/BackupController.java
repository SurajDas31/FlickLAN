package com.flicklan.controller;

import com.flicklan.model.BackupData;
import com.flicklan.service.BackupRestoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/api/backup")
@Tag(name = "Backup & Restore", description = "Export and restore chat history snapshots with SHA-256 integrity checks")
public class BackupController {

    @Autowired
    private BackupRestoreService backupRestoreService;

    @Autowired
    private com.flicklan.service.DatabaseService databaseService;

    @Autowired
    private com.flicklan.service.LanDiscoveryService lanDiscoveryService;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @GetMapping("/export/peer/{peerId}")
    @Operation(summary = "Export Peer Direct Chat Backup", description = "Exports only the conversation with a specific peer as JSON")
    public ResponseEntity<byte[]> exportPeerBackup(
            @Parameter(description = "Target peer unique identifier")
            @PathVariable("peerId") String peerId,
            @Parameter(description = "Current user identifier")
            @RequestParam("currentUserId") String currentUserId) throws IOException {

        java.util.List<com.flicklan.model.Message> messages = databaseService.getDirectMessages(currentUserId, peerId, 5000);
        BackupData backup = new BackupData();
        backup.setSchemaVersion(1);
        backup.setAppVersion("1.0.0");
        backup.setExportDate(java.time.Instant.now().toString());
        backup.setExportedBy(lanDiscoveryService.getLocalHostName());
        backup.setMessages(messages);

        byte[] jsonData = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(backup);
        String filename = "chat_with_" + peerId + "_" + System.currentTimeMillis() + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonData);
    }

    @GetMapping("/export")
    @Operation(
            summary = "Export Chat Backup Archive",
            description = "Generates and streams a downloadable backup. Format can be 'json' (lightweight) or 'zip' (includes file attachments)"
    )
    public ResponseEntity<byte[]> exportBackup(
            @Parameter(description = "Backup format: json or zip", example = "json")
            @RequestParam(name = "format", defaultValue = "json") String format,
            @Parameter(description = "Whether to include media attachments when format is zip")
            @RequestParam(name = "includeMedia", defaultValue = "false") boolean includeMedia) throws IOException {

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        if ("zip".equalsIgnoreCase(format)) {
            byte[] zipData = backupRestoreService.exportZip(includeMedia);
            String filename = "flicklan_backup_" + timestamp + ".zip";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(zipData);
        } else {
            byte[] jsonData = backupRestoreService.exportJson();
            String filename = "flicklan_backup_" + timestamp + ".json";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonData);
        }
    }

    @PostMapping(value = "/inspect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Inspect Backup File (Pre-Restore Preview)",
            description = "Parses an uploaded backup file without applying changes, returning total message count, export date, and source"
    )
    public ResponseEntity<?> inspectBackup(
            @Parameter(description = "FlickLAN backup file (.json or .zip)", required = true)
            @RequestParam("file") MultipartFile file) {
        try {
            BackupData data = backupRestoreService.inspectBackup(file);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to parse backup file",
                    "details", e.getMessage()
            ));
        }
    }

    @PostMapping(value = "/restore", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Restore Messages from Backup",
            description = "Applies backup into SQLite database. Mode can be 'MERGE' (safe deduplication) or 'OVERWRITE' (wipe and replace)"
    )
    public ResponseEntity<?> restoreBackup(
            @Parameter(description = "FlickLAN backup file to restore", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Restore strategy: MERGE or OVERWRITE", example = "MERGE")
            @RequestParam(name = "mode", defaultValue = "MERGE") String mode) {
        try {
            BackupRestoreService.RestoreResult result = backupRestoreService.restoreBackup(file, mode);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to restore backup",
                    "details", e.getMessage()
            ));
        }
    }
}
