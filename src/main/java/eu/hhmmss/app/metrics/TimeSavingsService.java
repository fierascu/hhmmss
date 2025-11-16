package eu.hhmmss.app.metrics;

import eu.hhmmss.app.converter.HhmmssDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service to calculate time savings achieved by using the automated timesheet converter
 * versus manual document creation.
 */
@Slf4j
@Service
public class TimeSavingsService {

    // Estimated time to manually create a timesheet document (in minutes)
    private static final double MANUAL_CREATION_TIME_MINUTES = 15.0;

    // Estimated time to fill in each day's entry manually (in minutes)
    private static final double MANUAL_ENTRY_TIME_MINUTES = 0.5;

    // Average time for automated conversion (in seconds)
    private static final double AUTOMATED_CONVERSION_TIME_SECONDS = 2.0;

    /**
     * Calculate time savings for a specific timesheet conversion.
     *
     * @param timesheetData the parsed timesheet data
     * @return TimeSavings object with detailed calculations
     */
    public TimeSavings calculateTimeSavings(HhmmssDto timesheetData) {
        if (timesheetData == null) {
            log.warn("Timesheet data is null, cannot calculate savings");
            return TimeSavings.builder()
                    .timeSavedMinutes(0.0)
                    .build();
        }

        // Count number of days with entries
        int daysWithEntries = (int) timesheetData.getTasks().entrySet().stream()
                .filter(entry -> {
                    Pair<String, Double> taskData = entry.getValue();
                    return taskData != null &&
                            (taskData.getLeft() != null && !taskData.getLeft().trim().isEmpty() ||
                                    taskData.getRight() != null && taskData.getRight() > 0);
                })
                .count();

        // Calculate total hours worked
        double totalHours = timesheetData.getTasks().values().stream()
                .filter(pair -> pair != null && pair.getRight() != null)
                .mapToDouble(Pair::getRight)
                .sum();

        // Calculate manual time required
        double manualTimeMinutes = MANUAL_CREATION_TIME_MINUTES +
                (daysWithEntries * MANUAL_ENTRY_TIME_MINUTES);

        // Calculate automated time
        double automatedTimeMinutes = AUTOMATED_CONVERSION_TIME_SECONDS / 60.0;

        // Calculate savings
        double timeSavedMinutes = manualTimeMinutes - automatedTimeMinutes;

        // Calculate percentage improvement
        double percentImprovement = manualTimeMinutes > 0 ?
                (timeSavedMinutes / manualTimeMinutes) * 100.0 : 0.0;

        return TimeSavings.builder()
                .daysProcessed(daysWithEntries)
                .totalHoursInTimesheet(totalHours)
                .manualTimeMinutes(manualTimeMinutes)
                .automatedTimeMinutes(automatedTimeMinutes)
                .timeSavedMinutes(timeSavedMinutes)
                .percentImprovement(percentImprovement)
                .build();
    }

    /**
     * Calculate cumulative time savings for multiple conversions.
     *
     * @param numberOfConversions number of timesheets converted
     * @return cumulative TimeSavings
     */
    public TimeSavings calculateCumulativeSavings(int numberOfConversions) {
        // Assume average timesheet has 20 working days
        int avgDaysPerTimesheet = 20;

        double manualTimeMinutes = numberOfConversions *
                (MANUAL_CREATION_TIME_MINUTES + (avgDaysPerTimesheet * MANUAL_ENTRY_TIME_MINUTES));

        double automatedTimeMinutes = numberOfConversions *
                (AUTOMATED_CONVERSION_TIME_SECONDS / 60.0);

        double timeSavedMinutes = manualTimeMinutes - automatedTimeMinutes;

        double percentImprovement = manualTimeMinutes > 0 ?
                (timeSavedMinutes / manualTimeMinutes) * 100.0 : 0.0;

        return TimeSavings.builder()
                .numberOfConversions(numberOfConversions)
                .daysProcessed(numberOfConversions * avgDaysPerTimesheet)
                .manualTimeMinutes(manualTimeMinutes)
                .automatedTimeMinutes(automatedTimeMinutes)
                .timeSavedMinutes(timeSavedMinutes)
                .percentImprovement(percentImprovement)
                .build();
    }
}
