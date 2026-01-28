# Jira Ticket Viewer

A React-based UI for viewing Jira issues from a synced CSV file. Displays issues in a professional table with pastel styling, search/filter, column sorting, and automatic grouping of linked issues.

This project works both as a **standalone app** and as a **sub-project** inside `jira-ticket-manager`.

## Prerequisites

- **Node.js** (v18 or later) - [Download here](https://nodejs.org/)
- **npm** (comes bundled with Node.js)

Verify you have both installed:

```bash
node --version
npm --version
```

## Setup

1. **Navigate to the project:**

   ```bash
   cd jira-ticket-viewer
   ```

2. **Install all dependencies:**

   ```bash
   npm install
   ```

   This installs React, Vite, and all other required packages listed in `package.json`. No additional installs are needed.

3. **Start the development server:**

   ```bash
   npm run dev
   ```

4. **Open in browser:**

   Visit [http://localhost:5173](http://localhost:5173)

## CSV Data Source

The app looks for `synced-issues.csv` in this order:

1. `./data/` — local `data/` folder inside this project (for standalone use)
2. `../data/` — parent `data/` folder (when used as a sub-project of `jira-ticket-manager`)
3. Custom path via the `CSV_DATA_DIR` environment variable

### Using a custom CSV path

```bash
CSV_DATA_DIR=/path/to/your/data npm run dev
```

### Expected CSV Format

```
Key,Summary,Status,Priority,Assignee,Reporter,Created,Updated,Type,Labels,Linked Issues
PROJ-101,Implement auth,In Progress,High,Alice,Bob,2025-12-01,2026-01-15,Story,backend;security,NOC-501
```

- **Labels** are semicolon-separated (e.g., `backend;security`)
- **Linked Issues** — one external ticket key per issue (e.g., `NOC-501`)

## Features

- **Search** - Filter issues by any field (key, summary, assignee, status, etc.)
- **Column sorting** - Click any column header to sort ascending/descending
- **Linked issue grouping** - Issues linked to the same external ticket are grouped together
- **Color-coded badges** - Priority, status, and type each have distinct pastel colors
- **Responsive layout** - Works on desktop and mobile screens

## Scripts

| Command | Description |
|---------|-------------|
| `npm install` | Install dependencies |
| `npm run dev` | Start dev server at localhost:5173 |
| `npm run build` | Build for production (output in `dist/`) |
| `npm run preview` | Preview the production build locally |
