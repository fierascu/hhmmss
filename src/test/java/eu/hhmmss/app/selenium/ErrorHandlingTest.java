package eu.hhmmss.app.selenium;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Selenium tests for error handling scenarios
 */
class ErrorHandlingTest extends BaseSeleniumTest {

    private String getTestFilePath(String filename) {
        return Paths.get("src/test/resources", filename).toAbsolutePath().toString();
    }

    @Test
    void testInvalidFileTypeShowsError() {
        navigateToUploadPage();

        // Create a text file to test invalid file type
        String testFilePath = getTestFilePath("invalid-file.txt");
        File testFile = new File(testFilePath);
        assertTrue(testFile.exists(), "Test text file should exist");

        // Select the invalid file
        WebElement fileInput = driver.findElement(By.id("fileInput"));
        fileInput.sendKeys(testFilePath);

        // Upload button should remain disabled for invalid file type
        WebElement uploadButton = driver.findElement(By.id("uploadButton"));

        // Wait a bit for JavaScript validation
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Button should still be disabled because file extension is not valid
        assertFalse(uploadButton.isEnabled(),
            "Upload button should remain disabled for invalid file type");
    }

    @Test
    void testOversizedFileShowsError() {
        navigateToUploadPage();

        // Use the oversized file (200KB - exceeds 128KB limit for XLSX)
        String testFilePath = getTestFilePath("oversized.xlsx");
        File testFile = new File(testFilePath);
        assertTrue(testFile.exists(), "Test oversized file should exist");
        assertTrue(testFile.length() > 131072, "Test file should exceed 128KB limit");

        // Select the oversized file
        WebElement fileInput = driver.findElement(By.id("fileInput"));
        fileInput.sendKeys(testFilePath);

        // Upload button should be enabled (client-side validation only checks extension)
        WebElement uploadButton = driver.findElement(By.id("uploadButton"));
        wait.until(ExpectedConditions.elementToBeClickable(By.id("uploadButton")));

        // Click upload button
        uploadButton.click();

        // Wait for server response
        wait.until(ExpectedConditions.or(
            ExpectedConditions.presenceOfElementLocated(By.cssSelector(".alert.alert-success")),
            ExpectedConditions.presenceOfElementLocated(By.cssSelector(".alert.alert-error"))
        ));

        // Verify error message appears for oversized file
        List<WebElement> errorMessages = driver.findElements(By.cssSelector(".alert.alert-error"));
        assertTrue(errorMessages.size() > 0, "Error message should appear for oversized file");

        // Verify error message mentions file size
        if (errorMessages.size() > 0) {
            String errorText = errorMessages.get(0).getText().toLowerCase();
            assertTrue(errorText.contains("size") || errorText.contains("large") || errorText.contains("exceed"),
                "Error message should mention file size issue");
        }
    }

    @Test
    void testErrorMessageDisplaysCorrectly() {
        navigateToUploadPage();

        String testFilePath = getTestFilePath("oversized.xlsx");
        WebElement fileInput = driver.findElement(By.id("fileInput"));
        fileInput.sendKeys(testFilePath);

        WebElement uploadButton = driver.findElement(By.id("uploadButton"));
        wait.until(ExpectedConditions.elementToBeClickable(By.id("uploadButton")));
        uploadButton.click();

        // Wait for error message
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".alert.alert-error")));

        // Verify error message structure
        WebElement errorAlert = driver.findElement(By.cssSelector(".alert.alert-error"));
        assertNotNull(errorAlert, "Error alert should be present");

        // Verify error has [ERROR] prefix
        String errorText = errorAlert.getText();
        assertTrue(errorText.contains("[ERROR]"), "Error message should contain [ERROR] prefix");

        // Verify error message is visible
        assertTrue(errorAlert.isDisplayed(), "Error alert should be visible");
    }

    @Test
    void testFormResetAfterError() {
        navigateToUploadPage();

        String testFilePath = getTestFilePath("oversized.xlsx");
        WebElement fileInput = driver.findElement(By.id("fileInput"));
        fileInput.sendKeys(testFilePath);

        WebElement uploadButton = driver.findElement(By.id("uploadButton"));
        wait.until(ExpectedConditions.elementToBeClickable(By.id("uploadButton")));
        uploadButton.click();

        // Wait for error
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".alert.alert-error")));

        // Verify form elements are still present and functional
        WebElement newFileInput = driver.findElement(By.id("fileInput"));
        assertNotNull(newFileInput, "File input should still be present after error");

        WebElement newUploadButton = driver.findElement(By.id("uploadButton"));
        assertNotNull(newUploadButton, "Upload button should still be present after error");
    }

    @Test
    void testMultipleFilesNotAccepted() {
        navigateToUploadPage();

        // Verify file input does not have 'multiple' attribute
        WebElement fileInput = driver.findElement(By.id("fileInput"));
        String multipleAttr = fileInput.getAttribute("multiple");

        // multiple attribute should be null or "false"
        assertTrue(multipleAttr == null || multipleAttr.equals("false"),
            "File input should not accept multiple files");
    }

    @Test
    void testNoFileSelectedKeepsButtonDisabled() {
        navigateToUploadPage();

        // Verify button is disabled when no file is selected
        WebElement uploadButton = driver.findElement(By.id("uploadButton"));
        assertFalse(uploadButton.isEnabled(),
            "Upload button should remain disabled when no file is selected");
    }

    @Test
    void testErrorAlertHasCorrectStyling() {
        navigateToUploadPage();

        String testFilePath = getTestFilePath("oversized.xlsx");
        WebElement fileInput = driver.findElement(By.id("fileInput"));
        fileInput.sendKeys(testFilePath);

        WebElement uploadButton = driver.findElement(By.id("uploadButton"));
        wait.until(ExpectedConditions.elementToBeClickable(By.id("uploadButton")));
        uploadButton.click();

        // Wait for error
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".alert.alert-error")));

        // Verify error alert has correct classes
        WebElement errorAlert = driver.findElement(By.cssSelector(".alert.alert-error"));
        String className = errorAlert.getAttribute("class");

        assertTrue(className.contains("alert"), "Error alert should have 'alert' class");
        assertTrue(className.contains("alert-error"), "Error alert should have 'alert-error' class");
    }
}
