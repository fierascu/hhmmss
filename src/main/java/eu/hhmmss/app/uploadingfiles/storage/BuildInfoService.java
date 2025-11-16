package eu.hhmmss.app.uploadingfiles.storage;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
@Slf4j
@Getter
public class BuildInfoService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final String buildTime;
    private final String commitId;
    private final String branch;
    private final String version;

    public BuildInfoService(
            @Autowired(required = false) BuildProperties buildProperties,
            @Autowired(required = false) GitProperties gitProperties) {
        // Build time
        this.buildTime = buildProperties != null && buildProperties.getTime() != null
                ? formatInstant(buildProperties.getTime())
                : "N/A";

        // Version
        this.version = buildProperties != null && buildProperties.getVersion() != null
                ? buildProperties.getVersion()
                : "N/A";

        // Git commit ID (abbreviated)
        this.commitId = gitProperties != null && gitProperties.getShortCommitId() != null
                ? gitProperties.getShortCommitId()
                : "N/A";

        // Git branch
        this.branch = gitProperties != null && gitProperties.getBranch() != null
                ? gitProperties.getBranch()
                : "N/A";

        log.info("Build Info - Version: {}, Build Time: {}, Commit: {}, Branch: {}",
                version, buildTime, commitId, branch);
    }

    private String formatInstant(Instant instant) {
        return FORMATTER.format(instant);
    }

    public String getFormattedBuildInfo() {
        return String.format("v%s | Built: %s | Commit: %s | Branch: %s",
                version, buildTime, commitId, branch);
    }
}
