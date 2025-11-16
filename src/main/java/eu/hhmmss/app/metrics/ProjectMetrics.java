package eu.hhmmss.app.metrics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Contains calculated metrics about the project development history.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMetrics {
    private int totalCommits;
    private double estimatedHours;
    private LocalDateTime firstCommitDate;
    private LocalDateTime lastCommitDate;
    private long daysActive;
    private int uniqueAuthors;
    private Map<String, AuthorStats> authorStats;

    /**
     * Get estimated days worked (hours / 8).
     */
    public double getEstimatedDays() {
        return estimatedHours / 8.0;
    }

    /**
     * Get average hours per day (total hours / days active).
     */
    public double getAverageHoursPerDay() {
        return daysActive > 0 ? estimatedHours / daysActive : 0.0;
    }
}
