package eu.hhmmss.app.util;

import org.junit.jupiter.api.Test;

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
    void testGenerateCreatesUniqueUuids() {
        Set<UUID> uuids = new HashSet<>();
        int count = 3;

        for (int i = 0; i < count; i++) {
            UUID uuid = TimeBasedUuidGenerator.generate();
            assertTrue(uuids.add(uuid), "UUID should be unique");
        }

        assertEquals(count, uuids.size(), "All generated UUIDs should be unique");
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

}
