package eu.hhmmss.app.config;

import eu.hhmmss.core.security.XxeProtection;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * Triggers XXE protection setup (see {@link XxeProtection}) at Spring Boot startup.
 */
@Configuration
public class XxeProtectionConfig {

    @PostConstruct
    public void configureXxeProtection() {
        XxeProtection.configure();
    }
}
