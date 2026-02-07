import { createColumnHelper } from '@tanstack/react-table'
import { getIssueUrl } from './config'

const MONTHS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec']

function formatDateTime(raw) {
  if (!raw) return ''
  const d = new Date(raw)
  if (isNaN(d.getTime())) return raw
  const day = d.getDate()
  const mon = MONTHS[d.getMonth()]
  const year = d.getFullYear()
  let hours = d.getHours()
  const mins = String(d.getMinutes()).padStart(2, '0')
  const ampm = hours >= 12 ? 'PM' : 'AM'
  hours = hours % 12 || 12
  return `${day} ${mon} ${year} \u00B7 ${hours}:${mins} ${ampm}`
}

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

const columnHelper = createColumnHelper()

export const columns = [
  columnHelper.accessor('Key', {
    header: 'Key',
    size: 120,
    minSize: 60,
    cell: info => {
      const value = info.getValue() || ''
      const url = getIssueUrl(value)
      if (url) {
        return (
          <a href={url} target="_blank" rel="noopener noreferrer" className="issue-key">
            {value}
          </a>
        )
      }
      return <span className="issue-key">{value}</span>
    },
  }),
  columnHelper.accessor('Type', {
    header: 'Type',
    size: 80,
    minSize: 50,
    cell: info => {
      const value = info.getValue() || ''
      return <span className={`badge ${typeClass(value)}`}>{value}</span>
    },
  }),
  columnHelper.accessor('Summary', {
    header: 'Summary',
    size: 300,
    minSize: 100,
    cell: info => info.getValue() || '',
  }),
  columnHelper.accessor('Status', {
    header: 'Status',
    size: 100,
    minSize: 60,
    cell: info => {
      const value = info.getValue() || ''
      return <span className={`badge ${statusClass(value)}`}>{value}</span>
    },
  }),
  columnHelper.accessor('Priority', {
    header: 'Priority',
    size: 90,
    minSize: 60,
    cell: info => {
      const value = info.getValue() || ''
      return <span className={`badge ${priorityClass(value)}`}>{value}</span>
    },
  }),
  columnHelper.accessor('Severity', {
    header: 'Severity',
    size: 90,
    minSize: 60,
    cell: info => {
      const value = info.getValue() || ''
      if (!value) return <span className="no-value">&mdash;</span>
      return <span className={`badge ${severityClass(value)}`}>{value}</span>
    },
  }),
  columnHelper.accessor('Assignee', {
    header: 'Assignee',
    size: 120,
    minSize: 60,
    cell: info => info.getValue() || '',
  }),
  columnHelper.accessor('Reporter', {
    header: 'Reporter',
    size: 120,
    minSize: 60,
    cell: info => info.getValue() || '',
  }),
  columnHelper.accessor('Created', {
    header: 'Created',
    size: 175,
    minSize: 80,
    cell: info => formatDateTime(info.getValue()),
  }),
  columnHelper.accessor('Updated', {
    header: 'Updated',
    size: 175,
    minSize: 80,
    cell: info => formatDateTime(info.getValue()),
  }),
  columnHelper.accessor('Comments', {
    header: 'Comments',
    size: 90,
    minSize: 50,
    cell: info => {
      const value = info.getValue() || ''
      const count = parseInt(value, 10) || 0
      if (count === 0) return <span className="no-value">0</span>
      return <span className="comment-count">{count}</span>
    },
  }),
  columnHelper.accessor('Labels', {
    header: 'Labels',
    size: 150,
    minSize: 60,
    cell: info => {
      const value = info.getValue() || ''
      const labels = value ? value.split(';').filter(Boolean) : []
      return (
        <div className="tag-list">
          {labels.map(l => <span key={l} className="tag">{l}</span>)}
        </div>
      )
    },
  }),
  columnHelper.accessor(row => row['Linked Issues'], {
    id: 'Linked Issues',
    header: 'Linked',
    size: 150,
    minSize: 60,
    cell: info => {
      const value = info.getValue() || ''
      const links = value ? value.split(';').filter(Boolean) : []
      return (
        <div className="tag-list">
          {links.map(l => <span key={l} className="tag linked-tag">{l}</span>)}
        </div>
      )
    },
  }),
]
