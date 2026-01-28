package org.limits.api;

import com.atlassian.jira.rest.client.api.domain.BasicIssue;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueLink;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.api.domain.Transition;
import com.atlassian.jira.rest.client.api.domain.input.ComplexIssueInputFieldValue;
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.limits.client.JiraClient;
import org.limits.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

@Path("/api/tickets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TicketResource {

    private static final Logger LOG = LoggerFactory.getLogger(TicketResource.class);

    private final JiraClient jiraClient;

    public TicketResource(JiraClient jiraClient) {
        this.jiraClient = jiraClient;
    }

    /**
     * Get a single ticket by key
     * GET /api/tickets/{issueKey}
     */
    @GET
    @Path("/{issueKey}")
    public Response getTicket(@PathParam("issueKey") String issueKey) {
        try {
            LOG.info("Getting ticket: {}", issueKey);
            Issue issue = jiraClient.getIssue(issueKey);
            return Response.ok(toIssueResponse(issue)).build();
        } catch (JiraClient.JiraClientException e) {
            LOG.error("Error getting ticket {}: {}", issueKey, e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }

    /**
     * Search for tickets using JQL
     * GET /api/tickets?jql=project=TEST&startAt=0&maxResults=50
     */
    @GET
    public Response searchTickets(
            @QueryParam("jql") @NotEmpty String jql,
            @QueryParam("startAt") @DefaultValue("0") int startAt,
            @QueryParam("maxResults") @DefaultValue("50") int maxResults) {
        try {
            LOG.info("Searching tickets with JQL: {}", jql);
            SearchResult result = jiraClient.searchIssues(jql, startAt, maxResults);
            return Response.ok(toSearchResponse(result, startAt, maxResults)).build();
        } catch (JiraClient.JiraClientException e) {
            LOG.error("Error searching tickets: {}", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }

    /**
     * Create a new ticket
     * POST /api/tickets
     */
    @POST
    public Response createTicket(@Valid CreateIssueRequest request) {
        try {
            LOG.info("Creating ticket in project: {}", request.getProjectKey());

            IssueInputBuilder builder = new IssueInputBuilder()
                    .setProjectKey(request.getProjectKey())
                    .setSummary(request.getSummary())
                    .setFieldValue("issuetype",
                            ComplexIssueInputFieldValue.with("name", request.getIssueType()));

            if (request.getDescription() != null) {
                builder.setDescription(request.getDescription());
            }

            BasicIssue createdIssue = jiraClient.createIssue(builder.build());
            Issue fullIssue = jiraClient.getIssue(createdIssue.getKey());

            return Response.status(Response.Status.CREATED)
                    .entity(toIssueResponse(fullIssue))
                    .build();
        } catch (JiraClient.JiraClientException e) {
            LOG.error("Error creating ticket: {}", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }

    /**
     * Update an existing ticket
     * PUT /api/tickets/{issueKey}
     */
    @PUT
    @Path("/{issueKey}")
    public Response updateTicket(
            @PathParam("issueKey") String issueKey,
            @Valid UpdateIssueRequest request) {
        try {
            LOG.info("Updating ticket: {}", issueKey);

            IssueInputBuilder builder = new IssueInputBuilder();

            if (request.getSummary() != null) {
                builder.setSummary(request.getSummary());
            }
            if (request.getDescription() != null) {
                builder.setDescription(request.getDescription());
            }

            jiraClient.updateIssue(issueKey, builder.build());
            Issue updatedIssue = jiraClient.getIssue(issueKey);

            return Response.ok(toIssueResponse(updatedIssue)).build();
        } catch (JiraClient.JiraClientException e) {
            LOG.error("Error updating ticket {}: {}", issueKey, e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }

    /**
     * Delete a ticket
     * DELETE /api/tickets/{issueKey}
     */
    @DELETE
    @Path("/{issueKey}")
    public Response deleteTicket(
            @PathParam("issueKey") String issueKey,
            @QueryParam("deleteSubtasks") @DefaultValue("false") boolean deleteSubtasks) {
        try {
            LOG.info("Deleting ticket: {}", issueKey);
            jiraClient.deleteIssue(issueKey, deleteSubtasks);
            return Response.noContent().build();
        } catch (JiraClient.JiraClientException e) {
            LOG.error("Error deleting ticket {}: {}", issueKey, e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }

    /**
     * Get available transitions for a ticket
     * GET /api/tickets/{issueKey}/transitions
     */
    @GET
    @Path("/{issueKey}/transitions")
    public Response getTransitions(@PathParam("issueKey") String issueKey) {
        try {
            LOG.info("Getting transitions for ticket: {}", issueKey);
            Iterable<Transition> transitions = jiraClient.getTransitions(issueKey);

            List<TransitionResponse> response = StreamSupport.stream(transitions.spliterator(), false)
                    .map(t -> new TransitionResponse(t.getId(), t.getName()))
                    .toList();

            return Response.ok(response).build();
        } catch (JiraClient.JiraClientException e) {
            LOG.error("Error getting transitions for {}: {}", issueKey, e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }

    /**
     * Transition a ticket to a new status
     * POST /api/tickets/{issueKey}/transitions
     */
    @POST
    @Path("/{issueKey}/transitions")
    public Response transitionTicket(
            @PathParam("issueKey") String issueKey,
            TransitionRequest request) {
        try {
            LOG.info("Transitioning ticket {} with transition {}", issueKey, request.getTransitionId());
            jiraClient.transitionIssue(issueKey, request.getTransitionId());
            Issue updatedIssue = jiraClient.getIssue(issueKey);
            return Response.ok(toIssueResponse(updatedIssue)).build();
        } catch (JiraClient.JiraClientException e) {
            LOG.error("Error transitioning ticket {}: {}", issueKey, e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }

    /**
     * Add a comment to a ticket
     * POST /api/tickets/{issueKey}/comments
     */
    @POST
    @Path("/{issueKey}/comments")
    public Response addComment(
            @PathParam("issueKey") String issueKey,
            CommentRequest request) {
        try {
            LOG.info("Adding comment to ticket: {}", issueKey);
            jiraClient.addComment(issueKey, request.getBody());
            return Response.status(Response.Status.CREATED).build();
        } catch (JiraClient.JiraClientException e) {
            LOG.error("Error adding comment to {}: {}", issueKey, e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }

    // Conversion helpers

    private IssueResponse toIssueResponse(Issue issue) {
        IssueResponse response = new IssueResponse();
        response.setKey(issue.getKey());
        response.setSelf(issue.getSelf() != null ? issue.getSelf().toString() : null);
        response.setSummary(issue.getSummary());
        response.setDescription(issue.getDescription());
        response.setStatus(issue.getStatus() != null ? issue.getStatus().getName() : null);
        response.setIssueType(issue.getIssueType() != null ? issue.getIssueType().getName() : null);
        response.setPriority(issue.getPriority() != null ? issue.getPriority().getName() : null);
        response.setAssignee(issue.getAssignee() != null ? issue.getAssignee().getDisplayName() : null);
        response.setReporter(issue.getReporter() != null ? issue.getReporter().getDisplayName() : null);
        response.setProject(issue.getProject() != null ? issue.getProject().getKey() : null);
        response.setCreated(issue.getCreationDate() != null ? issue.getCreationDate().toString() : null);
        response.setUpdated(issue.getUpdateDate() != null ? issue.getUpdateDate().toString() : null);
        response.setLinkedIssues(toLinkedIssues(issue));
        return response;
    }

    private List<LinkedIssueInfo> toLinkedIssues(Issue issue) {
        Iterable<IssueLink> links = issue.getIssueLinks();
        if (links == null) {
            return List.of();
        }
        List<LinkedIssueInfo> linkedIssues = new ArrayList<>();
        for (IssueLink link : links) {
            String linkType = link.getIssueLinkType() != null
                    ? link.getIssueLinkType().getDescription()
                    : "linked to";
            linkedIssues.add(new LinkedIssueInfo(link.getTargetIssueKey(), linkType));
        }
        return linkedIssues;
    }

    private SearchResultResponse toSearchResponse(SearchResult result, int startAt, int maxResults) {
        SearchResultResponse response = new SearchResultResponse();
        response.setStartAt(startAt);
        response.setMaxResults(maxResults);
        response.setTotal(result.getTotal());

        List<IssueResponse> issues = new ArrayList<>();
        for (Issue issue : result.getIssues()) {
            issues.add(toIssueResponse(issue));
        }
        response.setIssues(issues);

        return response;
    }

    // Request/Response DTOs

    public static class IssueResponse {
        private String key;
        private String self;
        private String summary;
        private String description;
        private String status;
        private String issueType;
        private String priority;
        private String assignee;
        private String reporter;
        private String project;
        private String created;
        private String updated;
        private List<LinkedIssueInfo> linkedIssues;

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getSelf() { return self; }
        public void setSelf(String self) { this.self = self; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getIssueType() { return issueType; }
        public void setIssueType(String issueType) { this.issueType = issueType; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        public String getAssignee() { return assignee; }
        public void setAssignee(String assignee) { this.assignee = assignee; }
        public String getReporter() { return reporter; }
        public void setReporter(String reporter) { this.reporter = reporter; }
        public String getProject() { return project; }
        public void setProject(String project) { this.project = project; }
        public String getCreated() { return created; }
        public void setCreated(String created) { this.created = created; }
        public String getUpdated() { return updated; }
        public void setUpdated(String updated) { this.updated = updated; }
        public List<LinkedIssueInfo> getLinkedIssues() { return linkedIssues; }
        public void setLinkedIssues(List<LinkedIssueInfo> linkedIssues) { this.linkedIssues = linkedIssues; }
    }

    public static class LinkedIssueInfo {
        private String issueKey;
        private String linkType;

        public LinkedIssueInfo(String issueKey, String linkType) {
            this.issueKey = issueKey;
            this.linkType = linkType;
        }

        public String getIssueKey() { return issueKey; }
        public void setIssueKey(String issueKey) { this.issueKey = issueKey; }
        public String getLinkType() { return linkType; }
        public void setLinkType(String linkType) { this.linkType = linkType; }
    }

    public static class SearchResultResponse {
        private int startAt;
        private int maxResults;
        private int total;
        private List<IssueResponse> issues;

        public int getStartAt() { return startAt; }
        public void setStartAt(int startAt) { this.startAt = startAt; }
        public int getMaxResults() { return maxResults; }
        public void setMaxResults(int maxResults) { this.maxResults = maxResults; }
        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }
        public List<IssueResponse> getIssues() { return issues; }
        public void setIssues(List<IssueResponse> issues) { this.issues = issues; }
    }

    public static class TransitionResponse {
        private int id;
        private String name;

        public TransitionResponse(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class TransitionRequest {
        private int transitionId;

        public int getTransitionId() { return transitionId; }
        public void setTransitionId(int transitionId) { this.transitionId = transitionId; }
    }

    public static class CommentRequest {
        private String body;

        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
    }

    public static class ErrorResponse {
        private String error;

        public ErrorResponse(String error) {
            this.error = error;
        }

        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }
}
