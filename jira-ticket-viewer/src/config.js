// Configuration for the Jira Issue Viewer
// Update these values to match your Jira instance and user

export const JIRA_BASE_URL = 'https://jira.atlassian.com'

// Current user's display name (used for "My Issues" filter)
// Set this to your Jira display name to enable the "My Issues" filter
export const CURRENT_USER = ''  // e.g., 'John Doe'

// Build the URL to view an issue in Jira
export function getIssueUrl(issueKey) {
  if (!JIRA_BASE_URL || !issueKey) return null
  return `${JIRA_BASE_URL}/browse/${issueKey}`
}
