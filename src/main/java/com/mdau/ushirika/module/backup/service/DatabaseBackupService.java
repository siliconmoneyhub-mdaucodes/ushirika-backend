package com.mdau.ushirika.module.backup.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Nightly off-platform database backup -- dump the live Postgres database to one CSV file per
 * table, zip it, and upload to Cloudinary (already paid for and integrated for media, so this
 * needed no new billing account). Runs at midnight America/Chicago (the org's home timezone).
 * Every night's dump is kept as a "daily" backup for 30 days; the 1st-of-month dump is
 * additionally kept as a "monthly" backup for 4 years, satisfying the "records from up to 4
 * years ago" requirement without needing to retain 1,460 individual daily files. Retention is
 * enforced by listing each Cloudinary folder and deleting anything past its window -- no
 * separate backup-index table, so there's nothing extra to itself need backing up.
 *
 * Deliberately not pg_dump: Railway's build environment pins an old nixpkgs snapshot with no
 * Postgres 18 package available, and pg_dump refuses to dump from any server newer than itself --
 * a real version-matching problem with no simple fix that doesn't depend on Railway's nixpkgs
 * catalog catching up. Going over the same JDBC connection the app already uses successfully
 * every day sidesteps that entirely: no external binary, nothing to version-match, and CSV is
 * directly human-readable/spreadsheet-openable on its own, unlike a raw pg_dump SQL file.
 *
 * To restore: download the zip from Cloudinary (folder "backups/daily/" or "backups/monthly/" in
 * the dashboard) and unzip it -- each {table}.csv can be opened directly for a human-readable
 * look. To restore into a live database: first let the app itself (re)create the schema against
 * an empty database (normal Hibernate ddl-auto=update boot), then for each CSV run
 * `\copy "table_name" FROM 'table_name.csv' WITH (FORMAT csv, HEADER true)` in psql. Foreign-key
 * constraints can reject rows loaded out of dependency order -- if that happens, wrap the copies
 * in `SET session_replication_role = replica;` ... `SET session_replication_role = DEFAULT;`,
 * the standard Postgres technique for bulk-loading without enforcing FK order.
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
    private final DataSource dataSource;

    public DatabaseBackupService(
            DataSource dataSource,
            @Value("${app.cloudinary.cloud-name:NOT_SET}") String cloudName,
            @Value("${app.cloudinary.api-key:NOT_SET}") String apiKey,
            @Value("${app.cloudinary.api-secret:NOT_SET}") String apiSecret
    ) {
        this.dataSource = dataSource;
        this.cloudinaryConfigured = !"NOT_SET".equals(cloudName) && !"NOT_SET".equals(apiKey);
        this.cloudinary = cloudinaryConfigured
                ? new Cloudinary(ObjectUtils.asMap(
                        "cloud_name", cloudName, "api_key", apiKey, "api_secret", apiSecret, "secure", true))
                : new Cloudinary();
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

        Path zipFile = null;
        try {
            LocalDate today = LocalDate.now(ORG_ZONE);
            zipFile = Files.createTempFile("ushirika-backup-", ".zip");

            int tableCount = dumpToCsvZip(zipFile);
            long sizeBytes = Files.size(zipFile);

            String dailyPublicId = DAILY_PREFIX + today.format(DAILY_FMT);
            upload(zipFile, dailyPublicId);
            log.info("Database backup uploaded: {} ({} tables, {} bytes)", dailyPublicId, tableCount, sizeBytes);

            String publicId = dailyPublicId;
            if (today.getDayOfMonth() == 1) {
                String monthlyPublicId = MONTHLY_PREFIX + today.format(MONTHLY_FMT);
                upload(zipFile, monthlyPublicId);
                log.info("Database backup also archived as monthly: {}", monthlyPublicId);
                publicId = monthlyPublicId;
            }

            enforceRetention(DAILY_PREFIX, DAILY_FMT, today.minusDays(DAILY_RETENTION_DAYS));
            enforceRetention(MONTHLY_PREFIX, MONTHLY_FMT, today.minusMonths(MONTHLY_RETENTION_MONTHS));

            return new BackupResult(true, publicId, sizeBytes, "Backup uploaded successfully (" + tableCount + " tables).");
        } catch (Exception e) {
            log.error("Database backup failed: {}", e.getMessage(), e);
            return new BackupResult(false, null, 0, "Backup failed: " + e.getMessage());
        } finally {
            deleteQuietly(zipFile);
        }
    }

    /** Every existing backup, newest first -- for the low-priority admin "Backups" tab. Cloudinary
     * is queried live rather than an index table, same reasoning as enforceRetention(). */
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

    /** Writes one {table}.csv per public base table into the zip, over the app's own JDBC
     * connection. Table names come from information_schema (trusted catalog data, not user
     * input), so building the SELECT/identifier string directly is safe here. Returns the
     * number of tables dumped. */
    private int dumpToCsvZip(Path zipDest) throws Exception {
        try (Connection conn = dataSource.getConnection();
             var out = Files.newOutputStream(zipDest);
             ZipOutputStream zos = new ZipOutputStream(out)) {

            List<String> tables = listTables(conn);
            for (String table : tables) {
                zos.putNextEntry(new ZipEntry(table + ".csv"));
                writeTableCsv(conn, table, zos);
                zos.closeEntry();
            }
            return tables.size();
        }
    }

    private List<String> listTables(Connection conn) throws Exception {
        List<String> tables = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT table_name FROM information_schema.tables " +
                     "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name")) {
            while (rs.next()) tables.add(rs.getString(1));
        }
        return tables;
    }

    private void writeTableCsv(Connection conn, String table, ZipOutputStream zos) throws Exception {
        Writer writer = new OutputStreamWriter(zos, StandardCharsets.UTF_8);
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM \"" + table + "\"")) {
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();

            for (int i = 1; i <= cols; i++) {
                if (i > 1) writer.write(",");
                writer.write(csvEscape(meta.getColumnName(i)));
            }
            writer.write("\n");

            while (rs.next()) {
                for (int i = 1; i <= cols; i++) {
                    if (i > 1) writer.write(",");
                    Object val = rs.getObject(i);
                    writer.write(val == null ? "" : csvEscape(val.toString()));
                }
                writer.write("\n");
            }
        }
        writer.flush(); // never close -- that would close the shared zip stream
    }

    private static String csvEscape(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
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

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not delete temp backup file {}: {}", path, e.getMessage());
        }
    }
}
