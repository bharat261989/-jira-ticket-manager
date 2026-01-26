package org.limits.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.core.Configuration;
import org.limits.task.TaskConfig;

public class JiraConfiguration extends Configuration {

    @JsonProperty("jira")
    private JiraConfig jira = new JiraConfig();

    @JsonProperty("tasks")
    private TasksConfig tasks = new TasksConfig();

    public JiraConfig getJira() {
        return jira;
    }

    public void setJira(JiraConfig jira) {
        this.jira = jira;
    }

    public TasksConfig getTasks() {
        return tasks;
    }

    public void setTasks(TasksConfig tasks) {
        this.tasks = tasks;
    }

    /**
     * Configuration for background tasks
     */
    public static class TasksConfig {

        @JsonProperty
        private int schedulerPoolSize = 4;

        @JsonProperty
        private int onDemandPoolSize = 2;

        @JsonProperty
        private IssueSyncTaskConfig issueSync = new IssueSyncTaskConfig();

        @JsonProperty
        private StaleIssueCleanupTaskConfig staleIssueCleanup = new StaleIssueCleanupTaskConfig();

        public int getSchedulerPoolSize() {
            return schedulerPoolSize;
        }

        public void setSchedulerPoolSize(int schedulerPoolSize) {
            this.schedulerPoolSize = schedulerPoolSize;
        }

        public int getOnDemandPoolSize() {
            return onDemandPoolSize;
        }

        public void setOnDemandPoolSize(int onDemandPoolSize) {
            this.onDemandPoolSize = onDemandPoolSize;
        }

        public IssueSyncTaskConfig getIssueSync() {
            return issueSync;
        }

        public void setIssueSync(IssueSyncTaskConfig issueSync) {
            this.issueSync = issueSync;
        }

        public StaleIssueCleanupTaskConfig getStaleIssueCleanup() {
            return staleIssueCleanup;
        }

        public void setStaleIssueCleanup(StaleIssueCleanupTaskConfig staleIssueCleanup) {
            this.staleIssueCleanup = staleIssueCleanup;
        }
    }

    /**
     * Configuration for issue sync task
     */
    public static class IssueSyncTaskConfig extends TaskConfig {

        @JsonProperty
        private String jqlFilter = "project IS NOT EMPTY AND updated >= -1d";

        @JsonProperty
        private int batchSize = 100;

        public IssueSyncTaskConfig() {
            setIntervalMinutes(30);
        }

        public String getJqlFilter() {
            return jqlFilter;
        }

        public void setJqlFilter(String jqlFilter) {
            this.jqlFilter = jqlFilter;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }

    /**
     * Configuration for stale issue cleanup task
     */
    public static class StaleIssueCleanupTaskConfig extends TaskConfig {

        @JsonProperty
        private int staleDays = 30;

        @JsonProperty
        private String targetStatus = "Closed";

        @JsonProperty
        private boolean dryRun = true;

        public StaleIssueCleanupTaskConfig() {
            setEnabled(false);  // Disabled by default
            setIntervalMinutes(1440);  // Once a day
        }

        public int getStaleDays() {
            return staleDays;
        }

        public void setStaleDays(int staleDays) {
            this.staleDays = staleDays;
        }

        public String getTargetStatus() {
            return targetStatus;
        }

        public void setTargetStatus(String targetStatus) {
            this.targetStatus = targetStatus;
        }

        public boolean isDryRun() {
            return dryRun;
        }

        public void setDryRun(boolean dryRun) {
            this.dryRun = dryRun;
        }
    }

    public static class JiraConfig {

        @JsonProperty
        private String baseUrl;

        /**
         * Primary project key that tasks operate on by default.
         */
        @JsonProperty
        private String baseProject;

        @JsonProperty
        private String username;

        @JsonProperty
        private String apiToken;

        /**
         * Override for API token - takes precedence over apiToken if set.
         * Useful for injecting credentials via environment variables.
         */
        @JsonProperty
        private String apiTokenOverride;

        @JsonProperty
        private int connectionTimeout = 5000;

        @JsonProperty
        private int readTimeout = 30000;

        @JsonProperty
        private int socketTimeout = 30000;

        @JsonProperty
        private int maxConnections = 20;

        /**
         * Whether to validate Jira connectivity on startup.
         * Can be overridden by VALIDATE_ON_STARTUP env var.
         */
        @JsonProperty
        private boolean validateOnStartup = false;

        /**
         * Sample issue number to validate (e.g., 123 for PROJECT-123)
         */
        @JsonProperty
        private int sampleIssueNumber = 123;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getBaseProject() {
            return baseProject;
        }

        public void setBaseProject(String baseProject) {
            this.baseProject = baseProject;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        /**
         * Returns the effective API token, preferring the override if set.
         */
        public String getApiToken() {
            if (apiTokenOverride != null && !apiTokenOverride.isBlank()) {
                return apiTokenOverride;
            }
            return apiToken;
        }

        public void setApiToken(String apiToken) {
            this.apiToken = apiToken;
        }

        public String getApiTokenOverride() {
            return apiTokenOverride;
        }

        public void setApiTokenOverride(String apiTokenOverride) {
            this.apiTokenOverride = apiTokenOverride;
        }

        public int getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        public int getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(int readTimeout) {
            this.readTimeout = readTimeout;
        }

        public int getSocketTimeout() {
            return socketTimeout;
        }

        public void setSocketTimeout(int socketTimeout) {
            this.socketTimeout = socketTimeout;
        }

        public int getMaxConnections() {
            return maxConnections;
        }

        public void setMaxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
        }

        public boolean isValidateOnStartup() {
            return validateOnStartup;
        }

        public void setValidateOnStartup(boolean validateOnStartup) {
            this.validateOnStartup = validateOnStartup;
        }

        public int getSampleIssueNumber() {
            return sampleIssueNumber;
        }

        public void setSampleIssueNumber(int sampleIssueNumber) {
            this.sampleIssueNumber = sampleIssueNumber;
        }
    }
}
