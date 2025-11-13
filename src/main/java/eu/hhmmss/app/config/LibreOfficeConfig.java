package eu.hhmmss.app.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jodconverter.boot.autoconfigure.JodConverterLocalAutoConfiguration;
import org.jodconverter.core.DocumentConverter;
import org.jodconverter.core.office.OfficeManager;
import org.jodconverter.local.LocalConverter;
import org.jodconverter.local.office.LocalOfficeManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration class for LibreOffice/JodConverter validation.
 * Validates the LibreOffice installation path during application startup
 * and provides clear error messages if the path is invalid.
 *
 * This configuration disables the default JodConverter auto-configuration
 * and provides a custom DocumentConverter bean that handles errors gracefully.
 */
@Configuration
@EnableAutoConfiguration(exclude = JodConverterLocalAutoConfiguration.class)
@ConditionalOnProperty(name = "jodconverter.local.enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class LibreOfficeConfig {

    @Value("${jodconverter.local.office-home}")
    private String officeHome;

    @Value("${jodconverter.local.port-numbers:2002}")
    private String portNumbers;

    @Value("${jodconverter.local.max-tasks-per-process:10}")
    private int maxTasksPerProcess;

    /**
     * Validates the LibreOffice installation path during application startup.
     * Logs warnings if the path is invalid but doesn't prevent startup.
     */
    @PostConstruct
    public void validateLibreOfficePath() {
        log.info("Validating LibreOffice installation path: {}", officeHome);

        if (officeHome == null || officeHome.trim().isEmpty()) {
            log.error("=".repeat(80));
            log.error("LIBREOFFICE CONFIGURATION ERROR");
            log.error("=".repeat(80));
            log.error("The LibreOffice office-home path is not configured!");
            log.error("Property: jodconverter.local.office-home");
            log.error("Current value: {}", officeHome);
            log.error("=".repeat(80));
            log.error("PDF conversion features will NOT be available!");
            log.error("To fix this issue:");
            log.error("  1. Install LibreOffice on your system");
            log.error("  2. Update application.properties with the correct path:");
            log.error("     Windows: jodconverter.local.office-home=C:\\\\Program Files\\\\LibreOffice");
            log.error("     Linux:   jodconverter.local.office-home=/usr/lib/libreoffice");
            log.error("     macOS:   jodconverter.local.office-home=/Applications/LibreOffice.app/Contents");
            log.error("=".repeat(80));
            return;
        }

        Path officeHomePath = Paths.get(officeHome);

        if (!Files.exists(officeHomePath)) {
            log.error("=".repeat(80));
            log.error("LIBREOFFICE CONFIGURATION ERROR");
            log.error("=".repeat(80));
            log.error("The configured LibreOffice path does not exist!");
            log.error("Property: jodconverter.local.office-home");
            log.error("Current value: {}", officeHome);
            log.error("Resolved path: {}", officeHomePath.toAbsolutePath());
            log.error("=".repeat(80));
            log.error("PDF conversion features will NOT be available!");
            log.error("To fix this issue:");
            log.error("  1. Verify LibreOffice is installed on your system");
            log.error("  2. Update application.properties with the correct path:");
            log.error("     Windows: jodconverter.local.office-home=C:\\\\Program Files\\\\LibreOffice");
            log.error("     Linux:   jodconverter.local.office-home=/usr/lib/libreoffice");
            log.error("     macOS:   jodconverter.local.office-home=/Applications/LibreOffice.app/Contents");
            log.error("  3. Or disable JodConverter: jodconverter.local.enabled=false");
            log.error("=".repeat(80));
            return;
        }

        if (!Files.isDirectory(officeHomePath)) {
            log.error("=".repeat(80));
            log.error("LIBREOFFICE CONFIGURATION ERROR");
            log.error("=".repeat(80));
            log.error("The configured LibreOffice path is not a directory!");
            log.error("Property: jodconverter.local.office-home");
            log.error("Current value: {}", officeHome);
            log.error("=".repeat(80));
            log.error("PDF conversion features will NOT be available!");
            log.error("Please verify the path and restart the application.");
            log.error("=".repeat(80));
            return;
        }

        // Check for common LibreOffice executable locations
        boolean sofficeFound = checkForSofficeExecutable(officeHomePath);

        if (!sofficeFound) {
            log.warn("=".repeat(80));
            log.warn("LIBREOFFICE WARNING");
            log.warn("=".repeat(80));
            log.warn("Could not find LibreOffice executable (soffice) in the configured path.");
            log.warn("Path: {}", officeHome);
            log.warn("=".repeat(80));
            log.warn("The application will attempt to start LibreOffice, but it may fail.");
            log.warn("If you encounter conversion errors, please verify your LibreOffice installation.");
            log.warn("=".repeat(80));
        } else {
            log.info("LibreOffice installation validated successfully at: {}", officeHome);
        }
    }

    /**
     * Checks for the presence of the soffice executable in common locations.
     */
    private boolean checkForSofficeExecutable(Path officeHome) {
        // Common locations for soffice executable
        String[] possiblePaths = {
            "program/soffice",       // Linux/Windows
            "program/soffice.exe",   // Windows
            "program/soffice.bin",   // Linux
            "MacOS/soffice",         // macOS
            "Contents/MacOS/soffice" // macOS alternative
        };

        for (String relativePath : possiblePaths) {
            Path execPath = officeHome.resolve(relativePath);
            if (Files.exists(execPath)) {
                log.debug("Found soffice executable at: {}", execPath);
                return true;
            }
        }

        return false;
    }

    /**
     * Bean that indicates whether LibreOffice is properly configured.
     * Other components can inject this to check LibreOffice availability.
     */
    @Bean
    public LibreOfficeStatus libreOfficeStatus() {
        boolean available = false;

        if (officeHome != null && !officeHome.trim().isEmpty()) {
            Path officeHomePath = Paths.get(officeHome);
            available = Files.exists(officeHomePath) &&
                       Files.isDirectory(officeHomePath) &&
                       checkForSofficeExecutable(officeHomePath);
        }

        return new LibreOfficeStatus(available, officeHome);
    }

    /**
     * Creates the DocumentConverter bean with proper error handling.
     * Returns null if LibreOffice is not properly configured, allowing the app to start.
     */
    @Bean
    public DocumentConverter documentConverter() {
        LibreOfficeStatus status = libreOfficeStatus();

        if (!status.isAvailable()) {
            log.error("DocumentConverter bean will NOT be created due to LibreOffice configuration issues.");
            log.error("The application will start, but PDF conversion features will be unavailable.");
            return null;
        }

        try {
            log.info("Creating DocumentConverter with LibreOffice at: {}", officeHome);

            // Parse port numbers
            String[] ports = portNumbers.split(",");
            int[] portArray = new int[ports.length];
            for (int i = 0; i < ports.length; i++) {
                portArray[i] = Integer.parseInt(ports[i].trim());
            }

            // Build OfficeManager
            LocalOfficeManager.Builder builder = LocalOfficeManager.builder()
                    .officeHome(officeHome)
                    .portNumbers(portArray)
                    .maxTasksPerProcess(maxTasksPerProcess);

            OfficeManager officeManager = builder.build();

            // Start the office manager
            officeManager.start();
            log.info("OfficeManager started successfully");

            // Create and return the DocumentConverter
            DocumentConverter converter = LocalConverter.make(officeManager);
            log.info("DocumentConverter created successfully");

            return converter;

        } catch (Exception e) {
            log.error("=".repeat(80));
            log.error("FAILED TO CREATE DOCUMENTCONVERTER");
            log.error("=".repeat(80));
            log.error("An error occurred while initializing LibreOffice/JodConverter:", e);
            log.error("Error message: {}", e.getMessage());
            log.error("=".repeat(80));
            log.error("The application will start, but PDF conversion features will be unavailable.");
            log.error("Please verify your LibreOffice installation and configuration.");
            log.error("=".repeat(80));
            return null;
        }
    }

    /**
     * Simple status class to track LibreOffice availability.
     */
    public static class LibreOfficeStatus {
        private final boolean available;
        private final String configuredPath;

        public LibreOfficeStatus(boolean available, String configuredPath) {
            this.available = available;
            this.configuredPath = configuredPath;
        }

        public boolean isAvailable() {
            return available;
        }

        public String getConfiguredPath() {
            return configuredPath;
        }
    }
}
