// 后端分组接口语义验证（在已登录的浏览器上下文内发请求，避免明文输出 token）
// 用法：NODE_PATH=<demand_frontend/node_modules> node scripts/verify-table-group-api.cjs
const path = require('node:path');

const BASE = 'http://127.0.0.1:5170';
const BASE_ID = 1;
const log = (...a) => console.log(...a);

let pass = 0;
let fail = 0;
function check(label, ok, extra) {
  if (ok) {
    pass++;
    log('  PASS  ' + label);
  } else {
    fail++;
    log('  FAIL  ' + label + (extra ? '  ' + extra : ''));
  }
}

async function main() {
  const { chromium } = require('playwright');
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  const page = await ctx.newPage();
  page.on('pageerror', (e) => log('  pageerror: ' + e.message));

  // 在页面内用 fetch 调接口，token 只存在于页面上下文中
  const api = (method, url, body) =>
    page.evaluate(
      async ([m, u, b]) => {
        const token = document.cookie
          .split('; ')
          .map((c) => c.split('='))
          .find(([k]) => k === 'access_token')?.[1];
        const res = await fetch(u, {
          method: m,
          headers: {
            'Content-Type': 'application/json',
            ...(token ? { Authorization: 'Bearer ' + decodeURIComponent(token) } : {}),
          },
          body: b === undefined ? undefined : JSON.stringify(b),
        });
        let json = null;
        try {
          json = await res.json();
        } catch {
          json = null;
        }
        return { status: res.status, code: json?.code, message: json?.message, data: json?.data };
      },
      [method, url, body],
    );

  try {
    await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' });
    await page.locator('input[placeholder="请输入用户名"]').fill('admin');
    await page.locator('input[placeholder="请输入密码"]').fill('admin123');
    await page.locator('.login-btn').first().click();
    await page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 30000 });
    log('[登录] 成功');

    const G = `/api/v1/bitable`;
    const tree = () => api('GET', `${G}/bases/${BASE_ID}/table-groups`);
    const flat = (nodes, depth = 0, out = []) => {
      (nodes || []).forEach((n) => {
        out.push({ id: n.id, name: n.name, parentId: n.parentId, depth, tables: n.tableCount, total: n.totalTableCount });
        flat(n.children, depth + 1, out);
      });
      return out;
    };

    // 清理历史测试分组
    const before = await tree();
    for (const n of flat(before.data)) {
      if (n.name.startsWith('__T_')) {
        await api('DELETE', `${G}/table-groups/${n.id}`);
      }
    }

    log('\n[1] 建分组与层级');
    const a = await api('POST', `${G}/bases/${BASE_ID}/table-groups`, { name: '__T_A', parentId: null });
    check('创建根分组 __T_A', a.code === 200, JSON.stringify(a));
    const aId = a.data;

    const b = await api('POST', `${G}/bases/${BASE_ID}/table-groups`, { name: '__T_B', parentId: aId });
    check('创建子分组 __T_B（parentId=A）', b.code === 200, JSON.stringify(b));
    const bId = b.data;

    const c = await api('POST', `${G}/bases/${BASE_ID}/table-groups`, { name: '__T_C', parentId: bId });
    check('创建孙分组 __T_C（parentId=B）', c.code === 200, JSON.stringify(c));
    const cId = c.data;

    let t = flat((await tree()).data);
    check('A 的 parentId 为 null', t.find((x) => x.id === aId)?.parentId === null);
    check('B 的 parentId = A', t.find((x) => x.id === bId)?.parentId === aId);
    check('C 的 parentId = B', t.find((x) => x.id === cId)?.parentId === bId);
    check('层级深度 A=0/B=1/C=2', [aId, bId, cId].every((id, i) => t.find((x) => x.id === id)?.depth === i));

    log('\n[2] 防环校验');
    const cyc1 = await api('PUT', `${G}/table-groups/${aId}/move`, { parentId: aId });
    check('把 A 移到 A 自己被拒', cyc1.code !== 200, JSON.stringify(cyc1));
    const cyc2 = await api('PUT', `${G}/table-groups/${aId}/move`, { parentId: cId });
    check('把 A 移到自己的孙分组 C 被拒', cyc2.code !== 200, JSON.stringify(cyc2));
    const cyc3 = await api('PUT', `${G}/table-groups/${bId}/move`, { parentId: cId });
    check('把 B 移到自己的子分组 C 被拒', cyc3.code !== 200, JSON.stringify(cyc3));
    t = flat((await tree()).data);
    check('被拒后树结构未被破坏（A 仍在根）', t.find((x) => x.id === aId)?.depth === 0);

    log('\n[3] 合法移动与排序');
    const mv = await api('PUT', `${G}/table-groups/${cId}/move`, { parentId: null, sortOrder: 1 });
    check('把 C 移到根层级成功', mv.code === 200, JSON.stringify(mv));
    t = flat((await tree()).data);
    check('C 变为根层级(depth=0) 且 parentId=null',
      t.find((x) => x.id === cId)?.depth === 0 && t.find((x) => x.id === cId)?.parentId === null);

    log('\n[4] 数据表归组与计数');
    const tables = (await api('GET', `${G}/bases/${BASE_ID}/tables`)).data || [];
    const t1 = tables[0];
    const mvTable = await api('PUT', `${G}/tables/${t1.id}/group`, { groupId: bId });
    check(`数据表「${t1.name}」归入 B`, mvTable.code === 200, JSON.stringify(mvTable));
    t = flat((await tree()).data);
    check('B.tableCount = 1', t.find((x) => x.id === bId)?.tables === 1, JSON.stringify(t.find((x) => x.id === bId)));
    check('A.totalTableCount 汇总到子孙 = 1', t.find((x) => x.id === aId)?.total === 1);

    const ungroup = await api('PUT', `${G}/tables/${t1.id}/group`, { groupId: null });
    check('数据表移出分组(groupId=null)', ungroup.code === 200, JSON.stringify(ungroup));
    t = flat((await tree()).data);
    check('移出后 B.tableCount = 0', t.find((x) => x.id === bId)?.tables === 0);

    log('\n[5] 重命名');
    const rn = await api('PUT', `${G}/table-groups/${bId}`, { name: '__T_B2' });
    check('重命名 B -> __T_B2', rn.code === 200, JSON.stringify(rn));
    t = flat((await tree()).data);
    check('新名字已生效', t.find((x) => x.id === bId)?.name === '__T_B2');

    log('\n[6] 删除分组：子分组与数据表上移到父级');
    await api('PUT', `${G}/tables/${t1.id}/group`, { groupId: bId });
    const del = await api('DELETE', `${G}/table-groups/${bId}`);
    check('删除 B 成功', del.code === 200, JSON.stringify(del));
    t = flat((await tree()).data);
    check('B 已从树中消失', !t.some((x) => x.id === bId));
    check('C 未级联删除', t.some((x) => x.id === cId));
    const tablesAfter = (await api('GET', `${G}/bases/${BASE_ID}/tables`)).data || [];
    check('B 下的数据表未级联删除', tablesAfter.some((x) => x.id === t1.id));
    check('该数据表已上移到 A（父级）', tablesAfter.find((x) => x.id === t1.id)?.groupId === aId,
      'groupId=' + tablesAfter.find((x) => x.id === t1.id)?.groupId);

    log('\n[7] 权限：非法 parentId');
    const bad = await api('POST', `${G}/bases/${BASE_ID}/table-groups`, { name: '__T_BAD', parentId: 99999999 });
    check('parentId 不存在时被拒', bad.code !== 200, JSON.stringify(bad));

    log('\n[8] 清理');
    for (const n of flat((await tree()).data)) {
      if (n.name.startsWith('__T_')) await api('DELETE', `${G}/table-groups/${n.id}`);
    }
    await api('PUT', `${G}/tables/${t1.id}/group`, { groupId: null });
    const final = flat((await tree()).data);
    check('测试分组已清理干净', !final.some((n) => n.name.startsWith('__T_')), JSON.stringify(final.map((n) => n.name)));
    const finalTables = (await api('GET', `${G}/bases/${BASE_ID}/tables`)).data || [];
    check('测试数据表已回到未分组', finalTables.every((x) => x.groupId == null));
  } catch (e) {
    fail++;
    log('!! 执行异常: ' + e.message);
  } finally {
    log(`\n===== 结果：PASS ${pass} / FAIL ${fail} =====`);
    await browser.close();
    if (fail > 0) process.exitCode = 1;
  }
}

main();
