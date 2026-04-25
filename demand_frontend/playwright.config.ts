import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:5176',
    trace: 'on-first-retry',
  },
  reporter: [['list'], ['html', { open: 'never' }]],
})

