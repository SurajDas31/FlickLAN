package com.flicklan.controller;

import com.flicklan.model.UpdateInfo;
import com.flicklan.service.UpdateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/update")
@Tag(name = "Auto-Update", description = "Query update releases, download binaries, and trigger atomic application restart")
public class UpdateController {

    @Autowired
    private UpdateService updateService;

    @GetMapping("/check")
    @Operation(summary = "Check for Application Updates", description = "Queries GitHub Releases API or version manifest to determine if a newer version is available")
    public ResponseEntity<UpdateInfo> checkForUpdate() {
        UpdateInfo info = updateService.checkForUpdates();
        return ResponseEntity.ok(info);
    }

    @PostMapping("/apply")
    @Operation(summary = "Apply & Relaunch Update", description = "Downloads new executable in background, verifies SHA-256, and executes atomic Windows replacement script")
    public ResponseEntity<?> applyUpdate(@RequestBody Map<String, String> request) {
        String downloadUrl = request.get("downloadUrl");
        String sha256 = request.get("sha256");

        if (downloadUrl == null || downloadUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "downloadUrl is required"));
        }

        new Thread(() -> {
            updateService.applyUpdate(downloadUrl, sha256);
        }).start();

        return ResponseEntity.ok(Map.of(
                "status", "downloading",
                "message", "Update download initiated. Application will restart when ready."
        ));
    }
}
