package org.limits.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class UpdateIssueRequest {

    @JsonProperty
    private String summary;

    @JsonProperty
    private String description;

    @JsonProperty
    private String priority;

    @JsonProperty
    private String assignee;

    @JsonProperty
    private List<String> labels;

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    public List<String> getLabels() {
        return labels;
    }

    public void setLabels(List<String> labels) {
        this.labels = labels;
    }
}
