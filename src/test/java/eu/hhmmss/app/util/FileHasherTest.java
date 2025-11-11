package eu.hhmmss.app.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FileHasher utility class.
 */
class FileHasherTest {

    @Test
    void testComputeHashProducesValidSha256() throws IOException {
        String testData = "Hello, World!";
        InputStream inputStream = new ByteArrayInputStream(testData.getBytes(StandardCharsets.UTF_8));

        String hash = FileHasher.computeHash(inputStream);

        assertNotNull(hash);
        assertEquals(64, hash.length(), "SHA-256 hash should be 64 characters long");
        assertTrue(hash.matches("[0-9a-f]+"), "Hash should only contain lowercase hex characters");
    }

    @Test
    void testComputeHashIsConsistent() throws IOException {
        String testData = "Consistent data for testing";
        byte[] dataBytes = testData.getBytes(StandardCharsets.UTF_8);

        InputStream inputStream1 = new ByteArrayInputStream(dataBytes);
        String hash1 = FileHasher.computeHash(inputStream1);

        InputStream inputStream2 = new ByteArrayInputStream(dataBytes);
        String hash2 = FileHasher.computeHash(inputStream2);

        assertEquals(hash1, hash2, "Same input should produce the same hash");
    }

    @Test
    void testComputeHashProducesDifferentHashesForDifferentInputs() throws IOException {
        String testData1 = "First test data";
        String testData2 = "Second test data";

        InputStream inputStream1 = new ByteArrayInputStream(testData1.getBytes(StandardCharsets.UTF_8));
        String hash1 = FileHasher.computeHash(inputStream1);

        InputStream inputStream2 = new ByteArrayInputStream(testData2.getBytes(StandardCharsets.UTF_8));
        String hash2 = FileHasher.computeHash(inputStream2);

        assertNotEquals(hash1, hash2, "Different inputs should produce different hashes");
    }

    @Test
    void testComputeHashForEmptyInput() throws IOException {
        InputStream inputStream = new ByteArrayInputStream(new byte[0]);
        String hash = FileHasher.computeHash(inputStream);

        assertNotNull(hash);
        assertEquals(64, hash.length());
        // SHA-256 of empty input
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash);
    }

    @Test
    void testComputeHashForKnownInput() throws IOException {
        // Known test vector: SHA-256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        String testData = "abc";
        InputStream inputStream = new ByteArrayInputStream(testData.getBytes(StandardCharsets.UTF_8));

        String hash = FileHasher.computeHash(inputStream);

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash,
                "Hash should match known SHA-256 test vector");
    }

    @Test
    void testComputeShortHashLength() throws IOException {
        String testData = "Test data for short hash";
        InputStream inputStream = new ByteArrayInputStream(testData.getBytes(StandardCharsets.UTF_8));

        String shortHash = FileHasher.computeShortHash(inputStream);

        assertNotNull(shortHash);
        assertEquals(16, shortHash.length(), "Short hash should be 16 characters long");
        assertTrue(shortHash.matches("[0-9a-f]+"), "Short hash should only contain lowercase hex characters");
    }

    @Test
    void testComputeShortHashIsConsistent() throws IOException {
        String testData = "Consistent data for short hash testing";
        byte[] dataBytes = testData.getBytes(StandardCharsets.UTF_8);

        InputStream inputStream1 = new ByteArrayInputStream(dataBytes);
        String shortHash1 = FileHasher.computeShortHash(inputStream1);

        InputStream inputStream2 = new ByteArrayInputStream(dataBytes);
        String shortHash2 = FileHasher.computeShortHash(inputStream2);

        assertEquals(shortHash1, shortHash2, "Same input should produce the same short hash");
    }

    @Test
    void testComputeShortHashIsPrefixOfFullHash() throws IOException {
        String testData = "Test data to compare full and short hash";
        byte[] dataBytes = testData.getBytes(StandardCharsets.UTF_8);

        InputStream inputStream1 = new ByteArrayInputStream(dataBytes);
        String fullHash = FileHasher.computeHash(inputStream1);

        InputStream inputStream2 = new ByteArrayInputStream(dataBytes);
        String shortHash = FileHasher.computeShortHash(inputStream2);

        assertTrue(fullHash.startsWith(shortHash),
                "Short hash should be the first 16 characters of the full hash");
    }

    @Test
    void testComputeHashForLargeInput() throws IOException {
        // Create a larger input (1MB)
        byte[] largeData = new byte[1024 * 1024];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }

        InputStream inputStream = new ByteArrayInputStream(largeData);
        String hash = FileHasher.computeHash(inputStream);

        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]+"));
    }

    @Test
    void testIsValidHashForFullHash() {
        String validHash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        assertTrue(FileHasher.isValidHash(validHash, 64),
                "Should return true for valid 64-character hash");
    }

    @Test
    void testIsValidHashForShortHash() {
        String validShortHash = "ba7816bf8f01cfea";
        assertTrue(FileHasher.isValidHash(validShortHash, 16),
                "Should return true for valid 16-character hash");
    }

    @Test
    void testIsValidHashReturnsFalseForInvalidLength() {
        String invalidHash = "ba7816bf";
        assertFalse(FileHasher.isValidHash(invalidHash, 64),
                "Should return false for hash with invalid length");
    }

    @Test
    void testIsValidHashReturnsFalseForInvalidCharacters() {
        String invalidHash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015aG"; // 'G' is invalid
        assertFalse(FileHasher.isValidHash(invalidHash, 64),
                "Should return false for hash with invalid characters");
    }

    @Test
    void testIsValidHashReturnsFalseForNull() {
        assertFalse(FileHasher.isValidHash(null, 64),
                "Should return false for null hash");
    }

    @Test
    void testIsValidHashReturnsFalseForUppercaseHex() {
        String uppercaseHash = "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD";
        assertFalse(FileHasher.isValidHash(uppercaseHash, 64),
                "Should return false for uppercase hex (expects lowercase)");
    }

    @Test
    void testComputeHashForBinaryData() throws IOException {
        // Test with binary data (not text)
        byte[] binaryData = new byte[256];
        for (int i = 0; i < 256; i++) {
            binaryData[i] = (byte) i;
        }

        InputStream inputStream = new ByteArrayInputStream(binaryData);
        String hash = FileHasher.computeHash(inputStream);

        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]+"));

        // Verify consistency
        InputStream inputStream2 = new ByteArrayInputStream(binaryData);
        String hash2 = FileHasher.computeHash(inputStream2);
        assertEquals(hash, hash2);
    }

    @Test
    void testComputeHashHandlesSpecialCharacters() throws IOException {
        String testData = "Special characters: !@#$%^&*()_+-=[]{}|;':\",./<>?`~\n\r\t";
        InputStream inputStream = new ByteArrayInputStream(testData.getBytes(StandardCharsets.UTF_8));

        String hash = FileHasher.computeHash(inputStream);

        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]+"));
    }
}
