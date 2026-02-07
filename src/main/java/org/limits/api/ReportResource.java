package org.limits.api;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.limits.client.ConfluenceClient;
import org.limits.client.JiraClient;
import org.limits.client.ConfluenceClient.PageSummary;
import org.limits.config.ConfluenceConfig;
import org.limits.config.JiraConfiguration.JiraConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * API for generating Confluence reports from Jira issues (e.g. issues created in a date range).
 */
@Path("/api/reports")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReportResource {

    private static final Logger LOG = LoggerFactory.getLogger(ReportResource.class);
    private static final int SEARCH_PAGE_SIZE = 100;
    private static final DateTimeFormatter JIRA_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneOffset.UTC);

    private final JiraClient jiraClient;
    private final ConfluenceClient confluenceClient;
    private final JiraConfig jiraConfig;
    private final ConfluenceConfig confluenceConfig;

    public ReportResource(JiraClient jiraClient, ConfluenceClient confluenceClient,
                          JiraConfig jiraConfig, ConfluenceConfig confluenceConfig) {
        this.jiraClient = jiraClient;
        this.confluenceClient = confluenceClient;
        this.jiraConfig = jiraConfig;
        this.confluenceConfig = confluenceConfig;
    }

    /**
     * List all pages in a Confluence space.
     * GET /api/reports/confluence/spaces/{spaceKey}/pages
     */
    @GET
    @Path("/confluence/spaces/{spaceKey}/pages")
    public Response getConfluenceSpacePages(@PathParam("spaceKey") String spaceKey) {
        if (spaceKey == null || spaceKey.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorBody("spaceKey is required"))
                    .build();
        }
        try {
            List<PageSummary> pages = confluenceClient.getSpacePages(spaceKey.trim());
            List<ConfluencePageInfo> result = pages.stream()
                    .map(p -> new ConfluencePageInfo(p.getId(), p.getTitle(), p.getWebUrl()))
                    .toList();
            return Response.ok(new SpacePagesResponse(spaceKey, result)).build();
        } catch (ConfluenceClient.ConfluenceClientException e) {
            LOG.error("Confluence error listing space pages: {}", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorBody("Confluence error: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Generate a Confluence report of Jira issues created between startTime and endTime.
     * POST /api/reports/confluence
     */
    @POST
    @Path("/confluence")
    public Response generateConfluenceReport(ConfluenceReportRequest request) {
        if (request == null || request.getStartTime() == null || request.getEndTime() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorBody("startTime and endTime are required (ISO-8601)"))
                    .build();
        }

        Instant start;
        Instant end;
        try {
            start = Instant.parse(request.getStartTime());
            end = Instant.parse(request.getEndTime());
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorBody("Invalid date format. Use ISO-8601 (e.g. 2024-01-15T10:00:00Z)"))
                    .build();
        }

        if (!end.isAfter(start)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorBody("endTime must be after startTime"))
                    .build();
        }

        String spaceKey = request.getSpaceKey() != null && !request.getSpaceKey().isBlank()
                ? request.getSpaceKey().trim()
                : confluenceConfig.getDefaultSpaceKey();
        String projectKey = request.getProjectKey() != null && !request.getProjectKey().isBlank()
                ? request.getProjectKey().trim()
                : jiraConfig.getBaseProject();

        String startStr = JIRA_DATE_FORMAT.format(start);
        String endStr = JIRA_DATE_FORMAT.format(end);
        String jql = String.format("project = \"%s\" AND created >= \"%s\" AND created < \"%s\" ORDER BY created ASC",
                projectKey, startStr, endStr);

        LOG.info("Generating Confluence report: JQL={}, spaceKey={}", jql, spaceKey);

        try {
            List<Issue> allIssues = new ArrayList<>();
            int startAt = 0;
            SearchResult result;
            do {
                result = jiraClient.searchIssues(jql, startAt, SEARCH_PAGE_SIZE);
                for (Issue issue : result.getIssues()) {
                    allIssues.add(issue);
                }
                startAt += SEARCH_PAGE_SIZE;
            } while (startAt < result.getTotal());

            String title = String.format("Jira issues opened %s – %s", startStr, endStr);
            String bodyHtml = buildReportHtml(allIssues, startStr, endStr, projectKey);

            ConfluenceClient.CreatePageResult page = confluenceClient.createPage(spaceKey, title, bodyHtml);

            ConfluenceReportResponse response = new ConfluenceReportResponse();
            response.setPageId(page.getPageId());
            response.setPageUrl(page.getPageUrl());
            response.setIssueCount(allIssues.size());

            LOG.info("Created Confluence report: {} issues, page {}", allIssues.size(), page.getPageUrl());
            return Response.ok(response).build();

        } catch (JiraClient.JiraClientException e) {
            LOG.error("Jira error generating report: {}", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorBody("Jira error: " + e.getMessage()))
                    .build();
        } catch (ConfluenceClient.ConfluenceClientException e) {
            LOG.error("Confluence error generating report: {}", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorBody("Confluence error: " + e.getMessage()))
                    .build();
        }
    }

    private String buildReportHtml(List<Issue> issues, String startStr, String endStr, String projectKey) {
        StringBuilder html = new StringBuilder();
        html.append("<p>Report of <strong>Jira issues opened</strong> between <strong>").append(escape(startStr))
                .append("</strong> and <strong>").append(escape(endStr))
                .append("</strong> (project: ").append(escape(projectKey)).append(").</p>");
        html.append("<p><strong>Total: ").append(issues.size()).append("</strong> issue(s).</p>");
        html.append("<table data-layout=\"default\"><colgroup><col/><col/><col/><col/><col/><col/></colgroup>");
        html.append("<thead><tr>");
        html.append("<th>Key</th><th>Summary</th><th>Status</th><th>Type</th><th>Assignee</th><th>Created</th>");
        html.append("</tr></thead><tbody>");

        for (Issue issue : issues) {
            html.append("<tr>");
            html.append("<td>").append(escape(issue.getKey())).append("</td>");
            html.append("<td>").append(escape(issue.getSummary())).append("</td>");
            html.append("<td>").append(escape(issue.getStatus() != null ? issue.getStatus().getName() : "")).append("</td>");
            html.append("<td>").append(escape(issue.getIssueType() != null ? issue.getIssueType().getName() : "")).append("</td>");
            html.append("<td>").append(escape(issue.getAssignee() != null ? issue.getAssignee().getDisplayName() : "")).append("</td>");
            html.append("<td>").append(escape(issue.getCreationDate() != null ? issue.getCreationDate().toString() : "")).append("</td>");
            html.append("</tr>");
        }

        html.append("</tbody></table>");
        return html.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    public static class ConfluenceReportRequest {
        private String startTime;
        private String endTime;
        private String spaceKey;
        private String projectKey;

        public String getStartTime() { return startTime; }
        public void setStartTime(String startTime) { this.startTime = startTime; }
        public String getEndTime() { return endTime; }
        public void setEndTime(String endTime) { this.endTime = endTime; }
        public String getSpaceKey() { return spaceKey; }
        public void setSpaceKey(String spaceKey) { this.spaceKey = spaceKey; }
        public String getProjectKey() { return projectKey; }
        public void setProjectKey(String projectKey) { this.projectKey = projectKey; }
    }

    public static class ConfluenceReportResponse {
        private String pageId;
        private String pageUrl;
        private int issueCount;

        public String getPageId() { return pageId; }
        public void setPageId(String pageId) { this.pageId = pageId; }
        public String getPageUrl() { return pageUrl; }
        public void setPageUrl(String pageUrl) { this.pageUrl = pageUrl; }
        public int getIssueCount() { return issueCount; }
        public void setIssueCount(int issueCount) { this.issueCount = issueCount; }
    }

    public static class ConfluencePageInfo {
        private String id;
        private String title;
        private String webUrl;

        public ConfluencePageInfo(String id, String title, String webUrl) {
            this.id = id;
            this.title = title;
            this.webUrl = webUrl;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getWebUrl() { return webUrl; }
        public void setWebUrl(String webUrl) { this.webUrl = webUrl; }
    }

    public static class SpacePagesResponse {
        private String spaceKey;
        private List<ConfluencePageInfo> pages;

        public SpacePagesResponse(String spaceKey, List<ConfluencePageInfo> pages) {
            this.spaceKey = spaceKey;
            this.pages = pages;
        }

        public String getSpaceKey() { return spaceKey; }
        public void setSpaceKey(String spaceKey) { this.spaceKey = spaceKey; }
        public List<ConfluencePageInfo> getPages() { return pages; }
        public void setPages(List<ConfluencePageInfo> pages) { this.pages = pages; }
    }

    public static class ErrorBody {
        private String error;
        public ErrorBody(String error) { this.error = error; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }
}
