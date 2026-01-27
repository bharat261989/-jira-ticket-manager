package org.limits.task.impl;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import org.limits.client.JiraClient;
import org.limits.config.JiraConfiguration.IssueSyncTaskConfig;
import org.limits.task.AbstractBackgroundTask;
import org.limits.task.TaskResult;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Task that syncs unresolved issues from Jira for the configured base project.
 *
 * Features:
 * - Tracks last sync time in a file to fetch only updated issues
 * - Saves all synced issues to a CSV file
 * - Filters: project = baseProject AND resolution = Unresolved
 */
public class IssueSyncTask extends AbstractBackgroundTask<IssueSyncTaskConfig> {

    private static final String TASK_ID = "issue-sync";
    private static final String TASK_NAME = "Issue Sync Task";
    private static final String LAST_SYNC_FILE = "data/last-sync-time.txt";
    private static final String CSV_OUTPUT_FILE = "data/synced-issues.csv";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter JQL_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JiraClient jiraClient;
    private final String baseProject;

    public IssueSyncTask(IssueSyncTaskConfig config, JiraClient jiraClient, String baseProject) {
        super(config);
        this.jiraClient = jiraClient;
        this.baseProject = baseProject;
        ensureDataDirectoryExists();
    }

    @Override
    public String getTaskId() {
        return TASK_ID;
    }

    @Override
    public String getTaskName() {
        return TASK_NAME;
    }

    @Override
    protected TaskResult doExecute(Instant startTime) {
        int batchSize = config.getBatchSize();

        // Read last sync time
        LocalDateTime lastSyncTime = readLastSyncTime();

        // Build JQL query
        String jql = buildJqlQuery(lastSyncTime);

        log.info("Starting issue sync for project {} with JQL: {} (batch size: {})",
                baseProject, jql, batchSize);

        List<Issue> allIssues = new ArrayList<>();
        int totalProcessed = 0;
        int startAt = 0;
        int newIssues = 0;
        int updatedIssues = 0;

        try {
            while (true) {
                SearchResult result = jiraClient.searchIssues(jql, startAt, batchSize);
                int fetched = 0;

                for (Issue issue : result.getIssues()) {
                    allIssues.add(issue);

                    // Determine if new or updated based on creation date vs last sync
                    if (lastSyncTime == null || isNewIssue(issue, lastSyncTime)) {
                        newIssues++;
                    } else {
                        updatedIssues++;
                    }

                    fetched++;
                    totalProcessed++;
                }

                log.debug("Fetched {} issues (total: {})", fetched, totalProcessed);

                if (fetched < batchSize || totalProcessed >= result.getTotal()) {
                    break;
                }

                startAt += batchSize;
            }

            // Save issues to CSV
            String csvPath = saveToCsv(allIssues);

            // Update last sync time
            saveLastSyncTime(startTime);

            log.info("Issue sync completed. Total: {}, New: {}, Updated: {}. CSV saved to: {}",
                    totalProcessed, newIssues, updatedIssues, csvPath);

            return TaskResult.builder(getTaskId())
                    .success()
                    .message(String.format("Synced %d issues (%d new, %d updated)",
                            totalProcessed, newIssues, updatedIssues))
                    .startTime(startTime)
                    .endTime(Instant.now())
                    .addMetadata("totalIssues", totalProcessed)
                    .addMetadata("newIssues", newIssues)
                    .addMetadata("updatedIssues", updatedIssues)
                    .addMetadata("csvFile", csvPath)
                    .addMetadata("project", baseProject)
                    .build();

        } catch (Exception e) {
            log.error("Issue sync failed", e);
            return TaskResult.builder(getTaskId())
                    .failure()
                    .message("Sync failed: " + e.getMessage())
                    .startTime(startTime)
                    .endTime(Instant.now())
                    .addMetadata("issuesProcessed", totalProcessed)
                    .addMetadata("error", e.getClass().getSimpleName())
                    .build();
        }
    }

    /**
     * Build JQL query for unresolved issues in the base project.
     * If lastSyncTime is available, only fetch issues updated since then.
     */
    private String buildJqlQuery(LocalDateTime lastSyncTime) {
        StringBuilder jql = new StringBuilder();
        jql.append("project = ").append(baseProject);
        jql.append(" AND resolution = Unresolved");

        if (lastSyncTime != null) {
            String formattedTime = lastSyncTime.format(JQL_DATE_FORMATTER);
            jql.append(" AND updated >= \"").append(formattedTime).append("\"");
        }

        jql.append(" ORDER BY updated DESC");
        return jql.toString();
    }

    /**
     * Check if an issue is new (created after last sync time)
     */
    private boolean isNewIssue(Issue issue, LocalDateTime lastSyncTime) {
        if (issue.getCreationDate() == null) {
            return false;
        }
        LocalDateTime createdDate = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(issue.getCreationDate().getMillis()),
                ZoneId.systemDefault()
        );
        return createdDate.isAfter(lastSyncTime);
    }

    /**
     * Read the last sync time from file
     */
    private LocalDateTime readLastSyncTime() {
        Path path = Paths.get(LAST_SYNC_FILE);
        if (!Files.exists(path)) {
            log.info("No previous sync time found, will fetch all unresolved issues");
            return null;
        }

        try {
            String content = Files.readString(path).trim();
            LocalDateTime lastSync = LocalDateTime.parse(content, DATE_FORMATTER);
            log.info("Last sync time: {}", lastSync.format(DATE_FORMATTER));
            return lastSync;
        } catch (Exception e) {
            log.warn("Failed to read last sync time: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Save the current sync time to file
     */
    private void saveLastSyncTime(Instant syncTime) {
        try {
            LocalDateTime localTime = LocalDateTime.ofInstant(syncTime, ZoneId.systemDefault());
            Files.writeString(Paths.get(LAST_SYNC_FILE), localTime.format(DATE_FORMATTER));
            log.debug("Saved last sync time: {}", localTime.format(DATE_FORMATTER));
        } catch (IOException e) {
            log.error("Failed to save last sync time: {}", e.getMessage());
        }
    }

    /**
     * Save issues to CSV file
     */
    private String saveToCsv(List<Issue> issues) throws IOException {
        Path csvPath = Paths.get(CSV_OUTPUT_FILE);

        try (PrintWriter writer = new PrintWriter(new FileWriter(csvPath.toFile()))) {
            // Write header
            writer.println("Key,Summary,Priority,Status,Assignee,Created Date,Updated Date,Due Date");

            // Write each issue
            for (Issue issue : issues) {
                writer.println(formatIssueCsvRow(issue));
            }
        }

        log.info("Saved {} issues to {}", issues.size(), csvPath.toAbsolutePath());
        return csvPath.toAbsolutePath().toString();
    }

    /**
     * Format a single issue as a CSV row
     */
    private String formatIssueCsvRow(Issue issue) {
        StringBuilder row = new StringBuilder();

        // Key
        row.append(escapeCsv(issue.getKey())).append(",");

        // Summary
        row.append(escapeCsv(issue.getSummary())).append(",");

        // Priority
        String priority = issue.getPriority() != null ? issue.getPriority().getName() : "";
        row.append(escapeCsv(priority)).append(",");

        // Status
        String status = issue.getStatus() != null ? issue.getStatus().getName() : "";
        row.append(escapeCsv(status)).append(",");

        // Assignee
        String assignee = issue.getAssignee() != null ? issue.getAssignee().getDisplayName() : "Unassigned";
        row.append(escapeCsv(assignee)).append(",");

        // Created Date
        String createdDate = formatJiraDate(issue.getCreationDate());
        row.append(escapeCsv(createdDate)).append(",");

        // Updated Date
        String updatedDate = formatJiraDate(issue.getUpdateDate());
        row.append(escapeCsv(updatedDate)).append(",");

        // Due Date
        String dueDate = formatJiraDate(issue.getDueDate());
        row.append(escapeCsv(dueDate));

        return row.toString();
    }

    /**
     * Format Jira DateTime to string
     */
    private String formatJiraDate(org.joda.time.DateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        LocalDateTime localDate = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(dateTime.getMillis()),
                ZoneId.systemDefault()
        );
        return localDate.format(DATE_FORMATTER);
    }

    /**
     * Escape a value for CSV (handle commas, quotes, newlines)
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // If value contains comma, quote, or newline, wrap in quotes and escape existing quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Ensure the data directory exists
     */
    private void ensureDataDirectoryExists() {
        try {
            Files.createDirectories(Paths.get("data"));
        } catch (IOException e) {
            log.warn("Failed to create data directory: {}", e.getMessage());
        }
    }
}
