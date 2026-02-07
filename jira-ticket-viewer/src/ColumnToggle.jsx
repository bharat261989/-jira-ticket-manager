import { useState, useRef, useEffect } from 'react'

export default function ColumnToggle({ table, onReset }) {
  const [open, setOpen] = useState(false)
  const ref = useRef(null)

  useEffect(() => {
    if (!open) return
    function handleClickOutside(e) {
      if (ref.current && !ref.current.contains(e.target)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [open])

  const allColumns = table.getAllColumns()

  return (
    <div className="column-toggle" ref={ref}>
      <button
        className="filter-btn column-toggle-btn"
        onClick={() => setOpen(!open)}
      >
        Columns
        <span className="column-toggle-icon">{open ? '\u25B2' : '\u25BC'}</span>
      </button>
      {open && (
        <div className="column-toggle-dropdown">
          <div className="column-toggle-header">
            <label className="column-toggle-item">
              <input
                type="checkbox"
                checked={table.getIsAllColumnsVisible()}
                onChange={table.getToggleAllColumnsVisibilityHandler()}
              />
              <span>Toggle All</span>
            </label>
          </div>
          <div className="column-toggle-list">
            {allColumns.map(column => (
              <label key={column.id} className="column-toggle-item">
                <input
                  type="checkbox"
                  checked={column.getIsVisible()}
                  onChange={column.getToggleVisibilityHandler()}
                />
                <span>{column.columnDef.header}</span>
              </label>
            ))}
          </div>
          <div className="column-toggle-footer">
            <button className="column-toggle-reset" onClick={onReset}>
              Reset to defaults
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
