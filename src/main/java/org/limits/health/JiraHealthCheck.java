package org.limits.health;

import com.codahale.metrics.health.HealthCheck;
import org.limits.client.JiraClient;

public class JiraHealthCheck extends HealthCheck {

    private final JiraClient jiraClient;

    public JiraHealthCheck(JiraClient jiraClient) {
        this.jiraClient = jiraClient;
    }

    @Override
    protected Result check() {
        try {
            if (jiraClient.testConnection()) {
                return Result.healthy("Successfully connected to Jira");
            } else {
                return Result.unhealthy("Unable to connect to Jira");
            }
        } catch (Exception e) {
            return Result.unhealthy("Jira connection failed: " + e.getMessage());
        }
    }
}
