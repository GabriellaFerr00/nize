/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { readFileSync } from 'node:fs'
import { defineConfig } from 'vite'

/** Headers of the `/*` block in public/_headers, so `vite preview` (E2E) runs under the real CSP. */
function productionHeaders(): Record<string, string> {
  const headers: Record<string, string> = {}
  let inRootBlock = false
  for (const line of readFileSync('public/_headers', 'utf-8').split('\n')) {
    if (!line.startsWith(' ')) inRootBlock = line.trim() === '/*'
    else if (inRootBlock && line.includes(':')) {
      const [name, ...value] = line.trim().split(':')
      headers[name] = value.join(':').trim()
    }
  }
  delete headers['Strict-Transport-Security'] // preview is plain http
  return headers
}

// Minimum coverage (NFR-010); the pipeline sets COVERAGE_MIN.
const coverageMin = Number(process.env.COVERAGE_MIN ?? 80)

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // The API runs on :8080 in development; the browser only talks to the Vite origin.
    proxy: { '/api': 'http://localhost:8080' },
  },
  // `vite preview` (used by the E2E tests) talks to the API the same way.
  preview: {
    proxy: { '/api': 'http://localhost:8080' },
    headers: productionHeaders(),
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    include: ['src/**/*.test.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/test/**', 'src/main.tsx', 'src/vite-env.d.ts'],
      reporter: ['text-summary', 'lcov'],
      thresholds: { lines: coverageMin, statements: coverageMin, functions: coverageMin, branches: coverageMin },
    },
  },
})
