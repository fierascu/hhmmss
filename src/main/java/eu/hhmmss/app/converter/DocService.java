package eu.hhmmss.app.converter;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.defaultString;

@Slf4j
@Service
public class DocService {

    /**
     * Replace paragraph text that starts with a known label (e.g., "Period (month/year):") keeping the label
     * and swapping the value after ':' or tab. Works with your template which uses tabs.
     */
    private static void replaceInlineAfterLabel(XWPFDocument doc, String label, String newValue) {
        for (XWPFParagraph p : doc.getParagraphs()) {
            String text = p.getText();
            if (text == null) continue;
            String norm = text.trim();
            if (norm.startsWith(label)) {
                String replacement = label + "\t" + (newValue == null ? "" : newValue);
                // rebuild runs to preserve simple formatting
                for (int i = p.getRuns().size() - 1; i >= 0; i--) p.removeRun(i);
                XWPFRun r = p.createRun();
                r.setText(replacement);
                break;
            }
        }
    }

    private static void setCellText(XWPFTableCell cell, String text) {
        if (cell == null) return;
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        XWPFRun run = p.createRun();
        run.setText(text == null ? "" : text);
    }

    /**
     * Parses a period string in MM/YYYY format and returns the number of days in that month.
     *
     * @param period the period in MM/YYYY format (e.g., "01/2024")
     * @return number of days in the month, or 31 if parsing fails (default to max)
     */
    private static int getDaysInMonth(String period) {
        if (period == null || period.trim().isEmpty()) {
            return 31; // Default to 31 days
        }
        try {
            // Parse MM/YYYY format
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/yyyy");
            YearMonth yearMonth = YearMonth.parse(period.trim(), formatter);
            return yearMonth.lengthOfMonth();
        } catch (Exception e) {
            log.warn("Could not parse period '{}' to determine days in month, defaulting to 31", period);
            return 31; // Default to 31 days if parsing fails
        }
    }

    public static Path addDataToDocs(Path templateDocx, HhmmssDto hhmmssDto, Path outDocx) {
        try (FileInputStream fis = new FileInputStream(templateDocx.toFile());
             XWPFDocument doc = new XWPFDocument(fis)) {

            Map<String, String> meta = hhmmssDto.getMeta();
            replaceInlineAfterLabel(doc, "Specific contract reference:", meta.get("Specific Contract Reference:"));
            replaceInlineAfterLabel(doc, "Purchase order number (PO):", meta.get("Purchase Order no (PO):"));
            replaceInlineAfterLabel(doc, "Period (month/year):", meta.get("Period (month/year):"));

            String fullName = ((meta.getOrDefault("First Name of person:", "").trim() + " " +
                    meta.getOrDefault("Family Name of person:", "").trim())).trim();

            replaceInlineAfterLabel(doc, "Name of person:", fullName);
            replaceInlineAfterLabel(doc, "Profile:", ""); // left blank
            replaceInlineAfterLabel(doc, "Seniority level", meta.get("Profile - Seniority level:"));
            replaceInlineAfterLabel(doc, "Date and signature:", meta.get("Period (month/year):"));

            if (doc.getTables().isEmpty()) throw new IllegalStateException("No tables found in template.");
            XWPFTable t = doc.getTables().getFirst();

            DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.FRANCE);
            sym.setDecimalSeparator(',');
            DecimalFormat df = new DecimalFormat("0.00", sym);

            // Determine the number of days in the month from the period
            String period = meta.get("Period (month/year):");
            int daysInMonth = getDaysInMonth(period);
            log.info("Generating DOCX for {} days in month (period: {})", daysInMonth, period);

            // Track totals for all 6 hours columns
            double totalFlexibility = 0.0;
            double totalOutsideFlexibility = 0.0;
            double totalSaturdays = 0.0;
            double totalSundaysHolidays = 0.0;
            double totalStandby = 0.0;
            double totalNonInvoiceable = 0.0;

            for (int day = 1; day <= 31; day++) {
                XWPFTableRow row = t.getRow(day); // 1-based day aligns with row index (since header is 0)
                if (row == null) row = t.createRow();

                if (day <= daysInMonth) {
                    // Process days within the month
                    DayData dayData = hhmmssDto.getTasks().get(day);
                    if (dayData == null) continue;

                    setCellText(row.getCell(1), defaultString(dayData.getTask()));

                    // Write all 6 hours columns
                    if (dayData.getHoursFlexibilityPeriod() > 0) {
                        setCellText(row.getCell(2), df.format(dayData.getHoursFlexibilityPeriod()));
                        totalFlexibility += dayData.getHoursFlexibilityPeriod();
                    }
                    if (dayData.getHoursOutsideFlexibilityPeriod() > 0) {
                        setCellText(row.getCell(3), df.format(dayData.getHoursOutsideFlexibilityPeriod()));
                        totalOutsideFlexibility += dayData.getHoursOutsideFlexibilityPeriod();
                    }
                    if (dayData.getHoursSaturdays() > 0) {
                        setCellText(row.getCell(4), df.format(dayData.getHoursSaturdays()));
                        totalSaturdays += dayData.getHoursSaturdays();
                    }
                    if (dayData.getHoursSundaysHolidays() > 0) {
                        setCellText(row.getCell(5), df.format(dayData.getHoursSundaysHolidays()));
                        totalSundaysHolidays += dayData.getHoursSundaysHolidays();
                    }
                    if (dayData.getHoursStandby() > 0) {
                        setCellText(row.getCell(6), df.format(dayData.getHoursStandby()));
                        totalStandby += dayData.getHoursStandby();
                    }
                    if (dayData.getHoursNonInvoiceable() > 0) {
                        setCellText(row.getCell(7), df.format(dayData.getHoursNonInvoiceable()));
                        totalNonInvoiceable += dayData.getHoursNonInvoiceable();
                    }
                } else {
                    // Clear cells for days beyond the month
                    setCellText(row.getCell(1), "");
                    for (int col = 2; col <= 7; col++) {
                        setCellText(row.getCell(col), "");
                    }
                }
            }

            // Write totals for all 6 hours columns
            XWPFTableRow tot = t.getRow(32);
            if (tot == null) tot = t.createRow();
            setCellText(tot.getCell(2), df.format(totalFlexibility));
            setCellText(tot.getCell(3), df.format(totalOutsideFlexibility));
            setCellText(tot.getCell(4), df.format(totalSaturdays));
            setCellText(tot.getCell(5), df.format(totalSundaysHolidays));
            setCellText(tot.getCell(6), df.format(totalStandby));
            setCellText(tot.getCell(7), df.format(totalNonInvoiceable));

            try (FileOutputStream fos = new FileOutputStream(outDocx.toFile())) {
                doc.write(fos);
                log.info("Wrote: {}", outDocx.toAbsolutePath());
            } catch (Exception e) {
                log.error("Could not write to {}", outDocx, e);
            }
        } catch (IOException e) {
            log.error("Could not read {}", templateDocx, e);
        }

        return outDocx;
    }
}
