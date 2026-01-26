package org.limits.task;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class TaskResult {

    public enum Status {
        SUCCESS,
        FAILURE,
        SKIPPED
    }

    @JsonProperty
    private final String taskId;

    @JsonProperty
    private final Status status;

    @JsonProperty
    private final String message;

    @JsonProperty
    private final Instant startTime;

    @JsonProperty
    private final Instant endTime;

    @JsonProperty
    private final long durationMs;

    @JsonProperty
    private final Map<String, Object> metadata;

    private TaskResult(Builder builder) {
        this.taskId = builder.taskId;
        this.status = builder.status;
        this.message = builder.message;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.durationMs = builder.endTime.toEpochMilli() - builder.startTime.toEpochMilli();
        this.metadata = builder.metadata;
    }

    public String getTaskId() {
        return taskId;
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public static Builder builder(String taskId) {
        return new Builder(taskId);
    }

    public static class Builder {
        private final String taskId;
        private Status status = Status.SUCCESS;
        private String message;
        private Instant startTime = Instant.now();
        private Instant endTime = Instant.now();
        private Map<String, Object> metadata = new HashMap<>();

        public Builder(String taskId) {
            this.taskId = taskId;
        }

        public Builder status(Status status) {
            this.status = status;
            return this;
        }

        public Builder success() {
            this.status = Status.SUCCESS;
            return this;
        }

        public Builder failure() {
            this.status = Status.FAILURE;
            return this;
        }

        public Builder skipped() {
            this.status = Status.SKIPPED;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder addMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public TaskResult build() {
            return new TaskResult(this);
        }
    }
}
