package org.limits;

import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import org.limits.api.ReportResource;
import org.limits.api.TaskResource;
import org.limits.api.TicketResource;
import org.limits.client.ConfluenceClient;
import org.limits.client.JiraClient;
import org.limits.config.ConfluenceConfig;
import org.limits.config.HoconConfigLoader;
import org.limits.config.JiraConfiguration;
import org.limits.config.JiraConfiguration.JiraConfig;
import org.limits.config.JiraConfiguration.TasksConfig;
import org.limits.config.StartupValidator;
import org.limits.health.JiraHealthCheck;
import org.limits.task.TaskScheduler;
import org.limits.task.impl.IssueSyncTask;
import org.limits.task.impl.CommentWatchTask;
import org.limits.task.impl.StaleIssueCleanupTask;
import org.limits.task.impl.ConfluenceTestPageTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JiraTicketManagerApplication extends Application<JiraConfiguration> {

    private static final Logger LOG = LoggerFactory.getLogger(JiraTicketManagerApplication.class);

    public static void main(String[] args) throws Exception {
        new JiraTicketManagerApplication().run(args);
    }

    @Override
    public String getName() {
        return "jira-ticket-manager";
    }

    @Override
    public void initialize(Bootstrap<JiraConfiguration> bootstrap) {
        // Using default YAML config for Dropwizard bootstrap
        // Application config is loaded via HoconConfigLoader
    }

    @Override
    public void run(JiraConfiguration configuration, Environment environment) {
        LOG.info("Starting Jira Ticket Manager Application");

        // Load application config from HOCON (application.conf + user.conf)
        HoconConfigLoader hoconLoader = new HoconConfigLoader();
        JiraConfig jiraConfig = hoconLoader.loadJiraConfig();
        TasksConfig tasksConfig = hoconLoader.loadTasksConfig();
        ConfluenceConfig confluenceConfig = hoconLoader.loadConfluenceConfig();

        LOG.info("Connecting to Jira at: {} (project: {})",
                jiraConfig.getBaseUrl(), jiraConfig.getBaseProject());

        // Create Jira client
        final JiraClient jiraClient = new JiraClient(jiraConfig);

        // Run startup validation if enabled
        runStartupValidation(jiraClient, jiraConfig);

        // Create task scheduler
        final TaskScheduler taskScheduler = new TaskScheduler(
                tasksConfig.getSchedulerPoolSize(),
                tasksConfig.getOnDemandPoolSize()
        );

        // Register background tasks
        registerTasks(taskScheduler, tasksConfig, jiraClient, jiraConfig);

        // Register health check
        environment.healthChecks().register("jira", new JiraHealthCheck(jiraClient));

        // Register resources
        environment.jersey().register(new TicketResource(jiraClient));
        environment.jersey().register(new TaskResource(taskScheduler));

        if (confluenceConfig != null) {
            ConfluenceClient confluenceClient = new ConfluenceClient(confluenceConfig);
            environment.jersey().register(new ReportResource(jiraClient, confluenceClient, jiraConfig, confluenceConfig));
            taskScheduler.registerTask(new ConfluenceTestPageTask(confluenceClient, confluenceConfig));
            LOG.info("Confluence report API and confluence-test-page task enabled");
        } else {
            LOG.info("Confluence not configured; report API disabled");
        }

        // Register managed lifecycle components
        environment.lifecycle().manage(taskScheduler);
        environment.lifecycle().manage(new io.dropwizard.lifecycle.Managed() {
            @Override
            public void start() {
                LOG.info("Jira Ticket Manager started");
            }

            @Override
            public void stop() throws Exception {
                LOG.info("Shutting down Jira client");
                jiraClient.close();
            }
        });

        LOG.info("Jira Ticket Manager Application initialized successfully");
    }

    private void registerTasks(TaskScheduler scheduler, TasksConfig tasksConfig,
                               JiraClient jiraClient, JiraConfig jiraConfig) {
        // Register Issue Sync Task
        scheduler.registerTask(new IssueSyncTask(
                tasksConfig.getIssueSync(),
                jiraClient,
                jiraConfig.getBaseProject()
        ));

        // Register Stale Issue Cleanup Task
        scheduler.registerTask(new StaleIssueCleanupTask(tasksConfig.getStaleIssueCleanup(), jiraClient));

        // Register Comment Watch Task
        scheduler.registerTask(new CommentWatchTask(
                tasksConfig.getCommentWatch(),
                jiraClient,
                jiraConfig.getBaseProject()
        ));
        LOG.info("Registered {} background tasks for project: {}",
                scheduler.getAllTasks().size(), jiraConfig.getBaseProject());
    }

    /**
     * Run startup validation if enabled via config or environment variable.
     */
    private void runStartupValidation(JiraClient jiraClient, JiraConfig jiraConfig) {
        boolean shouldValidate = jiraConfig.isValidateOnStartup() || StartupValidator.shouldValidate();

        if (!shouldValidate) {
            LOG.info("Startup validation is disabled. Enable with VALIDATE_ON_STARTUP=true");
            return;
        }

        LOG.info("Startup validation is enabled");

        try {
            StartupValidator validator = new StartupValidator(
                    jiraClient,
                    jiraConfig,
                    true,
                    jiraConfig.getSampleIssueNumber()
            );
            validator.validate();

        } catch (StartupValidator.StartupValidationException e) {
            LOG.error("Startup validation failed: {}", e.getMessage());

            if (StartupValidator.isProductionMode()) {
                throw new RuntimeException("Startup validation failed in production mode", e);
            } else {
                LOG.warn("Continuing despite validation failure (non-production mode)");
            }
        }
    }
}
