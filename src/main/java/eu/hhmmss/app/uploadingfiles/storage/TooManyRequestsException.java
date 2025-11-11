package eu.hhmmss.app.uploadingfiles.storage;

/**
 * Exception thrown when the server receives too many concurrent requests
 * and cannot process additional requests within the configured timeout.
 * Should result in HTTP 429 (Too Many Requests) response.
 */
public class TooManyRequestsException extends RuntimeException {

    /**
     * Constructs a new TooManyRequestsException with the specified detail message.
     *
     * @param message the detail message
     */
    public TooManyRequestsException(String message) {
        super(message);
    }

    /**
     * Constructs a new TooManyRequestsException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public TooManyRequestsException(String message, Throwable cause) {
        super(message, cause);
    }
}
