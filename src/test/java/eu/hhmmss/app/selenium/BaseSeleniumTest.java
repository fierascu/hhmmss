package eu.hhmmss.app.selenium;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.Duration;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Base class for Selenium E2E tests.
 * Provides WebDriver setup, configuration, and utility methods.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseSeleniumTest {

    protected WebDriver driver;
    protected WebDriverWait wait;

    @LocalServerPort
    protected int port;

    @BeforeAll
    static void setupClass() {
        // Setup ChromeDriver using WebDriverManager
        WebDriverManager.chromedriver().setup();
    }

    @BeforeEach
    void setUp() {
        // Check if Chrome is available - skip tests if not
        try {
            // Configure Chrome options
            ChromeOptions options = new ChromeOptions();

            // Run in headless mode for CI/CD environments
            // Can be disabled by setting SELENIUM_HEADLESS=false environment variable
            String headless = System.getenv().getOrDefault("SELENIUM_HEADLESS", "true");
            if ("true".equals(headless)) {
                options.addArguments("--headless=new");
            }

            // Additional Chrome options for stability
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");

            // Create WebDriver instance
            driver = new ChromeDriver(options);

            // Set implicit wait and page load timeout
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));

            // Create explicit wait with 10 second timeout
            wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        } catch (Exception e) {
            // If Chrome setup fails, skip the test instead of failing it
            assumeTrue(false, "Chrome WebDriver is not available. Skipping Selenium test. " +
                    "To skip these tests, run: mvn test -DskipSelenium");
        }
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    /**
     * Get the base URL of the application
     */
    protected String getBaseUrl() {
        return "http://localhost:" + port;
    }

    /**
     * Navigate to a specific path
     */
    protected void navigateTo(String path) {
        driver.get(getBaseUrl() + path);
    }

    /**
     * Navigate to the upload page
     */
    protected void navigateToUploadPage() {
        navigateTo("/");
    }
}
