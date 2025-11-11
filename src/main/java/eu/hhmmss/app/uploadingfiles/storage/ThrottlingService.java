package eu.hhmmss.app.uploadingfiles.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Service for throttling concurrent file processing operations.
 * Uses a Semaphore to limit the number of simultaneous conversions
 * to prevent resource exhaustion from LibreOffice processes.
 */
@Service
public class ThrottlingService {

    private static final Logger logger = LoggerFactory.getLogger(ThrottlingService.class);

    private final Semaphore semaphore;
    private final int maxConcurrentRequests;
    private final long timeoutSeconds;

    /**
     * Creates a new ThrottlingService with configurable concurrency limits.
     *
     * @param maxConcurrentRequests Maximum number of concurrent conversion requests (default: 2)
     * @param timeoutSeconds Timeout in seconds for acquiring a permit (default: 30)
     */
    public ThrottlingService(
            @Value("${app.throttling.max-concurrent-requests:2}") int maxConcurrentRequests,
            @Value("${app.throttling.timeout-seconds:30}") long timeoutSeconds) {
        this.maxConcurrentRequests = maxConcurrentRequests;
        this.timeoutSeconds = timeoutSeconds;
        this.semaphore = new Semaphore(maxConcurrentRequests, true); // fair ordering

        logger.info("ThrottlingService initialized with max {} concurrent requests, {} second timeout",
                maxConcurrentRequests, timeoutSeconds);
    }

    /**
     * Attempts to acquire a permit for processing.
     * Blocks until a permit is available or timeout occurs.
     *
     * @throws TooManyRequestsException if unable to acquire permit within timeout
     */
    public void acquirePermit() throws TooManyRequestsException {
        try {
            logger.debug("Attempting to acquire permit. Available: {}/{}",
                    semaphore.availablePermits(), maxConcurrentRequests);

            boolean acquired = semaphore.tryAcquire(timeoutSeconds, TimeUnit.SECONDS);

            if (!acquired) {
                logger.warn("Failed to acquire permit within {} seconds. All {} slots busy.",
                        timeoutSeconds, maxConcurrentRequests);
                throw new TooManyRequestsException(
                        "Server is currently processing too many requests. Please try again in a few moments.");
            }

            logger.debug("Permit acquired. Available: {}/{}",
                    semaphore.availablePermits(), maxConcurrentRequests);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while waiting for permit", e);
            throw new TooManyRequestsException("Request processing was interrupted. Please try again.");
        }
    }

    /**
     * Releases a permit after processing completes.
     * Should always be called in a finally block to ensure permits are returned.
     */
    public void releasePermit() {
        semaphore.release();
        logger.debug("Permit released. Available: {}/{}",
                semaphore.availablePermits(), maxConcurrentRequests);
    }

    /**
     * Gets the number of currently available permits.
     *
     * @return number of available permits
     */
    public int getAvailablePermits() {
        return semaphore.availablePermits();
    }

    /**
     * Gets the maximum number of concurrent requests allowed.
     *
     * @return max concurrent requests
     */
    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }
}
