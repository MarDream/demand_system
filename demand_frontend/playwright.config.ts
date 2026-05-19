import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  webServer: {
    command: '.\\node_modules\\.bin\\vite.cmd --host 127.0.0.1 --port 5173',
    url: 'http://127.0.0.1:5173/login',
    reuseExistingServer: true,
    timeout: 120_000,
  },
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:5173',
    launchOptions: process.env.CHROME_PATH
      ? { executablePath: process.env.CHROME_PATH }
      : undefined,
    trace: 'on-first-retry',
  },
  reporter: [['list'], ['html', { open: 'never' }]],
})
