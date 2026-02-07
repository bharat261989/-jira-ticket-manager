import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'
import fs from 'fs'

function serveCsvPlugin() {
  // Use CSV_DATA_DIR env var if set, otherwise look for local data/ first, then ../data/
  const localData = path.resolve(import.meta.dirname, 'data')
  const parentData = path.resolve(import.meta.dirname, '..', 'data')
  const envDir = process.env.CSV_DATA_DIR

  const dataDir = envDir
    ? path.resolve(envDir)
    : fs.existsSync(localData)
      ? localData
      : parentData

  return {
    name: 'serve-csv',
    configureServer(server) {
      server.middlewares.use('/data', (req, res, next) => {
        const filePath = path.join(dataDir, req.url)
        if (fs.existsSync(filePath)) {
          res.setHeader('Content-Type', 'text/csv; charset=utf-8')
          fs.createReadStream(filePath).pipe(res)
        } else {
          next()
        }
      })
    }
  }
}

export default defineConfig({
  plugins: [react(), serveCsvPlugin()],
  server: {
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})
