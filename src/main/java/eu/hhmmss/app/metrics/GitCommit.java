package eu.hhmmss.app.metrics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a single git commit.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitCommit {
    private String hash;
    private String author;
    private LocalDateTime date;
    private String message;
}
