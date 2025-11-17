package eu.hhmmss.app.uploadingfiles.storage;

import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service that tracks file ownership by HTTP session.
 * Prevents IDOR (Insecure Direct Object Reference) vulnerabilities by ensuring
 * users can only access files they uploaded or generated in their session.
 *
 * Security Features:
 * - Session-based file ownership tracking
 * - Thread-safe concurrent data structures
 * - Automatic cleanup on session invalidation
 */
@Service
@Slf4j
public class FileOwnershipService {

    /**
     * Maps session ID to set of filenames owned by that session.
     * Thread-safe for concurrent access.
     */
    private final Map<String, Set<String>> sessionToFiles = new ConcurrentHashMap<>();

    /**
     * Maps filename to owning session ID for quick ownership lookup.
     * Thread-safe for concurrent access.
     */
    private final Map<String, String> fileToSession = new ConcurrentHashMap<>();

    /**
     * Tracks a file as belonging to the given session.
     *
     * @param sessionId the HTTP session ID
     * @param filename  the filename to track
     */
    public void trackFile(String sessionId, String filename) {
        if (sessionId == null || filename == null) {
            log.warn("Attempted to track file with null sessionId or filename");
            return;
        }

        // Add to session -> files mapping
        sessionToFiles.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(filename);

        // Add to file -> session mapping
        fileToSession.put(filename, sessionId);

        log.debug("Tracked file '{}' for session '{}'", filename, maskSessionId(sessionId));
    }

    /**
     * Verifies if the given session owns the specified file.
     *
     * @param session  the HTTP session
     * @param filename the filename to check
     * @return true if the session owns the file, false otherwise
     */
    public boolean verifyOwnership(HttpSession session, String filename) {
        if (session == null || filename == null) {
            log.warn("Ownership verification failed: null session or filename");
            return false;
        }

        String sessionId = session.getId();
        String owningSessionId = fileToSession.get(filename);

        boolean isOwner = sessionId.equals(owningSessionId);

        if (!isOwner) {
            log.warn("Access denied: Session '{}' attempted to access file '{}' owned by session '{}'",
                    maskSessionId(sessionId), filename, maskSessionId(owningSessionId));
        }

        return isOwner;
    }

    /**
     * Removes all files tracked for a given session.
     * Called when a session is invalidated or expired.
     *
     * @param sessionId the session ID to clean up
     */
    public void cleanupSession(String sessionId) {
        if (sessionId == null) {
            return;
        }

        Set<String> files = sessionToFiles.remove(sessionId);
        if (files != null) {
            // Remove all file -> session mappings for this session
            files.forEach(fileToSession::remove);
            log.info("Cleaned up {} file(s) for session '{}'", files.size(), maskSessionId(sessionId));
        }
    }

    /**
     * Removes tracking for a specific file.
     * Useful when files are deleted from the filesystem.
     *
     * @param filename the filename to stop tracking
     */
    public void removeFile(String filename) {
        if (filename == null) {
            return;
        }

        String sessionId = fileToSession.remove(filename);
        if (sessionId != null) {
            Set<String> files = sessionToFiles.get(sessionId);
            if (files != null) {
                files.remove(filename);
                log.debug("Removed file '{}' from session '{}'", filename, maskSessionId(sessionId));
            }
        }
    }

    /**
     * Gets all files owned by a session.
     *
     * @param sessionId the session ID
     * @return set of filenames, or empty set if none found
     */
    public Set<String> getFilesForSession(String sessionId) {
        Set<String> files = sessionToFiles.get(sessionId);
        return files != null ? Set.copyOf(files) : Set.of();
    }

    /**
     * Clears all ownership tracking data.
     * Primarily used for testing or system reset.
     */
    public void clearAll() {
        sessionToFiles.clear();
        fileToSession.clear();
        log.info("Cleared all file ownership tracking data");
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
