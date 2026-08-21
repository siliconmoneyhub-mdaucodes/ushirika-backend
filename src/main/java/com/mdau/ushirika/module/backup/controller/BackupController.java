package com.mdau.ushirika.module.backup.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.backup.service.DatabaseBackupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/superadmin/backup")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Backup", description = "Off-platform database backup to Cloudinary (SUPERADMIN only)")
public class BackupController {

    private final DatabaseBackupService backupService;

    @PostMapping("/run")
    @Operation(summary = "Run a database backup right now, on demand (same path the nightly 12am job uses)")
    public ResponseEntity<ApiResponse<DatabaseBackupService.BackupResult>> runNow() {
        DatabaseBackupService.BackupResult result = backupService.run();
        String message = result.success() ? "Backup completed successfully" : "Backup failed";
        return ResponseEntity.ok(ApiResponse.ok(message, result));
    }

    @GetMapping("/list")
    @Operation(summary = "List existing backups, newest first")
    public ResponseEntity<ApiResponse<List<DatabaseBackupService.BackupEntry>>> list() {
        return ResponseEntity.ok(ApiResponse.ok("Backups fetched", backupService.list()));
    }
}
