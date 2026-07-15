# 需求管理平台（demand_system）全方位 Web 测试测评报告

> 测评时间：2026-07-15  
> 测评人：端测测（Web 应用测试专家）  
> 被测环境：前端 dev server `http://127.0.0.1:5170`（Vite 代理 `/api` → `http://localhost:8081`），后端 Spring Boot 在线  
> 技术栈：Vue 3 + TypeScript + Vite + Element Plus / Spring Boot 3.2 + MyBatis-Plus + MySQL + Redis

---

## 0. 执行摘要（TL;DR）

| 维度 | 结论 | 关键风险 |
|------|------|----------|
| 后台响应日志 | ⚠️ 配置缺 access log / 响应耗时 | 生产排障无链路依据 |
| 后台 API Bug | 🔴 复现 2 个 P0 级故障 | 多维表格全挂、评分统计路由错 |
| 前端 Console | 🟡 happy path 干净，1 个 500 派生错误 | bitable 接口 500 透传到控制台 |
| 前端异常兜底 | 🟡 无全局错误边界 | 渲染异常无友好兜底 |
| 功能/E2E | 🟢 既有套件 29 通过 / 2 失败 | 失败项即下方 2 个 bug |
| 可访问性 | 🟡 基础合规，表单无 label | WCAG 1.3.1 / 3.3.2 |
| 性能 | ⚪ 未跑 Lighthouse | 建议建立基线 |

**一句话**：前端质量底线不错（happy path 0 崩溃、错误处理大多有 `ElMessage` 提示），但**后端有两处会导致整块功能不可用的硬伤**（多维表格缺表、评分路由缺 `/api` 前缀），且**后台缺乏响应日志**让这类问题很难被早期发现和定位。既有 E2E 套件其实已经把这 2 个 bug 测出来了（RATE/BITABLE 失败），只是没闭环修复。

---

## 1. 后台响应日志专项（重点）

### 1.1 日志配置现状（`logback-spring.xml`）
- 仅有 **业务 INFO 文件日志** + **ERROR 独立文件**（`demand-system-error.log`），按天滚动、保留 30 天。
- **缺口 1：没有 access / 响应耗时日志**。没有任何 Filter/Interceptor 记录 `method + uri + status + 耗时 + traceId`。出问题只能从业务日志反推，无法量化接口性能与异常分布。
- **缺口 2：traceId 未全链路贯通**。仅 `GlobalExceptionHandler` 在异常时打印 `traceId=xxx`，正常请求不携带；且 logback pattern 未包含 traceId，跨服务/跨请求无法串联。
- 第三方框架（Spring/MyBatis）已设为 WARN，合理；业务日志 INFO，合理。
- 当前错误日志仅有 3 条 ERROR：`端口 8081 被占用` 启动失败 1 次 + `GET /api/v1/assistant/sessions` 表不存在 2 次（见 1.3）。

### 1.2 建议的后台响应日志方案（整改项 B1）
新增一个 `ResponseBodyLogger` / `AccessLogFilter`（或引入 `spring-boot-starter-actuator` + `micrometer`），统一输出：
```
[traceId] method=GET uri=/api/v1/xxx status=200 cost=23ms ip=...
```
并在 logback pattern 加入 `%X{traceId}`（借助 `MDC`），让每条业务日志都带 traceId。

### 1.3 错误日志实况（已发生）
```
2026-07-15 12:59:09 | ERROR | GlobalExceptionHandler | Unexpected exception - traceId=d5b51f23, method=GET, uri=/api/v1/assistant/sessions
BadSqlGrammarException: Table 'demand_system.assistant_sessions' doesn't exist
```
- `assistant_sessions` 表缺失，发生在 12:59；但本次实时复现 `GET /api/v1/assistant/sessions` 已返回 **200** —— 说明该表在后续某次重启中被自动创建（疑似 `ddl-auto=update` 或某次手动脚本），**但对应迁移脚本未固化进 `database/`**。风险：全新库部署会再次复现该 500。（整改项 B2：把 assistant / 相关表 DDL 固化进 `init.sql` 或 migrations）
- 启动期 `Web server failed to start. Port 8081 was already in use` —— 说明有**遗留进程/重复启动**，部署脚本需先杀旧进程。（整改项 B3）

---

## 2. 后台 API 实测复现（重点 · 已稳定复现）

用 `admin/admin123` 登录拿 token，直接打接口，结果如下：

| # | 接口 | 实测 | 期望 | 严重度 | 根因 |
|---|------|------|------|--------|------|
| A1 | `GET /api/v1/bitable/bases` | **500** `bad SQL grammar / defaultParameterMap` | 200 列表 | 🔴 P0 | `bitable_bases` 等表未创建（迁移脚本 20260709 未执行） |
| A2 | `GET /api/v1/statistics/rating` | **500** `No static resource ...` | 200 | 🔴 P0 | 控制器 `@RequestMapping("/v1/statistics/rating")` **缺 `/api` 前缀** |
| A3 | `GET /v1/statistics/rating`（去掉 /api） | 200 | — | — | 印证 A2：后端真实路径无 `/api` |
| A4 | `GET /api/v1/assistant/sessions` | 200（曾 500） | 200 | 🟡 P2 | 表缺失曾发生，迁移未固化（见 B2） |
| A5 | `GET /api/v1/auth/login` | 200 + token | 200 | 🟢 | 登录链路正常 |

**A1 影响面**：所有 `/api/v1/bitable/*` 接口（`/bases`、`/tables`、`/records`、导入导出、AI 等十几个 Controller）都依赖这些表，**多维表格模块整体不可用**，且前端 `/bitable` 页面会触发 500 → 控制台报错（见第 3 节）。

**A2 影响面**：前端 `src/api/modules/statistics.ts` 调用 `/v1/statistics/rating`，经 axios `baseURL=/api` 拼成 `/api/v1/statistics/rating` → 命中错误路径 → **评分统计功能必挂**。当前未发现视图调用该接口（UI 可能未接线），但后端契约错误是确定的。

### 整改项（后端）
- **A1 → B4**：执行 `database/migrations/20260709_*.sql`（或对应多维表格建表脚本），跑通 `bitable_bases` 等表；补齐后回归 `GET /api/v1/bitable/bases`。
- **A2 → B5**：将 `RequirementRatingStatisticsController` 的 `@RequestMapping("/v1/statistics/rating")` 改为 `/api/v1/statistics/rating`（与全局 `/api/v1` 前缀一致）。**全局排查**：grep 一遍所有 Controller 的 `@RequestMapping`，确认无遗漏 `/api` 前缀的接口（这是类「路由一致性」回归点）。
- **A4 → B2**：把 `assistant_sessions` 及关联表 DDL 固化进 `init.sql` / migrations，杜绝全新库复发。

---

## 3. 前端 Console 告警与异常专项（重点）

### 3.1 实时审计（Playwright 注入 admin token，遍历 10 个核心页面）
脚本：`demand_frontend/console-audit.mjs`，证据：`demand_frontend/console-audit-result.json`

| 指标 | 结果 |
|------|------|
| console.error | **1**（来自 bitable 500 的 `Failed to load resource: 500`） |
| console.warning | 0 |
| 未捕获页面异常 (pageerror) | 0 |
| 失败请求 (requestfailed) | 0 |
| 4xx/5xx 响应 | **1**（`/api/v1/bitable/bases` → 500） |
| 遍历页面 | dashboard / requirements / bitable / 文档中心 / 知识库 / 用户管理 / 模型配置 / 工作流配置 / 迭代 / 通知中心 |

**结论**：核心页面 happy path **干净、无白屏、无未捕获异常**。唯一的红色信号就是后台 bitable 500 透传上来的那条 `console.error`——前端本身有 `ElMessage` 兜底提示，没有崩溃，但**根因在后端 A1**。

> 注：本机实测 `assistant/sessions` 已 200，故未在前端复现其历史 500；若全新库部署，文档中心/RAG 相关调用可能再次触发（见 B2）。

### 3.2 静态扫描（`src` 全量）
- `console.*` 共 **27 处**：`console.error` ×25、`console.warn` ×2、**0 个 `console.log`**。无噪音型 log，这点很好。
- ⚠️ **生产构建未剥离 console**：`vite.config.ts` / `package.json` 均无 `drop_console` / `terser` 配置 → 25 处 `console.error` 会随产物上线，生产环境 Console 会被错误刷屏，且可能泄露内部信息。
- ⚠️ **部分 `console.error` 没有配套用户提示**（静默失败）：如 `RegisterPage.vue:218/228`、`RequirementFilter.vue:163`、`RequirementForm.vue:150/160/181`、`settings/llm.vue:925/939/950`、`settings/users.vue:1036`、`WorkflowCopyDialog.vue:301/340/380` 等，仅有 `console.error(error)` 而无 `ElMessage`/兜底 UI。用户操作失败却无任何反馈。
- ⚠️ **无全局错误边界**：`main.ts` / `App.vue` 未配置 `app.config.errorHandler` 或 `onErrorCaptured` / ErrorBoundary。渲染期未捕获异常会直接抛到浏览器控制台，无「出错了，点此重试」之类的兜底页。

### 整改项（前端）
- **C1（P1）**：`vite.config.ts` build 阶段按 `import.meta.env.PROD` 剥离 `console.*`（用 `esbuild` 的 `drop: ['console']` 或 `terser` `drop_console`），或统一封装一个 `logger` 模块按环境开关。
- **C2（P1）**：补齐「只 `console.error` 无提示」的 catch 分支，加 `ElMessage.error(resolveErrorMessage(error, '...'))`（项目已有 `resolveErrorMessage` 工具，直接复用）。
- **C3（P2）**：加全局错误边界——`app.config.errorHandler` 统一上报 + 一个错误兜底组件，避免单点渲染异常整页白屏。

---

## 4. 功能 / E2E / 测试现状

- 既有 E2E 套件丰富：`demand_frontend/e2e/` 下 13 个 spec（auth / workflow-* / knowledge-qa / rag-dialog / full-integration 等），`full-integration-api.cjs` 覆盖 15 个模块。
- `test-report.json`（2026-07-13）：**29 通过 / 4 跳过 / 2 失败**，失败项正是 `RATE`（评分）与 `BITABLE`（多维表格）——**与本报告第 2 节复现的 A1/A2 完全吻合**，说明测试是有效的，只是修复未闭环。
- 建议：把 `start-all.bat e2e` 与 `mvn verify`（Testcontainers 后端集成测试）接入 CI，作为合并门禁；本次复现脚本可固化为常规冒烟用例。

---

## 5. API 测试清单与用例设计（要点）

针对 P0 接口及核心链路，建议的用例矩阵（完整 20+ 例可扩充）：

**A1 多维表格 `GET /api/v1/bitable/bases`**
- 正常：admin 登录 → 200 返回数组（修复后）
- 异常：未登录 → 401；非 admin → 403；表不存在 → 500（已现）
- 边界：空库返回 `[]` 而非 null；超大列表分页

**A2 评分 `GET /api/v1/statistics/rating`**
- 正常：`/api/v1/...` 路径 200（修复 `/api` 前缀后）
- 异常：错误路径 `/v1/...`（旧）应 404 而非 200（防止回归）
- 边界：`?start=&end=` 非法日期、跨年区间

**核心链路（已有覆盖，建议常态化）**
- `POST /api/v1/auth/login`：正确/错误密码/锁定/记住我
- 需求 CRUD、工作流提交/会签、知识库检索（RAG）、文档上传预览

---

## 6. 可访问性（A11y）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| `<html lang>` | ✅ `zh-CN` | 良好 |
| viewport meta | ✅ | 良好 |
| 表单 label | ❌ | 登录/注册/各类表单仅用 `placeholder`，缺 `<label>` 或 `aria-label`（WCAG 1.3.1 / 3.3.2 / 4.1.2） |
| 键盘可达 | 🟡 待测 | Element Plus 组件基本支持，需专项走查 |
| 颜色对比度 | 🟡 待测 | 主色 `#2563EB` 需对照正文对比度 ≥ 4.5:1 |
| 全局错误兜底 | ❌ | 见 C3 |

**整改项 D1（P2）**：为所有 `el-input` 增加 `aria-label` 或关联 `<label>`（可用 `label` slot 或 `aria-label` 属性），并用 `axe-core`/`pa11y` 做自动化 a11y 回归。

---

## 7. 性能（Performance）

未在本机跑 Lighthouse（需安装 + 构建产物）。建议：
- **P1**：对 `dist/` 生产构建跑 Lighthouse，建立基线（目标：Performance ≥ 90、Accessibility ≥ 90、Best-Practices ≥ 90、SEO ≥ 80）。
- **P2**：关注 `vite.config.ts` 已做 manualChunks 拆包（vendor-vue/element/editor/workflow/data/xlsx），保持；大列表页（需求/多维表格）建议虚拟滚动 + 分页，避免一次性渲染万级行。
- **P2**：Core Web Vitals 目标：LCP < 2.5s、INP < 200ms、CLS < 0.1（可用 Playwright + `web-vitals` 在 E2E 中埋点采集）。

---

## 8. 问题清单与整改优先级

| 编号 | 维度 | 问题 | 严重度 | 整改项 |
|------|------|------|--------|--------|
| A1 | 后端 | 多维表格表未创建 → 全模块 500 | 🔴 P0 | B4 执行迁移脚本 |
| A2 | 后端 | 评分路由缺 `/api` 前缀 → 功能挂 | 🔴 P0 | B5 改 @RequestMapping + 全量排查 |
| B1 | 后端 | 无 access/响应耗时/traceId 日志 | 🟡 P1 | 加 AccessLogFilter + MDC traceId |
| B2 | 后端 | assistant 等表 DDL 未固化 | 🟡 P2 | 补进 init.sql/migrations |
| B3 | 部署 | 端口冲突 / 重复启动 | 🟡 P1 | 启动前先杀旧进程 |
| C1 | 前端 | 生产未剥离 console | 🟡 P1 | build 阶段 drop console |
| C2 | 前端 | 部分 catch 只 console 无提示 | 🟡 P1 | 补 ElMessage |
| C3 | 前端 | 无全局错误边界 | 🟡 P2 | errorHandler + 兜底组件 |
| D1 | A11y | 表单无 label/aria | 🟡 P2 | 加 aria-label + axe 回归 |
| E1 | 性能 | 无 Lighthouse 基线 | ⚪ P2 | 建立基线 + CWV 采集 |

---

## 9. 分阶段整改建议

**阶段一（立即，≈0.5~1 天，可随紧急发版）**
1. B4：执行多维表格建表迁移脚本，验证 `GET /api/v1/bitable/bases` 200。
2. B5：评分 Controller 加 `/api` 前缀，grep 全量 Controller 复查前缀一致性。
3. 跑 `start-all.bat e2e` 确认 RATE/BITABLE 两个失败项转绿。

**阶段二（本周，质量基建）**
4. B1：加 access 日志 + traceId（MDC）。
5. C1/C2：生产剥离 console + 补静默失败点的用户提示。
6. 把 E2E + `mvn verify` 接入 CI 门禁。

**阶段三（迭代，健壮性/合规）**
7. C3：全局错误边界。
8. D1：表单 a11y + axe 自动化。
9. E1：Lighthouse 基线 + CWV 采集。
10. B2/B3：DDL 固化、启动杀进程。

---

## 10. 复用工具（本次产出）

- `demand_frontend/console-audit.mjs`：注入 token 遍历核心页面，抓取 Console / 未捕获异常 / 失败请求 / 5xx 响应。用法：`node console-audit.mjs`（结果写入 `console-audit-result.json`）。
- `demand_frontend/api-bug-repro.mjs`：用 admin token 复现可疑接口，打印 status + 错误体。用法：`node api-bug-repro.mjs`。
- 既有：`demand_frontend/e2e/*`、`demand_frontend/test-report.json`。

> 说明：测试只能降低风险不能消除风险。本报告基于**当前在线实例**实测，建议把复现脚本纳入常规冒烟，防止 P0 回归。
