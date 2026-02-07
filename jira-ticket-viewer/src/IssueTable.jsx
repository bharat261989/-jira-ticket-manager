import React from 'react'
import { flexRender } from '@tanstack/react-table'

export default function IssueTable({ table, groups, sortCol, sortDir, onSort }) {
  const totalIssues = groups.reduce((sum, g) => sum + g.issues.length, 0)
  if (totalIssues === 0) {
    return <div className="loading">No issues found</div>
  }

  const headerGroups = table.getHeaderGroups()
  const visibleColumns = table.getVisibleFlatColumns()
  const visibleColumnCount = visibleColumns.length

  return (
    <table
      className="issue-table"
      style={{ width: table.getCenterTotalSize(), tableLayout: 'fixed' }}
    >
      <thead>
        {headerGroups.map(headerGroup => (
          <tr key={headerGroup.id}>
            {headerGroup.headers.map(header => {
              const colKey = header.column.id
              return (
                <th
                  key={header.id}
                  style={{ width: header.getSize() }}
                  onClick={() => onSort(colKey)}
                >
                  {flexRender(header.column.columnDef.header, header.getContext())}
                  {sortCol === colKey && (
                    <span className="sort-indicator">
                      {sortDir === 'asc' ? '\u25B2' : '\u25BC'}
                    </span>
                  )}
                  <div
                    onMouseDown={header.getResizeHandler()}
                    onTouchStart={header.getResizeHandler()}
                    className={`resizer ${header.column.getIsResizing() ? 'isResizing' : ''}`}
                    onClick={e => e.stopPropagation()}
                  />
                </th>
              )
            })}
          </tr>
        ))}
      </thead>
      <tbody>
        {groups.map((group, gi) => (
          <React.Fragment key={`group-${gi}`}>
            {group.label && (
              <tr className="group-header-row">
                <td colSpan={visibleColumnCount}>
                  <span className="group-label">{group.label}</span>
                  <span className="group-count">{group.issues.length} issues</span>
                </td>
              </tr>
            )}
            {group.issues.map((issue, i) => (
              <tr
                key={issue.Key || `${gi}-${i}`}
                className={group.label ? 'grouped-row' : ''}
              >
                {visibleColumns.map(column => {
                  const value = column.columnDef.accessorKey
                    ? (issue[column.columnDef.accessorKey] || '')
                    : (column.columnDef.accessorFn ? column.columnDef.accessorFn(issue) : '') || ''
                  const cellFn = column.columnDef.cell
                  return (
                    <td key={column.id} style={{ width: column.getSize() }}>
                      {typeof cellFn === 'function'
                        ? cellFn({ getValue: () => value, row: { original: issue } })
                        : value}
                    </td>
                  )
                })}
              </tr>
            ))}
          </React.Fragment>
        ))}
      </tbody>
    </table>
  )
}
