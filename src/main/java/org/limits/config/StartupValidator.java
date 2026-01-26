package org.limits.config;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.Project;
import org.limits.client.JiraClient;
import org.limits.config.JiraConfiguration.JiraConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates Jira connectivity and configuration at startup.
 *
 * Enable validation by setting:
 * - Environment variable: VALIDATE_ON_STARTUP=true
 * - Or in config: jira.validateOnStartup = true
 *
 * Validation checks:
 * 1. Jira server is reachable
 * 2. Configured project exists
 * 3. Sample issue (project-123) exists (optional)
 */
public class StartupValidator {

    private static final Logger LOG = LoggerFactory.getLogger(StartupValidator.class);

    private final JiraClient jiraClient;
    private final JiraConfig jiraConfig;
    private final boolean validateSampleIssue;
    private final int sampleIssueNumber;

    public StartupValidator(JiraClient jiraClient, JiraConfig jiraConfig) {
        this(jiraClient, jiraConfig, true, 123);
    }

    public StartupValidator(JiraClient jiraClient, JiraConfig jiraConfig,
                           boolean validateSampleIssue, int sampleIssueNumber) {
        this.jiraClient = jiraClient;
        this.jiraConfig = jiraConfig;
        this.validateSampleIssue = validateSampleIssue;
        this.sampleIssueNumber = sampleIssueNumber;
    }

    /**
     * Run all validation checks.
     * @throws StartupValidationException if any validation fails
     */
    public void validate() throws StartupValidationException {
        LOG.info("Running startup validation...");

        validateConnection();
        validateProject();

        if (validateSampleIssue) {
            validateSampleIssue();
        }

        LOG.info("Startup validation completed successfully");
    }

    /**
     * Validate Jira server connectivity
     */
    private void validateConnection() throws StartupValidationException {
        LOG.info("Validating Jira connection to: {}", jiraConfig.getBaseUrl());

        if (!jiraClient.testConnection()) {
            throw new StartupValidationException(
                    "Failed to connect to Jira server at: " + jiraConfig.getBaseUrl() +
                    ". Please check the URL and credentials.");
        }

        LOG.info("✓ Jira connection successful");
    }

    /**
     * Validate the configured project exists
     */
    private void validateProject() throws StartupValidationException {
        String projectKey = jiraConfig.getBaseProject();
        LOG.info("Validating project exists: {}", projectKey);

        try {
            Project project = jiraClient.getRestClient()
                    .getProjectClient()
                    .getProject(projectKey)
                    .claim();

            LOG.info("✓ Project '{}' exists: {}", projectKey, project.getName());

        } catch (Exception e) {
            throw new StartupValidationException(
                    "Project '" + projectKey + "' not found or not accessible. " +
                    "Please check the baseProject configuration. Error: " + e.getMessage());
        }
    }

    /**
     * Validate a sample issue exists in the project
     */
    private void validateSampleIssue() throws StartupValidationException {
        String issueKey = jiraConfig.getBaseProject() + "-" + sampleIssueNumber;
        LOG.info("Validating sample issue exists: {}", issueKey);

        try {
            Issue issue = jiraClient.getIssue(issueKey);
            LOG.info("✓ Sample issue '{}' exists: {}", issueKey, issue.getSummary());

        } catch (JiraClient.JiraClientException e) {
            // Sample issue validation is a warning, not a hard failure
            LOG.warn("⚠ Sample issue '{}' not found. This is not critical, but you may want to " +
                    "verify the project has issues. Error: {}", issueKey, e.getMessage());
        }
    }

    /**
     * Check if validation should run based on environment variable or system property
     */
    public static boolean shouldValidate() {
        // Check environment variable
        String envValue = System.getenv("VALIDATE_ON_STARTUP");
        if (envValue != null) {
            return Boolean.parseBoolean(envValue);
        }

        // Check system property (can be passed as -DVALIDATE_ON_STARTUP=true)
        String propValue = System.getProperty("VALIDATE_ON_STARTUP");
        if (propValue != null) {
            return Boolean.parseBoolean(propValue);
        }

        return false;
    }

    /**
     * Check if running in production mode
     */
    public static boolean isProductionMode() {
        String env = System.getenv("APP_ENV");
        if (env == null) {
            env = System.getProperty("APP_ENV", "development");
        }
        return "production".equalsIgnoreCase(env) || "prod".equalsIgnoreCase(env);
    }

    public static class StartupValidationException extends Exception {
        public StartupValidationException(String message) {
            super(message);
        }

        public StartupValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
