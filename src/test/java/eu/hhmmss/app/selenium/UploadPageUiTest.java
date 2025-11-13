package eu.hhmmss.app.selenium;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Selenium tests for upload page UI elements and interactions
 */
class UploadPageUiTest extends BaseSeleniumTest {

    @Test
    void testUploadPageLoads() {
        navigateToUploadPage();

        // Verify page title
        assertTrue(driver.getTitle().contains("Upload Timesheet"));
    }

    @Test
    void testFileInputExists() {
        navigateToUploadPage();

        // Verify file input exists
        WebElement fileInput = driver.findElement(By.id("fileInput"));
        assertNotNull(fileInput);
        assertEquals("file", fileInput.getAttribute("type"));
    }

    @Test
    void testFileInputAcceptsCorrectFileTypes() {
        navigateToUploadPage();

        // Verify file input accepts expected file types
        WebElement fileInput = driver.findElement(By.id("fileInput"));
        String acceptAttr = fileInput.getAttribute("accept");

        assertTrue(acceptAttr.contains(".xls"));
        assertTrue(acceptAttr.contains(".xlsx"));
        assertTrue(acceptAttr.contains(".xlsm"));
        assertTrue(acceptAttr.contains(".xlsb"));
        assertTrue(acceptAttr.contains(".zip"));
    }

    @Test
    void testUploadButtonExists() {
        navigateToUploadPage();

        // Verify upload button exists
        WebElement uploadButton = driver.findElement(By.id("uploadButton"));
        assertNotNull(uploadButton);
        assertEquals("submit", uploadButton.getAttribute("type"));
    }

    @Test
    void testUploadButtonInitiallyDisabled() {
        navigateToUploadPage();

        // Verify upload button is initially disabled
        WebElement uploadButton = driver.findElement(By.id("uploadButton"));
        assertFalse(uploadButton.isEnabled());
    }

    @Test
    void testFormExists() {
        navigateToUploadPage();

        // Verify form exists with correct attributes
        WebElement form = driver.findElement(By.cssSelector("form"));
        assertNotNull(form);
        assertEquals("POST", form.getAttribute("method").toUpperCase());
        assertTrue(form.getAttribute("enctype").contains("multipart/form-data"));
    }

    @Test
    void testLabelExists() {
        navigateToUploadPage();

        // Verify label exists and points to file input
        WebElement label = driver.findElement(By.cssSelector("label[for='fileInput']"));
        assertNotNull(label);
        assertTrue(label.getText().contains("Select Excel Timesheet or ZIP file"));
    }

    @Test
    void testNoErrorMessageOnInitialLoad() {
        navigateToUploadPage();

        // Verify no error message is displayed on initial load
        assertEquals(0, driver.findElements(By.cssSelector(".alert.alert-error")).size());
    }

    @Test
    void testNoSuccessMessageOnInitialLoad() {
        navigateToUploadPage();

        // Verify no success message is displayed on initial load
        assertEquals(0, driver.findElements(By.cssSelector(".alert.alert-success")).size());
    }

    @Test
    void testPageHasHeader() {
        navigateToUploadPage();

        // Verify page has a header element
        assertTrue(driver.findElements(By.tagName("header")).size() > 0 ||
                   driver.findElements(By.tagName("h1")).size() > 0 ||
                   driver.findElements(By.tagName("h2")).size() > 0);
    }

    @Test
    void testPageHasFooter() {
        navigateToUploadPage();

        // Verify page has a footer element
        assertTrue(driver.findElements(By.tagName("footer")).size() > 0);
    }
}
