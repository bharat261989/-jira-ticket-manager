package org.limits.task;

import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

public class TaskScheduler implements Managed {

    private static final Logger LOG = LoggerFactory.getLogger(TaskScheduler.class);

    private final ScheduledExecutorService scheduledExecutor;
    private final ExecutorService onDemandExecutor;
    private final Map<String, BackgroundTask<?>> tasks;
    private final Map<String, ScheduledFuture<?>> scheduledFutures;
    private final Map<String, TaskResult> lastResults;
    private final Map<String, Future<TaskResult>> runningTasks;

    public TaskScheduler(int scheduledPoolSize, int onDemandPoolSize) {
        this.scheduledExecutor = Executors.newScheduledThreadPool(scheduledPoolSize, r -> {
            Thread t = new Thread(r, "task-scheduler");
            t.setDaemon(true);
            return t;
        });
        this.onDemandExecutor = Executors.newFixedThreadPool(onDemandPoolSize, r -> {
            Thread t = new Thread(r, "task-ondemand");
            t.setDaemon(true);
            return t;
        });
        this.tasks = new ConcurrentHashMap<>();
        this.scheduledFutures = new ConcurrentHashMap<>();
        this.lastResults = new ConcurrentHashMap<>();
        this.runningTasks = new ConcurrentHashMap<>();
    }

    public TaskScheduler() {
        this(4, 2);
    }

    /**
     * Register a task with the scheduler
     */
    public void registerTask(BackgroundTask<?> task) {
        String taskId = task.getTaskId();
        tasks.put(taskId, task);
        LOG.info("Registered task: {} ({})", task.getTaskName(), taskId);
    }

    /**
     * Schedule all registered and enabled tasks
     */
    public void scheduleAllTasks() {
        for (BackgroundTask<?> task : tasks.values()) {
            scheduleTask(task);
        }
    }

    /**
     * Schedule a single task
     */
    private void scheduleTask(BackgroundTask<?> task) {
        if (!task.isEnabled()) {
            LOG.info("Task {} is disabled, not scheduling", task.getTaskId());
            return;
        }

        String taskId = task.getTaskId();
        int initialDelay = task.getInitialDelayMinutes();
        int interval = task.getIntervalMinutes();

        ScheduledFuture<?> future = scheduledExecutor.scheduleAtFixedRate(
                () -> executeTask(task),
                initialDelay,
                interval,
                TimeUnit.MINUTES
        );

        scheduledFutures.put(taskId, future);
        LOG.info("Scheduled task {} to run every {} minutes (initial delay: {} minutes)",
                taskId, interval, initialDelay);
    }

    /**
     * Execute a task and store the result
     */
    private void executeTask(BackgroundTask<?> task) {
        String taskId = task.getTaskId();
        try {
            TaskResult result = task.execute();
            lastResults.put(taskId, result);
        } catch (Exception e) {
            LOG.error("Unexpected error executing task {}", taskId, e);
        }
    }

    /**
     * Run a task on-demand (async)
     * @return Future containing the TaskResult
     */
    public Future<TaskResult> runTaskAsync(String taskId) {
        BackgroundTask<?> task = tasks.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Unknown task: " + taskId);
        }

        // Check if task is already running
        Future<TaskResult> existing = runningTasks.get(taskId);
        if (existing != null && !existing.isDone()) {
            LOG.warn("Task {} is already running, returning existing future", taskId);
            return existing;
        }

        LOG.info("Running task {} on-demand", taskId);
        Future<TaskResult> future = onDemandExecutor.submit(() -> {
            TaskResult result = task.execute();
            lastResults.put(taskId, result);
            return result;
        });

        runningTasks.put(taskId, future);
        return future;
    }

    /**
     * Run a task on-demand (sync)
     */
    public TaskResult runTaskSync(String taskId, long timeoutSeconds)
            throws InterruptedException, ExecutionException, TimeoutException {
        Future<TaskResult> future = runTaskAsync(taskId);
        return future.get(timeoutSeconds, TimeUnit.SECONDS);
    }

    /**
     * Get the last result for a task
     */
    public Optional<TaskResult> getLastResult(String taskId) {
        return Optional.ofNullable(lastResults.get(taskId));
    }

    /**
     * Get all registered tasks
     */
    public Collection<BackgroundTask<?>> getAllTasks() {
        return Collections.unmodifiableCollection(tasks.values());
    }

    /**
     * Get a task by ID
     */
    public Optional<BackgroundTask<?>> getTask(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    /**
     * Check if a task is currently running
     */
    public boolean isTaskRunning(String taskId) {
        Future<TaskResult> future = runningTasks.get(taskId);
        return future != null && !future.isDone();
    }

    /**
     * Cancel a scheduled task
     */
    public boolean cancelScheduledTask(String taskId) {
        ScheduledFuture<?> future = scheduledFutures.remove(taskId);
        if (future != null) {
            future.cancel(false);
            LOG.info("Cancelled scheduled task: {}", taskId);
            return true;
        }
        return false;
    }

    /**
     * Reschedule a task (cancel existing schedule and create new one)
     */
    public void rescheduleTask(String taskId) {
        BackgroundTask<?> task = tasks.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Unknown task: " + taskId);
        }
        cancelScheduledTask(taskId);
        scheduleTask(task);
    }

    @Override
    public void start() {
        LOG.info("Starting task scheduler with {} registered tasks", tasks.size());
        scheduleAllTasks();
    }

    @Override
    public void stop() {
        LOG.info("Stopping task scheduler");

        // Cancel all scheduled tasks
        for (ScheduledFuture<?> future : scheduledFutures.values()) {
            future.cancel(false);
        }
        scheduledFutures.clear();

        // Shutdown executors
        scheduledExecutor.shutdown();
        onDemandExecutor.shutdown();

        try {
            if (!scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            if (!onDemandExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                onDemandExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduledExecutor.shutdownNow();
            onDemandExecutor.shutdownNow();
        }

        LOG.info("Task scheduler stopped");
    }
}
