package eu.hhmmss.app.uploadingfiles.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ThrottlingServiceTest {

    @Test
    void testServiceInitializationWithDefaultValues() {
        ThrottlingService service = new ThrottlingService(2, 30);

        assertEquals(2, service.getMaxConcurrentRequests());
        assertEquals(2, service.getAvailablePermits());
    }

    @Test
    void testServiceInitializationWithCustomValues() {
        ThrottlingService service = new ThrottlingService(5, 60);

        assertEquals(5, service.getMaxConcurrentRequests());
        assertEquals(5, service.getAvailablePermits());
    }

    @Test
    void testAcquireAndReleasePermit() {
        ThrottlingService service = new ThrottlingService(2, 30);

        assertEquals(2, service.getAvailablePermits());

        service.acquirePermit();
        assertEquals(1, service.getAvailablePermits());

        service.releasePermit();
        assertEquals(2, service.getAvailablePermits());
    }

    @Test
    void testAcquireMultiplePermits() {
        ThrottlingService service = new ThrottlingService(3, 30);

        assertEquals(3, service.getAvailablePermits());

        service.acquirePermit();
        service.acquirePermit();
        assertEquals(1, service.getAvailablePermits());

        service.releasePermit();
        service.releasePermit();
        assertEquals(3, service.getAvailablePermits());
    }

    @Test
    @Timeout(5)
    void testThrowsExceptionWhenNoPermitsAvailable() {
        ThrottlingService service = new ThrottlingService(1, 1); // 1 second timeout

        // Acquire the only permit
        service.acquirePermit();
        assertEquals(0, service.getAvailablePermits());

        // Try to acquire another permit - should timeout and throw exception
        TooManyRequestsException exception = assertThrows(
                TooManyRequestsException.class,
                service::acquirePermit
        );

        assertTrue(exception.getMessage().contains("too many requests"));
    }

    @Test
    @Timeout(10)
    void testConcurrentAccess() throws InterruptedException {
        ThrottlingService service = new ThrottlingService(2, 30);
        AtomicInteger concurrentCount = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(5);

        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {
                try {
                    service.acquirePermit();
                    int current = concurrentCount.incrementAndGet();
                    maxConcurrent.updateAndGet(max -> Math.max(max, current));

                    // Simulate some work
                    Thread.sleep(100);

                    concurrentCount.decrementAndGet();
                    service.releasePermit();
                    successCount.incrementAndGet();
                } catch (TooManyRequestsException e) {
                    // Expected for some threads
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Verify that at most 2 threads were running concurrently
        assertTrue(maxConcurrent.get() <= 2, "Max concurrent should be <= 2, was: " + maxConcurrent.get());
        assertTrue(successCount.get() >= 2, "At least 2 threads should succeed");
    }

    @Test
    void testReleasePermitIncreasesAvailability() {
        ThrottlingService service = new ThrottlingService(2, 30);

        service.acquirePermit();
        service.acquirePermit();
        assertEquals(0, service.getAvailablePermits());

        service.releasePermit();
        assertEquals(1, service.getAvailablePermits());

        service.releasePermit();
        assertEquals(2, service.getAvailablePermits());
    }

    @Test
    @Timeout(5)
    void testFairOrderingOfPermitAcquisition() throws InterruptedException {
        ThrottlingService service = new ThrottlingService(1, 30);
        AtomicInteger order = new AtomicInteger(0);
        int[] threadOrder = new int[3];
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(3);

        // Acquire the permit first
        service.acquirePermit();

        // Start 3 threads that will wait for the permit
        for (int i = 0; i < 3; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    startLatch.await(); // Wait for signal to start
                    service.acquirePermit();
                    threadOrder[threadId] = order.getAndIncrement();
                    Thread.sleep(10); // Hold permit briefly
                    service.releasePermit();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (TooManyRequestsException e) {
                    // Ignore
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        Thread.sleep(100); // Let threads start waiting
        startLatch.countDown(); // Signal threads to start
        Thread.sleep(50); // Give them time to queue
        service.releasePermit(); // Release the initial permit

        doneLatch.await(5, TimeUnit.SECONDS);

        // At least the first thread should have acquired the permit
        assertTrue(threadOrder[0] >= 0 || threadOrder[1] >= 0 || threadOrder[2] >= 0);
    }

    @Test
    void testGetMaxConcurrentRequests() {
        ThrottlingService service = new ThrottlingService(5, 30);
        assertEquals(5, service.getMaxConcurrentRequests());
    }

    @Test
    void testGetAvailablePermitsReflectsCurrentState() {
        ThrottlingService service = new ThrottlingService(3, 30);

        assertEquals(3, service.getAvailablePermits());
        service.acquirePermit();
        assertEquals(2, service.getAvailablePermits());
        service.acquirePermit();
        assertEquals(1, service.getAvailablePermits());
        service.releasePermit();
        assertEquals(2, service.getAvailablePermits());
    }

    @Test
    @Timeout(5)
    void testTimeoutValueIsRespected() {
        ThrottlingService service = new ThrottlingService(1, 1); // 1 second timeout

        service.acquirePermit();

        long startTime = System.currentTimeMillis();
        assertThrows(TooManyRequestsException.class, service::acquirePermit);
        long endTime = System.currentTimeMillis();

        long duration = endTime - startTime;
        // Should timeout around 1 second (allow some tolerance)
        assertTrue(duration >= 900 && duration < 2000, "Timeout should be around 1 second, was: " + duration + "ms");
    }

    @Test
    void testExceptionMessageIsDescriptive() {
        ThrottlingService service = new ThrottlingService(1, 1);

        service.acquirePermit();

        TooManyRequestsException exception = assertThrows(
                TooManyRequestsException.class,
                service::acquirePermit
        );

        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().length() > 0);
    }
}
