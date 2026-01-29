import { useState, useEffect } from 'react'
import './App.css'
import IssueTable from './IssueTable'

function parseCSV(text) {
  const lines = text.trim().split('\n')
  if (lines.length === 0) return []
  const headers = lines[0].split(',')
  return lines.slice(1).map(line => {
    const values = []
    let current = ''
    let inQuotes = false
    for (let i = 0; i < line.length; i++) {
      const ch = line[i]
      if (ch === '"') {
        inQuotes = !inQuotes
      } else if (ch === ',' && !inQuotes) {
        values.push(current.trim())
        current = ''
      } else {
        current += ch
      }
    }
    values.push(current.trim())
    const obj = {}
    headers.forEach((h, i) => {
      obj[h.trim()] = values[i] || ''
    })
    return obj
  })
}

function buildGroups(issues) {
  // Group by linked issue: each ticket links to at most one external ticket
  // (e.g. multiple PROJ tickets linked to the same NOC ticket)
  const buckets = {}
  const ungrouped = []

  for (const issue of issues) {
    const linked = (issue['Linked Issues'] || '').trim()
    if (linked) {
      if (!buckets[linked]) buckets[linked] = []
      buckets[linked].push(issue)
    } else {
      ungrouped.push(issue)
    }
  }

  // Groups with 2+ tickets first (sorted by size desc), then single-linked, then unlinked
  const multiGroups = []
  const singleLinked = []

  for (const [key, group] of Object.entries(buckets)) {
    if (group.length > 1) {
      multiGroups.push({ label: `Linked to ${key}`, issues: group })
    } else {
      singleLinked.push(...group)
    }
  }

  multiGroups.sort((a, b) => b.issues.length - a.issues.length)

  const result = [...multiGroups]
  if (singleLinked.length > 0 || ungrouped.length > 0) {
    result.push({ label: null, issues: [...singleLinked, ...ungrouped] })
  }
  return result
}

// Date filter helper functions
function getDateRange(filterType) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)

  const yesterday = new Date(today)
  yesterday.setDate(yesterday.getDate() - 1)

  switch (filterType) {
    case 'today':
      return { start: today, end: new Date(today.getTime() + 86400000) }
    case 'yesterday':
      return { start: yesterday, end: today }
    case 'last2days':
      return { start: yesterday, end: new Date(today.getTime() + 86400000) }
    case 'weekend': {
      // Find the most recent weekend (Saturday-Sunday)
      const dayOfWeek = today.getDay() // 0=Sun, 1=Mon, ..., 6=Sat
      let saturday, sunday

      if (dayOfWeek === 0) {
        // Today is Sunday - use yesterday (Sat) and today (Sun)
        saturday = new Date(today)
        saturday.setDate(saturday.getDate() - 1)
        sunday = new Date(today.getTime() + 86400000)
      } else if (dayOfWeek === 6) {
        // Today is Saturday - use today (Sat) and tomorrow (Sun)
        saturday = today
        sunday = new Date(today)
        sunday.setDate(sunday.getDate() + 2)
      } else {
        // Weekday - find last weekend
        const daysToLastSunday = dayOfWeek
        sunday = new Date(today)
        sunday.setDate(sunday.getDate() - daysToLastSunday)
        saturday = new Date(sunday)
        saturday.setDate(saturday.getDate() - 1)
        sunday = new Date(sunday.getTime() + 86400000) // End of Sunday
      }
      return { start: saturday, end: sunday }
    }
    default:
      return null
  }
}

function matchesDateFilter(issue, filterType) {
  if (!filterType) return true

  const createdStr = issue['Created Date'] || issue['Created']
  if (!createdStr) return false

  const created = new Date(createdStr)
  if (isNaN(created.getTime())) return false

  const range = getDateRange(filterType)
  if (!range) return true

  return created >= range.start && created < range.end
}

function App() {
  const [issues, setIssues] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [search, setSearch] = useState('')
  const [dateFilter, setDateFilter] = useState(null)
  const [sortCol, setSortCol] = useState(null)
  const [sortDir, setSortDir] = useState('asc')

  useEffect(() => {
    fetch('/data/synced-issues.csv')
      .then(res => {
        if (!res.ok) throw new Error(`Failed to load CSV (${res.status})`)
        return res.text()
      })
      .then(text => {
        setIssues(parseCSV(text))
        setLoading(false)
      })
      .catch(err => {
        setError(err.message)
        setLoading(false)
      })
  }, [])

  const handleSort = (col) => {
    if (sortCol === col) {
      setSortDir(d => d === 'asc' ? 'desc' : 'asc')
    } else {
      setSortCol(col)
      setSortDir('asc')
    }
  }

  const filtered = issues.filter(issue => {
    // Date filter
    if (!matchesDateFilter(issue, dateFilter)) return false

    // Text search
    if (!search) return true
    const q = search.toLowerCase()
    return Object.values(issue).some(v => v.toLowerCase().includes(q))
  })

  const sorted = [...filtered].sort((a, b) => {
    if (!sortCol) return 0
    const aVal = (a[sortCol] || '').toLowerCase()
    const bVal = (b[sortCol] || '').toLowerCase()
    if (aVal < bVal) return sortDir === 'asc' ? -1 : 1
    if (aVal > bVal) return sortDir === 'asc' ? 1 : -1
    return 0
  })

  // Group issues by shared linked issue key
  const groups = buildGroups(sorted)

  return (
    <div className="app">
      <header className="header">
        <h1>Jira Issue Tracker</h1>
        <p>Synced issues from project board</p>
      </header>
      <div className="toolbar">
        <input
          type="text"
          className="search-input"
          placeholder="Search issues by key, summary, assignee, status..."
          value={search}
          onChange={e => setSearch(e.target.value)}
        />
        <div className="date-filters">
          <button
            className={`filter-btn ${dateFilter === null ? 'active' : ''}`}
            onClick={() => setDateFilter(null)}
          >
            All
          </button>
          <button
            className={`filter-btn ${dateFilter === 'today' ? 'active' : ''}`}
            onClick={() => setDateFilter(dateFilter === 'today' ? null : 'today')}
          >
            Today
          </button>
          <button
            className={`filter-btn ${dateFilter === 'yesterday' ? 'active' : ''}`}
            onClick={() => setDateFilter(dateFilter === 'yesterday' ? null : 'yesterday')}
          >
            Yesterday
          </button>
          <button
            className={`filter-btn ${dateFilter === 'last2days' ? 'active' : ''}`}
            onClick={() => setDateFilter(dateFilter === 'last2days' ? null : 'last2days')}
          >
            Last 2 Days
          </button>
          <button
            className={`filter-btn ${dateFilter === 'weekend' ? 'active' : ''}`}
            onClick={() => setDateFilter(dateFilter === 'weekend' ? null : 'weekend')}
          >
            Weekend
          </button>
        </div>
        <span className="issue-count">
          {sorted.length} of {issues.length} issues
        </span>
      </div>
      <div className="table-container">
        {loading && <div className="loading">Loading issues...</div>}
        {error && <div className="error-msg">Error: {error}</div>}
        {!loading && !error && (
          <IssueTable
            groups={groups}
            sortCol={sortCol}
            sortDir={sortDir}
            onSort={handleSort}
          />
        )}
      </div>
    </div>
  )
}

export default App
