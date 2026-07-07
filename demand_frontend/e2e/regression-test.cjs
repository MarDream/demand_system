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

function setupListeners(page, tag) {
  const userKey = tag === 'ADMIN' ? 'admin' : 'wujiahua';
  page.on('console', msg => {
    const type = msg.type();
    const text = msg.text();
    logs[userKey].consoleMessages.push({ type, text, url: msg.location().url, line: msg.location().lineNumber, timestamp: new Date().toISOString() });
    if (type === 'error') {
      logs.summary.errors.push({ user: userKey, type, text, url: msg.location().url, line: msg.location().lineNumber });
    } else if (type === 'warning' && !text.includes('[vite]') && !text.includes('Download the Vue DevTools')) {
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
    const accessible = !url.includes('/login');
    return { path, title, url, accessible };
  } catch (e) {
    logs[userKey].consoleMessages.push({ type: 'NAV_ERROR', text: `${path}: ${e.message}`, timestamp: new Date().toISOString() });
    logs.summary.errors.push({ user: userKey, type: 'NAV_ERROR', text: path, detail: e.message });
    return { path, error: e.message };
  }
}

async function formLogin(page, username, password, tag) {
  console.log(`[${tag}] Form login as ${username}...`);
  try {
    await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(1000);
    
    // Fill username
    const usernameInput = page.locator('input[type="text"], input[placeholder*="用户"], input[placeholder*="账号"], input:first-of-type').first();
    await usernameInput.fill(username);
    
    // Fill password
    const passwordInput = page.locator('input[type="password"]').first();
    await passwordInput.fill(password);
    
    // Click login button
    const loginBtn = page.getByRole('button', { name: /登.*录|登录|Login/i }).first();
    await loginBtn.click();
    
    // Wait for redirect
    await page.waitForURL(url => !url.toString().includes('/login'), { timeout: 10000 });
    await page.waitForTimeout(2000);
    
    const finalUrl = page.url();
    const success = !finalUrl.includes('/login');
    console.log(`[${tag}] Form login ${success ? 'SUCCESS' : 'FAILED'} -> ${finalUrl}`);
    return { step: 'form_login', result: success ? 'SUCCESS' : 'FAILED', url: finalUrl };
  } catch (e) {
    console.error(`[${tag}] Form login FAILED: ${e.message}`);
    // Take screenshot for debugging
    await page.screenshot({ path: path.join(LOGS_DIR, `${tag}-form-login-fail.png`) }).catch(() => {});
    return { step: 'form_login', result: 'FAILED', error: e.message };
  }
}

async function testUser(browser, username, password, tag) {
  console.log(`\n========== [${tag}] TEST ==========`);
  const userKey = tag === 'ADMIN' ? 'admin' : 'wujiahua';
  const userLog = [];
  
  const context = await browser.newContext();
  const page = await context.newPage();
  setupListeners(page, tag);
  
  // Test 1: Form login (CORS fix verification)
  const formResult = await formLogin(page, username, password, tag);
  userLog.push(formResult);
  
  if (formResult.result === 'FAILED') {
    console.log(`[${tag}] Form login failed, falling back to API login...`);
    // Fallback: API login + cookie injection
    try {
      const resp = await fetch(`${API_BASE}/api/v1/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      });
      const json = await resp.json();
      const token = json.data.accessToken;
      const refreshToken = json.data.refreshToken;
      await context.addCookies([{
        name: 'access_token', value: token,
        domain: '127.0.0.1', path: '/', httpOnly: false, secure: false, sameSite: 'Lax'
      }, {
        name: 'refresh_token', value: refreshToken,
        domain: '127.0.0.1', path: '/', httpOnly: false, secure: false, sameSite: 'Lax'
      }]);
      await page.goto(`${BASE_URL}/dashboard`, { waitUntil: 'networkidle', timeout: 15000 });
      await page.waitForTimeout(2000);
      userLog.push({ step: 'api_login_fallback', result: 'SUCCESS', url: page.url() });
    } catch (e) {
      userLog.push({ step: 'api_login_fallback', result: 'FAILED', error: e.message });
      logs[userKey].actions = userLog;
      await context.close();
      return;
    }
  }
  
  // Test 2: Navigate all pages
  const pagesToTest = tag === 'ADMIN'
    ? [
        '/dashboard', '/requirements', '/iterations', '/statistics',
        '/settings/rag', '/settings/knowledge', '/settings/llm',
        '/system/workflow-config', '/system/workflow-migration',
        '/settings', '/settings/users', '/settings/roles', '/settings/menus',
        '/settings/projects', '/settings/requirements', '/settings/requirement-templates'
      ]
    : [
        '/dashboard', '/requirements', '/iterations', '/statistics',
        '/settings/rag', '/settings/knowledge',
        // These should be denied for non-admin
        '/settings', '/system/workflow-config', '/system/workflow-migration',
        '/settings/users', '/settings/roles', '/settings/menus', '/settings/projects'
      ];
  
  for (const p of pagesToTest) {
    const result = await navigateAndCheck(page, p, tag);
    userLog.push({ step: 'navigate', ...result });
    const status = result.error ? 'ERROR' : (result.accessible ? 'ACCESSIBLE' : 'REDIRECTED');
    console.log(`[${tag}] ${p}: ${status}${result.title ? ' (' + result.title + ')' : ''}`);
  }
  
  // Test 3: Logout
  try {
    await page.goto(`${BASE_URL}/dashboard`, { waitUntil: 'networkidle', timeout: 10000 });
    // Try clicking user menu / logout
    const logoutBtn = page.locator('button:has-text("退出"), button:has-text("登出"), a:has-text("退出"), a:has-text("登出")').first();
    if (await logoutBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
      await logoutBtn.click();
      await page.waitForTimeout(1000);
      userLog.push({ step: 'logout', result: 'SUCCESS', url: page.url() });
    } else {
      userLog.push({ step: 'logout', result: 'SKIPPED', reason: 'No logout button found' });
    }
  } catch (e) {
    userLog.push({ step: 'logout', result: 'ERROR', error: e.message });
  }
  
  await context.close();
  logs[userKey].actions = userLog;
}

async function run() {
  console.log('=== REGRESSION TEST (Post-Fix Verification) ===');
  console.log(`Base URL: ${BASE_URL}`);
  console.log(`API Base: ${API_BASE}`);
  console.log(`Timestamp: ${new Date().toISOString()}`);
  
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox', '--disable-setuid-sandbox'] });
  
  await testUser(browser, 'admin', 'admin123', 'ADMIN');
  await testUser(browser, 'wujiahua', 'wujiahua376', 'WUJIAHUA');
  
  await browser.close();
  
  // Save results
  const resultPath = path.join(LOGS_DIR, 'regression-test-results.json');
  fs.writeFileSync(resultPath, JSON.stringify(logs, null, 2));
  
  console.log('\n========== REGRESSION TEST COMPLETE ==========');
  console.log(`Results saved to: ${resultPath}`);
  console.log(`JS Console Errors: ${logs.summary.errors.length}`);
  console.log(`JS Console Warnings: ${logs.summary.warnings.length}`);
  console.log(`HTTP 4xx/5xx: ${logs.summary.httpErrors.length}`);
  console.log(`Page Crashes: ${logs.summary.pageErrors.length}`);
  
  // Print error details
  if (logs.summary.errors.length > 0) {
    console.log('\n--- Console Errors ---');
    logs.summary.errors.forEach((e, i) => console.log(`  ${i+1}. [${e.user}] ${e.text?.substring(0, 120)}`));
  }
  if (logs.summary.httpErrors.length > 0) {
    console.log('\n--- HTTP Errors ---');
    logs.summary.httpErrors.forEach((e, i) => console.log(`  ${i+1}. [${e.user}] ${e.status} ${e.url?.substring(0, 100)}`));
  }
  if (logs.summary.warnings.length > 0) {
    console.log('\n--- Console Warnings ---');
    logs.summary.warnings.forEach((e, i) => console.log(`  ${i+1}. [${e.user}] ${e.text?.substring(0, 120)}`));
  }
}

run().catch(e => { console.error('CRASH:', e); process.exit(1); });
