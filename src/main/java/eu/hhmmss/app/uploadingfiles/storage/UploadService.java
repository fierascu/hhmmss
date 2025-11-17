package eu.hhmmss.app.uploadingfiles.storage;

import eu.hhmmss.app.util.FileHasher;
import eu.hhmmss.app.util.FileTypeValidator;
import eu.hhmmss.app.util.TimeBasedUuidGenerator;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@Slf4j
public class UploadService {

    private Path rootLocation;
    private final FileCleanupService fileCleanupService;
    private final FileOwnershipService fileOwnershipService;

    public UploadService(FileCleanupService fileCleanupService, FileOwnershipService fileOwnershipService) {
        this.fileCleanupService = fileCleanupService;
        this.fileOwnershipService = fileOwnershipService;
    }

    @PostConstruct
    public void initialize() {
        String tempDir = System.getProperty("java.io.tmpdir");
        this.rootLocation = Paths.get(tempDir, "uploads");
        log.info("Using temporary upload location: {}", rootLocation.toAbsolutePath());

        try {
            Files.createDirectories(rootLocation);
            log.info("Temporary upload directory created/verified: {}", rootLocation.toAbsolutePath());

            // Clear all files in the uploads folder on startup for security purposes
            fileCleanupService.cleanupAllFiles();

            // Pre-generate templates for common months after cleanup
            fileCleanupService.preGenerateTemplates();

        } catch (IOException e) {
            throw new StorageException("Could not initialize temporary storage", e);
        }
    }

    /**
     * Stores an uploaded file with session-based ownership tracking.
     * Implements security recommendations from IDOR vulnerability audit.
     *
     * @param file the uploaded file
     * @param sessionId the HTTP session ID for ownership tracking
     * @return the secure filename with session ID prefix
     */
    public String store(MultipartFile file, String sessionId) {
        try {
            if (file.isEmpty()) {
                throw new StorageException("Failed to store empty file.");
            }

            if (sessionId == null || sessionId.trim().isEmpty()) {
                throw new StorageException("Session ID is required for file upload.");
            }

            String originalFilename = Objects.requireNonNull(file.getOriginalFilename());

            // Read file content once for validation and hashing (max 128KB per config)
            byte[] fileContent = file.getBytes();

            // Validate file content matches extension (security check)
            try (InputStream validationStream = new ByteArrayInputStream(fileContent)) {
                FileTypeValidator.validateFile(validationStream, originalFilename);
            } catch (IllegalArgumentException e) {
                throw new StorageException(e.getMessage());
            }

            // Compute file hash for integrity and traceability
            String fileHash;
            try (InputStream hashStream = new ByteArrayInputStream(fileContent)) {
                fileHash = FileHasher.computeShortHash(hashStream);
            }

            // Generate secure random UUID to prevent filename enumeration attacks
            UUID secureRandomUuid = TimeBasedUuidGenerator.generate();

            // Extract and sanitize original file extension to prevent path traversal
            String fileExtension = extractSafeExtension(originalFilename);

            // Build filename with session ID prefix: sessionID_UUID-hash-originalExtension
            // Example: ABC123DEF456_550e8400-e29b-41d4-a716-446655440000-a1b2c3d4e5f67890.xlsx
            // This prevents IDOR attacks by making filenames session-specific
            String sessionPrefix = sanitizeSessionId(sessionId);
            String secureFilename = sessionPrefix + "_" + secureRandomUuid + "-" + fileHash + fileExtension;

            Path destinationFile = this.rootLocation.resolve(Paths.get(secureFilename))
                    .normalize().toAbsolutePath();
            if (!destinationFile.getParent().equals(this.rootLocation.toAbsolutePath())) {
                // This is a security check
                throw new StorageException("Cannot store file outside current directory.");
            }

            // Write file content to destination
            Files.write(destinationFile, fileContent);

            // Track file ownership for this session
            fileOwnershipService.trackFile(sessionId, secureFilename);

            log.info("File '{}' uploaded successfully as: {} (UUID: {}, Hash: {}, Session: {}...)",
                    originalFilename, secureFilename, secureRandomUuid, fileHash,
                    sessionId.substring(0, Math.min(8, sessionId.length())));

            return secureFilename;
        } catch (IOException e) {
            throw new StorageException("Failed to store file.", e);
        }
    }

    /**
     * Sanitizes session ID for use in filename by taking first 12 alphanumeric characters.
     * Prevents path traversal and special character issues in filenames.
     *
     * @param sessionId the session ID to sanitize
     * @return sanitized session ID prefix
     */
    private String sanitizeSessionId(String sessionId) {
        if (sessionId == null) {
            return "UNKNOWN";
        }
        // Take first 12 characters and remove any non-alphanumeric characters
        String cleaned = sessionId.replaceAll("[^a-zA-Z0-9]", "");
        return cleaned.substring(0, Math.min(12, cleaned.length()));
    }

    /**
     * Extracts and sanitizes file extension from filename to prevent path traversal attacks.
     * Only allows safe characters (alphanumeric, dot, hyphen) and removes any path separators.
     *
     * Security: Prevents attackers from using malicious filenames like "test.xlsx/../../../etc/passwd"
     * to traverse outside the upload directory.
     *
     * @param filename the original filename
     * @return sanitized file extension (e.g., ".xlsx", ".xls") or empty string if invalid
     */
    private String extractSafeExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }

        // Find the last dot in the filename
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex <= 0 || lastDotIndex == filename.length() - 1) {
            return ""; // No extension or dot at the beginning/end
        }

        // Extract the part after the last dot
        String extension = filename.substring(lastDotIndex);

        // Security: Remove any path separators or special characters that could enable path traversal
        // Only allow: dot, alphanumeric characters, and hyphen (for extensions like .xlsm)
        extension = extension.replaceAll("[^.a-zA-Z0-9-]", "");

        // Additional security: Ensure extension doesn't contain multiple dots or start with anything but a dot
        if (!extension.startsWith(".") || extension.indexOf('.', 1) != -1) {
            return "";
        }

        // Validate it's a reasonable length (most extensions are 2-5 chars)
        if (extension.length() > 10) {
            return "";
        }

        return extension.toLowerCase(); // Normalize to lowercase
    }

    /**
     * Tracks ownership for a generated file (DOCX, PDF, etc.) based on source file.
     * Derived files inherit the session ownership of their source file.
     *
     * @param sourceFilename the original filename that was processed
     * @param generatedFilename the new filename that was generated
     * @param sessionId the session ID that owns both files
     */
    public void trackGeneratedFile(String sourceFilename, String generatedFilename, String sessionId) {
        fileOwnershipService.trackFile(sessionId, generatedFilename);
        log.debug("Tracked generated file '{}' from source '{}' for session {}...",
                generatedFilename, sourceFilename, sessionId.substring(0, Math.min(8, sessionId.length())));
    }

    public Stream<Path> loadAll() {
        try {
            return Files.walk(this.rootLocation, 1)
                    .filter(path -> !path.equals(this.rootLocation))
                    .map(this.rootLocation::relativize);
        } catch (IOException e) {
            throw new StorageException("Failed to read stored files", e);
        }
    }

    public Path load(String filename) {
        return rootLocation.resolve(filename);
    }

    public Resource loadAsResource(String filename) {
        try {
            Path file = load(filename);
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new StorageFileNotFoundException(
                        "Could not read file: " + filename);
            }
        } catch (MalformedURLException e) {
            throw new StorageFileNotFoundException("Could not read file: " + filename, e);
        }
    }

    public void deleteAll() {
        FileSystemUtils.deleteRecursively(rootLocation.toFile());
    }

    public void init() {
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new StorageException("Could not initialize storage", e);
        }
    }
}