import { getIssueUrl } from './config'

const COLUMNS = [
  { key: 'Key', label: 'Key' },
  { key: 'Type', label: 'Type' },
  { key: 'Summary', label: 'Summary' },
  { key: 'Status', label: 'Status' },
  { key: 'Priority', label: 'Priority' },
  { key: 'Severity', label: 'Severity' },
  { key: 'Assignee', label: 'Assignee' },
  { key: 'Reporter', label: 'Reporter' },
  { key: 'Created', label: 'Created' },
  { key: 'Updated', label: 'Updated' },
  { key: 'Labels', label: 'Labels' },
  { key: 'Linked Issues', label: 'Linked' },
]

function priorityClass(priority) {
  const p = (priority || '').toLowerCase()
  if (p === 'critical') return 'priority-critical'
  if (p === 'high') return 'priority-high'
  if (p === 'medium') return 'priority-medium'
  if (p === 'low') return 'priority-low'
  return ''
}

function statusClass(status) {
  const s = (status || '').toLowerCase()
  if (s === 'to do') return 'status-to-do'
  if (s === 'in progress') return 'status-in-progress'
  if (s === 'done') return 'status-done'
  return ''
}

function typeClass(type) {
  const t = (type || '').toLowerCase()
  if (t === 'story') return 'type-story'
  if (t === 'task') return 'type-task'
  if (t === 'bug') return 'type-bug'
  return ''
}

function severityClass(severity) {
  const s = (severity || '').toLowerCase()
  if (s.includes('1') || s.includes('sev1') || s.includes('sev 1')) return 'severity-1'
  if (s.includes('2') || s.includes('sev2') || s.includes('sev 2')) return 'severity-2'
  if (s.includes('3') || s.includes('sev3') || s.includes('sev 3')) return 'severity-3'
  if (s.includes('4') || s.includes('sev4') || s.includes('sev 4')) return 'severity-4'
  if (s.includes('5') || s.includes('sev5') || s.includes('sev 5')) return 'severity-5'
  return ''
}

function renderCell(col, value) {
  if (col === 'Key') {
    const url = getIssueUrl(value)
    if (url) {
      return (
        <a href={url} target="_blank" rel="noopener noreferrer" className="issue-key">
          {value}
        </a>
      )
    }
    return <span className="issue-key">{value}</span>
  }
  if (col === 'Priority') {
    return <span className={`badge ${priorityClass(value)}`}>{value}</span>
  }
  if (col === 'Status') {
    return <span className={`badge ${statusClass(value)}`}>{value}</span>
  }
  if (col === 'Type') {
    return <span className={`badge ${typeClass(value)}`}>{value}</span>
  }
  if (col === 'Severity') {
    if (!value) return <span className="no-value">—</span>
    return <span className={`badge ${severityClass(value)}`}>{value}</span>
  }
  if (col === 'Labels') {
    const labels = value ? value.split(';').filter(Boolean) : []
    return (
      <div className="tag-list">
        {labels.map(l => <span key={l} className="tag">{l}</span>)}
      </div>
    )
  }
  if (col === 'Linked Issues') {
    const links = value ? value.split(';').filter(Boolean) : []
    return (
      <div className="tag-list">
        {links.map(l => <span key={l} className="tag linked-tag">{l}</span>)}
      </div>
    )
  }
  return value
}

export default function IssueTable({ groups, sortCol, sortDir, onSort }) {
  const totalIssues = groups.reduce((sum, g) => sum + g.issues.length, 0)
  if (totalIssues === 0) {
    return <div className="loading">No issues found</div>
  }

  return (
    <table className="issue-table">
      <thead>
        <tr>
          {COLUMNS.map(col => (
            <th key={col.key} onClick={() => onSort(col.key)}>
              {col.label}
              {sortCol === col.key && (
                <span className="sort-indicator">
                  {sortDir === 'asc' ? '\u25B2' : '\u25BC'}
                </span>
              )}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {groups.map((group, gi) => (
          <>
            {group.label && (
              <tr key={`group-${gi}`} className="group-header-row">
                <td colSpan={COLUMNS.length}>
                  <span className="group-label">{group.label}</span>
                  <span className="group-count">{group.issues.length} issues</span>
                </td>
              </tr>
            )}
            {group.issues.map((issue, i) => (
              <tr key={issue.Key || `${gi}-${i}`} className={group.label ? 'grouped-row' : ''}>
                {COLUMNS.map(col => (
                  <td key={col.key}>{renderCell(col.key, issue[col.key] || '')}</td>
                ))}
              </tr>
            ))}
          </>
        ))}
      </tbody>
    </table>
  )
}
