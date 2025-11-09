package eu.hhmmss.app.converter;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Service for generating Excel files with extracted hhmmss data
 */
@Slf4j
@Service
public class XlsGeneratorService {

    /**
     * Generates a new Excel file containing the extracted hhmmss data
     *
     * @param data the parsed hhmmss data from the original file
     * @param outputPath path where the generated Excel file should be saved
     * @throws IOException if file cannot be written
     */
    public void generateExtractedDataXls(HhmmssDto data, Path outputPath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Extracted Data");

            int rowNum = 0;

            // Create title
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Extracted HHMMSS Data");
            CellStyle titleStyle = createTitleStyle(workbook);
            titleCell.setCellStyle(titleStyle);

            rowNum++; // Empty row

            // Write metadata section
            Row metaHeaderRow = sheet.createRow(rowNum++);
            Cell metaHeaderCell = metaHeaderRow.createCell(0);
            metaHeaderCell.setCellValue("Metadata");
            CellStyle headerStyle = createHeaderStyle(workbook);
            metaHeaderCell.setCellStyle(headerStyle);

            // Write each metadata entry
            for (Map.Entry<String, String> entry : data.getMeta().entrySet()) {
                Row metaRow = sheet.createRow(rowNum++);
                metaRow.createCell(0).setCellValue(entry.getKey());
                metaRow.createCell(1).setCellValue(entry.getValue());
            }

            rowNum++; // Empty row

            // Write tasks section
            Row tasksHeaderRow = sheet.createRow(rowNum++);
            tasksHeaderRow.createCell(0).setCellValue("Day");
            tasksHeaderRow.createCell(1).setCellValue("Task");
            tasksHeaderRow.createCell(2).setCellValue("Hours");
            tasksHeaderRow.createCell(3).setCellValue("HH:MM:SS");

            // Apply header style
            for (int i = 0; i < 4; i++) {
                tasksHeaderRow.getCell(i).setCellStyle(headerStyle);
            }

            // Write task data sorted by day
            data.getTasks().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    int currentRow = sheet.getLastRowNum() + 1;
                    Row taskRow = sheet.createRow(currentRow);

                    Integer day = entry.getKey();
                    Pair<String, Double> taskData = entry.getValue();
                    String taskDescription = taskData.getLeft();
                    Double hours = taskData.getRight();

                    taskRow.createCell(0).setCellValue(day);
                    taskRow.createCell(1).setCellValue(taskDescription);
                    taskRow.createCell(2).setCellValue(hours != null ? hours : 0.0);
                    taskRow.createCell(3).setCellValue(convertHoursToHhmmss(hours));
                });

            // Auto-size columns
            for (int i = 0; i < 4; i++) {
                sheet.autoSizeColumn(i);
            }

            // Write to file
            try (FileOutputStream fileOut = new FileOutputStream(outputPath.toFile())) {
                workbook.write(fileOut);
                log.info("Generated extracted data XLS at: {}", outputPath);
            }
        }
    }

    /**
     * Converts hours (as double) to HH:MM:SS format
     *
     * @param hours decimal hours (e.g., 8.5 = 8 hours 30 minutes)
     * @return formatted string in HH:MM:SS format
     */
    private String convertHoursToHhmmss(Double hours) {
        if (hours == null || hours == 0.0) {
            return "00:00:00";
        }

        int totalSeconds = (int) Math.round(hours * 3600);
        int h = totalSeconds / 3600;
        int m = (totalSeconds % 3600) / 60;
        int s = totalSeconds % 60;

        return String.format("%02d:%02d:%02d", h, m, s);
    }

    /**
     * Creates a style for title cells
     */
    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        return style;
    }

    /**
     * Creates a style for header cells
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
}
