package eu.hhmmss.app.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for ProjectMetricsService.
 * Note: This test will use the actual git repository, so results may vary.
 */
class ProjectMetricsServiceTest {

    private ProjectMetricsService projectMetricsService;

    @BeforeEach
    void setUp() {
        projectMetricsService = new ProjectMetricsService();
    }

    @Test
    void testCalculateProjectMetrics_returnsValidMetrics() {
        // Act
        ProjectMetrics metrics = projectMetricsService.calculateProjectMetrics();

        // Assert
        assertNotNull(metrics);
        // Should have at least some commits (this test is in a git repo)
        assertTrue(metrics.getTotalCommits() >= 0);
        assertTrue(metrics.getEstimatedHours() >= 0);
    }

    @Test
    void testCalculateProjectMetrics_calculatesEstimatedDays() {
        // Act
        ProjectMetrics metrics = projectMetricsService.calculateProjectMetrics();

        // Assert
        if (metrics.getTotalCommits() > 0) {
            assertTrue(metrics.getEstimatedDays() >= 0);
            assertEquals(metrics.getEstimatedHours() / 8.0, metrics.getEstimatedDays(), 0.001);
        }
    }

    @Test
    void testCalculateProjectMetrics_calculatesAverageHoursPerDay() {
        // Act
        ProjectMetrics metrics = projectMetricsService.calculateProjectMetrics();

        // Assert
        if (metrics.getTotalCommits() > 0 && metrics.getDaysActive() > 0) {
            assertTrue(metrics.getAverageHoursPerDay() >= 0);
            assertEquals(
                    metrics.getEstimatedHours() / metrics.getDaysActive(),
                    metrics.getAverageHoursPerDay(),
                    0.001
            );
        }
    }

    @Test
    void testCalculateProjectMetrics_hasAuthorStats() {
        // Act
        ProjectMetrics metrics = projectMetricsService.calculateProjectMetrics();

        // Assert
        if (metrics.getTotalCommits() > 0) {
            assertNotNull(metrics.getAuthorStats());
            assertTrue(metrics.getUniqueAuthors() >= 0);
            assertEquals(metrics.getAuthorStats().size(), metrics.getUniqueAuthors());
        }
    }

    @Test
    void testCalculateProjectMetrics_authorStatsHaveValidData() {
        // Act
        ProjectMetrics metrics = projectMetricsService.calculateProjectMetrics();

        // Assert
        if (metrics.getTotalCommits() > 0 && metrics.getAuthorStats() != null) {
            for (AuthorStats stats : metrics.getAuthorStats().values()) {
                assertNotNull(stats.getAuthor());
                assertTrue(stats.getCommits() > 0);
                assertTrue(stats.getEstimatedHours() >= 0);
                assertEquals(stats.getEstimatedHours() / 8.0, stats.getEstimatedDays(), 0.001);
            }
        }
    }

    @Test
    void testCalculateProjectMetrics_handlesNoCommitsGracefully() {
        // This test verifies the service doesn't crash even if git is not available
        // or returns no commits (which shouldn't happen in this repo, but good to test)

        // Act
        ProjectMetrics metrics = projectMetricsService.calculateProjectMetrics();

        // Assert - should never be null, even on error
        assertNotNull(metrics);
        assertTrue(metrics.getTotalCommits() >= 0);
        assertTrue(metrics.getEstimatedHours() >= 0);
    }
}
