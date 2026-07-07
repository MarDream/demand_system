const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const LOGS_DIR = path.join(__dirname, 'logs');
if (!fs.existsSync(LOGS_DIR)) fs.mkdirSync(LOGS_DIR, { recursive: true });

const BASE_URL = 'http://127.0.0.1:5170';
const API_BASE = 'http://127.0.0.1:8081';

let logs = {
  admin: { actions: [], consoleMessages: [] },
  wujiahua: { actions: [], consoleMessages: [] },
  summary: { errors: [], warnings: [], httpErrors: [], pageErrors: [] }
};

async function apiLogin(username, password) {
  const resp = await fetch(`${API_BASE}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  });
  if (!resp.ok) {
    throw new Error(`API login failed: ${resp.status} ${await resp.text()}`);
  }
  const json = await resp.json();
  return {
    accessToken: json.data.accessToken,
    refreshToken: json.data.refreshToken,
    userInfo: JSON.parse(Buffer.from(json.data.accessToken.split('.')[1], 'base64').toString())
  };
}

function setupListeners(page, tag) {
  const userKey = tag === 'ADMIN' ? 'admin' : 'wujiahua';
  page.on('console', msg => {
    const type = msg.type();
    const text = msg.text();
    logs[userKey].consoleMessages.push({ type, text, url: msg.location().url, line: msg.location().lineNumber, timestamp: new Date().toISOString() });
    if (type === 'error') {
      logs.summary.errors.push({ user: userKey, type, text, url: msg.location().url, line: msg.location().lineNumber });
    } else if (type === 'warning' && !text.includes('[vite]')) {
      logs.summary.warnings.push({ user: userKey, type, text, url: msg.location().url });
    }
  });
  page.on('pageerror', err => {
    logs[userKey].consoleMessages.push({ type: 'PAGE_ERROR', text: err.message, stack: err.stack, timestamp: new Date().toISOString() });
    logs.summary.pageErrors.push({ user: userKey, type: 'PAGE_ERROR', text: err.message });
  });
  page.on('response', async resp => {
    if (resp.status() >= 400) {
      const url = resp.url();
      logs[userKey].consoleMessages.push({ type: 'HTTP_ERROR', status: resp.status(), url, timestamp: new Date().toISOString() });
      logs.summary.httpErrors.push({ user: userKey, status: resp.status(), url });
    }
  });
}

async function navigateAndCheck(page, path, tag) {
  const userKey = tag === 'ADMIN' ? 'admin' : 'wujiahua';
  try {
    await page.goto(`${BASE_URL}${path}`, { waitUntil: 'networkidle', timeout: 25000 });
    await page.waitForTimeout(800);
    const title = await page.title().catch(() => 'N/A');
    const url = page.url();
    const accessible = !url.includes('/login') && !url.includes('/403') && !url.includes('/forbidden');
    return { path, title, url, accessible };
  } catch (e) {
    logs[userKey].consoleMessages.push({ type: 'NAV_ERROR', text: `${path}: ${e.message}`, timestamp: new Date().toISOString() });
    logs.summary.errors.push({ user: userKey, type: 'NAV_ERROR', text: path, detail: e.message });
    return { path, error: e.message };
  }
}

async function testUser(browser, username, password, tag) {
  console.log(`\n========== [${tag}] TEST ==========`);
  const userKey = tag === 'ADMIN' ? 'admin' : 'wujiahua';
  const userLog = [];
  
  // API login first
  console.log(`[${tag}] API login as ${username}...`);
  let loginInfo;
  try {
    loginInfo = await apiLogin(username, password);
    console.log(`[${tag}] API login OK. roles=${loginInfo.userInfo.roles?.join(',')}`);
    userLog.push({ step: 'api_login', result: 'SUCCESS', roles: loginInfo.userInfo.roles });
  } catch (e) {
    console.error(`[${tag}] API login FAILED: ${e.message}`);
    userLog.push({ step: 'api_login', result: 'FAILED', error: e.message });
    logs[userKey].actions = userLog;
    return;
  }
  
  // Create browser context with auth cookies
  const context = await browser.newContext();
  await context.addCookies([{
    name: 'access_token',
    value: loginInfo.accessToken,
    domain: '127.0.0.1',
    path: '/',
    httpOnly: false,
    secure: false,
    sameSite: 'Lax'
  }, {
    name: 'refresh_token',
    value: loginInfo.refreshToken,
    domain: '127.0.0.1',
    path: '/',
    httpOnly: false,
    secure: false,
    sameSite: 'Lax'
  }]);
  
  const page = await context.newPage();
  setupListeners(page, tag);
  
  // Verify login by navigating to dashboard
  const dash = await navigateAndCheck(page, '/dashboard', tag);
  userLog.push({ step: 'login_verify', ...dash });
  console.log(`[${tag}] Dashboard: ${dash.accessible ? 'ACCESSIBLE' : 'REDIRECTED'} -> ${dash.url}`);
  
  // Navigate all relevant pages
  const pagesToTest = tag === 'ADMIN'
    ? [
        '/dashboard', '/requirements', '/iterations', '/statistics',
        '/settings/rag', '/settings/knowledge', '/settings/llm',
        '/system/workflow-config', '/system/workflow-config/editor', '/system/workflow-migration',
        '/settings', '/settings/users', '/settings/roles', '/settings/menus',
        '/settings/projects', '/settings/requirements', '/settings/requirement-templates'
      ]
    : [
        '/dashboard', '/requirements', '/iterations', '/statistics',
        '/settings/rag', '/settings/knowledge',
        '/settings', '/system/workflow-config', '/system/workflow-migration',
        '/settings/users', '/settings/roles', '/settings/menus', '/settings/projects'
      ];
  
  for (const p of pagesToTest) {
    const result = await navigateAndCheck(page, p, tag);
    userLog.push({ step: 'navigate', ...result });
    const status = result.error ? 'ERROR' : (result.accessible ? 'ACCESSIBLE' : 'REDIRECTED');
    console.log(`[${tag}] ${p}: ${status}${result.title ? ' (' + result.title + ')' : ''}`);
  }
  
  await context.close();
  logs[userKey].actions = userLog;
}

async function run() {
  console.log('Starting full functional test run...');
  console.log(`Base URL: ${BASE_URL}`);
  console.log(`API Base: ${API_BASE}`);
  
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox', '--disable-setuid-sandbox'] });
  
  await testUser(browser, 'admin', 'admin123', 'ADMIN');
  await testUser(browser, 'wujiahua', 'wujiahua376', 'WUJIAHUA');
  
  await browser.close();
  
  // Save results
  const resultPath = path.join(LOGS_DIR, 'e2e-test-results.json');
  fs.writeFileSync(resultPath, JSON.stringify(logs, null, 2));
  
  console.log('\n========== TEST COMPLETE ==========');
  console.log(`[RESULTS] Saved to ${resultPath}`);
  console.log(`[RESULTS] JS Console Errors: ${logs.summary.errors.length}`);
  console.log(`[RESULTS] JS Console Warnings: ${logs.summary.warnings.length}`);
  console.log(`[RESULTS] HTTP 4xx/5xx: ${logs.summary.httpErrors.length}`);
  console.log(`[RESULTS] Page Crashes: ${logs.summary.pageErrors.length}`);
}

run().catch(e => { console.error('CRASH:', e); process.exit(1); });
