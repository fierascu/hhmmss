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

    public UploadService(FileCleanupService fileCleanupService) {
        this.fileCleanupService = fileCleanupService;
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
        } catch (IOException e) {
            throw new StorageException("Could not initialize temporary storage", e);
        }
    }

    public String store(MultipartFile file) {
        try {
            if (file.isEmpty()) {
                throw new StorageException("Failed to store empty file.");
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

            // Generate time-based UUID for creation time traceability
            UUID timeBasedUuid = TimeBasedUuidGenerator.generate();

            // Extract original file extension
            String fileExtension = "";
            int lastDotIndex = originalFilename.lastIndexOf('.');
            if (lastDotIndex > 0) {
                fileExtension = originalFilename.substring(lastDotIndex);
            }

            // Build filename: UUID-hash-originalExtension
            // Example: 018c5e1e-3f2a-7b4c-9d6e-1a2b3c4d5e6f-a1b2c3d4e5f67890.xlsx
            String secureFilename = timeBasedUuid + "-" + fileHash + fileExtension;

            Path destinationFile = this.rootLocation.resolve(Paths.get(secureFilename))
                    .normalize().toAbsolutePath();
            if (!destinationFile.getParent().equals(this.rootLocation.toAbsolutePath())) {
                // This is a security check
                throw new StorageException("Cannot store file outside current directory.");
            }

            // Write file content to destination
            Files.write(destinationFile, fileContent);
            log.info("File '{}' uploaded successfully as: {} (UUID: {}, Hash: {})",
                    originalFilename, secureFilename, timeBasedUuid, fileHash);

            return secureFilename;
        } catch (IOException e) {
            throw new StorageException("Failed to store file.", e);
        }
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