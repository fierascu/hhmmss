package eu.hhmmss.app.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class FileTypeValidatorTest {

    @Test
    void testValidateExcelFile_ValidXlsx() throws IOException {
        // XLSX files start with PK (ZIP magic bytes)
        byte[] xlsxHeader = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};
        InputStream inputStream = new ByteArrayInputStream(xlsxHeader);

        assertDoesNotThrow(() -> FileTypeValidator.validateExcelFile(inputStream, "test.xlsx"));
    }

    @Test
    void testValidateExcelFile_ValidXlsm() throws IOException {
        // XLSM files also start with PK (ZIP magic bytes)
        byte[] xlsmHeader = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};
        InputStream inputStream = new ByteArrayInputStream(xlsmHeader);

        assertDoesNotThrow(() -> FileTypeValidator.validateExcelFile(inputStream, "test.xlsm"));
    }

    @Test
    void testValidateExcelFile_ValidXlsb() throws IOException {
        // XLSB files also start with PK (ZIP magic bytes)
        byte[] xlsbHeader = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};
        InputStream inputStream = new ByteArrayInputStream(xlsbHeader);

        assertDoesNotThrow(() -> FileTypeValidator.validateExcelFile(inputStream, "test.xlsb"));
    }

    @Test
    void testValidateExcelFile_ValidXls() throws IOException {
        // XLS files start with OLE2 magic bytes
        byte[] xlsHeader = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        InputStream inputStream = new ByteArrayInputStream(xlsHeader);

        assertDoesNotThrow(() -> FileTypeValidator.validateExcelFile(inputStream, "test.xls"));
    }

    @Test
    void testValidateExcelFile_InvalidExtension() {
        byte[] validHeader = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};
        InputStream inputStream = new ByteArrayInputStream(validHeader);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> FileTypeValidator.validateExcelFile(inputStream, "test.txt"));

        assertEquals("File must have an Excel extension (.xls, .xlsx, .xlsm, or .xlsb)", exception.getMessage());
    }

    @Test
    void testValidateExcelFile_WindowsExecutable() {
        // Windows executable magic bytes (MZ)
        byte[] exeHeader = {0x4D, 0x5A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        InputStream inputStream = new ByteArrayInputStream(exeHeader);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> FileTypeValidator.validateExcelFile(inputStream, "malicious.xlsx"));

        assertEquals("File appears to be a Windows executable (.exe), not an Excel file", exception.getMessage());
    }

    @Test
    void testValidateExcelFile_LinuxExecutable() {
        // Linux executable magic bytes (ELF)
        byte[] elfHeader = {0x7F, 0x45, 0x4C, 0x46, 0x00, 0x00, 0x00, 0x00};
        InputStream inputStream = new ByteArrayInputStream(elfHeader);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> FileTypeValidator.validateExcelFile(inputStream, "malicious.xlsx"));

        assertEquals("File appears to be a Linux executable (ELF), not an Excel file", exception.getMessage());
    }

    @Test
    void testValidateExcelFile_TooSmall() {
        byte[] tinyFile = {0x00};
        InputStream inputStream = new ByteArrayInputStream(tinyFile);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> FileTypeValidator.validateExcelFile(inputStream, "test.xlsx"));

        assertEquals("File is too small to be a valid Excel file", exception.getMessage());
    }

    @Test
    void testValidateExcelFile_EmptyFile() {
        byte[] emptyFile = {};
        InputStream inputStream = new ByteArrayInputStream(emptyFile);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> FileTypeValidator.validateExcelFile(inputStream, "test.xlsx"));

        assertEquals("File is too small to be a valid Excel file", exception.getMessage());
    }

    @Test
    void testValidateExcelFile_WrongMagicBytesForXlsx() {
        // Invalid magic bytes for XLSX
        byte[] invalidHeader = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        InputStream inputStream = new ByteArrayInputStream(invalidHeader);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> FileTypeValidator.validateExcelFile(inputStream, "test.xlsx"));

        assertTrue(exception.getMessage().contains("File has Excel extension (.xlsx/.xlsm/.xlsb) but content is not a valid ZIP/Excel file"));
    }

    @Test
    void testValidateExcelFile_WrongMagicBytesForXls() {
        // Invalid magic bytes for XLS (only 2 bytes, need 8)
        byte[] invalidHeader = {0x00, 0x00};
        InputStream inputStream = new ByteArrayInputStream(invalidHeader);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> FileTypeValidator.validateExcelFile(inputStream, "test.xls"));

        assertTrue(exception.getMessage().contains("File has Excel extension (.xls) but content is not a valid OLE2/Excel file"));
    }

    @Test
    void testValidateExcelFile_XlsWithWrongContent() {
        // Valid length but wrong magic bytes for XLS
        byte[] invalidHeader = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        InputStream inputStream = new ByteArrayInputStream(invalidHeader);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> FileTypeValidator.validateExcelFile(inputStream, "test.xls"));

        assertTrue(exception.getMessage().contains("File has Excel extension (.xls) but content is not a valid OLE2/Excel file"));
    }

    @Test
    void testValidateExcelFile_CaseInsensitiveExtension() throws IOException {
        // Test with uppercase extension
        byte[] xlsxHeader = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};
        InputStream inputStream = new ByteArrayInputStream(xlsxHeader);

        assertDoesNotThrow(() -> FileTypeValidator.validateExcelFile(inputStream, "TEST.XLSX"));
    }

    @Test
    void testValidateExcelFile_MixedCaseExtension() throws IOException {
        // Test with mixed case extension
        byte[] xlsxHeader = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};
        InputStream inputStream = new ByteArrayInputStream(xlsxHeader);

        assertDoesNotThrow(() -> FileTypeValidator.validateExcelFile(inputStream, "test.XlSx"));
    }
}
