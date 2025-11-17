package eu.hhmmss.app.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TimeBasedUuidGenerator utility class.
 *
 * Note: This class was updated to test UUID v4 (random) generation
 * instead of UUID v7 (time-based) for security reasons.
 */
class TimeBasedUuidGeneratorTest {

    @Test
    void testGenerateCreatesValidUuidV4() {
        UUID uuid = TimeBasedUuidGenerator.generate();

        assertNotNull(uuid);
        assertEquals(4, uuid.version(), "UUID should be version 4 (random)");
        assertEquals(2, uuid.variant(), "UUID should have RFC 4122 variant");
    }

    @Test
    void testGenerateCreatesUniqueUuids() {
        Set<UUID> uuids = new HashSet<>();
        int count = 10000;

        for (int i = 0; i < count; i++) {
            UUID uuid = TimeBasedUuidGenerator.generate();
            assertTrue(uuids.add(uuid), "UUID should be unique");
        }

        assertEquals(count, uuids.size(), "All generated UUIDs should be unique");
    }

    @Test
    void testGenerateCreatesNonPredictableUuids() {
        // Generate multiple UUIDs and verify they are not sequential
        UUID uuid1 = TimeBasedUuidGenerator.generate();
        UUID uuid2 = TimeBasedUuidGenerator.generate();
        UUID uuid3 = TimeBasedUuidGenerator.generate();

        // UUIDs should be different
        assertNotEquals(uuid1, uuid2);
        assertNotEquals(uuid2, uuid3);
        assertNotEquals(uuid1, uuid3);

        // String representations should not be sequential
        String str1 = uuid1.toString();
        String str2 = uuid2.toString();
        String str3 = uuid3.toString();

        // Random UUIDs should not have predictable ordering
        assertNotEquals(str1, str2);
        assertNotEquals(str2, str3);
    }

    @Test
    void testUuidsAreVersion4Random() {
        // Generate multiple UUIDs and verify they are all version 4
        for (int i = 0; i < 100; i++) {
            UUID uuid = TimeBasedUuidGenerator.generate();
            assertEquals(4, uuid.version(),
                    "All generated UUIDs should be version 4 (random)");
        }
    }

    @Test
    void testUuidsHaveProperRfc4122Variant() {
        // Generate multiple UUIDs and verify they have the correct variant
        for (int i = 0; i < 100; i++) {
            UUID uuid = TimeBasedUuidGenerator.generate();
            assertEquals(2, uuid.variant(),
                    "All generated UUIDs should have RFC 4122 variant (2)");
        }
    }

    @Test
    void testConcurrentGeneration() throws InterruptedException {
        // Test that concurrent UUID generation produces unique values
        Set<UUID> uuids = new HashSet<>();
        int threadCount = 10;
        int uuidsPerThread = 100;

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < uuidsPerThread; j++) {
                    synchronized (uuids) {
                        uuids.add(TimeBasedUuidGenerator.generate());
                    }
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(threadCount * uuidsPerThread, uuids.size(),
                "All concurrently generated UUIDs should be unique");
    }
}
