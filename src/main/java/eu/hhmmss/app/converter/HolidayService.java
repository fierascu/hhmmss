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

    public HolidayService(@Value("${holidays:}") String holidaysProperty) {
        loadHolidays(holidaysProperty);
    }

    /**
     * Loads holidays from the comma-separated property value.
     * Automatically groups holidays by year based on the date.
     *
     * @param holidaysProperty comma-separated string of holiday dates (format: YYYY-MM-DD)
     */
    private void loadHolidays(String holidaysProperty) {
        if (holidaysProperty == null || holidaysProperty.trim().isEmpty()) {
            log.warn("No holidays configured");
            return;
        }

        String[] dateStrings = holidaysProperty.split(",");
        int totalLoaded = 0;

        for (String dateStr : dateStrings) {
            dateStr = dateStr.trim();
            if (!dateStr.isEmpty()) {
                try {
                    LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);
                    int year = date.getYear();

                    holidaysByYear.computeIfAbsent(year, k -> new HashSet<>()).add(date);
                    totalLoaded++;
                } catch (Exception e) {
                    log.warn("Failed to parse holiday date: {}", dateStr, e);
                }
            }
        }

        log.info("Loaded {} holidays across {} years", totalLoaded, holidaysByYear.size());
        holidaysByYear.forEach((year, holidays) ->
            log.info("  Year {}: {} holidays", year, holidays.size())
        );
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
