package org.limits.task;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TaskConfig {

    @JsonProperty
    private boolean enabled = true;

    @JsonProperty
    private int intervalMinutes = 60;

    @JsonProperty
    private int initialDelayMinutes = 1;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getIntervalMinutes() {
        return intervalMinutes;
    }

    public void setIntervalMinutes(int intervalMinutes) {
        this.intervalMinutes = intervalMinutes;
    }

    public int getInitialDelayMinutes() {
        return initialDelayMinutes;
    }

    public void setInitialDelayMinutes(int initialDelayMinutes) {
        this.initialDelayMinutes = initialDelayMinutes;
    }
}
