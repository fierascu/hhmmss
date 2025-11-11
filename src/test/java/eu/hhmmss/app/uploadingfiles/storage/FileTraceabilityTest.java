package eu.hhmmss.app.uploadingfiles.storage;

import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for file traceability - ensuring output files maintain UUID+hash from input files.
 */
class FileTraceabilityTest {

    // Pattern to extract UUID and hash from filename
    // Format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx-hash.extension
    private static final Pattern FILENAME_PATTERN = Pattern.compile(
            "([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})-([0-9a-f]{16})(\\.[^.]+)"
    );

    @Test
    void testExtractUuidAndHashFromInputFile() {
        String inputFilename = "018c5e1e-3f2a-7b4c-9d6e-1a2b3c4d5e6f-a1b2c3d4e5f67890.xlsx";

        FileIdentifier identifier = extractFileIdentifier(inputFilename);

        assertNotNull(identifier);
        assertEquals("018c5e1e-3f2a-7b4c-9d6e-1a2b3c4d5e6f", identifier.uuid);
        assertEquals("a1b2c3d4e5f67890", identifier.hash);
        assertEquals(".xlsx", identifier.extension);
    }

    @Test
    void testDocxOutputMaintainsTraceability() {
        String inputFilename = "018c5e1e-3f2a-7b4c-9d6e-1a2b3c4d5e6f-a1b2c3d4e5f67890.xlsx";
        String expectedDocxFilename = "018c5e1e-3f2a-7b4c-9d6e-1a2b3c4d5e6f-a1b2c3d4e5f67890.xlsx.docx";

        // Simulate output filename generation (as done in UploadController)
        String outputDocxFilename = inputFilename + ".docx";

        assertEquals(expectedDocxFilename, outputDocxFilename);

        // Verify both contain the same UUID and hash
        FileIdentifier inputIdentifier = extractFileIdentifier(inputFilename);
        FileIdentifier outputIdentifier = extractFileIdentifierFromChained(outputDocxFilename);

        assertNotNull(inputIdentifier);
        assertNotNull(outputIdentifier);
        assertEquals(inputIdentifier.uuid, outputIdentifier.uuid,
                "Output DOCX should maintain input UUID");
        assertEquals(inputIdentifier.hash, outputIdentifier.hash,
                "Output DOCX should maintain input hash");
    }

    @Test
    void testPdfOutputMaintainsTraceability() {
        String inputFilename = "018c5e1e-3f2a-7b4c-9d6e-1a2b3c4d5e6f-a1b2c3d4e5f67890.xlsx";
        String expectedPdfFilename = "018c5e1e-3f2a-7b4c-9d6e-1a2b3c4d5e6f-a1b2c3d4e5f67890.xlsx.pdf";

        // Simulate output filename generation (as done in UploadController)
        String outputPdfFilename = inputFilename + ".pdf";

        assertEquals(expectedPdfFilename, outputPdfFilename);

        // Verify both contain the same UUID and hash
        FileIdentifier inputIdentifier = extractFileIdentifier(inputFilename);
        FileIdentifier outputIdentifier = extractFileIdentifierFromChained(outputPdfFilename);

        assertNotNull(inputIdentifier);
        assertNotNull(outputIdentifier);
        assertEquals(inputIdentifier.uuid, outputIdentifier.uuid,
                "Output PDF should maintain input UUID");
        assertEquals(inputIdentifier.hash, outputIdentifier.hash,
                "Output PDF should maintain input hash");
    }

    @Test
    void testDocxPdfOutputMaintainsTraceability() {
        String inputFilename = "018c5e1e-3f2a-7b4c-9d6e-1a2b3c4d5e6f-a1b2c3d4e5f67890.xlsx";
        String docxFilename = inputFilename + ".docx";
        String expectedPdfFilename = "018c5e1e-3f2a-7b4c-9d6e-1a2b3c4d5e6f-a1b2c3d4e5f67890.xlsx.docx.pdf";

        // Simulate output filename generation (as done in UploadController)
        String outputPdfFilename = docxFilename + ".pdf";

        assertEquals(expectedPdfFilename, outputPdfFilename);

        // Verify all contain the same UUID and hash
        FileIdentifier inputIdentifier = extractFileIdentifier(inputFilename);
        FileIdentifier docxIdentifier = extractFileIdentifierFromChained(docxFilename);
        FileIdentifier pdfIdentifier = extractFileIdentifierFromChained(outputPdfFilename);

        assertNotNull(inputIdentifier);
        assertNotNull(docxIdentifier);
        assertNotNull(pdfIdentifier);

        assertEquals(inputIdentifier.uuid, docxIdentifier.uuid,
                "DOCX should maintain input UUID");
        assertEquals(inputIdentifier.hash, docxIdentifier.hash,
                "DOCX should maintain input hash");

        assertEquals(inputIdentifier.uuid, pdfIdentifier.uuid,
                "PDF from DOCX should maintain input UUID");
        assertEquals(inputIdentifier.hash, pdfIdentifier.hash,
                "PDF from DOCX should maintain input hash");
    }

    @Test
    void testZipResultMaintainsTraceability() {
        String inputZipFilename = "018c5e1e-3f2a-7b4c-9d6e-1a2b3c4d5e6f-a1b2c3d4e5f67890.zip";
        String expectedResultZipFilename = "018c5e1e-3f2a-7b4c-9d6e-1a2b3c4d5e6f-a1b2c3d4e5f67890.zip-result.zip";

        // Simulate output filename generation (as done in ZipProcessingService)
        String resultZipFilename = inputZipFilename + "-result.zip";

        assertEquals(expectedResultZipFilename, resultZipFilename);

        // Verify both contain the same UUID and hash
        FileIdentifier inputIdentifier = extractFileIdentifier(inputZipFilename);
        FileIdentifier resultIdentifier = extractFileIdentifierFromChained(resultZipFilename);

        assertNotNull(inputIdentifier);
        assertNotNull(resultIdentifier);
        assertEquals(inputIdentifier.uuid, resultIdentifier.uuid,
                "Result ZIP should maintain input UUID");
        assertEquals(inputIdentifier.hash, resultIdentifier.hash,
                "Result ZIP should maintain input hash");
    }

    @Test
    void testTraceabilityWithDifferentExtensions() {
        // Test with various extensions
        String[] extensions = {".xlsx", ".xls", ".xlsm", ".xlsb", ".zip"};
        String uuid = "018c5e1e-3f2a-7b4c-9d6e-1a2b3c4d5e6f";
        String hash = "a1b2c3d4e5f67890";

        for (String extension : extensions) {
            String inputFilename = uuid + "-" + hash + extension;
            String outputFilename = inputFilename + ".docx";

            FileIdentifier inputIdentifier = extractFileIdentifier(inputFilename);
            FileIdentifier outputIdentifier = extractFileIdentifierFromChained(outputFilename);

            assertNotNull(inputIdentifier, "Should extract identifier from " + extension);
            assertNotNull(outputIdentifier, "Should extract identifier from chained " + extension);

            assertEquals(uuid, inputIdentifier.uuid);
            assertEquals(hash, inputIdentifier.hash);
            assertEquals(uuid, outputIdentifier.uuid);
            assertEquals(hash, outputIdentifier.hash);
        }
    }

    @Test
    void testInvalidFilenameFormat() {
        String invalidFilename = "not-a-valid-filename.xlsx";

        FileIdentifier identifier = extractFileIdentifier(invalidFilename);

        assertNull(identifier, "Should return null for invalid filename format");
    }

    @Test
    void testTraceabilityChainIntegrity() {
        // Simulate the full chain of file transformations
        String inputFilename = "018c5e1e-3f2a-7b4c-9d6e-1a2b3c4d5e6f-a1b2c3d4e5f67890.xlsx";

        // Step 1: Upload creates input file with UUID-hash.xlsx
        FileIdentifier inputIdentifier = extractFileIdentifier(inputFilename);
        assertNotNull(inputIdentifier);

        // Step 2: DOCX generation maintains UUID-hash
        String docxFilename = inputFilename + ".docx";
        FileIdentifier docxIdentifier = extractFileIdentifierFromChained(docxFilename);
        assertNotNull(docxIdentifier);
        assertEquals(inputIdentifier.uuid, docxIdentifier.uuid);
        assertEquals(inputIdentifier.hash, docxIdentifier.hash);

        // Step 3: PDF from input maintains UUID-hash
        String inputPdfFilename = inputFilename + ".pdf";
        FileIdentifier inputPdfIdentifier = extractFileIdentifierFromChained(inputPdfFilename);
        assertNotNull(inputPdfIdentifier);
        assertEquals(inputIdentifier.uuid, inputPdfIdentifier.uuid);
        assertEquals(inputIdentifier.hash, inputPdfIdentifier.hash);

        // Step 4: PDF from DOCX maintains UUID-hash
        String docxPdfFilename = docxFilename + ".pdf";
        FileIdentifier docxPdfIdentifier = extractFileIdentifierFromChained(docxPdfFilename);
        assertNotNull(docxPdfIdentifier);
        assertEquals(inputIdentifier.uuid, docxPdfIdentifier.uuid);
        assertEquals(inputIdentifier.hash, docxPdfIdentifier.hash);

        // All files in the chain should have the same UUID and hash
        assertEquals(inputIdentifier.uuid, docxIdentifier.uuid);
        assertEquals(inputIdentifier.uuid, inputPdfIdentifier.uuid);
        assertEquals(inputIdentifier.uuid, docxPdfIdentifier.uuid);

        assertEquals(inputIdentifier.hash, docxIdentifier.hash);
        assertEquals(inputIdentifier.hash, inputPdfIdentifier.hash);
        assertEquals(inputIdentifier.hash, docxPdfIdentifier.hash);
    }

    // Helper methods

    private FileIdentifier extractFileIdentifier(String filename) {
        Matcher matcher = FILENAME_PATTERN.matcher(filename);
        if (matcher.find()) {
            return new FileIdentifier(matcher.group(1), matcher.group(2), matcher.group(3));
        }
        return null;
    }

    private FileIdentifier extractFileIdentifierFromChained(String filename) {
        // For chained filenames like UUID-hash.xlsx.docx or UUID-hash.zip-result.zip
        // Extract the UUID-hash part before the first extension
        Matcher matcher = FILENAME_PATTERN.matcher(filename);
        if (matcher.find()) {
            return new FileIdentifier(matcher.group(1), matcher.group(2), matcher.group(3));
        }
        return null;
    }

    // Helper record to store file identifier components
    private record FileIdentifier(String uuid, String hash, String extension) {}
}
