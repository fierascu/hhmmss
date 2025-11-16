package eu.hhmmss.app.metrics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Statistics for a specific author/contributor.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorStats {
    private String author;
    private int commits;
    private double estimatedHours;

    /**
     * Get estimated days worked (hours / 8).
     */
    public double getEstimatedDays() {
        return estimatedHours / 8.0;
    }
}
