package eu.hhmmss.app.uploadingfiles.storage;

import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * HTTP Session Listener that implements true "convert and forget" functionality
 * by deleting all files owned by a session when it expires.
 *
 * Security Benefits:
 * - Better privacy: Files are deleted as soon as session ends
 * - Prevents data accumulation: No orphaned files in temporary storage
 * - True ephemeral behavior: Aligns with "convert and forget" UX
 *
 * Implementation:
 * - Listens for session destruction events (timeout or explicit invalidation)
 * - Retrieves all files owned by the expired session
 * - Deletes files from filesystem
 * - Cleans up ownership tracking data
 */
@Component
@WebListener
@Slf4j
public class SessionCleanupListener implements HttpSessionListener {

    private final FileOwnershipService fileOwnershipService;
    private Path rootLocation;

    @Autowired
    public SessionCleanupListener(FileOwnershipService fileOwnershipService) {
        this.fileOwnershipService = fileOwnershipService;
        // Initialize root location from system temp directory
        String tempDir = System.getProperty("java.io.tmpdir");
        this.rootLocation = Paths.get(tempDir, "uploads");
    }

    @Override
    public void sessionCreated(HttpSessionEvent event) {
        // No action needed on session creation
        String sessionId = event.getSession().getId();
        log.debug("Session created: {}...", maskSessionId(sessionId));
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        String sessionId = event.getSession().getId();
        log.info("Session destroyed: {}... - Starting file cleanup", maskSessionId(sessionId));

        // Get all files owned by this session
        Set<String> filesToDelete = fileOwnershipService.getFilesForSession(sessionId);

        if (filesToDelete.isEmpty()) {
            log.debug("No files to delete for session: {}...", maskSessionId(sessionId));
        } else {
            log.info("Deleting {} file(s) for expired session: {}...",
                    filesToDelete.size(), maskSessionId(sessionId));

            int deletedCount = 0;
            int failedCount = 0;

            // Delete each file from the filesystem
            for (String filename : filesToDelete) {
                try {
                    Path filePath = rootLocation.resolve(filename).normalize().toAbsolutePath();

                    // Security check: ensure file is within upload directory
                    if (!filePath.startsWith(rootLocation.toAbsolutePath())) {
                        log.warn("Security: Attempted to delete file outside upload directory: {}", filename);
                        failedCount++;
                        continue;
                    }

                    // Delete the file if it exists
                    if (Files.exists(filePath)) {
                        Files.delete(filePath);
                        deletedCount++;
                        log.debug("Deleted file: {} for session: {}...", filename, maskSessionId(sessionId));
                    } else {
                        log.debug("File already deleted: {} for session: {}...", filename, maskSessionId(sessionId));
                    }

                } catch (IOException e) {
                    failedCount++;
                    log.error("Failed to delete file: {} for session: {}... - Error: {}",
                            filename, maskSessionId(sessionId), e.getMessage());
                }
            }

            log.info("Session cleanup completed for {}... - Deleted: {}, Failed: {}, Total: {}",
                    maskSessionId(sessionId), deletedCount, failedCount, filesToDelete.size());
        }

        // Clean up ownership tracking data
        fileOwnershipService.cleanupSession(sessionId);
    }

    /**
     * Masks a session ID for logging to prevent session fixation attacks.
     * Shows only first 8 characters.
     *
     * @param sessionId the session ID to mask
     * @return masked session ID
     */
    private String maskSessionId(String sessionId) {
        if (sessionId == null) {
            return "null";
        }
        if (sessionId.length() <= 8) {
            return sessionId.substring(0, Math.min(4, sessionId.length())) + "***";
        }
        return sessionId.substring(0, 8) + "***";
    }
}
