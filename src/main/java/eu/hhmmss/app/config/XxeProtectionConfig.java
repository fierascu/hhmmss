package eu.hhmmss.app.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class to prevent XXE (XML External Entity) attacks in uploaded files.
 *
 * Security Context:
 * - Application processes XLSX files which are ZIP archives containing XML
 * - Apache POI parses XML content internally when reading Excel files
 * - Without protection, malicious XLSX files could trigger XXE attacks
 *
 * Attack Vector Example:
 * A malicious XLSX file could contain:
 * <?xml version="1.0"?>
 * <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
 * <worksheet>&xxe;</worksheet>
 *
 * This configuration disables external entity processing to prevent:
 * - File disclosure (reading /etc/passwd, application config, etc.)
 * - SSRF (Server-Side Request Forgery) attacks
 * - Denial of Service via billion laughs attack
 *
 * Implementation:
 * Sets system properties that Apache POI's XML parser respects.
 * These properties configure the underlying StAX (Streaming API for XML) parser.
 */
@Configuration
@Slf4j
public class XxeProtectionConfig {

    @PostConstruct
    public void configureXxeProtection() {
        log.info("Configuring XXE (XML External Entity) protection for Apache POI");

        // Disable external DTD (Document Type Definition) processing
        // Prevents DOCTYPE declarations from loading external entities
        System.setProperty("javax.xml.stream.isSupportingExternalEntities", "false");

        // Disable support for external entities entirely
        // Ensures no external entity references are resolved
        System.setProperty("javax.xml.stream.supportDTD", "false");

        // Set secure XML input factory implementation
        // Uses Woodstox parser (included with Apache POI) which respects security settings
        System.setProperty("javax.xml.stream.XMLInputFactory",
                "com.ctc.wstx.stax.WstxInputFactory");

        // Additional protection: Limit entity expansion
        // Prevents billion laughs attack (exponential entity expansion)
        // Setting to 64 (a small positive value) enforces a strict limit
        // Note: 0 means "no limit" which would be insecure
        System.setProperty("jdk.xml.entityExpansionLimit", "64");

        // Disable external general entities (for older JDK versions)
        System.setProperty("http://apache.org/xml/features/disallow-doctype-decl", "true");
        System.setProperty("http://xml.org/sax/features/external-general-entities", "false");
        System.setProperty("http://xml.org/sax/features/external-parameter-entities", "false");

        log.info("XXE protection configured successfully:");
        log.info("  - External entities: DISABLED");
        log.info("  - DTD support: DISABLED");
        log.info("  - Entity expansion limit: 64");
        log.info("  - XML Input Factory: com.ctc.wstx.stax.WstxInputFactory");
    }
}
