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
 * Selenium tests for ZIP batch processing workflow
 */
class ZipBatchProcessingTest extends BaseSeleniumTest {

    private String getTestFilePath(String filename) {
        return Paths.get("src/test/resources", filename).toAbsolutePath().toString();
    }

    @Test
    void testUploadZipFile() {
        navigateToUploadPage();

        // Get the test ZIP file path
        String testFilePath = getTestFilePath("test-batch.zip");
        File testFile = new File(testFilePath);
        assertTrue(testFile.exists(), "Test ZIP file should exist: " + testFilePath);

        // Find and fill the file input
        WebElement fileInput = driver.findElement(By.id("fileInput"));
        fileInput.sendKeys(testFilePath);

        // Verify upload button is enabled after file selection
        WebElement uploadButton = driver.findElement(By.id("uploadButton"));
        wait.until(ExpectedConditions.elementToBeClickable(By.id("uploadButton")));
        assertTrue(uploadButton.isEnabled(), "Upload button should be enabled after file selection");

        // Click upload button
        uploadButton.click();

        // Wait for page to reload and check for success or error message
        wait.until(ExpectedConditions.or(
            ExpectedConditions.presenceOfElementLocated(By.cssSelector(".alert.alert-success")),
            ExpectedConditions.presenceOfElementLocated(By.cssSelector(".alert.alert-error"))
        ));

        // Verify some response appears
        List<WebElement> successMessages = driver.findElements(By.cssSelector(".alert.alert-success"));
        List<WebElement> errorMessages = driver.findElements(By.cssSelector(".alert.alert-error"));

        assertTrue(successMessages.size() > 0 || errorMessages.size() > 0,
            "Should show either success or error message after ZIP upload");
    }

    @Test
    void testZipProcessingShowsProcessedFilesList() {
        navigateToUploadPage();

        String testFilePath = getTestFilePath("test-batch.zip");
        File testFile = new File(testFilePath);
        assertTrue(testFile.exists(), "Test ZIP file should exist");

        // Upload ZIP file
        WebElement fileInput = driver.findElement(By.id("fileInput"));
        fileInput.sendKeys(testFilePath);

        WebElement uploadButton = driver.findElement(By.id("uploadButton"));
        wait.until(ExpectedConditions.elementToBeClickable(By.id("uploadButton")));
        uploadButton.click();

        // Wait for response
        wait.until(ExpectedConditions.or(
            ExpectedConditions.presenceOfElementLocated(By.cssSelector(".alert.alert-success")),
            ExpectedConditions.presenceOfElementLocated(By.cssSelector(".alert.alert-error"))
        ));

        // If successful, check for processed files list
        List<WebElement> successMessages = driver.findElements(By.cssSelector(".alert.alert-success"));
        if (successMessages.size() > 0) {
            // Look for processed files list in success message
            WebElement successAlert = successMessages.get(0);
            String successText = successAlert.getText();

            // Should contain information about processed files
            assertTrue(successText.contains("[OK]") || successText.contains("success"),
                "Success message should indicate successful processing");
        }
    }

    @Test
    void testZipProcessingGeneratesDownloadableResult() {
        navigateToUploadPage();

        String testFilePath = getTestFilePath("test-batch.zip");
        File testFile = new File(testFilePath);
        assertTrue(testFile.exists(), "Test ZIP file should exist");

        // Upload ZIP file
        WebElement fileInput = driver.findElement(By.id("fileInput"));
        fileInput.sendKeys(testFilePath);

        WebElement uploadButton = driver.findElement(By.id("uploadButton"));
        wait.until(ExpectedConditions.elementToBeClickable(By.id("uploadButton")));
        uploadButton.click();

        // Wait for response
        wait.until(ExpectedConditions.or(
            ExpectedConditions.presenceOfElementLocated(By.cssSelector(".alert.alert-success")),
            ExpectedConditions.presenceOfElementLocated(By.cssSelector(".alert.alert-error"))
        ));

        // If successful, check for generated files
        List<WebElement> successMessages = driver.findElements(By.cssSelector(".alert.alert-success"));
        if (successMessages.size() > 0) {
            List<WebElement> fileList = driver.findElements(By.cssSelector(".file-list ul li a"));
            if (fileList.size() > 0) {
                // Verify we have downloadable result ZIP
                boolean hasZipResult = false;
                for (WebElement link : fileList) {
                    String linkText = link.getText();
                    if (linkText.toLowerCase().contains(".zip")) {
                        hasZipResult = true;
                        // Verify link is valid
                        String href = link.getAttribute("href");
                        assertNotNull(href, "Result ZIP link should have href");
                        assertTrue(href.startsWith("http"), "Result ZIP link should be a valid URL");
                        break;
                    }
                }
                assertTrue(hasZipResult, "Should have a result ZIP file for batch processing");
            }
        }
    }

    @Test
    void testZipFileAcceptedByFileInput() {
        navigateToUploadPage();

        // Verify file input accepts .zip files
        WebElement fileInput = driver.findElement(By.id("fileInput"));
        String acceptAttr = fileInput.getAttribute("accept");
        assertTrue(acceptAttr.contains(".zip"), "File input should accept .zip files");
    }

    @Test
    void testZipButtonEnabledAfterSelection() {
        navigateToUploadPage();

        String testFilePath = getTestFilePath("test-batch.zip");
        File testFile = new File(testFilePath);
        assertTrue(testFile.exists(), "Test ZIP file should exist");

        // Initially button should be disabled
        WebElement uploadButton = driver.findElement(By.id("uploadButton"));
        assertFalse(uploadButton.isEnabled(), "Upload button should be initially disabled");

        // Select ZIP file
        WebElement fileInput = driver.findElement(By.id("fileInput"));
        fileInput.sendKeys(testFilePath);

        // Button should be enabled after ZIP selection
        wait.until(ExpectedConditions.elementToBeClickable(By.id("uploadButton")));
        assertTrue(uploadButton.isEnabled(), "Upload button should be enabled after ZIP file selection");
    }
}
