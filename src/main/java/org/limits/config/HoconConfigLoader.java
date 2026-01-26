package org.limits.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.limits.config.JiraConfiguration.IssueSyncTaskConfig;
import org.limits.config.JiraConfiguration.JiraConfig;
import org.limits.config.JiraConfiguration.StaleIssueCleanupTaskConfig;
import org.limits.config.JiraConfiguration.TasksConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Loads application configuration from HOCON files.
 *
 * Load order (later overrides earlier):
 * 1. application.conf (from classpath)
 * 2. user.conf (from classpath) - for local overrides
 * 3. System properties (-Dkey=value)
 * 4. Environment variables
 */
public class HoconConfigLoader {

    private static final Logger LOG = LoggerFactory.getLogger(HoconConfigLoader.class);

    private final Config config;

    public HoconConfigLoader() {
        // Load order: user.conf overrides application.conf
        Config appConfig = ConfigFactory.parseResources("application.conf");
        Config userConfig = ConfigFactory.parseResources("user.conf");

        // user.conf -> application.conf -> system properties -> env vars
        this.config = ConfigFactory.systemEnvironment()
                .withFallback(ConfigFactory.systemProperties())
                .withFallback(userConfig)
                .withFallback(appConfig)
                .resolve();

        LOG.info("Loaded HOCON configuration (application.conf + user.conf)");
    }

    public HoconConfigLoader(String configFile) {
        // Load from specific file with standard fallbacks
        Config fileConfig = ConfigFactory.parseFile(new File(configFile));
        Config appConfig = ConfigFactory.parseResources("application.conf");
        Config userConfig = ConfigFactory.parseResources("user.conf");

        this.config = ConfigFactory.systemEnvironment()
                .withFallback(ConfigFactory.systemProperties())
                .withFallback(fileConfig)
                .withFallback(userConfig)
                .withFallback(appConfig)
                .resolve();

        LOG.info("Loaded HOCON configuration from: {}", configFile);
    }

    /**
     * Get the raw Typesafe Config object for advanced usage
     */
    public Config getConfig() {
        return config;
    }

    /**
     * Load Jira configuration
     */
    public JiraConfig loadJiraConfig() {
        Config jiraConf = config.getConfig("jira");

        JiraConfig jiraConfig = new JiraConfig();
        jiraConfig.setBaseUrl(jiraConf.getString("baseUrl"));
        jiraConfig.setBaseProject(jiraConf.getString("baseProject"));
        jiraConfig.setUsername(jiraConf.getString("username"));
        jiraConfig.setApiToken(jiraConf.getString("apiToken"));
        jiraConfig.setConnectionTimeout(jiraConf.getInt("connectionTimeout"));
        jiraConfig.setReadTimeout(jiraConf.getInt("readTimeout"));
        jiraConfig.setSocketTimeout(jiraConf.getInt("socketTimeout"));
        jiraConfig.setMaxConnections(jiraConf.getInt("maxConnections"));

        // Validation settings
        if (jiraConf.hasPath("validateOnStartup")) {
            jiraConfig.setValidateOnStartup(jiraConf.getBoolean("validateOnStartup"));
        }
        if (jiraConf.hasPath("sampleIssueNumber")) {
            jiraConfig.setSampleIssueNumber(jiraConf.getInt("sampleIssueNumber"));
        }

        LOG.info("Jira config: baseUrl={}, baseProject={}, username={}, validateOnStartup={}",
                jiraConfig.getBaseUrl(), jiraConfig.getBaseProject(),
                jiraConfig.getUsername(), jiraConfig.isValidateOnStartup());

        return jiraConfig;
    }

    /**
     * Load tasks configuration
     */
    public TasksConfig loadTasksConfig() {
        Config tasksConf = config.getConfig("tasks");

        TasksConfig tasksConfig = new TasksConfig();
        tasksConfig.setSchedulerPoolSize(tasksConf.getInt("schedulerPoolSize"));
        tasksConfig.setOnDemandPoolSize(tasksConf.getInt("onDemandPoolSize"));

        // Issue Sync Task
        Config issueSyncConf = tasksConf.getConfig("issueSync");
        IssueSyncTaskConfig issueSyncConfig = new IssueSyncTaskConfig();
        issueSyncConfig.setEnabled(issueSyncConf.getBoolean("enabled"));
        issueSyncConfig.setIntervalMinutes(issueSyncConf.getInt("intervalMinutes"));
        issueSyncConfig.setInitialDelayMinutes(issueSyncConf.getInt("initialDelayMinutes"));
        issueSyncConfig.setJqlFilter(issueSyncConf.getString("jqlFilter"));
        issueSyncConfig.setBatchSize(issueSyncConf.getInt("batchSize"));
        tasksConfig.setIssueSync(issueSyncConfig);

        // Stale Issue Cleanup Task
        Config staleConf = tasksConf.getConfig("staleIssueCleanup");
        StaleIssueCleanupTaskConfig staleConfig = new StaleIssueCleanupTaskConfig();
        staleConfig.setEnabled(staleConf.getBoolean("enabled"));
        staleConfig.setIntervalMinutes(staleConf.getInt("intervalMinutes"));
        staleConfig.setInitialDelayMinutes(staleConf.getInt("initialDelayMinutes"));
        staleConfig.setStaleDays(staleConf.getInt("staleDays"));
        staleConfig.setTargetStatus(staleConf.getString("targetStatus"));
        staleConfig.setDryRun(staleConf.getBoolean("dryRun"));
        tasksConfig.setStaleIssueCleanup(staleConfig);

        return tasksConfig;
    }

    /**
     * Load complete JiraConfiguration (combines all sections)
     */
    public JiraConfiguration loadFullConfiguration() {
        JiraConfiguration configuration = new JiraConfiguration();
        configuration.setJira(loadJiraConfig());
        configuration.setTasks(loadTasksConfig());
        return configuration;
    }
}
