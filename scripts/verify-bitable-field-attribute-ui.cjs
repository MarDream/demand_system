// 验证多维表格「字段属性设置」的 UI 闭环 —— 本次补充开发的核心诉求是
// 「新增字段时就能提供相应的属性设置」，本脚本逐项取证：
//
//   [A] 新增字段弹框现在直接展示 通用属性 + 随类型变化的 类型属性
//   [B] 切换字段类型时属性集随之切换（旧类型的属性被清掉）
//   [C] 配置随新增落库为规范化键，并在「字段配置」抽屉正确回填（读路径归一化）
//   [D] 字段级权限面板可配置，且「隐藏」在网格中真实生效（列消失）
//
// 用法（NODE_PATH 必须是 Windows 路径，否则解析不到 playwright）：
//   NODE_PATH=E:/Project/Vue_demo/demand_system/demand_frontend/node_modules \
//     node scripts/verify-bitable-field-attribute-ui.cjs
const path = require('node:path');
const fs = require('node:fs');

const BASE = 'http://127.0.0.1:5170';
const OUT = path.join(__dirname, 'out');

const FIELD_NAME = '__FA_UI_NUM';

let pass = 0;
let fail = 0;
function check(label, ok, extra) {
  if (ok) {
    pass++;
    console.log('  PASS  ' + label);
  } else {
    fail++;
    console.log('  FAIL  ' + label + (extra ? '  ' + extra : ''));
  }
}

async function main() {
  const { chromium } = require('playwright');
  fs.mkdirSync(OUT, { recursive: true });

  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();
  const errs = [];
  page.on('pageerror', (e) => errs.push('pageerror: ' + e.message));
  page.on('console', (m) => {
    if (m.type() === 'error') errs.push('console: ' + m.text());
  });

  // ---------- 工具 ----------
  // token 存在 cookie access_token 中，但后端鉴权认的是 Authorization 头，
  // 这里手动带上，行为与前端 axios 请求拦截器一致。
  let token = '';
  const api = (p, init) =>
    page.evaluate(
      async ({ p, init, token }) => {
        const opts = Object.assign({ method: 'GET' }, init || {});
        opts.credentials = 'include';
        opts.headers = Object.assign(
          { 'Content-Type': 'application/json' },
          token ? { Authorization: 'Bearer ' + token } : {},
          opts.headers || {},
        );
        const r = await fetch('/api' + p, opts);
        let body = null;
        try {
          body = await r.json();
        } catch {
          /* 非 JSON 响应 */
        }
        return { status: r.status, body };
      },
      { p, init, token },
    );

  const dataOf = (resp) => (resp && resp.body ? resp.body.data : null);

  const waitNoToast = () =>
    page
      .waitForFunction(() => !document.querySelector('.el-message'), { timeout: 8000 })
      .catch(() => {});
  const lastToast = async () => {
    await page.waitForSelector('.el-message', { timeout: 8000 }).catch(() => {});
    return page
      .locator('.el-message')
      .last()
      .innerText()
      .catch(() => '');
  };

  /** 添加字段弹框 / 字段配置抽屉 里的属性表单 */
  const dlgForm = page.locator('.el-dialog .field-attr-form');
  const drawerForm = page.locator('.el-drawer .field-attr-form');

  const labelsOf = async (root) =>
    (await root.locator('.el-form-item__label').allTextContents()).map((s) => s.trim());

  /** 按 label 精确命中表单项（Element Plus 的必填星号走 ::before，不污染文本） */
  const item = (root, label) =>
    root
      .locator('.el-form-item')
      .filter({ has: page.locator('.el-form-item__label', { hasText: new RegExp('^\\s*' + label + '\\s*$') }) })
      .first();

  const hasLabel = (list, label) => list.includes(label);

  const setNumber = async (root, label, value) => {
    const inp = item(root, label).locator('input').first();
    await inp.fill(String(value));
    await inp.press('Tab');
    await page.waitForTimeout(150);
  };

  const setText = async (root, label, value) => {
    const inp = item(root, label).locator('input').first();
    await inp.fill(value);
    await page.waitForTimeout(100);
  };

  const toggleSwitch = async (root, label) => {
    await item(root, label).locator('.el-switch').first().click();
    await page.waitForTimeout(150);
  };

  /** vxe-grid 表头文本（用于断言「列是否存在」） */
  const gridHeaders = async () => {
    const byColumn = await page.locator('.vxe-header--column').allInnerTexts().catch(() => []);
    if (byColumn.length) return byColumn.join('|');
    return page
      .locator('.vxe-table--header')
      .first()
      .innerText()
      .catch(() => '');
  };

  let baseId = null;
  let tableId = null;
  let tableName = '';
  let fieldId = null;
  let myUserId = null;
  let myRoleCode = null;
  let myRoleName = '';

  try {
    // ---------- 登录 ----------
    await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' });
    await page.locator('input[placeholder="请输入用户名"]').fill('admin');
    await page.locator('input[placeholder="请输入密码"]').fill('admin123');
    await page.locator('.login-btn').first().click();
    await page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 30000 });
    token = (await ctx.cookies(BASE)).find((c) => c.name === 'access_token')?.value || '';
    check('登录后拿到 access_token', !!token);
    console.log('[1] 登录成功');

    const me = await api('/v1/auth/me');
    myUserId = dataOf(me)?.id ?? dataOf(me)?.userId ?? null;

    // ---------- 进入编辑器 ----------
    await page.goto(BASE + '/bitable', { waitUntil: 'domcontentloaded' });
    await page.locator('.bgtree-node').first().waitFor({ timeout: 30000 });
    await page.waitForTimeout(800);
    const baseNode = page.locator('.bgtree-node--base').first();
    baseId = Number(String(await baseNode.getAttribute('data-key')).replace(/^b:/, ''));
    check('解析出多维表格 ID', Number.isFinite(baseId) && baseId > 0, 'baseId=' + baseId);

    await page.goto(`${BASE}/bitable/${baseId}`, { waitUntil: 'domcontentloaded' });
    await page.locator('.tgt-node').first().waitFor({ timeout: 30000 });
    await page.waitForTimeout(1200);

    const activeTable = page.locator('.tgt-node--table.is-active');
    tableId = Number(String(await activeTable.getAttribute('data-key')).replace(/^t:/, ''));
    tableName = (await activeTable.locator('.tgt-node__name').innerText()).trim();
    check('解析出当前数据表 ID', Number.isFinite(tableId) && tableId > 0, 'tableId=' + tableId);
    console.log('    目标: base=' + baseId + ' table=' + tableId + ' ' + tableName);

    const fieldsBefore = dataOf(await api(`/v1/bitable/tables/${tableId}/fields`)) || [];
    // 保证可重复执行：清掉上次异常中断留下的同名字段（删完要刷新，否则 UI 仍是旧快照）
    const stale = fieldsBefore.find((f) => f.name === FIELD_NAME);
    if (stale) {
      await api(`/v1/bitable/fields/${stale.id}`, { method: 'DELETE' });
      console.log('    清理上次残留字段 id=' + stale.id);
      await page.reload({ waitUntil: 'domcontentloaded' });
      await page.locator('.tgt-node').first().waitFor({ timeout: 30000 });
      await page.waitForTimeout(1500);
      const again = page.locator('.tgt-node--table.is-active');
      tableId = Number(String(await again.getAttribute('data-key')).replace(/^t:/, ''));
      tableName = (await again.locator('.tgt-node__name').innerText()).trim();
    }
    const fieldCountBefore = (dataOf(await api(`/v1/bitable/tables/${tableId}/fields`)) || []).length;

    // 当前用户在 Base 内的系统角色（字段权限面板要选到「自己」那一条才会真实生效）
    const members = dataOf(await api(`/v1/bitable/bases/${baseId}/members`)) || [];
    const mine = members.find((m) => Number(m.userId) === Number(myUserId));
    myRoleCode = mine?.role ?? null;
    const roles = dataOf(await api(`/v1/bitable/bases/${baseId}/roles`)) || [];
    myRoleName = roles.find((r) => r.systemRoleCode === myRoleCode)?.name ?? '';
    console.log('    当前用户: id=' + myUserId + ' 角色=' + myRoleCode + ' 显示名=' + myRoleName);

    // ================= [A] 新增字段弹框展示完整属性 =================
    console.log('\n[A] 新增字段弹框的属性集');
    await page.getByRole('button', { name: /添加字段/ }).first().click();
    await dlgForm.waitFor({ timeout: 15000 });
    await page.waitForTimeout(400);

    const dlgLabels = await labelsOf(dlgForm);
    check('弹框出现「字段类型」选择器（新增时可选类型）', hasLabel(dlgLabels, '字段类型'), JSON.stringify(dlgLabels));
    check('弹框出现「字段名称」', hasLabel(dlgLabels, '字段名称'));
    check('弹框出现「字段描述」', hasLabel(dlgLabels, '字段描述'));
    check('弹框出现「字段宽度」', hasLabel(dlgLabels, '字段宽度'));
    check('弹框出现「表单隐藏」', hasLabel(dlgLabels, '表单隐藏'));
    check('文本类型出现「是否必填」', hasLabel(dlgLabels, '是否必填'));
    check('文本类型出现「值唯一」', hasLabel(dlgLabels, '值唯一'));
    check('文本类型出现「输入提示」', hasLabel(dlgLabels, '输入提示'));
    // 文本类型专属
    check('文本类型出现「输入模式」', hasLabel(dlgLabels, '输入模式'));
    check('文本类型出现「最大长度」', hasLabel(dlgLabels, '最大长度'));
    check('文本类型出现「格式校验」', hasLabel(dlgLabels, '格式校验'));
    check('文本类型出现「默认值」', hasLabel(dlgLabels, '默认值'));
    await page.screenshot({ path: path.join(OUT, 'fa-add-dialog-text.png') }).catch(() => {});

    // ================= [B] 切换类型 → 属性集切换 =================
    console.log('\n[B] 切换字段类型后的属性集');
    await item(dlgForm, '字段类型').locator('.el-select').first().click();
    await page.waitForTimeout(300);
    await page
      .locator('.el-select-dropdown__item:visible')
      .filter({ hasText: /^\s*数字\s*$/ })
      .first()
      .click();
    await page.waitForTimeout(500);

    const numLabels = await labelsOf(dlgForm);
    check('数字类型出现「数字格式」', hasLabel(numLabels, '数字格式'));
    check('数字类型出现「小数位数」', hasLabel(numLabels, '小数位数'));
    check('数字类型出现「千分位」', hasLabel(numLabels, '千分位'));
    check('数字类型出现「前缀」', hasLabel(numLabels, '前缀'));
    check('数字类型出现「后缀」', hasLabel(numLabels, '后缀'));
    check('数字类型出现「最小值」', hasLabel(numLabels, '最小值'));
    check('数字类型出现「最大值」', hasLabel(numLabels, '最大值'));
    check('数字类型出现「默认值」', hasLabel(numLabels, '默认值'));
    check('切到数字后文本专属属性「输入模式」已移除', !hasLabel(numLabels, '输入模式'));
    check('切到数字后文本专属属性「最大长度」已移除', !hasLabel(numLabels, '最大长度'));
    check('通用属性「值唯一」在数字类型下仍保留', hasLabel(numLabels, '值唯一'));
    await page.screenshot({ path: path.join(OUT, 'fa-add-dialog-number.png') }).catch(() => {});

    // ================= [C] 填配置 → 提交 → 落库 → 抽屉回填 =================
    console.log('\n[C] 配置落库与编辑回填');
    await setText(dlgForm, '字段名称', FIELD_NAME);
    await setNumber(dlgForm, '小数位数', 3);
    await setText(dlgForm, '前缀', '¥');
    await setNumber(dlgForm, '最小值', 1);
    await setNumber(dlgForm, '最大值', 99);
    await toggleSwitch(dlgForm, '值唯一');

    await page.locator('.el-dialog__footer .el-button--primary').last().click();
    const addToast = await lastToast();
    check('新增字段成功提示', /添加成功/.test(addToast), addToast);
    await waitNoToast();
    await page.waitForTimeout(1200);

    const fieldsAfter = dataOf(await api(`/v1/bitable/tables/${tableId}/fields`)) || [];
    const created = fieldsAfter.find((f) => f.name === FIELD_NAME);
    check('新增字段已出现在字段列表', !!created, 'fields=' + fieldsAfter.map((f) => f.name).join(','));
    fieldId = created?.id ?? null;

    let cfg = created?.config ?? null;
    if (typeof cfg === 'string') {
      try {
        cfg = JSON.parse(cfg);
      } catch {
        cfg = null;
      }
    }
    cfg = cfg || {};
    check('落库 config 为规范化键 precision=3', Number(cfg.precision) === 3, JSON.stringify(cfg));
    check('落库 config prefix="¥"', cfg.prefix === '¥', JSON.stringify(cfg.prefix));
    check('落库 config min=1 / max=99', Number(cfg.min) === 1 && Number(cfg.max) === 99, `${cfg.min}/${cfg.max}`);
    check('落库 config unique=true', cfg.unique === true, String(cfg.unique));
    check('未混入旧键 format', !('format' in cfg), JSON.stringify(Object.keys(cfg)));
    check('网格表头已出现新列', (await gridHeaders()).includes(FIELD_NAME));

    // 打开「字段配置」抽屉 → 编辑该字段 → 断言表单回填
    await page.getByRole('button', { name: /更多/ }).first().click();
    await page.waitForTimeout(400);
    await page
      .locator('.el-dropdown-menu__item:visible')
      .filter({ hasText: '字段配置' })
      .first()
      .click();
    // 抽屉先出现字段列表；属性表单要等选中某个字段（editingField）后才渲染
    await page.locator('.el-drawer .field-item').first().waitFor({ timeout: 15000 });
    await page.waitForTimeout(500);

    await page
      .locator('.field-item')
      .filter({ has: page.locator('.field-item__name', { hasText: FIELD_NAME }) })
      .first()
      .click();
    await drawerForm.waitFor({ timeout: 15000 });
    await page.waitForTimeout(600);

    const drawerLabels = await labelsOf(drawerForm);
    check('抽屉识别为数字类型（出现「小数位数」）', hasLabel(drawerLabels, '小数位数'), JSON.stringify(drawerLabels));
    check('抽屉回填「小数位数」=3', (await item(drawerForm, '小数位数').locator('input').first().inputValue()) === '3');
    check('抽屉回填「前缀」=¥', (await item(drawerForm, '前缀').locator('input').first().inputValue()) === '¥');
    check('抽屉回填「最小值」=1', (await item(drawerForm, '最小值').locator('input').first().inputValue()) === '1');
    check('抽屉回填「最大值」=99', (await item(drawerForm, '最大值').locator('input').first().inputValue()) === '99');
    check(
      '抽屉回填「值唯一」为开启',
      (await item(drawerForm, '值唯一').locator('.el-switch').first().getAttribute('class'))?.includes('is-checked') === true,
    );
    await page.screenshot({ path: path.join(OUT, 'fa-drawer-backfill.png') }).catch(() => {});
    await page.locator('.el-drawer__close-btn').first().click();
    await page.waitForTimeout(800);

    // ================= [D] 字段级权限面板 + 隐藏真实生效 =================
    console.log('\n[D] 字段级权限面板与生效');
    await page.getByRole('button', { name: /高级权限/ }).first().click();
    const permDlg = page.locator('.permission-manage-dialog');
    await permDlg.waitFor({ timeout: 20000 });
    await page.waitForTimeout(1200);

    check('权限弹框已打开', await permDlg.isVisible());

    // 字段权限分区只在「表权限不为无权限」时出现。挑一个对该表已有显式表权限的角色，
    // 这样既能验证面板渲染，又不必为当前用户凭空写入一条表权限记录（避免污染配置）。
    const roleWithPerm = roles.find((r) =>
      (r.permissions || []).some(
        (p) => Number(p.tableId) === Number(tableId) && p.permissionLevel && p.permissionLevel !== 'none',
      ),
    );
    const testRoleCode = roleWithPerm?.systemRoleCode ?? null;
    console.log('    用角色做面板取证: ' + (roleWithPerm?.name ?? '(无)') + ' / ' + testRoleCode);

    await permDlg
      .locator('.perm-role-item')
      .filter({ has: page.locator('.perm-role-item__name', { hasText: roleWithPerm?.name ?? '' }) })
      .first()
      .click();
    await page.waitForTimeout(600);
    await permDlg
      .locator('.perm-tree__table')
      .filter({ has: page.locator('.perm-tree__table-name', { hasText: tableName }) })
      .first()
      .click();
    await page.waitForTimeout(1500);

    check('右侧出现「字段权限」分组', (await permDlg.locator('.perm-settings__title--sub').count()) > 0);
    const permRows = permDlg.locator('.perm-field-row');
    const permRowCount = await permRows.count();
    check(
      '字段权限列出全部字段（' + fieldCountBefore + '→' + fieldsAfter.length + '）',
      permRowCount === fieldsAfter.length,
      'rows=' + permRowCount,
    );

    const targetRow = permRows
      .filter({ has: page.locator('.perm-field-row__name', { hasText: FIELD_NAME }) })
      .first();
    check('字段权限包含新建字段行', (await targetRow.count()) > 0);
    await targetRow.locator('.el-radio-button').filter({ hasText: '隐藏' }).first().click();
    await page.waitForTimeout(400);
    check('出现「有未保存的修改」', (await permDlg.locator('.perm-footer__hint').count()) > 0);
    await page.screenshot({ path: path.join(OUT, 'fa-perm-panel.png') }).catch(() => {});

    await permDlg.locator('.perm-footer .el-button--primary').first().click();
    await page.waitForTimeout(2500);

    const permSaved = dataOf(await api(`/v1/bitable/bases/${baseId}/field-permissions?tableId=${tableId}`)) || [];
    const savedRow = permSaved.find(
      (r) => Number(r.fieldId) === Number(fieldId) && r.systemRoleCode === testRoleCode,
    );
    check('面板保存的字段权限已落库为 hidden', savedRow?.permissionLevel === 'hidden', JSON.stringify(savedRow));

    await page.locator('.el-dialog__headerbtn').last().click();
    await page.waitForTimeout(1500);

    // 面板写入路径已验证，先清掉这条记录，避免污染其它用例
    await api(`/v1/bitable/bases/${baseId}/field-permissions/batch-save`, {
      method: 'POST',
      body: JSON.stringify({
        baseId,
        changes: [
          { tableId, fieldId, roleType: 'system', systemRoleCode: testRoleCode, permissionLevel: 'editable' },
        ],
      }),
    });

    // 生效链路：把「当前用户自己的角色」对该字段置为 hidden，刷新后列应消失
    const hideMine = await api(`/v1/bitable/bases/${baseId}/field-permissions/batch-save`, {
      method: 'POST',
      body: JSON.stringify({
        baseId,
        changes: [{ tableId, fieldId, roleType: 'system', systemRoleCode: myRoleCode, permissionLevel: 'hidden' }],
      }),
    });
    check('为当前用户角色写入 hidden 成功', hideMine.status === 200, 'status=' + hideMine.status);

    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.locator('.tgt-node').first().waitFor({ timeout: 30000 });
    await page.waitForTimeout(1800);
    check('隐藏后网格表头不再包含该字段', !(await gridHeaders()).includes(FIELD_NAME), await gridHeaders());
    await page.screenshot({ path: path.join(OUT, 'fa-grid-hidden.png') }).catch(() => {});

    // 还原：把字段权限改回「可编辑」（可编辑即删除配置行）
    const reset = await api(`/v1/bitable/bases/${baseId}/field-permissions/batch-save`, {
      method: 'POST',
      body: JSON.stringify({
        baseId,
        changes: [
          {
            tableId,
            fieldId,
            roleType: 'system',
            systemRoleCode: myRoleCode,
            permissionLevel: 'editable',
          },
        ],
      }),
    });
    check('还原字段权限为可编辑', reset.status === 200 && dataOf(reset) !== undefined, 'status=' + reset.status);

    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.locator('.tgt-node').first().waitFor({ timeout: 30000 });
    await page.waitForTimeout(1500);
    check('还原后网格表头重新出现该列', (await gridHeaders()).includes(FIELD_NAME), await gridHeaders());

    check('全程无前端运行时报错', errs.length === 0, errs.slice(0, 3).join(' | '));
  } finally {
    // ---------- 清理：先清权限行，再删字段 ----------
    try {
      if (fieldId && baseId && tableId) {
        await api(`/v1/bitable/bases/${baseId}/field-permissions/batch-save`, {
          method: 'POST',
          body: JSON.stringify({
            baseId,
            changes: [{ tableId, fieldId, roleType: 'system', systemRoleCode: myRoleCode, permissionLevel: 'editable' }],
          }),
        });
        const del = await api(`/v1/bitable/fields/${fieldId}`, { method: 'DELETE' });
        console.log('  ----  清理字段 ' + fieldId + ': status=' + del.status);
      }
    } catch (e) {
      console.log('  ----  清理失败: ' + e.message);
    }
    await browser.close();
  }

  console.log('\n' + '='.repeat(72));
  console.log('结果：PASS ' + pass + ' / FAIL ' + fail);
  console.log('='.repeat(72));
  if (fail > 0) process.exitCode = 1;
}

main().catch((e) => {
  console.error('脚本异常：', e);
  process.exitCode = 1;
});
