package eu.hhmmss.app.converter;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HolidayServiceTest {

    @Test
    void testIsWeekendSaturday() {
        // January 6, 2024 is a Saturday
        LocalDate saturday = LocalDate.of(2024, 1, 6);
        assertTrue(HolidayService.isWeekend(saturday));
    }

    @Test
    void testIsWeekendSunday() {
        // January 7, 2024 is a Sunday
        LocalDate sunday = LocalDate.of(2024, 1, 7);
        assertTrue(HolidayService.isWeekend(sunday));
    }

    @Test
    void testIsWeekendWeekday() {
        // January 8, 2024 is a Monday
        LocalDate monday = LocalDate.of(2024, 1, 8);
        assertFalse(HolidayService.isWeekend(monday));
    }

    @Test
    void testIsHolidayNewYear2024() {
        HolidayService service = new HolidayService();
        // January 1, 2024 is New Year's Day (configured in holidays.json)
        LocalDate newYear = LocalDate.of(2024, 1, 1);
        assertTrue(service.isHoliday(newYear));
    }

    @Test
    void testIsHolidayChristmas2024() {
        HolidayService service = new HolidayService();
        // December 25, 2024 is Christmas (configured in holidays.json)
        LocalDate christmas = LocalDate.of(2024, 12, 25);
        assertTrue(service.isHoliday(christmas));
    }

    @Test
    void testIsHolidayNonHoliday() {
        HolidayService service = new HolidayService();
        // January 15, 2024 is not a holiday
        LocalDate regularDay = LocalDate.of(2024, 1, 15);
        assertFalse(service.isHoliday(regularDay));
    }

    @Test
    void testIsWeekendOrHolidaySaturday() {
        HolidayService service = new HolidayService();
        LocalDate saturday = LocalDate.of(2024, 1, 6);
        assertTrue(service.isWeekendOrHoliday(saturday));
    }

    @Test
    void testIsWeekendOrHolidayHoliday() {
        HolidayService service = new HolidayService();
        LocalDate newYear = LocalDate.of(2024, 1, 1);
        assertTrue(service.isWeekendOrHoliday(newYear));
    }

    @Test
    void testIsWeekendOrHolidayRegularWeekday() {
        HolidayService service = new HolidayService();
        LocalDate monday = LocalDate.of(2024, 1, 8);
        assertFalse(service.isWeekendOrHoliday(monday));
    }

    @Test
    void testGetHolidaysForYear2024() {
        HolidayService service = new HolidayService();
        Set<LocalDate> holidays2024 = service.getHolidaysForYear(2024);

        assertNotNull(holidays2024);
        assertFalse(holidays2024.isEmpty());

        // Verify some expected holidays
        assertTrue(holidays2024.contains(LocalDate.of(2024, 1, 1))); // New Year
        assertTrue(holidays2024.contains(LocalDate.of(2024, 12, 25))); // Christmas
    }

    @Test
    void testGetHolidaysForYear2025() {
        HolidayService service = new HolidayService();
        Set<LocalDate> holidays2025 = service.getHolidaysForYear(2025);

        assertNotNull(holidays2025);
        assertFalse(holidays2025.isEmpty());

        // Verify some expected holidays for 2025
        assertTrue(holidays2025.contains(LocalDate.of(2025, 1, 1))); // New Year
    }

    @Test
    void testGetHolidaysForYearNotConfigured() {
        HolidayService service = new HolidayService();
        Set<LocalDate> holidays2030 = service.getHolidaysForYear(2030);

        // Should return empty set for years without configured holidays
        assertNotNull(holidays2030);
        assertTrue(holidays2030.isEmpty());
    }

    @Test
    void testHolidayFromDifferentYears() {
        HolidayService service = new HolidayService();

        // New Year 2024
        assertTrue(service.isHoliday(LocalDate.of(2024, 1, 1)));

        // New Year 2025
        assertTrue(service.isHoliday(LocalDate.of(2025, 1, 1)));

        // New Year 2030 (not configured)
        assertFalse(service.isHoliday(LocalDate.of(2030, 1, 1)));
    }
}
