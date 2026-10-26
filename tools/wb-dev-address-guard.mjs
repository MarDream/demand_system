/**
 * 生产产物「dev 地址」防泄漏守卫
 * ---------------------------------------------------------------------------
 * 背景：2026-10-12 线上事故 —— `useBitableWebSocket.ts` 里
 *   `|| 'ws://localhost:8081'` 的 dev 兜底值被原样打进生产产物，
 *   用户浏览器去连自己的机器 → ERR_CONNECTION_REFUSED。
 *
 * 本模块由 `demand_frontend/vite.config.ts` 的 `guardNoDevAddress` 插件调用，
 * 在 `generateBundle` 阶段扫描产物文本，命中即让构建失败。
 *
 * 判定级别按「这段代码是谁写的」来选：
 *   · broad  —— 纯业务 chunk / index.html：连裸的 localhost 字面量也抓
 *   · strict —— 第三方与业务混装的 chunk：只抓「带端口的地址」，避免误报
 *     ⚠️ 不能全量 broad：vendor 包自带 localhost（正则源码、axios 占位、
 *        词法分析器关键字），全量扫必然误报。
 *
 * 逃生阀：`ALLOW_DEV_ADDR=1 npm run build`（仅限本地验证，勿用于部署）
 * ---------------------------------------------------------------------------
 */

/**
 * 需要拦截的主机名 —— 注意这里是**正则源码片段**，点号必须转义。
 * 若不转义，`0.0.0.0` 会退化成「0 任意字符 0 任意字符 0 任意字符 0」，
 * 把 `setHours(0,0,0,0)` 这类普通数字序列误判成 dev 地址。
 *
 * 除回环地址外，还覆盖 RFC1918 私网段（README 明确要求：
 * 「指向私网 IP 的绝对 URL」同样不许进生产）。
 */
const HOSTS = [
  'localhost',
  '127\\.0\\.0\\.1',
  '0\\.0\\.0\\.0',
  '\\[::1\\]', // IPv6 回环只认 URL 里的方括号写法，裸 ::1 误报风险高
  '10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}',                    // 10.0.0.0/8
  '192\\.168\\.\\d{1,3}\\.\\d{1,3}',                        // 192.168.0.0/16
  '172\\.(?:1[6-9]|2\\d|3[01])\\.\\d{1,3}\\.\\d{1,3}',      // 172.16.0.0/12
];

/** strict：必须带端口，例如 localhost:8081 / 127.0.0.1:8012 */
const STRICT_RE = new RegExp(
  `(?:${HOSTS.join('|')}):\\d{2,5}`,
  'gi',
);

/** broad：带不带端口都算，例如 localhost / ws://localhost:8081 */
const BROAD_RE = new RegExp(
  `(?:${HOSTS.join('|')})(?::\\d{2,5})?`,
  'gi',
);

/**
 * 扫描一段产物文本里的 dev 地址。
 *
 * @param {string} code   产物文本
 * @param {'strict'|'broad'} level 判定级别
 * @returns {Array<{ text: string, index: number, line: number }>} 命中列表
 */
export function scanText(code, level = 'strict') {
  if (typeof code !== 'string' || !code) return [];

  const re = level === 'broad' ? BROAD_RE : STRICT_RE;
  re.lastIndex = 0;

  const hits = [];
  const seen = new Set();
  let m;

  while ((m = re.exec(code)) !== null) {
    const text = m[0];
    const index = m.index;

    // 同一位置重复命中（大小写差异）只记一次
    const key = `${index}:${text.toLowerCase()}`;
    if (seen.has(key)) continue;
    seen.add(key);

    hits.push({ text, index, line: lineOf(code, index) });

    // 防御空匹配导致的死循环
    if (re.lastIndex === index) re.lastIndex += 1;
  }

  return hits;
}

/** 由字符偏移算出 1-based 行号 */
function lineOf(code, index) {
  let line = 1;
  for (let i = 0; i < index && i < code.length; i += 1) {
    if (code.charCodeAt(i) === 10) line += 1;
  }
  return line;
}

/** 取命中位置附近的上下文片段，便于定位 */
function snippet(code, hit, radius = 60) {
  const start = Math.max(0, hit.index - radius);
  const end = Math.min(code.length, hit.index + hit.text.length + radius);
  const raw = code.slice(start, end).replace(/\s+/g, ' ');
  const prefix = start > 0 ? '…' : '';
  const suffix = end < code.length ? '…' : '';
  return `${prefix}${raw}${suffix}`;
}

/**
 * 把命中结果渲染成可读的失败信息。
 *
 * @param {Array<{ fileName: string, level: 'strict'|'broad', code: string, hits: any[] }>} findings
 * @returns {string}
 */
export function renderFailure(findings) {
  const lines = [];
  lines.push('');
  lines.push('════════════════════════════════════════════════════════════════════');
  lines.push('  [guard-no-dev-address] 生产产物中发现 dev 地址，构建已中止');
  lines.push('════════════════════════════════════════════════════════════════════');
  lines.push('');
  lines.push(`共 ${findings.length} 个产物文件命中：`);
  lines.push('');

  findings.forEach((f, i) => {
    lines.push(`${i + 1}) ${f.fileName}   (level=${f.level}, 命中 ${f.hits.length} 处)`);
    f.hits.slice(0, 8).forEach((h) => {
      lines.push(`   · 第 ${h.line} 行  ${h.text}`);
      if (f.code) lines.push(`     ${snippet(f.code, h)}`);
    });
    if (f.hits.length > 8) lines.push(`   · …另有 ${f.hits.length - 8} 处`);
    lines.push('');
  });

  lines.push('────────────────────────────────────────────────────────────────────');
  lines.push('修复建议：');
  lines.push('  · 移除代码里的 dev 兜底地址，改用相对路径（如 /api、/ws）');
  lines.push('    或运行时注入（window.__APP_CONFIG__ / import.meta.env）');
  lines.push('  · 确认是误报时，可用 ALLOW_DEV_ADDR=1 npm run build 放行');
  lines.push('    ⚠️ 该放行仅限本地验证，产物不得用于部署');
  lines.push('────────────────────────────────────────────────────────────────────');
  lines.push('');

  return lines.join('\n');
}
