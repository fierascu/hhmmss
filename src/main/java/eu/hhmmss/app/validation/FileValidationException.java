package eu.hhmmss.app.validation;

/**
 * Exception thrown when file validation fails.
 */
public class FileValidationException extends Exception {

    private final ValidationErrorCode errorCode;

    public FileValidationException(ValidationErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public FileValidationException(ValidationErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ValidationErrorCode getErrorCode() {
        return errorCode;
    }

    public enum ValidationErrorCode {
        INVALID_FILE_TYPE,
        FILE_TOO_LARGE,
        FILE_EMPTY,
        INVALID_EXCEL_FORMAT,
        MISSING_REQUIRED_SHEET,
        SECURITY_VIOLATION,
        CORRUPT_FILE
    }
}
