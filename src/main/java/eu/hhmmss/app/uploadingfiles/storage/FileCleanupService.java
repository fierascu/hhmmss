package eu.hhmmss.app.uploadingfiles.storage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

@Service
@Slf4j
public class FileCleanupService {

    private Path uploadLocation;

    @Value("${cleanup.retention.days:7}")
    private int retentionDays;

    @PostConstruct
    public void initialize() {
        String tempDir = System.getProperty("java.io.tmpdir");
        this.uploadLocation = Paths.get(tempDir, "uploads");
        log.info("File cleanup service initialized. Upload location: {}, Retention: {} days",
                uploadLocation.toAbsolutePath(), retentionDays);
    }

    /**
     * Scheduled job that runs at midnight every day to clean up old files.
     * Cron expression: "0 0 0 * * *" means:
     * - second: 0
     * - minute: 0
     * - hour: 0 (midnight)
     * - day of month: * (every day)
     * - month: * (every month)
     * - day of week: * (every day of week)
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void cleanupOldFiles() {
        log.info("Starting scheduled cleanup of files older than {} days", retentionDays);

        if (!Files.exists(uploadLocation)) {
            log.info("Upload directory does not exist, skipping cleanup: {}", uploadLocation);
            return;
        }

        Instant cutoffTime = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deletedCount = 0;
        int errorCount = 0;
        long totalBytesFreed = 0;

        try (Stream<Path> files = Files.list(uploadLocation)) {
            for (Path file : files.toList()) {
                try {
                    if (Files.isDirectory(file)) {
                        log.debug("Skipping directory: {}", file.getFileName());
                        continue;
                    }

                    BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                    Instant fileModifiedTime = attrs.lastModifiedTime().toInstant();

                    if (fileModifiedTime.isBefore(cutoffTime)) {
                        long fileSize = attrs.size();
                        Files.delete(file);
                        deletedCount++;
                        totalBytesFreed += fileSize;
                        log.debug("Deleted old file: {} (modified: {}, size: {} bytes)",
                                file.getFileName(), fileModifiedTime, fileSize);
                    }
                } catch (IOException e) {
                    errorCount++;
                    log.error("Failed to delete file: {}", file.getFileName(), e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to list files in upload directory: {}", uploadLocation, e);
            return;
        }

        if (deletedCount > 0 || errorCount > 0) {
            log.info("Cleanup completed. Deleted {} file(s), freed {} bytes. Errors: {}",
                    deletedCount, totalBytesFreed, errorCount);
        } else {
            log.info("Cleanup completed. No files to delete.");
        }
    }

    /**
     * Manual cleanup method for testing or administrative purposes.
     * @return number of files deleted
     */
    public int manualCleanup() {
        log.info("Manual cleanup triggered");
        cleanupOldFiles();
        return 0; // Return value can be enhanced to track deletion count if needed
    }
}
