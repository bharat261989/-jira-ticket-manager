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

function App() {
  const [issues, setIssues] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [search, setSearch] = useState('')
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
