package eu.hhmmss.app.converter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DayData {
    private String task;
    private double hoursFlexibilityPeriod;
    private double hoursOutsideFlexibilityPeriod;
    private double hoursSaturdays;
    private double hoursSundaysHolidays;
    private double hoursStandby;
    private double hoursNonInvoiceable;
}
