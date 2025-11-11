package eu.hhmmss.app.util;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for generating cryptographic hashes of files.
 * Uses SHA-256 algorithm for secure file identification and integrity verification.
 */
public class FileHasher {

    private static final String ALGORITHM = "SHA-256";
    private static final int BUFFER_SIZE = 8192;

    /**
     * Computes the SHA-256 hash of an input stream.
     *
     * @param inputStream The input stream to hash
     * @return Hexadecimal string representation of the hash (64 characters)
     * @throws IOException If an I/O error occurs while reading the stream
     * @throws RuntimeException If SHA-256 algorithm is not available (should never happen)
     */
    public static String computeHash(InputStream inputStream) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }

            byte[] hashBytes = digest.digest();
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 should always be available in Java 8+
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Computes a short hash (first 16 characters of SHA-256) for compact file naming.
     * Provides 64 bits of entropy, which is sufficient for file identification
     * while keeping filenames shorter.
     *
     * @param inputStream The input stream to hash
     * @return Hexadecimal string representation of the short hash (16 characters)
     * @throws IOException If an I/O error occurs while reading the stream
     */
    public static String computeShortHash(InputStream inputStream) throws IOException {
        String fullHash = computeHash(inputStream);
        return fullHash.substring(0, 16);
    }

    /**
     * Converts a byte array to a hexadecimal string.
     *
     * @param bytes The byte array to convert
     * @return Hexadecimal string representation
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Validates that a hash string is a valid hexadecimal string of the expected length.
     *
     * @param hash The hash string to validate
     * @param expectedLength The expected length (64 for full SHA-256, 16 for short hash)
     * @return true if the hash is valid, false otherwise
     */
    public static boolean isValidHash(String hash, int expectedLength) {
        if (hash == null || hash.length() != expectedLength) {
            return false;
        }
        return hash.matches("[0-9a-f]+");
    }
}
