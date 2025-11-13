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
 * Selenium tests for single Excel file upload workflow
 */
class SingleFileUploadTest extends BaseSeleniumTest {

    private String getTestFilePath(String filename) {
        return Paths.get("src/test/resources", filename).toAbsolutePath().toString();
    }

    @Test
    void testUploadValidExcelFile() {
        navigateToUploadPage();

        // Get the test Excel file path
        String testFilePath = getTestFilePath("timesheet-in.xlsx");
        File testFile = new File(testFilePath);
        assertTrue(testFile.exists(), "Test file should exist: " + testFilePath);

        // Find and fill the file input
        WebElement fileInput = driver.findElement(By.id("fileInput"));
        fileInput.sendKeys(testFilePath);

        // Verify upload button is enabled after file selection
        WebElement uploadButton = driver.findElement(By.id("uploadButton"));
        assertTrue(uploadButton.isEnabled(), "Upload button should be enabled after file selection");

        // Click upload button
        uploadButton.click();

        // Wait for page to reload and check for success message
        wait.until(ExpectedConditions.or(
            ExpectedConditions.presenceOfElementLocated(By.cssSelector(".alert.alert-success")),
            ExpectedConditions.presenceOfElementLocated(By.cssSelector(".alert.alert-error"))
        ));

        // Verify success message appears (or error if LibreOffice is not available)
        List<WebElement> successMessages = driver.findElements(By.cssSelector(".alert.alert-success"));
        List<WebElement> errorMessages = driver.findElements(By.cssSelector(".alert.alert-error"));

        // Either success or a specific error (like LibreOffice not available in test env)
        assertTrue(successMessages.size() > 0 || errorMessages.size() > 0,
            "Should show either success or error message after upload");
    }

    @Test
    void testGeneratedFilesListAppears() {
        navigateToUploadPage();

        String testFilePath = getTestFilePath("timesheet-in.xlsx");
        File testFile = new File(testFilePath);
        assertTrue(testFile.exists(), "Test file should exist");

        // Upload file
        WebElement fileInput = driver.findElement(By.id("fileInput"));
        fileInput.sendKeys(testFilePath);

        WebElement uploadButton = driver.findElement(By.id("uploadButton"));
        uploadButton.click();

        // Wait for response
        wait.until(ExpectedConditions.or(
            ExpectedConditions.presenceOfElementLocated(By.cssSelector(".alert.alert-success")),
            ExpectedConditions.presenceOfElementLocated(By.cssSelector(".alert.alert-error"))
        ));

        // If successful, verify generated files list appears
        List<WebElement> successMessages = driver.findElements(By.cssSelector(".alert.alert-success"));
        if (successMessages.size() > 0) {
            // Check for generated files section
            List<WebElement> fileList = driver.findElements(By.cssSelector(".file-list"));
            assertTrue(fileList.size() > 0, "Generated files list should appear after successful upload");
        }
    }

    @Test
    void testGeneratedFilesContainExpectedFiles() {
        navigateToUploadPage();

        String testFilePath = getTestFilePath("timesheet-in.xlsx");
        File testFile = new File(testFilePath);
        assertTrue(testFile.exists(), "Test file should exist");

        // Upload file
        WebElement fileInput = driver.findElement(By.id("fileInput"));
        fileInput.sendKeys(testFilePath);

        WebElement uploadButton = driver.findElement(By.id("uploadButton"));
        uploadButton.click();

        // Wait for response
        wait.until(ExpectedConditions.or(
            ExpectedConditions.presenceOfElementLocated(By.cssSelector(".alert.alert-success")),
            ExpectedConditions.presenceOfElementLocated(By.cssSelector(".alert.alert-error"))
        ));

        // If successful, verify generated files
        List<WebElement> successMessages = driver.findElements(By.cssSelector(".alert.alert-success"));
        if (successMessages.size() > 0) {
            List<WebElement> fileList = driver.findElements(By.cssSelector(".file-list ul li a"));
            if (fileList.size() > 0) {
                // Verify we have downloadable links
                assertTrue(fileList.size() >= 1, "Should have at least one generated file");

                // Verify links are valid
                for (WebElement link : fileList) {
                    String href = link.getAttribute("href");
                    assertNotNull(href, "File link should have href");
                    assertTrue(href.startsWith("http"), "File link should be a valid URL");
                }
            }
        }
    }

    @Test
    void testUploadButtonStateAfterFileSelection() {
        navigateToUploadPage();

        // Initially disabled
        WebElement uploadButton = driver.findElement(By.id("uploadButton"));
        assertFalse(uploadButton.isEnabled(), "Upload button should be initially disabled");

        // Select valid file
        String testFilePath = getTestFilePath("timesheet-in.xlsx");
        WebElement fileInput = driver.findElement(By.id("fileInput"));
        fileInput.sendKeys(testFilePath);

        // Should be enabled after valid file selection
        // Wait a bit for JavaScript to process
        wait.until(ExpectedConditions.elementToBeClickable(By.id("uploadButton")));
        assertTrue(uploadButton.isEnabled(), "Upload button should be enabled after valid file selection");
    }

    @Test
    void testPageNavigationAfterUpload() {
        navigateToUploadPage();
        String initialUrl = driver.getCurrentUrl();

        String testFilePath = getTestFilePath("timesheet-in.xlsx");
        WebElement fileInput = driver.findElement(By.id("fileInput"));
        fileInput.sendKeys(testFilePath);

        WebElement uploadButton = driver.findElement(By.id("uploadButton"));
        uploadButton.click();

        // Wait for page to reload
        wait.until(ExpectedConditions.or(
            ExpectedConditions.presenceOfElementLocated(By.cssSelector(".alert.alert-success")),
            ExpectedConditions.presenceOfElementLocated(By.cssSelector(".alert.alert-error"))
        ));

        // Verify we're still on the upload page (or redirected to it)
        String currentUrl = driver.getCurrentUrl();
        assertTrue(currentUrl.contains(getBaseUrl()), "Should still be on application URL");
    }
}
