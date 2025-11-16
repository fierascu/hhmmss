package eu.hhmmss.app.metrics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contains calculated time savings metrics for timesheet conversions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeSavings {
    private int numberOfConversions;
    private int daysProcessed;
    private double totalHoursInTimesheet;
    private double manualTimeMinutes;
    private double automatedTimeMinutes;
    private double timeSavedMinutes;
    private double percentImprovement;

    /**
     * Get time saved in hours.
     */
    public double getTimeSavedHours() {
        return timeSavedMinutes / 60.0;
    }

    /**
     * Get manual time in hours.
     */
    public double getManualTimeHours() {
        return manualTimeMinutes / 60.0;
    }

    /**
     * Get automated time in hours.
     */
    public double getAutomatedTimeHours() {
        return automatedTimeMinutes / 60.0;
    }

    /**
     * Get time saved formatted as human-readable string.
     */
    public String getTimeSavedFormatted() {
        if (timeSavedMinutes < 60) {
            return String.format("%.1f minutes", timeSavedMinutes);
        } else if (timeSavedMinutes < 1440) {
            return String.format("%.2f hours", getTimeSavedHours());
        } else {
            return String.format("%.2f days", getTimeSavedHours() / 24.0);
        }
    }
}
