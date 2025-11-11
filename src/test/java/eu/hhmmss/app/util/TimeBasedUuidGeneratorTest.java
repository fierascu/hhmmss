package eu.hhmmss.app.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TimeBasedUuidGenerator utility class.
 */
class TimeBasedUuidGeneratorTest {

    @Test
    void testGenerateCreatesValidUuidV7() {
        UUID uuid = TimeBasedUuidGenerator.generate();

        assertNotNull(uuid);
        assertEquals(7, uuid.version(), "UUID should be version 7");
        assertEquals(2, uuid.variant(), "UUID should have RFC 4122 variant");
    }

    @Test
    void testGenerateCreatesUniqueUuids() {
        Set<UUID> uuids = new HashSet<>();
        int count = 1000;

        for (int i = 0; i < count; i++) {
            UUID uuid = TimeBasedUuidGenerator.generate();
            assertTrue(uuids.add(uuid), "UUID should be unique");
        }

        assertEquals(count, uuids.size(), "All generated UUIDs should be unique");
    }

    @Test
    void testGenerateAtCreatesUuidWithSpecificTimestamp() {
        Instant testInstant = Instant.parse("2025-01-15T10:30:00Z");
        UUID uuid = TimeBasedUuidGenerator.generateAt(testInstant);

        assertNotNull(uuid);
        assertEquals(7, uuid.version(), "UUID should be version 7");

        long extractedTimestamp = TimeBasedUuidGenerator.extractTimestamp(uuid);
        assertEquals(testInstant.toEpochMilli(), extractedTimestamp,
                "Extracted timestamp should match the original timestamp");
    }

    @Test
    void testExtractTimestampFromCurrentTime() {
        Instant before = Instant.now();
        UUID uuid = TimeBasedUuidGenerator.generate();
        Instant after = Instant.now();

        long extractedTimestamp = TimeBasedUuidGenerator.extractTimestamp(uuid);
        Instant extractedInstant = Instant.ofEpochMilli(extractedTimestamp);

        // Allow for small timing differences (within 10ms buffer)
        assertTrue(
                (extractedInstant.isAfter(before.minusMillis(1)) || extractedInstant.equals(before)) &&
                (extractedInstant.isBefore(after.plusMillis(10)) || extractedInstant.equals(after)),
                "Extracted timestamp should be between before and after timestamps (with buffer)"
        );
    }

    @Test
    void testExtractTimestampThrowsExceptionForNonV7Uuid() {
        UUID randomUuid = UUID.randomUUID(); // This creates a version 4 UUID

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TimeBasedUuidGenerator.extractTimestamp(randomUuid),
                "Should throw exception for non-v7 UUID"
        );

        assertTrue(exception.getMessage().contains("not version 7"));
    }

    @Test
    void testIsVersion7ReturnsTrueForV7Uuid() {
        UUID uuid = TimeBasedUuidGenerator.generate();
        assertTrue(TimeBasedUuidGenerator.isVersion7(uuid),
                "Should return true for version 7 UUID");
    }

    @Test
    void testIsVersion7ReturnsFalseForNonV7Uuid() {
        UUID randomUuid = UUID.randomUUID(); // Version 4
        assertFalse(TimeBasedUuidGenerator.isVersion7(randomUuid),
                "Should return false for non-version 7 UUID");
    }

    @Test
    void testLexicographicalSortingByTimestamp() {
        Instant instant1 = Instant.parse("2025-01-15T10:00:00Z");
        Instant instant2 = Instant.parse("2025-01-15T11:00:00Z");
        Instant instant3 = Instant.parse("2025-01-15T12:00:00Z");

        UUID uuid1 = TimeBasedUuidGenerator.generateAt(instant1);
        UUID uuid2 = TimeBasedUuidGenerator.generateAt(instant2);
        UUID uuid3 = TimeBasedUuidGenerator.generateAt(instant3);

        String str1 = uuid1.toString();
        String str2 = uuid2.toString();
        String str3 = uuid3.toString();

        assertTrue(str1.compareTo(str2) < 0,
                "UUID from earlier time should sort before UUID from later time");
        assertTrue(str2.compareTo(str3) < 0,
                "UUID from earlier time should sort before UUID from later time");
        assertTrue(str1.compareTo(str3) < 0,
                "UUID from earliest time should sort before UUID from latest time");
    }

    @Test
    void testTimestampAccuracy() {
        long expectedMillis = 1705315800000L; // 2024-01-15T10:30:00Z
        Instant testInstant = Instant.ofEpochMilli(expectedMillis);

        UUID uuid = TimeBasedUuidGenerator.generateAt(testInstant);
        long extractedMillis = TimeBasedUuidGenerator.extractTimestamp(uuid);

        assertEquals(expectedMillis, extractedMillis,
                "Extracted timestamp should exactly match the input timestamp");
    }

    @Test
    void testMultipleUuidsGeneratedQuicklyHaveDifferentRandomComponents() {
        Instant sameInstant = Instant.now();
        UUID uuid1 = TimeBasedUuidGenerator.generateAt(sameInstant);
        UUID uuid2 = TimeBasedUuidGenerator.generateAt(sameInstant);

        // Even with the same timestamp, the random components should make them different
        assertNotEquals(uuid1, uuid2,
                "UUIDs generated at the same instant should still be unique due to random components");

        // But they should have the same timestamp
        assertEquals(
                TimeBasedUuidGenerator.extractTimestamp(uuid1),
                TimeBasedUuidGenerator.extractTimestamp(uuid2),
                "UUIDs generated at the same instant should have the same timestamp"
        );
    }

    @Test
    void testGenerateAtWithFutureDate() {
        Instant futureInstant = Instant.now().plus(Duration.ofDays(365));
        UUID uuid = TimeBasedUuidGenerator.generateAt(futureInstant);

        assertNotNull(uuid);
        assertEquals(7, uuid.version());

        long extractedTimestamp = TimeBasedUuidGenerator.extractTimestamp(uuid);
        assertEquals(futureInstant.toEpochMilli(), extractedTimestamp);
    }

    @Test
    void testGenerateAtWithPastDate() {
        Instant pastInstant = Instant.now().minus(Duration.ofDays(365));
        UUID uuid = TimeBasedUuidGenerator.generateAt(pastInstant);

        assertNotNull(uuid);
        assertEquals(7, uuid.version());

        long extractedTimestamp = TimeBasedUuidGenerator.extractTimestamp(uuid);
        assertEquals(pastInstant.toEpochMilli(), extractedTimestamp);
    }
}
