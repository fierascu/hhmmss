package eu.hhmmss.app.converter;

import eu.hhmmss.app.uploadingfiles.storage.StorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Service to process ZIP files containing multiple Excel timesheets.
 * Extracts each Excel file, converts it to DOCX, and packages all results into a new ZIP.
 */
@Slf4j
@Service
public class ZipProcessingService {

    /**
     * Processes a ZIP file containing multiple Excel timesheets.
     *
     * @param zipPath Path to the uploaded ZIP file
     * @param uploadedZipFilename Original uploaded filename (with UUID-hash for traceability)
     * @param templatePath Path to the DOCX template
     * @param outputDir Directory where output files will be stored
     * @return ZipProcessingResult containing the result ZIP path and processing stats
     * @throws StorageException if processing fails
     */
    public ZipProcessingResult processZipFile(Path zipPath, String uploadedZipFilename, Path templatePath, Path outputDir) {
        List<Path> convertedFiles = new ArrayList<>();
        List<String> processedFileNames = new ArrayList<>();
        List<String> failedFileNames = new ArrayList<>();

        // Create temporary extraction directory
        Path extractDir = null;
        try {
            extractDir = Files.createTempDirectory("zip-extract-");
            log.info("Created temporary extraction directory: {}", extractDir);

            // Extract ZIP file
            extractZipFile(zipPath, extractDir);

            // Find all Excel files in the extracted directory
            List<Path> excelFiles = findExcelFiles(extractDir);
            log.info("Found {} Excel files in ZIP", excelFiles.size());

            if (excelFiles.isEmpty()) {
                throw new StorageException("No Excel files found in ZIP archive");
            }

            // Process each Excel file
            for (Path excelFile : excelFiles) {
                String fileName = excelFile.getFileName().toString();
                try {
                    log.info("Processing Excel file: {}", fileName);

                    // Read timesheet data
                    HhmmssDto hhmmssDto = XlsService.readTimesheet(excelFile);

                    // Generate output DOCX filename (based on original Excel filename)
                    String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
                    String outputFileName = baseName + "_timesheet.docx";
                    Path outputDocx = outputDir.resolve(outputFileName);

                    // Convert to DOCX
                    DocService.addDataToDocs(templatePath, hhmmssDto, outputDocx);

                    convertedFiles.add(outputDocx);
                    processedFileNames.add(fileName);
                    log.info("Successfully converted: {} -> {}", fileName, outputFileName);

                } catch (Exception e) {
                    log.error("Failed to process Excel file: {}", fileName, e);
                    failedFileNames.add(fileName + " (Error: " + e.getMessage() + ")");
                }
            }

            if (convertedFiles.isEmpty()) {
                throw new StorageException("Failed to convert any Excel files from the ZIP archive");
            }

            // Create result ZIP with all converted DOCX files
            // Maintain traceability: use uploaded filename as base for result ZIP
            String resultZipName = uploadedZipFilename + "-result.zip";
            Path resultZipPath = outputDir.resolve(resultZipName);
            createZipFile(convertedFiles, resultZipPath);

            // Clean up individual DOCX files (keep only the ZIP)
            for (Path docxFile : convertedFiles) {
                try {
                    Files.deleteIfExists(docxFile);
                } catch (IOException e) {
                    log.warn("Could not delete temporary DOCX file: {}", docxFile, e);
                }
            }

            log.info("Created result ZIP: {} with {} converted files", resultZipName, convertedFiles.size());

            return new ZipProcessingResult(
                resultZipPath,
                resultZipName,
                processedFileNames.size(),
                failedFileNames.size(),
                processedFileNames,
                failedFileNames
            );

        } catch (IOException e) {
            log.error("Error processing ZIP file: {}", zipPath, e);
            throw new StorageException("Failed to process ZIP file: " + e.getMessage(), e);
        } finally {
            // Clean up extraction directory
            if (extractDir != null) {
                try {
                    deleteDirectory(extractDir);
                    log.info("Cleaned up extraction directory: {}", extractDir);
                } catch (IOException e) {
                    log.warn("Could not delete extraction directory: {}", extractDir, e);
                }
            }
        }
    }

    /**
     * Extracts a ZIP file to the specified directory.
     */
    private void extractZipFile(Path zipPath, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // Skip directories
                if (entry.isDirectory()) {
                    continue;
                }

                // Security check: prevent path traversal attacks
                Path destPath = destDir.resolve(entry.getName()).normalize();
                if (!destPath.startsWith(destDir)) {
                    throw new IOException("ZIP entry is outside of the target directory: " + entry.getName());
                }

                // Create parent directories if needed
                Files.createDirectories(destPath.getParent());

                // Extract file
                try (FileOutputStream fos = new FileOutputStream(destPath.toFile())) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }

                log.debug("Extracted: {}", entry.getName());
                zis.closeEntry();
            }
        }
    }

    /**
     * Finds all Excel files in the given directory (recursively).
     */
    private List<Path> findExcelFiles(Path directory) throws IOException {
        List<Path> excelFiles = new ArrayList<>();

        try (var stream = Files.walk(directory)) {
            stream.filter(Files::isRegularFile)
                  .filter(this::isExcelFile)
                  .forEach(excelFiles::add);
        }

        return excelFiles;
    }

    /**
     * Checks if a file is an Excel file based on extension.
     */
    private boolean isExcelFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".xls") ||
               fileName.endsWith(".xlsx") ||
               fileName.endsWith(".xlsm") ||
               fileName.endsWith(".xlsb");
    }

    /**
     * Creates a ZIP file containing all the specified files.
     */
    private void createZipFile(List<Path> files, Path zipPath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            for (Path file : files) {
                ZipEntry entry = new ZipEntry(file.getFileName().toString());
                zos.putNextEntry(entry);

                try (FileInputStream fis = new FileInputStream(file.toFile())) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                }

                zos.closeEntry();
                log.debug("Added to ZIP: {}", file.getFileName());
            }
        }
    }

    /**
     * Recursively deletes a directory and all its contents.
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        try (var stream = Files.walk(directory)) {
            stream.sorted((a, b) -> b.compareTo(a)) // Reverse order to delete children before parents
                  .forEach(path -> {
                      try {
                          Files.delete(path);
                      } catch (IOException e) {
                          log.warn("Could not delete: {}", path, e);
                      }
                  });
        }
    }

    /**
     * Result of ZIP processing operation.
     */
    public record ZipProcessingResult(
        Path resultZipPath,
        String resultZipFileName,
        int successCount,
        int failureCount,
        List<String> processedFiles,
        List<String> failedFiles
    ) {}
}
