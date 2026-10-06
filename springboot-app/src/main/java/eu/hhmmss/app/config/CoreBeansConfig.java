package eu.hhmmss.app.config;

import eu.hhmmss.core.converter.HolidayService;
import eu.hhmmss.core.converter.PdfService;
import eu.hhmmss.core.converter.XlsService;
import eu.hhmmss.core.converter.ZipProcessingService;
import eu.hhmmss.core.metrics.TimeSavingsService;
import eu.hhmmss.core.storage.FileOwnershipService;
import eu.hhmmss.core.storage.ThrottlingService;
import org.jodconverter.core.DocumentConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the framework-agnostic {@code core} module classes as Spring beans.
 * These classes carry no Spring annotations themselves so that they can be
 * reused as-is by the Quarkus proof of concept, wired with CDI instead.
 */
@Configuration
public class CoreBeansConfig {

    @Bean
    public HolidayService holidayService(
            @Value("${holidays:}") String holidays,
            @Value("${epLongFridays:}") String epLongFridays) {
        return new HolidayService(holidays, epLongFridays);
    }

    @Bean
    public XlsService xlsService(HolidayService holidayService) {
        return new XlsService(holidayService);
    }

    @Bean
    public PdfService pdfService(DocumentConverter documentConverter) {
        return new PdfService(documentConverter);
    }

    @Bean
    public ZipProcessingService zipProcessingService(PdfService pdfService) {
        return new ZipProcessingService(pdfService);
    }

    @Bean
    public TimeSavingsService timeSavingsService() {
        return new TimeSavingsService();
    }

    @Bean
    public FileOwnershipService fileOwnershipService() {
        return new FileOwnershipService();
    }

    @Bean
    public ThrottlingService throttlingService(
            @Value("${app.throttling.max-concurrent-requests:2}") int maxConcurrentRequests,
            @Value("${app.throttling.timeout-seconds:30}") long timeoutSeconds) {
        return new ThrottlingService(maxConcurrentRequests, timeoutSeconds);
    }
}
