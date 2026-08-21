package com.mdau.ushirika.module.backup.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Nightly off-platform database backup -- pg_dump the live Postgres database, zip it, and upload
 * to Cloudinary (already paid for and integrated for media, so this needed no new billing
 * account). Runs at midnight America/Chicago (the org's home timezone). Every night's dump is
 * kept as a "daily" backup for 30 days; the 1st-of-month dump is additionally kept as a
 * "monthly" backup for 4 years, satisfying the "records from up to 4 years ago" requirement
 * without needing to retain 1,460 individual daily files. Retention is enforced by listing each
 * Cloudinary folder and deleting anything past its window -- no separate backup-index table, so
 * there's nothing extra to itself need backing up.
 *
 * To restore: download the zip from Cloudinary (folder "backups/daily/" or "backups/monthly/" in
 * the dashboard), unzip it, then `psql <connection-string> < backup.sql`. The dump is plain SQL
 * with --no-owner --no-privileges so it's portable to any Postgres instance regardless of the
 * role names that existed on the original one.
 */
@Slf4j
@Service
public class DatabaseBackupService {

    private static final ZoneId ORG_ZONE = ZoneId.of("America/Chicago");
    private static final int DAILY_RETENTION_DAYS = 30;
    private static final int MONTHLY_RETENTION_MONTHS = 48; // 4 years
    private static final DateTimeFormatter DAILY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTHLY_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String DAILY_PREFIX = "backups/daily/";
    private static final String MONTHLY_PREFIX = "backups/monthly/";

    private final Cloudinary cloudinary;
    private final boolean cloudinaryConfigured;
    private final String jdbcUrl;
    private final String dbUsername;
    private final String dbPassword;

    public DatabaseBackupService(
            @Value("${app.cloudinary.cloud-name:NOT_SET}") String cloudName,
            @Value("${app.cloudinary.api-key:NOT_SET}") String apiKey,
            @Value("${app.cloudinary.api-secret:NOT_SET}") String apiSecret,
            @Value("${spring.datasource.url}") String jdbcUrl,
            @Value("${spring.datasource.username}") String dbUsername,
            @Value("${spring.datasource.password}") String dbPassword
    ) {
        this.cloudinaryConfigured = !"NOT_SET".equals(cloudName) && !"NOT_SET".equals(apiKey);
        this.cloudinary = cloudinaryConfigured
                ? new Cloudinary(ObjectUtils.asMap(
                        "cloud_name", cloudName, "api_key", apiKey, "api_secret", apiSecret, "secure", true))
                : new Cloudinary();
        this.jdbcUrl = jdbcUrl;
        this.dbUsername = dbUsername;
        this.dbPassword = dbPassword;
    }

    public record BackupResult(boolean success, String publicId, long sizeBytes, String message) {}

    public record BackupEntry(String publicId, String type, String url, long sizeBytes, String createdAt) {}

    @Scheduled(cron = "0 0 0 * * *", zone = "America/Chicago")
    public void nightlyBackup() {
        run();
    }

    /** Callable directly for an on-demand "back up now" trigger (see BackupController), same
     * path the nightly schedule uses. Never throws -- failures are logged and returned in the
     * result so a scheduled run failing overnight doesn't also crash anything else. */
    public BackupResult run() {
        if (!cloudinaryConfigured) {
            log.warn("Database backup skipped -- Cloudinary is not configured (app.cloudinary.*).");
            return new BackupResult(false, null, 0, "Cloudinary is not configured.");
        }

        Path dumpFile = null;
        Path zipFile = null;
        try {
            LocalDate today = LocalDate.now(ORG_ZONE);
            dumpFile = Files.createTempFile("ushirika-backup-", ".sql");
            zipFile = Files.createTempFile("ushirika-backup-", ".zip");

            runPgDump(dumpFile);
            zip(dumpFile, zipFile, "ushirika_" + today.format(DAILY_FMT) + ".sql");
            long sizeBytes = Files.size(zipFile);

            String dailyPublicId = DAILY_PREFIX + today.format(DAILY_FMT);
            upload(zipFile, dailyPublicId);
            log.info("Database backup uploaded: {} ({} bytes)", dailyPublicId, sizeBytes);

            String publicId = dailyPublicId;
            if (today.getDayOfMonth() == 1) {
                String monthlyPublicId = MONTHLY_PREFIX + today.format(MONTHLY_FMT);
                upload(zipFile, monthlyPublicId);
                log.info("Database backup also archived as monthly: {}", monthlyPublicId);
                publicId = monthlyPublicId;
            }

            enforceRetention(DAILY_PREFIX, DAILY_FMT, today.minusDays(DAILY_RETENTION_DAYS));
            enforceRetention(MONTHLY_PREFIX, MONTHLY_FMT, today.minusMonths(MONTHLY_RETENTION_MONTHS));

            return new BackupResult(true, publicId, sizeBytes, "Backup uploaded successfully.");
        } catch (Exception e) {
            log.error("Database backup failed: {}", e.getMessage(), e);
            return new BackupResult(false, null, 0, "Backup failed: " + e.getMessage());
        } finally {
            deleteQuietly(dumpFile);
            deleteQuietly(zipFile);
        }
    }

    /** Every existing backup, newest first -- for the low-priority admin "Backups" tab. Cloudinary
     * is queried live rather than an index table, same reasoning as enforceRetention(). Downloading
     * a zip and restoring it locally (see the class doc) is currently the only way to turn an old
     * backup back into something readable; there's no server-side SQL-to-report conversion yet. */
    @SuppressWarnings("unchecked")
    public List<BackupEntry> list() {
        if (!cloudinaryConfigured) return List.of();

        List<BackupEntry> entries = new ArrayList<>();
        for (var pair : Map.of(DAILY_PREFIX, "daily", MONTHLY_PREFIX, "monthly").entrySet()) {
            try {
                Map<String, Object> result = cloudinary.api().resources(ObjectUtils.asMap(
                        "type", "upload",
                        "resource_type", "raw",
                        "prefix", pair.getKey(),
                        "max_results", 500
                ));
                List<Map<String, Object>> resources = (List<Map<String, Object>>) result.get("resources");
                if (resources == null) continue;
                for (Map<String, Object> r : resources) {
                    entries.add(new BackupEntry(
                            String.valueOf(r.get("public_id")),
                            pair.getValue(),
                            String.valueOf(r.get("secure_url")),
                            r.get("bytes") instanceof Number n ? n.longValue() : 0,
                            String.valueOf(r.get("created_at"))
                    ));
                }
            } catch (Exception e) {
                log.error("Backup listing failed for prefix {}: {}", pair.getKey(), e.getMessage());
            }
        }
        entries.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));
        return entries;
    }

    private void runPgDump(Path outFile) throws IOException, InterruptedException {
        DbTarget target = parseJdbcUrl(jdbcUrl);

        ProcessBuilder pb = new ProcessBuilder(
                "pg_dump",
                "--host=" + target.host(),
                "--port=" + target.port(),
                "--username=" + dbUsername,
                "--dbname=" + target.database(),
                "--no-password",
                "--no-owner",
                "--no-privileges",
                "--format=plain",
                "--file=" + outFile.toAbsolutePath()
        );
        pb.environment().put("PGPASSWORD", dbPassword);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        String output;
        try (InputStream in = process.getInputStream()) {
            output = new String(in.readAllBytes());
        }
        boolean finished = process.waitFor(10, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("pg_dump timed out after 10 minutes");
        }
        if (process.exitValue() != 0) {
            throw new IOException("pg_dump exited with code " + process.exitValue() + ": " + output);
        }
    }

    private void zip(Path source, Path zipDest, String entryName) throws IOException {
        try (var out = Files.newOutputStream(zipDest);
             ZipOutputStream zos = new ZipOutputStream(out)) {
            zos.putNextEntry(new ZipEntry(entryName));
            Files.copy(source, zos);
            zos.closeEntry();
        }
    }

    private void upload(Path zipFile, String publicId) throws IOException {
        cloudinary.uploader().upload(zipFile.toFile(), ObjectUtils.asMap(
                "public_id", publicId,
                "resource_type", "raw",
                "overwrite", true
        ));
    }

    /** Deletes every asset under the given prefix whose date (parsed from its public_id) falls
     * before the cutoff. Cloudinary itself is the source of truth for what backups exist -- no
     * separate index to keep in sync. */
    @SuppressWarnings("unchecked")
    private void enforceRetention(String prefix, DateTimeFormatter fmt, LocalDate cutoff) {
        try {
            Map<String, Object> result = cloudinary.api().resources(ObjectUtils.asMap(
                    "type", "upload",
                    "resource_type", "raw",
                    "prefix", prefix,
                    "max_results", 500
            ));
            List<Map<String, Object>> resources = (List<Map<String, Object>>) result.get("resources");
            if (resources == null) return;

            List<String> stale = new ArrayList<>();
            for (Map<String, Object> resource : resources) {
                String publicId = String.valueOf(resource.get("public_id"));
                String dateToken = publicId.substring(prefix.length());
                try {
                    LocalDate parsed = fmt.equals(MONTHLY_FMT)
                            ? LocalDate.parse(dateToken + "-01", DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                            : LocalDate.parse(dateToken, fmt);
                    if (parsed.isBefore(cutoff)) stale.add(publicId);
                } catch (Exception parseEx) {
                    log.warn("Backup retention: could not parse date from public_id {}, leaving it alone", publicId);
                }
            }

            if (!stale.isEmpty()) {
                cloudinary.api().deleteResources(stale, ObjectUtils.asMap("resource_type", "raw"));
                log.info("Backup retention: deleted {} stale backup(s) under {}: {}", stale.size(), prefix, stale);
            }
        } catch (Exception e) {
            log.error("Backup retention cleanup failed for prefix {}: {}", prefix, e.getMessage());
        }
    }

    private record DbTarget(String host, int port, String database) {}

    private static DbTarget parseJdbcUrl(String jdbcUrl) throws IOException {
        try {
            String cleaned = jdbcUrl.startsWith("jdbc:") ? jdbcUrl.substring(5) : jdbcUrl;
            URI uri = new URI(cleaned);
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String path = uri.getPath();
            String database = path != null && path.startsWith("/") ? path.substring(1) : path;
            if (host == null || database == null || database.isBlank()) {
                throw new IOException("Could not parse host/database from datasource URL");
            }
            return new DbTarget(host, port, database);
        } catch (URISyntaxException e) {
            throw new IOException("Invalid datasource URL: " + e.getMessage());
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not delete temp backup file {}: {}", path, e.getMessage());
        }
    }
}
