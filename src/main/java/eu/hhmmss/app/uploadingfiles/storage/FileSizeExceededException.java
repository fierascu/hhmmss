package eu.hhmmss.app.uploadingfiles.storage;

/**
 * Exception thrown when an uploaded file exceeds the allowed size limit
 * for its specific file type (XLSX or ZIP).
 */
public class FileSizeExceededException extends StorageException {

    /**
     * Constructs a new FileSizeExceededException with the specified detail message.
     *
     * @param message the detail message
     */
    public FileSizeExceededException(String message) {
        super(message);
    }

    /**
     * Constructs a new FileSizeExceededException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public FileSizeExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
