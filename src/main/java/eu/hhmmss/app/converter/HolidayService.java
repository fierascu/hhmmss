package eu.hhmmss.app.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class HolidayService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final Map<Integer, Set<LocalDate>> holidaysByYear = new HashMap<>();

    public HolidayService() {
        loadHolidays();
    }

    /**
     * Loads holidays from the JSON configuration file.
     */
    private void loadHolidays() {
        try {
            ClassPathResource resource = new ClassPathResource("config/holidays.json");
            if (!resource.exists()) {
                log.warn("Holidays configuration file not found, continuing without holidays");
                return;
            }

            try (InputStream inputStream = resource.getInputStream()) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(inputStream);

                Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    int year = Integer.parseInt(entry.getKey());
                    Set<LocalDate> holidays = new HashSet<>();

                    JsonNode yearHolidays = entry.getValue();
                    if (yearHolidays.isArray()) {
                        for (JsonNode dateNode : yearHolidays) {
                            String dateStr = dateNode.asText();
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
            }
        } catch (IOException e) {
            log.error("Failed to load holidays configuration", e);
        }
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
