package com.flicklan.controller;

import com.flicklan.service.LanDiscoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
@Tag(name = "P2P File Transfers", description = "Direct chunked file sharing between LAN nodes")
public class FileController {
    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    @Value("${app.data.dir:./data}")
    private String dataDir;

    @Autowired
    private LanDiscoveryService lanDiscoveryService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload File for LAN Peer Sharing",
            description = "Stores a file locally and returns a direct LAN download link to share with peers"
    )
    public ResponseEntity<?> uploadFile(
            @Parameter(description = "Binary file payload to upload", required = true)
            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Empty file provided"));
        }

        try {
            File uploadsDir = new File(dataDir, "uploads");
            if (!uploadsDir.exists()) {
                uploadsDir.mkdirs();
            }

            String originalName = file.getOriginalFilename();
            String extension = "";
            if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf("."));
            }

            String storedFileName = UUID.randomUUID() + extension;
            Path targetPath = Paths.get(uploadsDir.getAbsolutePath(), storedFileName);
            Files.copy(file.getInputStream(), targetPath);

            String hostIp = lanDiscoveryService.getLocalLanIp();
            int port = lanDiscoveryService.getServerPort();
            String fullDownloadUrl = String.format("http://%s:%d/api/files/download/%s", hostIp, port, storedFileName);

            Map<String, Object> response = new HashMap<>();
            response.put("originalName", originalName);
            response.put("storedName", storedFileName);
            response.put("size", file.getSize());
            response.put("downloadUrl", fullDownloadUrl);

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            logger.error("Error storing uploaded file", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to upload file"));
        }
    }

    @GetMapping("/download/{filename:.+}")
    @Operation(summary = "Direct P2P File Stream Download", description = "Streams a stored attachment to a requesting peer at full local network bandwidth")
    public ResponseEntity<Resource> downloadFile(
            @Parameter(description = "Unique stored file name")
            @PathVariable("filename") String filename) {
        try {
            Path uploadsDir = Paths.get(dataDir, "uploads").toAbsolutePath().normalize();
            Path filePath = uploadsDir.resolve(filename).normalize();

            // Security Loophole Fix: Prevent path traversal attacks (e.g. ../../windows/system32)
            if (!filePath.startsWith(uploadsDir)) {
                logger.warn("Potential Path Traversal attempt blocked for filename: {}", filename);
                return ResponseEntity.status(403).build();
            }

            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            String contentType = "application/octet-stream";
            try {
                String probed = Files.probeContentType(filePath);
                if (probed != null) {
                    contentType = probed;
                }
            } catch (IOException ignored) {
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
