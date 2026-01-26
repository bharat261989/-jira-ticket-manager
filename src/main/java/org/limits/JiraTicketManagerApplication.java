package org.limits;

import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import org.limits.api.TaskResource;
import org.limits.api.TicketResource;
import org.limits.client.JiraClient;
import org.limits.config.JiraConfiguration;
import org.limits.config.JiraConfiguration.TasksConfig;
import org.limits.health.JiraHealthCheck;
import org.limits.task.TaskScheduler;
import org.limits.task.impl.IssueSyncTask;
import org.limits.task.impl.StaleIssueCleanupTask;
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
        // Enable environment variable substitution in configuration
        bootstrap.setConfigurationSourceProvider(
                new SubstitutingSourceProvider(
                        bootstrap.getConfigurationSourceProvider(),
                        new EnvironmentVariableSubstitutor(false)
                )
        );
    }

    @Override
    public void run(JiraConfiguration configuration, Environment environment) {
        LOG.info("Starting Jira Ticket Manager Application");
        LOG.info("Connecting to Jira at: {}", configuration.getJira().getBaseUrl());

        // Create Jira client
        final JiraClient jiraClient = new JiraClient(configuration.getJira());

        // Create task scheduler
        TasksConfig tasksConfig = configuration.getTasks();
        final TaskScheduler taskScheduler = new TaskScheduler(
                tasksConfig.getSchedulerPoolSize(),
                tasksConfig.getOnDemandPoolSize()
        );

        // Register background tasks
        registerTasks(taskScheduler, tasksConfig, jiraClient);

        // Register health check
        environment.healthChecks().register("jira", new JiraHealthCheck(jiraClient));

        // Register resources
        environment.jersey().register(new TicketResource(jiraClient));
        environment.jersey().register(new TaskResource(taskScheduler));

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

    private void registerTasks(TaskScheduler scheduler, TasksConfig config, JiraClient jiraClient) {
        // Register Issue Sync Task
        scheduler.registerTask(new IssueSyncTask(config.getIssueSync(), jiraClient));

        // Register Stale Issue Cleanup Task
        scheduler.registerTask(new StaleIssueCleanupTask(config.getStaleIssueCleanup(), jiraClient));

        // Add more tasks here as needed
        LOG.info("Registered {} background tasks", scheduler.getAllTasks().size());
    }
}
