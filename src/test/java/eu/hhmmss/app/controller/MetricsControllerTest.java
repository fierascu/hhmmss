package eu.hhmmss.app.controller;

import eu.hhmmss.app.metrics.TimeSavings;
import eu.hhmmss.app.metrics.TimeSavingsService;
import eu.hhmmss.app.uploadingfiles.storage.BuildInfoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MetricsController.class)
class MetricsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TimeSavingsService timeSavingsService;

    @MockBean
    private BuildInfoService buildInfoService;

    @Test
    void testShowMetrics_displaysMetricsPage() throws Exception {
        // Arrange
        TimeSavings savings = TimeSavings.builder()
                .numberOfConversions(1)
                .daysProcessed(20)
                .timeSavedMinutes(14.0)
                .percentImprovement(95.0)
                .build();

        when(timeSavingsService.calculateCumulativeSavings(anyInt())).thenReturn(savings);

        // Act & Assert
        mockMvc.perform(get("/metrics")
                        .sessionAttr("theme", "terminal"))
                .andExpect(status().isOk())
                .andExpect(view().name("metrics"))
                .andExpect(model().attributeExists("oneConversion"))
                .andExpect(model().attributeExists("tenConversions"))
                .andExpect(model().attributeExists("hundredConversions"));
    }

    @Test
    void testGetTimeSavings_returnsJson() throws Exception {
        // Arrange
        TimeSavings savings = TimeSavings.builder()
                .numberOfConversions(10)
                .timeSavedMinutes(140.0)
                .percentImprovement(95.0)
                .build();

        when(timeSavingsService.calculateCumulativeSavings(10)).thenReturn(savings);

        // Act & Assert
        mockMvc.perform(get("/metrics/savings?conversions=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numberOfConversions").value(10))
                .andExpect(jsonPath("$.timeSavedMinutes").value(140.0))
                .andExpect(jsonPath("$.percentImprovement").value(95.0));
    }

    @Test
    void testGetTimeSavings_usesDefaultConversions() throws Exception {
        // Arrange
        TimeSavings savings = TimeSavings.builder()
                .numberOfConversions(1)
                .timeSavedMinutes(14.0)
                .build();

        when(timeSavingsService.calculateCumulativeSavings(1)).thenReturn(savings);

        // Act & Assert
        mockMvc.perform(get("/metrics/savings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numberOfConversions").value(1));
    }

    @Test
    void testShowMetrics_handlesErrors() throws Exception {
        // Arrange
        when(timeSavingsService.calculateCumulativeSavings(anyInt()))
                .thenThrow(new RuntimeException("Calculation error"));

        // Act & Assert
        mockMvc.perform(get("/metrics")
                        .sessionAttr("theme", "terminal"))
                .andExpect(status().isOk())
                .andExpect(view().name("metrics"))
                .andExpect(model().attributeExists("error"));
    }
}
