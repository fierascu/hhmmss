package eu.hhmmss.app.metrics;

import eu.hhmmss.app.converter.DayData;
import eu.hhmmss.app.converter.HhmmssDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TimeSavingsServiceTest {

    private TimeSavingsService timeSavingsService;

    @BeforeEach
    void setUp() {
        timeSavingsService = new TimeSavingsService();
    }

    @Test
    void testCalculateTimeSavings_withValidTimesheet() {
        // Arrange
        HhmmssDto timesheet = new HhmmssDto();
        Map<Integer, DayData> tasks = new HashMap<>();

        // Add 20 working days
        for (int i = 1; i <= 20; i++) {
            tasks.put(i, DayData.builder()
                    .task("Work task")
                    .hoursFlexibilityPeriod(8.0)
                    .build());
        }
        timesheet.setTasks(tasks);

        // Act
        TimeSavings savings = timeSavingsService.calculateTimeSavings(timesheet);

        // Assert
        assertNotNull(savings);
        assertEquals(20, savings.getDaysProcessed());
        assertEquals(160.0, savings.getTotalHoursInTimesheet());
        assertTrue(savings.getTimeSavedMinutes() > 0);
        assertTrue(savings.getPercentImprovement() > 90); // Should be very high improvement
    }

    @Test
    void testCalculateTimeSavings_withEmptyTimesheet() {
        // Arrange
        HhmmssDto timesheet = new HhmmssDto();
        timesheet.setTasks(new HashMap<>());

        // Act
        TimeSavings savings = timeSavingsService.calculateTimeSavings(timesheet);

        // Assert
        assertNotNull(savings);
        assertEquals(0, savings.getDaysProcessed());
        assertEquals(0.0, savings.getTotalHoursInTimesheet());
        assertTrue(savings.getTimeSavedMinutes() > 0); // Still saves base creation time
    }

    @Test
    void testCalculateTimeSavings_withNullTimesheet() {
        // Act
        TimeSavings savings = timeSavingsService.calculateTimeSavings(null);

        // Assert
        assertNotNull(savings);
        assertEquals(0.0, savings.getTimeSavedMinutes());
    }

    @Test
    void testCalculateTimeSavings_withPartialEntries() {
        // Arrange
        HhmmssDto timesheet = new HhmmssDto();
        Map<Integer, DayData> tasks = new HashMap<>();

        // Some days with tasks, some without
        tasks.put(1, DayData.builder().task("Work").hoursFlexibilityPeriod(8.0).build());
        tasks.put(2, DayData.builder().task("").hoursFlexibilityPeriod(0.0).build()); // Empty entry
        tasks.put(3, DayData.builder().task("Work").hoursFlexibilityPeriod(8.0).build());
        tasks.put(4, DayData.builder().task(null).build()); // Null entry

        timesheet.setTasks(tasks);

        // Act
        TimeSavings savings = timeSavingsService.calculateTimeSavings(timesheet);

        // Assert
        assertNotNull(savings);
        assertEquals(2, savings.getDaysProcessed()); // Only days 1 and 3 have valid entries
        assertEquals(16.0, savings.getTotalHoursInTimesheet());
    }

    @Test
    void testCalculateCumulativeSavings_singleConversion() {
        // Act
        TimeSavings savings = timeSavingsService.calculateCumulativeSavings(1);

        // Assert
        assertNotNull(savings);
        assertEquals(1, savings.getNumberOfConversions());
        assertEquals(20, savings.getDaysProcessed()); // Average 20 days per timesheet
        assertTrue(savings.getTimeSavedMinutes() > 0);
        assertTrue(savings.getPercentImprovement() > 90);
    }

    @Test
    void testCalculateCumulativeSavings_multipleConversions() {
        // Act
        TimeSavings savings = timeSavingsService.calculateCumulativeSavings(10);

        // Assert
        assertNotNull(savings);
        assertEquals(10, savings.getNumberOfConversions());
        assertEquals(200, savings.getDaysProcessed()); // 10 timesheets × 20 days
        assertTrue(savings.getTimeSavedMinutes() > 0);
        assertTrue(savings.getTimeSavedHours() > 1); // Should save multiple hours
    }

    @Test
    void testTimeSavings_formattedOutput() {
        // Test that formatted time string is generated correctly
        TimeSavings savings = timeSavingsService.calculateCumulativeSavings(1);

        assertNotNull(savings.getTimeSavedFormatted());
        assertTrue(savings.getTimeSavedFormatted().length() > 0);
    }

    @Test
    void testTimeSavings_conversions() {
        // Arrange
        HhmmssDto timesheet = new HhmmssDto();
        Map<Integer, DayData> tasks = new HashMap<>();
        tasks.put(1, DayData.builder().task("Work").hoursFlexibilityPeriod(8.0).build());
        timesheet.setTasks(tasks);

        // Act
        TimeSavings savings = timeSavingsService.calculateTimeSavings(timesheet);

        // Assert - test conversion methods
        assertTrue(savings.getTimeSavedHours() > 0);
        assertTrue(savings.getManualTimeHours() > 0);
        assertTrue(savings.getAutomatedTimeHours() >= 0);
        assertEquals(savings.getTimeSavedMinutes() / 60.0, savings.getTimeSavedHours(), 0.001);
    }
}
