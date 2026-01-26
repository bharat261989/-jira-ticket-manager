package org.limits.api;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.limits.task.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Path("/api/tasks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TaskResource {

    private static final Logger LOG = LoggerFactory.getLogger(TaskResource.class);
    private static final long DEFAULT_TIMEOUT_SECONDS = 300;

    private final TaskScheduler taskScheduler;

    public TaskResource(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    /**
     * List all registered tasks
     * GET /api/tasks
     */
    @GET
    public Response listTasks(@QueryParam("category") String category) {
        List<TaskInfo> tasks;

        if (category != null) {
            try {
                TaskCategory cat = TaskCategory.valueOf(category.toUpperCase());
                tasks = taskScheduler.getTasksByCategory(cat).stream()
                        .map(this::toTaskInfo)
                        .toList();
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Invalid category: " + category))
                        .build();
            }
        } else {
            tasks = taskScheduler.getAllTasks().stream()
                    .map(this::toTaskInfo)
                    .toList();
        }

        return Response.ok(tasks).build();
    }

    /**
     * Get task details
     * GET /api/tasks/{taskId}
     */
    @GET
    @Path("/{taskId}")
    public Response getTask(@PathParam("taskId") String taskId) {
        Optional<BackgroundTask<?>> task = taskScheduler.getTask(taskId);
        if (task.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Task not found: " + taskId))
                    .build();
        }

        TaskInfo info = toTaskInfo(task.get());
        return Response.ok(info).build();
    }

    /**
     * Get last execution result for a task
     * GET /api/tasks/{taskId}/result
     */
    @GET
    @Path("/{taskId}/result")
    public Response getTaskResult(@PathParam("taskId") String taskId) {
        Optional<BackgroundTask<?>> task = taskScheduler.getTask(taskId);
        if (task.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Task not found: " + taskId))
                    .build();
        }

        Optional<TaskResult> result = taskScheduler.getLastResult(taskId);
        if (result.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("No execution result available for task: " + taskId))
                    .build();
        }

        return Response.ok(result.get()).build();
    }

    /**
     * Run a task on-demand (async)
     * POST /api/tasks/{taskId}/run
     */
    @POST
    @Path("/{taskId}/run")
    public Response runTask(
            @PathParam("taskId") String taskId,
            @QueryParam("async") @DefaultValue("true") boolean async,
            @QueryParam("timeout") @DefaultValue("300") long timeoutSeconds) {

        Optional<BackgroundTask<?>> task = taskScheduler.getTask(taskId);
        if (task.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Task not found: " + taskId))
                    .build();
        }

        if (taskScheduler.isTaskRunning(taskId)) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse("Task is already running: " + taskId))
                    .build();
        }

        LOG.info("Running task {} on-demand (async={})", taskId, async);

        if (async) {
            taskScheduler.runTaskAsync(taskId);
            return Response.accepted()
                    .entity(new RunTaskResponse(taskId, "Task started", true))
                    .build();
        } else {
            try {
                TaskResult result = taskScheduler.runTaskSync(taskId, timeoutSeconds);
                return Response.ok(result).build();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Response.serverError()
                        .entity(new ErrorResponse("Task execution interrupted"))
                        .build();
            } catch (ExecutionException e) {
                return Response.serverError()
                        .entity(new ErrorResponse("Task execution failed: " + e.getCause().getMessage()))
                        .build();
            } catch (TimeoutException e) {
                return Response.status(Response.Status.REQUEST_TIMEOUT)
                        .entity(new ErrorResponse("Task execution timed out after " + timeoutSeconds + " seconds"))
                        .build();
            }
        }
    }

    /**
     * Cancel a scheduled task
     * DELETE /api/tasks/{taskId}/schedule
     */
    @DELETE
    @Path("/{taskId}/schedule")
    public Response cancelScheduledTask(@PathParam("taskId") String taskId) {
        Optional<BackgroundTask<?>> task = taskScheduler.getTask(taskId);
        if (task.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Task not found: " + taskId))
                    .build();
        }

        boolean cancelled = taskScheduler.cancelScheduledTask(taskId);
        if (cancelled) {
            return Response.ok(new MessageResponse("Task schedule cancelled: " + taskId)).build();
        } else {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("No active schedule found for task: " + taskId))
                    .build();
        }
    }

    /**
     * Reschedule a task
     * POST /api/tasks/{taskId}/reschedule
     */
    @POST
    @Path("/{taskId}/reschedule")
    public Response rescheduleTask(@PathParam("taskId") String taskId) {
        Optional<BackgroundTask<?>> task = taskScheduler.getTask(taskId);
        if (task.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Task not found: " + taskId))
                    .build();
        }

        taskScheduler.rescheduleTask(taskId);
        return Response.ok(new MessageResponse("Task rescheduled: " + taskId)).build();
    }

    private TaskInfo toTaskInfo(BackgroundTask<?> task) {
        TaskInfo info = new TaskInfo();
        info.setTaskId(task.getTaskId());
        info.setTaskName(task.getTaskName());
        info.setCategory(task.getCategory().name());
        info.setEnabled(task.isEnabled());
        info.setIntervalMinutes(task.getIntervalMinutes());
        info.setRunning(taskScheduler.isTaskRunning(task.getTaskId()));

        taskScheduler.getLastResult(task.getTaskId()).ifPresent(result -> {
            info.setLastStatus(result.getStatus().name());
            info.setLastExecutionTime(result.getEndTime().toString());
        });

        return info;
    }

    // DTOs

    public static class TaskInfo {
        private String taskId;
        private String taskName;
        private String category;
        private boolean enabled;
        private int intervalMinutes;
        private boolean running;
        private String lastStatus;
        private String lastExecutionTime;

        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        public String getTaskName() { return taskName; }
        public void setTaskName(String taskName) { this.taskName = taskName; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getIntervalMinutes() { return intervalMinutes; }
        public void setIntervalMinutes(int intervalMinutes) { this.intervalMinutes = intervalMinutes; }
        public boolean isRunning() { return running; }
        public void setRunning(boolean running) { this.running = running; }
        public String getLastStatus() { return lastStatus; }
        public void setLastStatus(String lastStatus) { this.lastStatus = lastStatus; }
        public String getLastExecutionTime() { return lastExecutionTime; }
        public void setLastExecutionTime(String lastExecutionTime) { this.lastExecutionTime = lastExecutionTime; }
    }

    public static class RunTaskResponse {
        private String taskId;
        private String message;
        private boolean async;

        public RunTaskResponse(String taskId, String message, boolean async) {
            this.taskId = taskId;
            this.message = message;
            this.async = async;
        }

        public String getTaskId() { return taskId; }
        public String getMessage() { return message; }
        public boolean isAsync() { return async; }
    }

    public static class MessageResponse {
        private String message;

        public MessageResponse(String message) {
            this.message = message;
        }

        public String getMessage() { return message; }
    }

    public static class ErrorResponse {
        private String error;

        public ErrorResponse(String error) {
            this.error = error;
        }

        public String getError() { return error; }
    }
}
