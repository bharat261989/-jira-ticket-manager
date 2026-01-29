// Configuration for the Jira Issue Viewer
// Update JIRA_BASE_URL to match your Jira instance

export const JIRA_BASE_URL = 'https://jira.atlassian.com'

// Build the URL to view an issue in Jira
export function getIssueUrl(issueKey) {
  if (!JIRA_BASE_URL || !issueKey) return null
  return `${JIRA_BASE_URL}/browse/${issueKey}`
}
