package org.limits.task.impl;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import org.limits.client.JiraClient;
import org.limits.config.JiraConfiguration.IssueSyncTaskConfig;
import org.limits.task.AbstractBackgroundTask;
import org.limits.task.TaskResult;

import java.time.Instant;

/**
 * Task that syncs issues from Jira based on a JQL filter.
 * This is an example task that demonstrates how to implement a background task.
 */
public class IssueSyncTask extends AbstractBackgroundTask<IssueSyncTaskConfig> {

    private static final String TASK_ID = "issue-sync";
    private static final String TASK_NAME = "Issue Sync Task";

    private final JiraClient jiraClient;

    public IssueSyncTask(IssueSyncTaskConfig config, JiraClient jiraClient) {
        super(config);
        this.jiraClient = jiraClient;
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
        String jql = config.getJqlFilter();
        int batchSize = config.getBatchSize();

        log.info("Starting issue sync with JQL: {} (batch size: {})", jql, batchSize);

        int totalProcessed = 0;
        int startAt = 0;

        try {
            while (true) {
                SearchResult result = jiraClient.searchIssues(jql, startAt, batchSize);
                int fetched = 0;

                for (Issue issue : result.getIssues()) {
                    processIssue(issue);
                    fetched++;
                    totalProcessed++;
                }

                log.debug("Processed {} issues (total: {})", fetched, totalProcessed);

                if (fetched < batchSize || totalProcessed >= result.getTotal()) {
                    break;
                }

                startAt += batchSize;
            }

            log.info("Issue sync completed. Processed {} issues", totalProcessed);

            return TaskResult.builder(getTaskId())
                    .success()
                    .message("Synced " + totalProcessed + " issues")
                    .startTime(startTime)
                    .endTime(Instant.now())
                    .addMetadata("issuesProcessed", totalProcessed)
                    .addMetadata("jqlFilter", jql)
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
     * Process a single issue. Override this method to implement custom sync logic.
     */
    protected void processIssue(Issue issue) {
        // Default implementation just logs the issue
        // Subclasses can override to persist to database, send notifications, etc.
        log.debug("Processing issue: {} - {}", issue.getKey(), issue.getSummary());
    }
}
