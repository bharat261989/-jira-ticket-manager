# Jira Ticket Manager

A Dropwizard-based application that manages Jira tickets through background tasks and a REST API.

## Background Tasks

### Issue Sync Task

Syncs unresolved issues from Jira for the configured base project. Tracks last sync time for incremental updates and saves results to `data/synced-issues.csv`.

| Config Key         | Default | Description                              |
|--------------------|---------|------------------------------------------|
| `enabled`          | `true`  | Enable/disable the task                  |
| `intervalMinutes`  | `30`    | How often the task runs                  |
| `initialDelayMinutes` | `1`  | Delay before first run                   |
| `batchSize`        | `100`   | Number of issues fetched per batch       |
| `minTicketNumber`  | `0`     | Skip issues below this number (0 = all)  |

### Stale Issue Cleanup Task

Identifies and optionally transitions stale issues that haven't been updated within a configurable number of days.

| Config Key         | Default   | Description                              |
|--------------------|-----------|------------------------------------------|
| `enabled`          | `false`   | Enable/disable the task                  |
| `intervalMinutes`  | `1440`    | How often the task runs (default: daily) |
| `initialDelayMinutes` | `60`   | Delay before first run                   |
| `staleDays`        | `30`      | Days of inactivity before considered stale |
| `targetStatus`     | `Closed`  | Status to transition stale issues to     |
| `dryRun`           | `true`    | If true, only logs without transitioning |

### Comment Watch Task

Monitors active issues (from the synced CSV) for new comments and produces notifications via styled console output and a persistent log file.

**How it works:**
1. Reads issue keys from `data/synced-issues.csv` (produced by the Issue Sync Task)
2. Fetches each issue from Jira and inspects its comments
3. Compares comment timestamps against the last check time (stored in `data/last-comment-check-time.txt`)
4. For new comments, prints a styled console notification and appends to `data/comment-notifications.log`

**Configuration:**

| Config Key           | Default | Description                                      |
|----------------------|---------|--------------------------------------------------|
| `enabled`            | `true`  | Enable/disable the task                          |
| `intervalMinutes`    | `15`    | How often the task runs                          |
| `initialDelayMinutes`| `2`     | Delay before first run                           |
| `maxCommentLength`   | `200`   | Max characters of comment body in log file       |

**Output files:**
- `data/comment-notifications.log` — append-only log with one line per new comment:
  ```
  [2026-01-28 10:30:00] PROJ-101 | Author: John Doe | Comment: First 200 chars...
  ```
- `data/last-comment-check-time.txt` — timestamp of the last check run

## Configuration

Configuration uses HOCON format (`src/main/resources/application.conf`). Local overrides can be placed in `user.conf` (gitignored). All values support environment variable overrides.

## Running

```bash
mvn clean compile
mvn exec:java
```
