package org.limits.task;

public interface BackgroundTask<T extends TaskConfig> {

    /**
     * Unique identifier for this task
     */
    String getTaskId();

    /**
     * Human-readable name for this task
     */
    String getTaskName();

    /**
     * Category this task belongs to
     */
    TaskCategory getCategory();

    /**
     * Get the configuration for this task
     */
    T getConfig();

    /**
     * Execute the task
     * @return TaskResult containing execution details
     */
    TaskResult execute();

    /**
     * Check if this task is currently enabled
     */
    default boolean isEnabled() {
        return getConfig() != null && getConfig().isEnabled();
    }

    /**
     * Get the interval in minutes between executions
     */
    default int getIntervalMinutes() {
        return getConfig() != null ? getConfig().getIntervalMinutes() : 60;
    }

    /**
     * Get the initial delay in minutes before first execution
     */
    default int getInitialDelayMinutes() {
        return getConfig() != null ? getConfig().getInitialDelayMinutes() : 1;
    }
}
