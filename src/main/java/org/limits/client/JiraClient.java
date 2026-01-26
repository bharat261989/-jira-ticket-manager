package org.limits.client;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.BasicIssue;
import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.api.domain.Transition;
import com.atlassian.jira.rest.client.api.domain.input.IssueInput;
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder;
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import org.limits.config.JiraConfiguration.JiraConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class JiraClient implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(JiraClient.class);

    private final JiraRestClient restClient;
    private final JiraConfig config;

    public JiraClient(JiraConfig config) {
        this.config = config;
        URI jiraUri = URI.create(config.getBaseUrl().replaceAll("/$", ""));

        AsynchronousJiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();
        this.restClient = factory.createWithBasicHttpAuthentication(
                jiraUri,
                config.getUsername(),
                config.getApiToken()
        );

        LOG.info("Initialized Jira client for: {}", jiraUri);
    }

    /**
     * Get a single issue by key (e.g., "PROJECT-123")
     */
    public Issue getIssue(String issueKey) {
        LOG.debug("Fetching issue: {}", issueKey);
        try {
            return restClient.getIssueClient()
                    .getIssue(issueKey)
                    .get(config.getReadTimeout(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JiraClientException("Interrupted while fetching issue: " + issueKey, e);
        } catch (ExecutionException e) {
            throw new JiraClientException("Failed to fetch issue: " + issueKey, e.getCause());
        } catch (TimeoutException e) {
            throw new JiraClientException("Timeout fetching issue: " + issueKey, e);
        }
    }

    /**
     * Search for issues using JQL
     */
    public SearchResult searchIssues(String jql, int startAt, int maxResults) {
        LOG.debug("Searching issues with JQL: {}", jql);
        try {
            return restClient.getSearchClient()
                    .searchJql(jql, maxResults, startAt, null)
                    .get(config.getReadTimeout(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JiraClientException("Interrupted while searching issues", e);
        } catch (ExecutionException e) {
            throw new JiraClientException("Failed to search issues", e.getCause());
        } catch (TimeoutException e) {
            throw new JiraClientException("Timeout searching issues", e);
        }
    }

    /**
     * Create a new issue
     */
    public BasicIssue createIssue(IssueInput issueInput) {
        LOG.debug("Creating issue");
        try {
            return restClient.getIssueClient()
                    .createIssue(issueInput)
                    .get(config.getReadTimeout(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JiraClientException("Interrupted while creating issue", e);
        } catch (ExecutionException e) {
            throw new JiraClientException("Failed to create issue", e.getCause());
        } catch (TimeoutException e) {
            throw new JiraClientException("Timeout creating issue", e);
        }
    }

    /**
     * Update an existing issue
     */
    public void updateIssue(String issueKey, IssueInput issueInput) {
        LOG.debug("Updating issue: {}", issueKey);
        try {
            restClient.getIssueClient()
                    .updateIssue(issueKey, issueInput)
                    .get(config.getReadTimeout(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JiraClientException("Interrupted while updating issue: " + issueKey, e);
        } catch (ExecutionException e) {
            throw new JiraClientException("Failed to update issue: " + issueKey, e.getCause());
        } catch (TimeoutException e) {
            throw new JiraClientException("Timeout updating issue: " + issueKey, e);
        }
    }

    /**
     * Delete an issue
     */
    public void deleteIssue(String issueKey, boolean deleteSubtasks) {
        LOG.debug("Deleting issue: {}", issueKey);
        try {
            restClient.getIssueClient()
                    .deleteIssue(issueKey, deleteSubtasks)
                    .get(config.getReadTimeout(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JiraClientException("Interrupted while deleting issue: " + issueKey, e);
        } catch (ExecutionException e) {
            throw new JiraClientException("Failed to delete issue: " + issueKey, e.getCause());
        } catch (TimeoutException e) {
            throw new JiraClientException("Timeout deleting issue: " + issueKey, e);
        }
    }

    /**
     * Get available transitions for an issue
     */
    public Iterable<Transition> getTransitions(String issueKey) {
        LOG.debug("Getting transitions for issue: {}", issueKey);
        try {
            Issue issue = getIssue(issueKey);
            return restClient.getIssueClient()
                    .getTransitions(issue)
                    .get(config.getReadTimeout(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JiraClientException("Interrupted while getting transitions for: " + issueKey, e);
        } catch (ExecutionException e) {
            throw new JiraClientException("Failed to get transitions for: " + issueKey, e.getCause());
        } catch (TimeoutException e) {
            throw new JiraClientException("Timeout getting transitions for: " + issueKey, e);
        }
    }

    /**
     * Transition an issue to a new status
     */
    public void transitionIssue(String issueKey, int transitionId) {
        LOG.debug("Transitioning issue {} with transition {}", issueKey, transitionId);
        try {
            Issue issue = getIssue(issueKey);
            TransitionInput transitionInput = new TransitionInput(transitionId);
            restClient.getIssueClient()
                    .transition(issue, transitionInput)
                    .get(config.getReadTimeout(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JiraClientException("Interrupted while transitioning issue: " + issueKey, e);
        } catch (ExecutionException e) {
            throw new JiraClientException("Failed to transition issue: " + issueKey, e.getCause());
        } catch (TimeoutException e) {
            throw new JiraClientException("Timeout transitioning issue: " + issueKey, e);
        }
    }

    /**
     * Add a comment to an issue
     */
    public void addComment(String issueKey, String commentBody) {
        LOG.debug("Adding comment to issue: {}", issueKey);
        try {
            Issue issue = getIssue(issueKey);
            Comment comment = Comment.valueOf(commentBody);
            restClient.getIssueClient()
                    .addComment(issue.getCommentsUri(), comment)
                    .get(config.getReadTimeout(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JiraClientException("Interrupted while adding comment to: " + issueKey, e);
        } catch (ExecutionException e) {
            throw new JiraClientException("Failed to add comment to: " + issueKey, e.getCause());
        } catch (TimeoutException e) {
            throw new JiraClientException("Timeout adding comment to: " + issueKey, e);
        }
    }

    /**
     * Test the connection to Jira
     */
    public boolean testConnection() {
        try {
            restClient.getMetadataClient()
                    .getServerInfo()
                    .get(config.getConnectionTimeout(), TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception e) {
            LOG.error("Failed to connect to Jira", e);
            return false;
        }
    }

    /**
     * Get the underlying Jira REST client for advanced operations
     */
    public JiraRestClient getRestClient() {
        return restClient;
    }

    @Override
    public void close() throws IOException {
        restClient.close();
    }

    public static class JiraClientException extends RuntimeException {
        public JiraClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
