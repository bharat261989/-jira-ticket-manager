package org.limits.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public abstract class AbstractBackgroundTask<T extends TaskConfig> implements BackgroundTask<T> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final T config;

    protected AbstractBackgroundTask(T config) {
        this.config = config;
    }

    @Override
    public T getConfig() {
        return config;
    }

    @Override
    public TaskResult execute() {
        Instant startTime = Instant.now();
        String taskId = getTaskId();

        log.info("Starting task: {} ({})", getTaskName(), taskId);

        if (!isEnabled()) {
            log.info("Task {} is disabled, skipping", taskId);
            return TaskResult.builder(taskId)
                    .skipped()
                    .message("Task is disabled")
                    .startTime(startTime)
                    .endTime(Instant.now())
                    .build();
        }

        try {
            TaskResult result = doExecute(startTime);
            log.info("Task {} completed with status: {}", taskId, result.getStatus());
            return result;
        } catch (Exception e) {
            log.error("Task {} failed with exception", taskId, e);
            return TaskResult.builder(taskId)
                    .failure()
                    .message("Exception: " + e.getMessage())
                    .startTime(startTime)
                    .endTime(Instant.now())
                    .build();
        }
    }

    /**
     * Implement the actual task logic here.
     * @param startTime the time the task started
     * @return TaskResult with execution details
     */
    protected abstract TaskResult doExecute(Instant startTime);
}
