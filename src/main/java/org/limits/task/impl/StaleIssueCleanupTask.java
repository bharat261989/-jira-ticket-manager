package org.limits.task.impl;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.api.domain.Transition;
import org.limits.client.JiraClient;
import org.limits.config.JiraConfiguration.StaleIssueCleanupTaskConfig;
import org.limits.task.AbstractBackgroundTask;
import org.limits.task.TaskCategory;
import org.limits.task.TaskResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Task that identifies and optionally transitions stale issues.
 * This task demonstrates a cleanup category task with dry-run support.
 */
public class StaleIssueCleanupTask extends AbstractBackgroundTask<StaleIssueCleanupTaskConfig> {

    private static final String TASK_ID = "stale-issue-cleanup";
    private static final String TASK_NAME = "Stale Issue Cleanup Task";

    private final JiraClient jiraClient;

    public StaleIssueCleanupTask(StaleIssueCleanupTaskConfig config, JiraClient jiraClient) {
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
    public TaskCategory getCategory() {
        return TaskCategory.CLEANUP;
    }

    @Override
    protected TaskResult doExecute(Instant startTime) {
        int staleDays = config.getStaleDays();
        String targetStatus = config.getTargetStatus();
        boolean dryRun = config.isDryRun();

        String jql = String.format("updated <= -%dd AND status != \"%s\"", staleDays, targetStatus);

        log.info("Starting stale issue cleanup (dryRun={}, staleDays={}, targetStatus={})",
                dryRun, staleDays, targetStatus);

        List<String> processedIssues = new ArrayList<>();
        List<String> failedIssues = new ArrayList<>();
        int startAt = 0;
        int batchSize = 50;

        try {
            while (true) {
                SearchResult result = jiraClient.searchIssues(jql, startAt, batchSize);
                int fetched = 0;

                for (Issue issue : result.getIssues()) {
                    fetched++;
                    String issueKey = issue.getKey();

                    if (dryRun) {
                        log.info("[DRY-RUN] Would transition {} to {}", issueKey, targetStatus);
                        processedIssues.add(issueKey);
                    } else {
                        try {
                            transitionToStatus(issue, targetStatus);
                            processedIssues.add(issueKey);
                            log.info("Transitioned {} to {}", issueKey, targetStatus);
                        } catch (Exception e) {
                            log.warn("Failed to transition {}: {}", issueKey, e.getMessage());
                            failedIssues.add(issueKey);
                        }
                    }
                }

                if (fetched < batchSize) {
                    break;
                }
                startAt += batchSize;
            }

            String message = dryRun
                    ? String.format("[DRY-RUN] Would process %d stale issues", processedIssues.size())
                    : String.format("Processed %d stale issues (%d failed)", processedIssues.size(), failedIssues.size());

            log.info("Stale issue cleanup completed: {}", message);

            return TaskResult.builder(getTaskId())
                    .status(failedIssues.isEmpty() ? TaskResult.Status.SUCCESS : TaskResult.Status.FAILURE)
                    .message(message)
                    .startTime(startTime)
                    .endTime(Instant.now())
                    .addMetadata("processedCount", processedIssues.size())
                    .addMetadata("failedCount", failedIssues.size())
                    .addMetadata("dryRun", dryRun)
                    .addMetadata("staleDays", staleDays)
                    .addMetadata("targetStatus", targetStatus)
                    .build();

        } catch (Exception e) {
            log.error("Stale issue cleanup failed", e);
            return TaskResult.builder(getTaskId())
                    .failure()
                    .message("Cleanup failed: " + e.getMessage())
                    .startTime(startTime)
                    .endTime(Instant.now())
                    .addMetadata("error", e.getClass().getSimpleName())
                    .build();
        }
    }

    private void transitionToStatus(Issue issue, String targetStatus) {
        Iterable<Transition> transitions = jiraClient.getTransitions(issue.getKey());

        for (Transition transition : transitions) {
            // Match transition by name (e.g., "Close", "Done", "Resolve")
            if (transition.getName().equalsIgnoreCase(targetStatus)) {
                jiraClient.transitionIssue(issue.getKey(), transition.getId());
                return;
            }
        }

        throw new IllegalStateException("No transition found to status: " + targetStatus);
    }
}
