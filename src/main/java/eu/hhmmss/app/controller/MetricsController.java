package eu.hhmmss.app.controller;

import eu.hhmmss.app.metrics.ProjectMetrics;
import eu.hhmmss.app.metrics.ProjectMetricsService;
import eu.hhmmss.app.metrics.TimeSavings;
import eu.hhmmss.app.metrics.TimeSavingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Controller for displaying project metrics and time savings.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/metrics")
public class MetricsController {

    private final ProjectMetricsService projectMetricsService;
    private final TimeSavingsService timeSavingsService;

    /**
     * Display metrics page.
     */
    @GetMapping
    public String showMetrics(Model model) {
        try {
            // Calculate project metrics
            ProjectMetrics projectMetrics = projectMetricsService.calculateProjectMetrics();
            model.addAttribute("projectMetrics", projectMetrics);

            // Calculate example time savings (for 1 conversion, 10 conversions, 100 conversions)
            TimeSavings oneConversion = timeSavingsService.calculateCumulativeSavings(1);
            TimeSavings tenConversions = timeSavingsService.calculateCumulativeSavings(10);
            TimeSavings hundredConversions = timeSavingsService.calculateCumulativeSavings(100);

            model.addAttribute("oneConversion", oneConversion);
            model.addAttribute("tenConversions", tenConversions);
            model.addAttribute("hundredConversions", hundredConversions);

            log.info("Displaying metrics page - {} commits, {:.2f} hours estimated",
                    projectMetrics.getTotalCommits(), projectMetrics.getEstimatedHours());

        } catch (Exception e) {
            log.error("Error loading metrics", e);
            model.addAttribute("error", "Unable to load metrics: " + e.getMessage());
        }

        return "metrics";
    }

    /**
     * Get project metrics as JSON.
     */
    @GetMapping("/project")
    @ResponseBody
    public ProjectMetrics getProjectMetrics() {
        return projectMetricsService.calculateProjectMetrics();
    }

    /**
     * Get time savings calculation as JSON.
     */
    @GetMapping("/savings")
    @ResponseBody
    public TimeSavings getTimeSavings(
            @RequestParam(defaultValue = "1") int conversions) {
        return timeSavingsService.calculateCumulativeSavings(conversions);
    }
}
