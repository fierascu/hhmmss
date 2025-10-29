package eu.hhmmss.app.converter;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;

import static eu.hhmmss.app.converter.XlsService.*;
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

            double total = 0.0;
            for (int day = 1; day <= 31; day++) {
                XWPFTableRow row = t.getRow(day); // 1-based day aligns with row index (since header is 0)
                if (row == null) row = t.createRow();

                Pair<String, Double> dailyTask = hhmmssDto.getTasks().get(day);
                if (dailyTask == null) continue;
                setCellText(row.getCell(1), defaultString(dailyTask.getKey()));

                double h = dailyTask.getValue();
                if (h > 0) {
                    setCellText(row.getCell(2), df.format(h));
                    total += h;
                }
            }

            XWPFTableRow tot = t.getRow(32);
            if (tot == null) tot = t.createRow();
            setCellText(tot.getCell(2), df.format(total));

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
