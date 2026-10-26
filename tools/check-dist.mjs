#!/usr/bin/env node
/**
 * dist 产物校验
 * ---------------------------------------------------------------------------
 * 由 `demand_frontend` 的 build 脚本在 vite build 之后调用：
 *   "build": "vue-tsc && vite build && node ../tools/check-dist.mjs dist"
 *
 * 与 vite 插件里的 guard 互补：
 *   · guard 在打包内存里扫（能定位到源 chunk，但只有 vite 加载配置后生效）
 *   · 本脚本在产物落盘后扫（兜底，防止 guard 被绕过或插件未启用）
 *
 * 校验项：
 *   1. dist 目录存在且含 index.html
 *   2. index.html 里引用的 /assets/* 文件真实存在
 *   3. 所有 .html / .js / .css 不含 dev 地址（html 用 broad，js/css 用 strict）
 *   4. 提示是否残留 .map 源映射（泄漏源码，非致命）
 *
 * 用法：node tools/check-dist.mjs dist
 * 逃生阀：ALLOW_DEV_ADDR=1
 * ---------------------------------------------------------------------------
 */
import { readdirSync, readFileSync, statSync, existsSync } from 'node:fs';
import { join, relative, extname } from 'node:path';
import { scanText } from './wb-dev-address-guard.mjs';

const args = process.argv.slice(2);
const distDir = args.find((a) => !a.startsWith('--')) || 'dist';
const baseArg = args.find((a) => a.startsWith('--expect-base='));
// H5 产物部署在 /h5/ 下，构建时 vite 的 base 也是 '/h5/'，
// 因此 index.html 的资源引用必须以 /h5/ 开头。PC 端不传此参数。
const expectBase = baseArg ? baseArg.split('=')[1].replace(/^\/+|\/+$/g, '') : '';
const allowDevAddr = process.env.ALLOW_DEV_ADDR === '1';

const errors = [];
const warnings = [];

function fail(msg) {
  errors.push(msg);
}
function warn(msg) {
  warnings.push(msg);
}

/** 递归列出文件 */
function walk(dir, acc = []) {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    const st = statSync(full);
    if (st.isDirectory()) walk(full, acc);
    else acc.push(full);
  }
  return acc;
}

// ---------- 1. 目录与入口 ----------
if (!existsSync(distDir)) {
  console.error(`[check-dist] ✗ 产物目录不存在: ${distDir}`);
  process.exit(1);
}

const indexPath = join(distDir, 'index.html');
if (!existsSync(indexPath)) {
  fail(`缺少入口文件: ${relative(distDir, indexPath)}`);
}

const files = walk(distDir);
if (!files.length) fail('产物目录为空');

// ---------- 2. index.html 引用的资源必须存在 ----------
if (existsSync(indexPath)) {
  const html = readFileSync(indexPath, 'utf8');
  const refs = [...html.matchAll(/(?:src|href)="([^"]+)"/g)]
    .map((m) => m[1])
    .filter((u) => u.startsWith('/') && !u.startsWith('//'));

  for (const ref of refs) {
    // 指定了 base 时，引用必须带该前缀
    if (expectBase && !ref.startsWith(`/${expectBase}/`)) {
      fail(`index.html 的资源引用缺少 base 前缀 /${expectBase}/: ${ref}`);
      continue;
    }

    // 去掉前导 / 与 base 前缀后，映射到磁盘路径
    let rel = ref.replace(/^\//, '');
    if (expectBase) rel = rel.slice(expectBase.length + 1);

    const target = join(distDir, rel);
    if (!existsSync(target)) fail(`index.html 引用的资源不存在: ${ref}`);
  }
}

// ---------- 3. dev 地址扫描 ----------
const TEXT_EXT = new Set(['.html', '.js', '.mjs', '.css', '.json']);
let scanned = 0;

for (const file of files) {
  if (!TEXT_EXT.has(extname(file))) continue;
  const code = readFileSync(file, 'utf8');
  scanned += 1;

  // html 是手写的，用 broad；js/css 多为三方混装，用 strict 避免误报
  const level = extname(file) === '.html' ? 'broad' : 'strict';
  const hits = scanText(code, level);

  if (hits.length) {
    const rel = relative(distDir, file);
    const sample = hits.slice(0, 5).map((h) => `第${h.line}行 ${h.text}`).join('; ');
    if (allowDevAddr) {
      warn(`ALLOW_DEV_ADDR=1 放行 ${rel}: ${sample}`);
    } else {
      fail(`${rel} 含 dev 地址（${hits.length} 处）: ${sample}`);
    }
  }
}

// ---------- 4. 源映射残留 ----------
const maps = files.filter((f) => f.endsWith('.map'));
if (maps.length) {
  warn(`残留 ${maps.length} 个 .map 源映射文件（会泄漏源码，建议生产关闭 sourcemap）`);
}

// ---------- 汇总 ----------
if (warnings.length) {
  console.warn('\n[check-dist] ⚠️ 警告：');
  warnings.forEach((w) => console.warn(`  · ${w}`));
}

if (errors.length) {
  console.error('\n[check-dist] ✗ 校验未通过：');
  errors.forEach((e) => console.error(`  · ${e}`));
  console.error('');
  process.exit(1);
}

const totalSize = files.reduce((n, f) => n + statSync(f).size, 0);
console.log(
  `[check-dist] ✓ 通过：${files.length} 个文件 / ${scanned} 个文本文件已扫描 / ` +
    `${(totalSize / 1024 / 1024).toFixed(2)} MB`,
);
