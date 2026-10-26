#!/usr/bin/env node
/**
 * 守卫自测
 * ---------------------------------------------------------------------------
 * 校验 `tools/wb-dev-address-guard.mjs` 的判定是否符合预期。
 * README 约定：`node tools/selftest-guard.mjs`
 *
 * 为什么需要自测：守卫是「宁可误报也不能漏报」的闸门，但它自己写错了会
 * 两头出错 —— 要么漏放 dev 地址进生产（2026-10-12 事故），
 * 要么把正常代码误判成 dev 地址导致构建失败（2026-10-26 重建时踩过：
 * `0.0.0.0` 的点号没转义，`setHours(0,0,0,0)` 被误报）。
 *
 * 用例分两组：
 *   · 必须命中（catch）—— 真 dev 地址
 *   · 必须放过（pass） —— 容易误报的正常代码
 * ---------------------------------------------------------------------------
 */
import { scanText, renderFailure } from './wb-dev-address-guard.mjs';

let failed = 0;
let passed = 0;

/** 断言：在指定级别下必须命中 */
function catchCase(name, code, level = 'broad') {
  const hits = scanText(code, level);
  if (hits.length > 0) {
    passed += 1;
    console.log(`  ✓ [${level}] 命中  ${name}  → ${hits[0].text}`);
  } else {
    failed += 1;
    console.error(`  ✗ [${level}] 漏报  ${name}  （应命中但未命中）`);
  }
}

/** 断言：在指定级别下必须不命中 */
function passCase(name, code, level = 'broad') {
  const hits = scanText(code, level);
  if (hits.length === 0) {
    passed += 1;
    console.log(`  ✓ [${level}] 放过  ${name}`);
  } else {
    failed += 1;
    console.error(
      `  ✗ [${level}] 误报  ${name}  （不该命中却命中 ${hits.length} 处：${hits
        .slice(0, 3)
        .map((h) => h.text)
        .join(', ')}）`,
    );
  }
}

console.log('\n[selftest-guard] 必须命中的用例');
catchCase('ws 回环地址', "const u = 'ws://localhost:8081'");
catchCase('http 回环地址', "const u = 'http://127.0.0.1:8080/api'");
catchCase('裸 localhost（broad）', "const h = 'localhost'");
catchCase('私网 192.168 段', "const u = 'http://192.168.1.20:8081/api'");
catchCase('私网 10 段', "const u = 'http://10.1.2.3:9000'");
catchCase('私网 172.16-31 段', "const u = 'http://172.20.0.5:8080'");
catchCase('IPv6 回环', "const u = 'http://[::1]:8081'");
catchCase('strict 带端口回环', "const u = 'localhost:9000'", 'strict');
catchCase('strict 带端口私网', "const u = '192.168.1.5:8081'", 'strict');

console.log('\n[selftest-guard] 必须放过的用例');
passCase('setHours 零值（曾误报）', 'y.setHours(0,0,0,0); O.setHours(0,0,0,0)');
passCase('纯数字串（曾误报）', 'const t = 0000000;');
passCase('相对路径 API', "const base = '/api'");
passCase('同源推导 WebSocket', "const ws = `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}`");
passCase('公网域名', "const u = 'https://example.com/api'");
passCase('strict 裸 localhost 不带端口', "const h = 'localhost'", 'strict');
passCase('版本号样式的数字', "const v = '1.2.3.4'");
passCase('空字符串', '');

// ---------- renderFailure 形状 ----------
console.log('\n[selftest-guard] renderFailure 输出');
const msg = renderFailure([
  { fileName: 'assets/x.js', level: 'broad', code: "const u='ws://localhost:8081'", hits: scanText("const u='ws://localhost:8081'", 'broad') },
]);
if (typeof msg === 'string' && msg.includes('assets/x.js') && msg.includes('localhost:8081')) {
  passed += 1;
  console.log('  ✓ 包含文件名与命中内容');
} else {
  failed += 1;
  console.error('  ✗ 输出缺少文件名或命中内容');
}

// ---------- 汇总 ----------
console.log('');
if (failed) {
  console.error(`[selftest-guard] ✗ 失败 ${failed} 项 / 通过 ${passed} 项\n`);
  process.exit(1);
}
console.log(`[selftest-guard] ✓ 全部通过（${passed} 项）\n`);
