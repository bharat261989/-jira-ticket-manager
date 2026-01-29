package org.limits.task.impl;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueLink;
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

/**
 * Task that syncs unresolved issues from Jira for the configured base project.
 *
 * Features:
 * - Tracks last sync time in a file to fetch only updated issues
 * - Saves synced issues to a CSV file incrementally
 * - Filters: project = baseProject AND resolution = Unresolved
 * - Excludes tickets with labels: anchore, SecurityCentral
 * - Optional: skip tickets before a minimum ticket number
 */
public class IssueSyncTask extends AbstractBackgroundTask<IssueSyncTaskConfig> {

    private static final String TASK_ID = "issue-sync";
    private static final String TASK_NAME = "Issue Sync Task";
    private static final String LAST_SYNC_FILE = "data/last-sync-time.txt";
    private static final String CSV_OUTPUT_FILE = "data/synced-issues.csv";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter JQL_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ANSI escape codes for styling
    private static final String BOLD = "\u001B[1m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";
    private static final String GREEN = "\u001B[32m";
    private static final String RESET = "\u001B[0m";

    private final JiraClient jiraClient;
    private final String baseProject;

    // CSV writer for incremental writing
    private PrintWriter csvWriter;
    private Path csvPath;

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
        int minTicketNumber = config.getMinTicketNumber();

        // Read last sync time
        LocalDateTime lastSyncTime = readLastSyncTime();

        // Build JQL query
        String jql = buildJqlQuery(lastSyncTime, minTicketNumber);

        log.info("Starting issue sync for project {} with JQL: {} (batch size: {})",
                baseProject, jql, batchSize);

        int totalProcessed = 0;
        int startAt = 0;
        int newIssues = 0;
        int updatedIssues = 0;
        int skippedIssues = 0;

        try {
            // Initialize CSV file with header
            initializeCsvFile();

            while (true) {
                SearchResult result = jiraClient.searchIssues(jql, startAt, batchSize);
                int fetched = 0;

                for (Issue issue : result.getIssues()) {
                    // Check if ticket number is below minimum (additional safety check)
                    if (minTicketNumber > 0 && getTicketNumber(issue.getKey()) < minTicketNumber) {
                        skippedIssues++;
                        log.debug("Skipping {} (below minimum ticket number {})",
                                issue.getKey(), minTicketNumber);
                        fetched++;
                        continue;
                    }

                    // Write to CSV immediately
                    appendToCsv(issue);

                    // Determine if new or updated based on creation date vs last sync
                    if (lastSyncTime == null || isNewIssue(issue, lastSyncTime)) {
                        newIssues++;
                        printNewTicketNotification(issue);
                    } else {
                        updatedIssues++;
                    }

                    fetched++;
                    totalProcessed++;
                }

                log.debug("Fetched {} issues (total processed: {}, skipped: {})",
                        fetched, totalProcessed, skippedIssues);

                if (fetched < batchSize || (totalProcessed + skippedIssues) >= result.getTotal()) {
                    break;
                }

                startAt += batchSize;
            }

            // Close CSV file
            closeCsvFile();

            // Update last sync time
            saveLastSyncTime(startTime);

            log.info("Issue sync completed. Total: {}, New: {}, Updated: {}, Skipped: {}. CSV saved to: {}",
                    totalProcessed, newIssues, updatedIssues, skippedIssues, csvPath.toAbsolutePath());

            return TaskResult.builder(getTaskId())
                    .success()
                    .message(String.format("Synced %d issues (%d new, %d updated, %d skipped)",
                            totalProcessed, newIssues, updatedIssues, skippedIssues))
                    .startTime(startTime)
                    .endTime(Instant.now())
                    .addMetadata("totalIssues", totalProcessed)
                    .addMetadata("newIssues", newIssues)
                    .addMetadata("updatedIssues", updatedIssues)
                    .addMetadata("skippedIssues", skippedIssues)
                    .addMetadata("csvFile", csvPath.toAbsolutePath().toString())
                    .addMetadata("project", baseProject)
                    .build();

        } catch (Exception e) {
            log.error("Issue sync failed", e);
            closeCsvFile();
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
     * Excludes tickets with labels: anchore, SecurityCentral
     * If lastSyncTime is available, only fetch issues updated since then.
     * If minTicketNumber is set, only fetch issues with key >= that number.
     */
    private String buildJqlQuery(LocalDateTime lastSyncTime, int minTicketNumber) {
        StringBuilder jql = new StringBuilder();
        jql.append("project = ").append(baseProject);
        jql.append(" AND resolution = Unresolved");

        // Exclude specific labels (but allow empty labels)
        jql.append(" AND (labels IS EMPTY OR labels NOT IN (\"anchore\", \"SecurityCentral\"))");

        if (lastSyncTime != null) {
            String formattedTime = lastSyncTime.format(JQL_DATE_FORMATTER);
            jql.append(" AND updated >= \"").append(formattedTime).append("\"");
        }

        // Filter by minimum ticket number using key comparison
        if (minTicketNumber > 0) {
            jql.append(" AND key >= ").append(baseProject).append("-").append(minTicketNumber);
        }

        jql.append(" ORDER BY key ASC");
        return jql.toString();
    }

    /**
     * Extract the numeric part of a ticket key (e.g., "PROJ-123" -> 123)
     */
    private int getTicketNumber(String issueKey) {
        if (issueKey == null || !issueKey.contains("-")) {
            return 0;
        }
        try {
            String numberPart = issueKey.substring(issueKey.lastIndexOf('-') + 1);
            return Integer.parseInt(numberPart);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Print a bold single-line notification for new tickets to stdout
     */
    private void printNewTicketNotification(Issue issue) {
        String priority = issue.getPriority() != null ? issue.getPriority().getName() : "None";
        String assignee = issue.getAssignee() != null ? issue.getAssignee().getDisplayName() : "Unassigned";
        String status = issue.getStatus() != null ? issue.getStatus().getName() : "Unknown";

        String created = formatJiraDate(issue.getCreationDate());
        String dueDate = issue.getDueDate() != null ? " | Due: " + formatJiraDate(issue.getDueDate()) : "";
        String links = formatLinkedIssues(issue);
        String linksDisplay = links.isEmpty() ? "" : " | Links: " + links;

        String summary = sanitizeForConsole(issue.getSummary());

        System.out.println(BOLD + YELLOW + "🆕 NEW " + RESET
                + BOLD + "[" + issue.getKey() + "]" + RESET
                + " " + summary
                + CYAN + " | " + priority + " | " + status + " | " + assignee
                + " | Created: " + created + dueDate + linksDisplay + RESET);
    }

    /**
     * Format linked issues as a compact string.
     * Example: "caused by NOC-789, blocks LM-456"
     */
    private String formatLinkedIssues(Issue issue) {
        Iterable<IssueLink> links = issue.getIssueLinks();
        if (links == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (IssueLink link : links) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            String linkType = link.getIssueLinkType() != null
                    ? link.getIssueLinkType().getDescription()
                    : "linked to";
            sb.append(linkType).append(" ").append(link.getTargetIssueKey());
        }
        return sb.toString();
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
     * Initialize CSV file with header
     */
    private void initializeCsvFile() throws IOException {
        csvPath = Paths.get(CSV_OUTPUT_FILE);
        csvWriter = new PrintWriter(new BufferedWriter(new FileWriter(csvPath.toFile())));
        csvWriter.println("Key,Summary,Priority,Status,Assignee,Created Date,Updated Date,Due Date,Linked Issues");
        csvWriter.flush();
        log.debug("Initialized CSV file: {}", csvPath.toAbsolutePath());
    }

    /**
     * Append a single issue to the CSV file
     */
    private void appendToCsv(Issue issue) {
        if (csvWriter != null) {
            csvWriter.println(formatIssueCsvRow(issue));
            csvWriter.flush();
        }
    }

    /**
     * Close the CSV file
     */
    private void closeCsvFile() {
        if (csvWriter != null) {
            csvWriter.close();
            csvWriter = null;
        }
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
        row.append(escapeCsv(dueDate)).append(",");

        // Linked Issues
        row.append(escapeCsv(formatLinkedIssues(issue)));

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
     * Sanitize text for single-line console output by replacing newlines and
     * other control characters with spaces.
     */
    private String sanitizeForConsole(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[\\r\\n\\t]+", " ").trim();
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
