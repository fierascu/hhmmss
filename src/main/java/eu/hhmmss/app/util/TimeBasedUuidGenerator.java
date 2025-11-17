package eu.hhmmss.app.util;

import java.util.UUID;

/**
 * Generates secure random UUIDs (UUID v4) for filename generation.
 *
 * Security Note:
 * This class was changed from UUID v7 (time-based) to UUID v4 (random) to address
 * security concern CWE-330 (Use of Insufficiently Random Values).
 *
 * UUID v4 provides:
 * - Cryptographically secure random values (122 bits of randomness)
 * - No predictable timestamp component
 * - Protection against filename enumeration attacks
 * - Standard RFC 4122 compliance
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc4122#section-4.4">RFC 4122 Section 4.4</a>
 */
public class TimeBasedUuidGenerator {

    /**
     * Generates a cryptographically secure random UUID v4.
     *
     * UUID v4 uses SecureRandom internally to generate 122 random bits,
     * providing strong protection against prediction and enumeration attacks.
     *
     * @return A new random UUID v4
     */
    public static UUID generate() {
        // UUID.randomUUID() generates a version 4 (random) UUID using SecureRandom
        // This provides cryptographically strong random values, preventing enumeration
        return UUID.randomUUID();
    }
}
