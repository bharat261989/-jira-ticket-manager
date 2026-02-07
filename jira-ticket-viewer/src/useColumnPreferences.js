import { useState, useCallback, useRef, useEffect } from 'react'

const VISIBILITY_KEY = 'jira-viewer-column-visibility'
const SIZING_KEY = 'jira-viewer-column-sizes'

function loadFromStorage(key, fallback) {
  try {
    const stored = localStorage.getItem(key)
    return stored ? JSON.parse(stored) : fallback
  } catch {
    return fallback
  }
}

function saveToStorage(key, value) {
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch {
    // localStorage full or unavailable
  }
}

export function useColumnPreferences() {
  const [columnVisibility, setColumnVisibility] = useState(
    () => loadFromStorage(VISIBILITY_KEY, {})
  )
  const [columnSizing, setColumnSizing] = useState(
    () => loadFromStorage(SIZING_KEY, {})
  )

  const sizingSaveTimer = useRef(null)

  useEffect(() => {
    return () => clearTimeout(sizingSaveTimer.current)
  }, [])

  const onColumnVisibilityChange = useCallback((updater) => {
    setColumnVisibility(prev => {
      const next = typeof updater === 'function' ? updater(prev) : updater
      saveToStorage(VISIBILITY_KEY, next)
      return next
    })
  }, [])

  const onColumnSizingChange = useCallback((updater) => {
    setColumnSizing(prev => {
      const next = typeof updater === 'function' ? updater(prev) : updater
      clearTimeout(sizingSaveTimer.current)
      sizingSaveTimer.current = setTimeout(() => saveToStorage(SIZING_KEY, next), 300)
      return next
    })
  }, [])

  const resetPreferences = useCallback(() => {
    setColumnVisibility({})
    setColumnSizing({})
    localStorage.removeItem(VISIBILITY_KEY)
    localStorage.removeItem(SIZING_KEY)
  }, [])

  return {
    columnVisibility,
    columnSizing,
    onColumnVisibilityChange,
    onColumnSizingChange,
    resetPreferences,
  }
}
