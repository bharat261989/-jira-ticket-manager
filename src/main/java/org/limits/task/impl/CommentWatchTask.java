package org.limits.task.impl;

import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.Issue;
import org.limits.client.JiraClient;
import org.limits.config.JiraConfiguration.CommentWatchTaskConfig;
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
 * Task that monitors active issues for new comments and notifies via
 * console output and a dedicated log file.
 *
 * Reads the list of active issue keys from the synced-issues CSV,
 * fetches each issue, and checks for comments added since the last run.
 */
public class CommentWatchTask extends AbstractBackgroundTask<CommentWatchTaskConfig> {

    private static final String TASK_ID = "comment-watch";
    private static final String TASK_NAME = "Comment Watch Task";
    private static final String LAST_CHECK_FILE = "data/last-comment-check-time.txt";
    private static final String NOTIFICATION_LOG_FILE = "data/comment-notifications.log";
    private static final String CSV_INPUT_FILE = "data/synced-issues.csv";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ANSI escape codes for styling (distinct from IssueSyncTask's yellow theme)
    private static final String BOLD = "\u001B[1m";
    private static final String MAGENTA = "\u001B[35m";
    private static final String GREEN = "\u001B[32m";
    private static final String RESET = "\u001B[0m";

    private final JiraClient jiraClient;
    private final String baseProject;

    public CommentWatchTask(CommentWatchTaskConfig config, JiraClient jiraClient, String baseProject) {
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
        int maxCommentLength = config.getMaxCommentLength();

        // Read active issue keys from CSV
        List<String> issueKeys = readIssueKeysFromCsv();
        if (issueKeys.isEmpty()) {
            log.info("No active issues found in {}. Skipping comment check.", CSV_INPUT_FILE);
            return TaskResult.builder(getTaskId())
                    .success()
                    .message("No active issues to check")
                    .startTime(startTime)
                    .endTime(Instant.now())
                    .addMetadata("totalIssuesChecked", 0)
                    .addMetadata("newCommentsFound", 0)
                    .build();
        }

        // Read last check time
        Instant lastCheckTime = readLastCheckTime();

        int totalIssuesChecked = 0;
        int newCommentsFound = 0;
        int issuesWithNewComments = 0;

        try {
            for (String issueKey : issueKeys) {
                try {
                    Issue issue = jiraClient.getIssue(issueKey);
                    totalIssuesChecked++;

                    Iterable<Comment> comments = issue.getComments();
                    if (comments == null) {
                        continue;
                    }

                    int issueNewComments = 0;
                    for (Comment comment : comments) {
                        if (comment.getCreationDate() == null) {
                            continue;
                        }

                        Instant commentTime = Instant.ofEpochMilli(comment.getCreationDate().getMillis());

                        if (lastCheckTime == null || commentTime.isAfter(lastCheckTime)) {
                            String author = comment.getAuthor() != null
                                    ? comment.getAuthor().getDisplayName()
                                    : "Unknown";

                            // Skip automated comments if filtering is enabled
                            if (config.isFilterAutomatedComments() && isAutomatedAuthor(author)) {
                                log.debug("Skipping automated comment from '{}' on {}", author, issueKey);
                                continue;
                            }

                            issueNewComments++;
                            newCommentsFound++;

                            String body = comment.getBody() != null ? comment.getBody() : "";

                            printCommentNotification(issueKey, author, body);
                            appendToNotificationLog(issueKey, author, body, commentTime, maxCommentLength);
                        }
                    }

                    if (issueNewComments > 0) {
                        issuesWithNewComments++;
                    }

                } catch (Exception e) {
                    log.warn("Failed to check comments for issue {}: {}", issueKey, e.getMessage());
                }
            }

            // Save current time as last check time
            saveLastCheckTime(startTime);

            log.info("Comment watch completed. Issues checked: {}, New comments: {}, Issues with new comments: {}",
                    totalIssuesChecked, newCommentsFound, issuesWithNewComments);

            return TaskResult.builder(getTaskId())
                    .success()
                    .message(String.format("Checked %d issues, found %d new comments across %d issues",
                            totalIssuesChecked, newCommentsFound, issuesWithNewComments))
                    .startTime(startTime)
                    .endTime(Instant.now())
                    .addMetadata("totalIssuesChecked", totalIssuesChecked)
                    .addMetadata("newCommentsFound", newCommentsFound)
                    .addMetadata("issuesWithNewComments", issuesWithNewComments)
                    .addMetadata("project", baseProject)
                    .build();

        } catch (Exception e) {
            log.error("Comment watch failed", e);
            return TaskResult.builder(getTaskId())
                    .failure()
                    .message("Comment watch failed: " + e.getMessage())
                    .startTime(startTime)
                    .endTime(Instant.now())
                    .addMetadata("totalIssuesChecked", totalIssuesChecked)
                    .addMetadata("newCommentsFound", newCommentsFound)
                    .addMetadata("error", e.getClass().getSimpleName())
                    .build();
        }
    }

    /**
     * Read issue keys from the first column of the synced-issues CSV file.
     * Skips the header row.
     */
    private List<String> readIssueKeysFromCsv() {
        List<String> keys = new ArrayList<>();
        Path csvPath = Paths.get(CSV_INPUT_FILE);

        if (!Files.exists(csvPath)) {
            log.warn("CSV file not found: {}", csvPath.toAbsolutePath());
            return keys;
        }

        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue; // skip header
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                // First column is the issue key (before the first comma)
                int commaIndex = trimmed.indexOf(',');
                String key = commaIndex > 0 ? trimmed.substring(0, commaIndex) : trimmed;
                keys.add(key);
            }
        } catch (IOException e) {
            log.error("Failed to read CSV file: {}", e.getMessage());
        }

        log.debug("Read {} issue keys from CSV", keys.size());
        return keys;
    }

    /**
     * Read the last check time from file.
     * Returns null on first run or if file cannot be read.
     */
    private Instant readLastCheckTime() {
        Path path = Paths.get(LAST_CHECK_FILE);
        if (!Files.exists(path)) {
            log.info("No previous comment check time found, will fetch all comments");
            return null;
        }

        try {
            String content = Files.readString(path).trim();
            LocalDateTime localTime = LocalDateTime.parse(content, DATE_FORMATTER);
            Instant checkTime = localTime.atZone(ZoneId.systemDefault()).toInstant();
            log.info("Last comment check time: {}", localTime.format(DATE_FORMATTER));
            return checkTime;
        } catch (Exception e) {
            log.warn("Failed to read last comment check time: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Save the current check time to file.
     */
    private void saveLastCheckTime(Instant checkTime) {
        try {
            LocalDateTime localTime = LocalDateTime.ofInstant(checkTime, ZoneId.systemDefault());
            Files.writeString(Paths.get(LAST_CHECK_FILE), localTime.format(DATE_FORMATTER));
            log.debug("Saved last comment check time: {}", localTime.format(DATE_FORMATTER));
        } catch (IOException e) {
            log.error("Failed to save last comment check time: {}", e.getMessage());
        }
    }

    /**
     * Print a styled console notification for a new comment.
     */
    private void printCommentNotification(String issueKey, String author, String body) {
        String snippet = truncate(body, 100);
        System.out.println(BOLD + MAGENTA + "💬 COMMENT " + RESET
                + BOLD + MAGENTA + "[" + issueKey + "]" + RESET
                + GREEN + " by " + author + RESET
                + " | " + snippet);
    }

    /**
     * Append a comment notification entry to the log file.
     */
    private void appendToNotificationLog(String issueKey, String author, String body,
                                         Instant commentTime, int maxLength) {
        LocalDateTime localTime = LocalDateTime.ofInstant(commentTime, ZoneId.systemDefault());
        String timestamp = localTime.format(DATE_FORMATTER);
        String snippet = truncate(body, maxLength);
        // Replace newlines in snippet to keep it on one line
        snippet = snippet.replace("\n", " ").replace("\r", "");

        String logLine = String.format("[%s] %s | Author: %s | Comment: %s",
                timestamp, issueKey, author, snippet);

        try (FileWriter fw = new FileWriter(NOTIFICATION_LOG_FILE, true);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.println(logLine);
        } catch (IOException e) {
            log.error("Failed to append to notification log: {}", e.getMessage());
        }
    }

    /**
     * Truncate a string to the specified length, appending "..." if truncated.
     */
    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    /**
     * Check if an author name matches any of the configured automated/bot patterns.
     * Patterns support * as a wildcard (matches any characters).
     */
    private boolean isAutomatedAuthor(String authorName) {
        if (authorName == null || authorName.isEmpty()) {
            return false;
        }

        for (String pattern : config.getAutomatedAuthorPatterns()) {
            if (matchesPattern(authorName, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Match a string against a pattern with wildcard support.
     * * matches zero or more characters.
     */
    private boolean matchesPattern(String text, String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }

        // Convert glob pattern to regex: escape special chars, replace * with .*
        String regex = pattern
                .replace(".", "\\.")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("?", "\\?")
                .replace("*", ".*");

        try {
            return text.matches("(?i)" + regex);  // Case-insensitive match
        } catch (Exception e) {
            log.warn("Invalid pattern '{}': {}", pattern, e.getMessage());
            return false;
        }
    }

    /**
     * Ensure the data directory exists.
     */
    private void ensureDataDirectoryExists() {
        try {
            Files.createDirectories(Paths.get("data"));
        } catch (IOException e) {
            log.warn("Failed to create data directory: {}", e.getMessage());
        }
    }
}
