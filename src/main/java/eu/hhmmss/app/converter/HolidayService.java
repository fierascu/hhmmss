package eu.hhmmss.app.converter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class HolidayService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final Map<Integer, Set<LocalDate>> holidaysByYear = new HashMap<>();

    public HolidayService(
            @Value("${holidays.2024:}") String holidays2024,
            @Value("${holidays.2025:}") String holidays2025,
            @Value("${holidays.2026:}") String holidays2026) {
        loadHolidays(2024, holidays2024);
        loadHolidays(2025, holidays2025);
        loadHolidays(2026, holidays2026);
    }

    /**
     * Loads holidays from the comma-separated property value.
     *
     * @param year the year for these holidays
     * @param holidaysProperty comma-separated string of holiday dates
     */
    private void loadHolidays(int year, String holidaysProperty) {
        if (holidaysProperty == null || holidaysProperty.trim().isEmpty()) {
            log.warn("No holidays configured for year {}", year);
            return;
        }

        Set<LocalDate> holidays = new HashSet<>();
        String[] dateStrings = holidaysProperty.split(",");

        for (String dateStr : dateStrings) {
            dateStr = dateStr.trim();
            if (!dateStr.isEmpty()) {
                try {
                    LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);
                    holidays.add(date);
                } catch (Exception e) {
                    log.warn("Failed to parse holiday date: {}", dateStr, e);
                }
            }
        }

        holidaysByYear.put(year, holidays);
        log.info("Loaded {} holidays for year {}", holidays.size(), year);
    }

    /**
     * Checks if a given date is a weekend (Saturday or Sunday).
     *
     * @param date the date to check
     * @return true if the date is a weekend, false otherwise
     */
    public static boolean isWeekend(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }

    /**
     * Checks if a given date is a configured holiday.
     *
     * @param date the date to check
     * @return true if the date is a holiday, false otherwise
     */
    public boolean isHoliday(LocalDate date) {
        Set<LocalDate> holidays = holidaysByYear.get(date.getYear());
        return holidays != null && holidays.contains(date);
    }

    /**
     * Checks if a given date is either a weekend or a holiday.
     *
     * @param date the date to check
     * @return true if the date is a weekend or holiday, false otherwise
     */
    public boolean isWeekendOrHoliday(LocalDate date) {
        return isWeekend(date) || isHoliday(date);
    }

    /**
     * Gets all holidays for a specific year.
     *
     * @param year the year to get holidays for
     * @return set of holiday dates, or empty set if no holidays configured for that year
     */
    public Set<LocalDate> getHolidaysForYear(int year) {
        return holidaysByYear.getOrDefault(year, Collections.emptySet());
    }
}
