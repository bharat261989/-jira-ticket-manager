package org.limits.task.impl;

import org.limits.client.ConfluenceClient;
import org.limits.config.ConfluenceConfig;
import org.limits.task.AbstractBackgroundTask;
import org.limits.task.TaskConfig;
import org.limits.task.TaskResult;

import java.time.Instant;

/**
 * Admin / on-demand task that creates a test page in the configured Confluence space
 * to verify Confluence connectivity and permissions.
 */
public class ConfluenceTestPageTask extends AbstractBackgroundTask<TaskConfig> {

    private static final String TASK_ID = "confluence-test-page";
    private static final String TASK_NAME = "Confluence test page (create page in configured space)";

    private final ConfluenceClient confluenceClient;
    private final ConfluenceConfig confluenceConfig;

    public ConfluenceTestPageTask(ConfluenceClient confluenceClient, ConfluenceConfig confluenceConfig) {
        super(createConfig());
        this.confluenceClient = confluenceClient;
        this.confluenceConfig = confluenceConfig;
    }

    private static TaskConfig createConfig() {
        TaskConfig c = new TaskConfig();
        c.setEnabled(true);
        c.setIntervalMinutes(10080); // 7 days – effectively schedule-only; run via API for testing
        c.setInitialDelayMinutes(60);
        return c;
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
        String spaceKey = confluenceConfig.getDefaultSpaceKey();
        String title = "Jira Ticket Manager – Confluence test page";
        String bodyHtml = "<p>This page was created by the <strong>Jira Ticket Manager</strong> admin task <code>confluence-test-page</code>.</p>"
                + "<p>If you see this, Confluence connectivity and the configured space (<strong>" + escape(spaceKey) + "</strong>) are working.</p>"
                + "<p><em>You can delete this page if it was only used for testing.</em></p>";

        ConfluenceClient.CreatePageResult result = confluenceClient.createPage(spaceKey, title, bodyHtml);

        return TaskResult.builder(getTaskId())
                .success()
                .message("Test page created in space " + spaceKey)
                .startTime(startTime)
                .endTime(Instant.now())
                .addMetadata("pageId", result.getPageId())
                .addMetadata("pageUrl", result.getPageUrl())
                .addMetadata("spaceKey", spaceKey)
                .build();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
