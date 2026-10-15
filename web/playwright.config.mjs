import { defineConfig } from '@playwright/test';
import { fileURLToPath } from 'node:url';

const executablePath = process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH;
const launchOptions = executablePath ? { executablePath } : {};

export default defineConfig({
  testDir: './tests',
  testMatch: ['browser.spec.mjs', 'lifecycle.spec.mjs'],
  outputDir: '../out/web/browser-results',
  timeout: 30_000,
  expect: { timeout: 8_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { outputFolder: '../out/web/browser-report', open: 'never' }]],
  use: {
    baseURL: 'http://127.0.0.1:4173/Game-Quoridor/',
    headless: true,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    acceptDownloads: true,
    launchOptions,
  },
  projects: [
    { name: 'desktop', use: { browserName: 'chromium', viewport: { width: 1280, height: 900 } } },
    { name: 'mobile', testMatch: 'browser.spec.mjs', use: { browserName: 'chromium', viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, deviceScaleFactor: 1 } },
  ],
  webServer: {
    command: 'node scripts/serve.mjs --port 4173',
    cwd: fileURLToPath(new URL('.', import.meta.url)),
    url: 'http://127.0.0.1:4173/Game-Quoridor/',
    reuseExistingServer: false,
    timeout: 15_000,
  },
});
