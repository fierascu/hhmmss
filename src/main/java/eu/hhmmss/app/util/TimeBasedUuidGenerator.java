package eu.hhmmss.app.util;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * Generates time-based UUIDs (UUID v7) as per RFC 9562.
 * UUID v7 provides:
 * - Time-ordered values (lexicographically sortable)
 * - 48-bit timestamp in milliseconds
 * - Better database index performance
 * - File creation time traceability
 */
public class TimeBasedUuidGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generates a UUID v7 based on current timestamp.
     *
     * Format (128 bits):
     * - 48 bits: Unix timestamp in milliseconds
     * - 4 bits: Version (0111 for v7)
     * - 12 bits: Random data
     * - 2 bits: Variant (10)
     * - 62 bits: Random data
     *
     * @return A new time-based UUID v7
     */
    public static UUID generate() {
        return generateAt(Instant.now());
    }

    /**
     * Generates a UUID v7 for a specific timestamp.
     * Useful for testing and specific time requirements.
     *
     * @param instant The timestamp to encode in the UUID
     * @return A new time-based UUID v7 for the given timestamp
     */
    public static UUID generateAt(Instant instant) {
        long timestamp = instant.toEpochMilli();

        // Generate random bytes for the remaining bits
        byte[] randomBytes = new byte[10];
        RANDOM.nextBytes(randomBytes);

        // Build the UUID following v7 specification
        long mostSigBits = buildMostSignificantBits(timestamp, randomBytes);
        long leastSigBits = buildLeastSignificantBits(randomBytes);

        return new UUID(mostSigBits, leastSigBits);
    }

    /**
     * Extracts the timestamp from a UUID v7.
     *
     * @param uuid The UUID v7 to extract timestamp from
     * @return The timestamp in milliseconds since epoch
     * @throws IllegalArgumentException if the UUID is not version 7
     */
    public static long extractTimestamp(UUID uuid) {
        if (uuid.version() != 7) {
            throw new IllegalArgumentException("UUID is not version 7: " + uuid);
        }

        // Extract the 48-bit timestamp from most significant bits
        return uuid.getMostSignificantBits() >>> 16;
    }

    /**
     * Checks if a UUID is version 7 (time-based).
     *
     * @param uuid The UUID to check
     * @return true if the UUID is version 7, false otherwise
     */
    public static boolean isVersion7(UUID uuid) {
        return uuid.version() == 7;
    }

    /**
     * Builds the most significant 64 bits of the UUID.
     *
     * Layout:
     * - 48 bits: timestamp
     * - 4 bits: version (0111 = 7)
     * - 12 bits: random data
     */
    private static long buildMostSignificantBits(long timestamp, byte[] randomBytes) {
        long mostSigBits = 0L;

        // First 48 bits: timestamp in milliseconds
        mostSigBits |= (timestamp & 0xFFFFFFFFFFFFL) << 16;

        // Next 4 bits: version (7)
        mostSigBits |= 0x7000L;

        // Last 12 bits: random data
        mostSigBits |= (randomBytes[0] & 0x0FL) << 8;
        mostSigBits |= (randomBytes[1] & 0xFFL);

        return mostSigBits;
    }

    /**
     * Builds the least significant 64 bits of the UUID.
     *
     * Layout:
     * - 2 bits: variant (10)
     * - 62 bits: random data
     */
    private static long buildLeastSignificantBits(byte[] randomBytes) {
        ByteBuffer buffer = ByteBuffer.wrap(randomBytes, 2, 8);
        long leastSigBits = buffer.getLong();

        // Set variant bits to 10 (RFC 4122 variant)
        leastSigBits &= 0x3FFFFFFFFFFFFFFFL;
        leastSigBits |= 0x8000000000000000L;

        return leastSigBits;
    }
}
