package eu.hhmmss.app.util;

import java.io.IOException;
import java.io.InputStream;

/**
 * Utility class for validating file types based on magic bytes (file signatures).
 * This helps detect when files have been renamed with incorrect extensions.
 */
public class FileTypeValidator {

    // Magic bytes for various file formats
    private static final byte[] XLSX_MAGIC = {0x50, 0x4B}; // PK - ZIP archive (XLSX are ZIP files)
    private static final byte[] XLS_MAGIC = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1}; // OLE2 format

    // Executable file magic bytes (to explicitly block)
    private static final byte[] EXE_MAGIC = {0x4D, 0x5A}; // MZ - DOS/Windows executable
    private static final byte[] ELF_MAGIC = {0x7F, 0x45, 0x4C, 0x46}; // ELF - Linux executable

    /**
     * Validates that the file content matches its expected Excel extension.
     *
     * @param inputStream The input stream of the file to validate
     * @param filename The original filename with extension
     * @throws IOException If unable to read the file
     * @throws IllegalArgumentException If the file content doesn't match the Excel extension
     */
    public static void validateExcelFile(InputStream inputStream, String filename) throws IOException {
        String lowerFilename = filename.toLowerCase();

        // Determine expected format based on extension
        boolean isXlsx = lowerFilename.endsWith(".xlsx") ||
                        lowerFilename.endsWith(".xlsm") ||
                        lowerFilename.endsWith(".xlsb");
        boolean isXls = lowerFilename.endsWith(".xls");

        if (!isXlsx && !isXls) {
            throw new IllegalArgumentException("File must have an Excel extension (.xls, .xlsx, .xlsm, or .xlsb)");
        }

        // Read enough bytes to check various file signatures
        byte[] header = new byte[8];
        int bytesRead = inputStream.read(header);

        if (bytesRead < 2) {
            throw new IllegalArgumentException("File is too small to be a valid Excel file");
        }

        // Check for executable files first (security check)
        if (matchesMagicBytes(header, EXE_MAGIC)) {
            throw new IllegalArgumentException("File appears to be a Windows executable (.exe), not an Excel file");
        }
        if (matchesMagicBytes(header, ELF_MAGIC)) {
            throw new IllegalArgumentException("File appears to be a Linux executable (ELF), not an Excel file");
        }

        // Validate Excel format magic bytes
        if (isXlsx) {
            // XLSX files are ZIP archives, should start with PK
            if (!matchesMagicBytes(header, XLSX_MAGIC)) {
                throw new IllegalArgumentException(
                    "File has Excel extension (.xlsx/.xlsm/.xlsb) but content is not a valid ZIP/Excel file. " +
                    "The file may have been renamed or corrupted."
                );
            }
        } else if (isXls) {
            // XLS files use OLE2 format
            if (bytesRead < 8 || !matchesMagicBytes(header, XLS_MAGIC)) {
                throw new IllegalArgumentException(
                    "File has Excel extension (.xls) but content is not a valid OLE2/Excel file. " +
                    "The file may have been renamed or corrupted."
                );
            }
        }
    }

    /**
     * Checks if the file header matches the expected magic bytes.
     *
     * @param header The file header bytes
     * @param magicBytes The expected magic bytes
     * @return true if the header matches the magic bytes
     */
    private static boolean matchesMagicBytes(byte[] header, byte[] magicBytes) {
        if (header.length < magicBytes.length) {
            return false;
        }

        for (int i = 0; i < magicBytes.length; i++) {
            if (header[i] != magicBytes[i]) {
                return false;
            }
        }

        return true;
    }
}
