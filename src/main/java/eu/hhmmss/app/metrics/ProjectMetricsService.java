package eu.hhmmss.app.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service to calculate project development metrics from git history.
 * Analyzes commits to estimate time spent working on the project.
 */
@Slf4j
@Service
public class ProjectMetricsService {

    private static final DateTimeFormatter GIT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");

    // Maximum time between commits to consider them part of the same session (2 hours)
    private static final long MAX_SESSION_GAP_HOURS = 2;

    // Default session duration when it's the last commit in a session (1 hour)
    private static final long DEFAULT_SESSION_DURATION_HOURS = 1;

    /**
     * Calculate comprehensive project metrics from git history.
     *
     * @return ProjectMetrics object containing all calculated metrics
     */
    public ProjectMetrics calculateProjectMetrics() {
        try {
            List<GitCommit> commits = getGitCommits();

            if (commits.isEmpty()) {
                log.warn("No commits found in git history");
                return ProjectMetrics.builder()
                        .totalCommits(0)
                        .estimatedHours(0.0)
                        .build();
            }

            // Sort commits by date (oldest first)
            commits.sort(Comparator.comparing(GitCommit::getDate));

            // Calculate estimated work hours using session-based approach
            double estimatedHours = calculateEstimatedHours(commits);

            // Calculate statistics by author
            Map<String, AuthorStats> authorStats = calculateAuthorStats(commits);

            // Calculate date range
            LocalDateTime firstCommit = commits.get(0).getDate();
            LocalDateTime lastCommit = commits.get(commits.size() - 1).getDate();
            long daysActive = Duration.between(firstCommit, lastCommit).toDays() + 1;

            return ProjectMetrics.builder()
                    .totalCommits(commits.size())
                    .estimatedHours(estimatedHours)
                    .firstCommitDate(firstCommit)
                    .lastCommitDate(lastCommit)
                    .daysActive(daysActive)
                    .authorStats(authorStats)
                    .uniqueAuthors(authorStats.size())
                    .build();

        } catch (Exception e) {
            log.error("Error calculating project metrics", e);
            return ProjectMetrics.builder()
                    .totalCommits(0)
                    .estimatedHours(0.0)
                    .build();
        }
    }

    /**
     * Calculate estimated work hours using session-based approach.
     * Groups commits into work sessions based on time gaps between commits.
     */
    private double calculateEstimatedHours(List<GitCommit> commits) {
        double totalHours = 0.0;

        for (int i = 0; i < commits.size(); i++) {
            if (i == commits.size() - 1) {
                // Last commit: add default session duration
                totalHours += DEFAULT_SESSION_DURATION_HOURS;
            } else {
                // Calculate time to next commit
                LocalDateTime current = commits.get(i).getDate();
                LocalDateTime next = commits.get(i + 1).getDate();
                long hoursBetween = Duration.between(current, next).toHours();

                if (hoursBetween <= MAX_SESSION_GAP_HOURS) {
                    // Part of same session: add actual time to next commit
                    totalHours += (double) hoursBetween;
                } else {
                    // End of session: add default session duration
                    totalHours += DEFAULT_SESSION_DURATION_HOURS;
                }
            }
        }

        return totalHours;
    }

    /**
     * Calculate statistics per author.
     */
    private Map<String, AuthorStats> calculateAuthorStats(List<GitCommit> commits) {
        Map<String, List<GitCommit>> commitsByAuthor = commits.stream()
                .collect(Collectors.groupingBy(GitCommit::getAuthor));

        Map<String, AuthorStats> stats = new HashMap<>();

        for (Map.Entry<String, List<GitCommit>> entry : commitsByAuthor.entrySet()) {
            String author = entry.getKey();
            List<GitCommit> authorCommits = entry.getValue();

            // Sort commits by date
            authorCommits.sort(Comparator.comparing(GitCommit::getDate));

            // Calculate estimated hours for this author
            double hours = calculateEstimatedHours(authorCommits);

            stats.put(author, AuthorStats.builder()
                    .author(author)
                    .commits(authorCommits.size())
                    .estimatedHours(hours)
                    .build());
        }

        return stats;
    }

    /**
     * Retrieve git commits from the repository.
     */
    private List<GitCommit> getGitCommits() throws Exception {
        List<GitCommit> commits = new ArrayList<>();

        // Execute git log command
        ProcessBuilder pb = new ProcessBuilder(
                "git", "log", "--all", "--no-merges",
                "--pretty=format:%h|%an|%ai|%s"
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|", 4);
                if (parts.length >= 4) {
                    commits.add(GitCommit.builder()
                            .hash(parts[0])
                            .author(parts[1])
                            .date(LocalDateTime.parse(parts[2], GIT_DATE_FORMAT))
                            .message(parts[3])
                            .build());
                }
            }
        }

        process.waitFor();

        log.info("Retrieved {} commits from git history", commits.size());
        return commits;
    }
}
