package org.limits.task;

public enum TaskCategory {
    SYNC,           // Synchronization tasks (e.g., sync issues from Jira)
    CLEANUP,        // Cleanup tasks (e.g., archive old issues)
    NOTIFICATION,   // Notification tasks (e.g., send alerts)
    REPORTING,      // Reporting tasks (e.g., generate reports)
    MAINTENANCE     // Maintenance tasks (e.g., health checks)
}
