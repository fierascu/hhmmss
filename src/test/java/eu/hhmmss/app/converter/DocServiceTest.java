package eu.hhmmss.app.converter;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DocServiceTest {

    @Test
    void testAddDataToDocs() throws Exception {
        Path xlsxFilePath = Path.of("src/test/resources/timesheet-in.xlsx");
        HhmmssDto hhmmssXlsxFormat = XlsService.readTimesheet(xlsxFilePath);
        assertNotNull(hhmmssXlsxFormat);
        assertEquals(30, hhmmssXlsxFormat.getTasks().size());
        assertEquals("work", hhmmssXlsxFormat.getTasks().get(30).getKey());
        assertEquals(8.5d, hhmmssXlsxFormat.getTasks().get(30).getValue());
        assertEquals(6, hhmmssXlsxFormat.getMeta().size());
        assertEquals("contract_no", hhmmssXlsxFormat.getMeta().get("Specific Contract Reference:"));

        Path docxInFilePath = Path.of("src/test/resources/timesheet-out.docx");
        Path docxOutFilePath = Path.of("src/test/resources/timesheet-out-generated.docx");
        Path fileProcessed = DocService.addDataToDocs(docxInFilePath, hhmmssXlsxFormat, docxOutFilePath);

        assertNotNull(fileProcessed);
    }
}