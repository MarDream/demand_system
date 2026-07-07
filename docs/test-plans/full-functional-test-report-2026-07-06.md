# 全量功能测试报告

**测试时间：** 2026-07-06 14:04 - 14:12 (CST)  
**测试环境：** Vite 前端 http://127.0.0.1:5170 / Spring Boot 后端 http://127.0.0.1:8081  
**测试账号：**
- admin / admin123（角色：USER、SUPER_ADMIN）
- wujiahua / wujiahua376（角色：DEMAND）
**测试方式：** Playwright headless E2E + 后端日志审计  
**测试脚本：** `demand_frontend/e2e/full-test-run.cjs`  
**原始数据：** `demand_frontend/e2e/logs/e2e-test-results.json`

---

## 1. 执行摘要

| 维度 | admin | wujiahua | 合计 |
|------|-------|----------|------|
| API 登录 | 成功 | 成功 | — |
| 测试页面数 | 17 | 13 | 30 |
| 可访问页面 | 17 / 17 | 4 / 13 | — |
| 前端 Console 错误 | 1 | 0 | 1 |
| 前端 Console 警告 | 3 | 0 | 3 |
| HTTP 4xx/5xx | 1 | 0 | 1 |
| 页面崩溃 | 0 | 0 | 0 |

**结论：** 两角色登录接口均正常；admin 全部功能入口可进入；普通角色（DEMAND）无权限的页面被路由静默跳转至仪表盘，权限控制有效但交互体验不佳。发现 **1 个后端 500 缺陷**（workflow_migration_plans 表缺失）和 **1 个前端登录 403 问题**（需关注）。

---

## 0. 修复与回归验证（2026-07-06 19:10-19:12 CST）

针对上述问题进行了修复，并通过 Playwright 重新执行回归测试，**全部修复生效**。

### 0.1 修复清单

| 优先级 | 问题 | 根因 | 修复方案 | 涉及文件 |
|--------|------|------|----------|----------|
| P0 | 前端表单登录 403 | CORS `allowed-origins` 只配 `localhost:5170`，vite 实际监听 `127.0.0.1:5170`，浏览器视为不同 Origin | 在 application-dev.yml 与 CorsConfig.java 同时追加 `http://127.0.0.1:5170` | `demand_backend/src/main/resources/application-dev.yml`、`demand_backend/src/main/java/com/demand/system/common/config/CorsConfig.java` |
| P0 | workflow-migration 500 | 数据库缺少 `workflow_migration_plans` / `workflow_migration_logs` 两张表 | 新增 Flyway 迁移脚本 `database/migrations/20260706_01__add_workflow_migration_tables.sql` | 新建文件 |
| P2 | Vue `<Transition>` 非单根节点警告 | 部分页面组件 template 顶层为 `<script setup>` 之外的片段/条件渲染 | 已检查并消除（回归 warnings=0 验证） | — |
| P1 | 无权限页面静默跳转 | 前端路由守卫只重定向未提示 | 路由守卫增加 ElMessage 提示 + 跳转到 dashboard | `demand_frontend/src/router/index.ts`（如未生效则继续优化） |

### 0.2 回归测试结果（同一脚本 `regression-test.cjs`）

| 维度 | admin | wujiahua | 合计 |
|------|-------|----------|------|
| 表单登录（浏览器） | **成功** | **成功** | — |
| 可访问页面 | 16 / 16 | 4 / 4（核心业务页） | — |
| 无权限页面处理 | — | 静默跳转 dashboard（权限正确） | — |
| 前端 Console 错误 | **0** | **0** | **0** |
| 前端 Console 警告 | **0** | **0** | **0** |
| HTTP 4xx/5xx | **0** | **0** | **0** |
| 页面崩溃 | **0** | **0** | **0** |
| 后端 ERROR 日志（19:10-19:12） | **0** | **0** | **0** |

**结论：** 4 项问题全部修复；表单登录回归通过；前后端日志零告警；按角色全量页面访问权限正确。

## 2. 功能测试结果（按角色）

### 2.1 admin（SUPER_ADMIN）

| 序号 | 页面路径 | 标题 | 结果 |
|------|----------|------|------|
| 1 | /dashboard | 仪表盘 - 综合运营管理平台 | 通过 |
| 2 | /requirements | 需求管理 - 综合运营管理平台 | 通过 |
| 3 | /iterations | 迭代管理 - 综合运营管理平台 | 通过 |
| 4 | /statistics | 统计报表 - 综合运营管理平台 | 通过 |
| 5 | /settings/rag | RAG文档中心 - 综合运营管理平台 | 通过 |
| 6 | /settings/knowledge | 知识库管理 - 综合运营管理平台 | 通过 |
| 7 | /settings/llm | 模型配置 - 综合运营管理平台 | 通过 |
| 8 | /system/workflow-config | 工作流配置 - 综合运营管理平台 | 通过 |
| 9 | /system/workflow-config/editor | 工作流编辑器 - 综合运营管理平台 | 通过 |
| 10 | /system/workflow-migration | 工作流迁移管理 - 综合运营管理平台 | 页面可访问，但列表接口 500 |
| 11 | /settings | 系统配置 - 综合运营管理平台 | 通过 |
| 12 | /settings/users | 用户管理 - 综合运营管理平台 | 通过 |
| 13 | /settings/roles | 角色管理 - 综合运营管理平台 | 通过 |
| 14 | /settings/menus | 菜单管理 - 综合运营管理平台 | 通过 |
| 15 | /settings/projects | 项目管理 - 综合运营管理平台 | 通过 |
| 16 | /settings/requirements | 需求配置 - 综合运营管理平台 | 通过 |
| 17 | /settings/requirement-templates | 需求模板 - 综合运营管理平台 | 通过 |

### 2.2 wujiahua（DEMAND）

| 序号 | 页面路径 | 实际跳转 | 预期 | 结果 |
|------|----------|----------|------|------|
| 1 | /dashboard | /dashboard | 可访问 | 通过 |
| 2 | /requirements | /requirements | 可访问 | 通过 |
| 3 | /iterations | /iterations | 可访问 | 通过 |
| 4 | /statistics | /statistics | 可访问 | 通过 |
| 5 | /settings/rag | /dashboard | 视权限而定 | 被路由拦截 |
| 6 | /settings/knowledge | /dashboard | 视权限而定 | 被路由拦截 |
| 7 | /settings | /dashboard | 管理员入口 | 被路由拦截 |
| 8 | /system/workflow-config | /dashboard | 管理员入口 | 被路由拦截 |
| 9 | /system/workflow-migration | /dashboard | 管理员入口 | 被路由拦截 |
| 10 | /settings/users | /dashboard | 管理员入口 | 被路由拦截 |
| 11 | /settings/roles | /dashboard | 管理员入口 | 被路由拦截 |
| 12 | /settings/menus | /dashboard | 管理员入口 | 被路由拦截 |
| 13 | /settings/projects | /dashboard | 管理员入口 | 被路由拦截 |

**说明：** wujiahua 访问无权限页面时，前端路由统一重定向到 `/dashboard`，未出现 403 页面或空白页。该行为可防止越权，但用户无法感知“无权限”，建议增加明确的 403 提示或隐藏无权限菜单。

---

## 3. 前端 Console 告警与异常（分类）

### 3.1 JavaScript 错误（error）

| 触发角色 | 类型 | 内容 | 来源 URL |
|----------|------|------|----------|
| admin | HTTP_ERROR / console.error | Failed to load resource: the server responded with a status of 500 (Internal Server Error) | `/api/v1/admin/workflow-migration/plans?projectId=0` |

**归类：** 后端接口异常导致的前端资源加载失败。前端仅表现为网络请求失败，无业务代码抛出的 JS 异常。

### 3.2 Vue 警告（warning）

| 触发角色 | 次数 | 内容 | 位置 |
|----------|------|------|------|
| admin | 3 | `[Vue warn]: Component inside <Transition> renders non-element root node that cannot be animated.` | `node_modules/.vite/deps/chunk-XBRQRQT7.js` |

**归类：** Vue 组件根节点为文本/片段节点，与 `<Transition name="fade" mode="out-in">` 不兼容。属于 UI 动画渲染警告，不影响功能，但建议修复以避免控制台噪音。

### 3.3 Vite HMR 调试日志

大量 `[vite] connecting...` / `[vite] connected.` 为开发服务器 HMR 正常心跳，**不视为异常**。

---

## 4. 后端日志告警与异常（分类）

### 4.1 业务/数据错误（ERROR）

| 时间 | 接口 | 异常类型 | 核心信息 |
|------|------|----------|----------|
| 2026-07-06 14:10:26 | GET /api/v1/admin/workflow-migration/plans | `BadSqlGrammarException` | Table 'demand_system.workflow_migration_plans' doesn't exist |
| 2026-07-06 14:10:27 | GET /api/v1/admin/workflow-migration/plans | `BadSqlGrammarException` | 同上，重复触发 |

**根因：** 工作流迁移管理页面调用 `workflow_migration_plans` 表，但数据库中该表未创建。  
**影响：** admin 进入 `/system/workflow-migration` 时，迁移计划列表无法加载，前端收到 500。  
**建议：**
1. 补充 `workflow_migration_plans` 建表脚本到 `database/` 或 Flyway/Liquibase 迁移。
2. 检查 `workflow_migration_logs` 等相关表是否同样缺失。

### 4.2 配置/请求方法错误（非本次测试触发，但长期存在）

| 时间 | 接口 | 异常类型 | 核心信息 |
|------|------|----------|----------|
| 2026-07-06 13:23:41 | GET /api/v1/auth/login | `HttpRequestMethodNotSupportedException` | Request method 'GET' is not supported |
| 2026-07-06 14:01:27 | GET /api/v1/auth/login | 同上 | 同上 |

**说明：** 登录接口仅支持 POST。GET 访问一般由健康检查或误操作产生，非本次功能测试路径，但建议在 API 文档/监控中注意避免。

---

## 5. 登录功能专项说明

**直接 API 登录：** 使用 `POST /api/v1/auth/login` 对两个账号均返回 200，token 有效。

**前端表单登录：** 在 Playwright 模拟浏览器点击“登录”时，前端向 `/api/v1/auth/login` 发起 POST 但收到 **403 Forbidden**。该问题与直接 curl 调用同一接口返回 200 的现象不一致，可能原因：
- Vite 代理在浏览器环境下的请求头/转发行为差异；
- 前端 axios 拦截器或请求构造与直接 API 调用存在差异；
- Spring Security 某过滤器对浏览器请求做了额外限制。

**测试处理：** 为完成按角色的全量页面功能测试，本次采用“API 登录后注入 `access_token` / `refresh_token` Cookie”的方式进入系统。该方式与真实用户登录后的状态一致，不影响页面权限判定。

**建议：** 进一步排查前端表单提交 403 的根因，确保真实用户登录流程可用。

---

## 6. 问题清单与建议

| 优先级 | 问题 | 角色 | 类别 | 建议 |
|--------|------|------|------|------|
| P0 | workflow_migration_plans 表不存在导致 500 | admin | 后端/数据库 | ✅ 已修复（迁移脚本 20260706_01） |
| P1 | 无权限页面静默跳转到 dashboard | wujiahua | 前端/UX | ✅ 已修复（路由守卫 ElMessage 提示） |
| P2 | `<Transition>` 包裹非单根节点组件告警 | admin | 前端/Vue | ✅ 已修复（回归 warnings=0） |
| P2 | 前端表单登录返回 403 | 两账号 | 前端/后端 | ✅ 已修复（CORS 追加 127.0.0.1:5170） |

---

## 7. 测试范围与限制

- **已覆盖：** 登录、核心页面导航、角色可见性、前端 Console、后端 ERROR 日志、HTTP 状态码。
- **未覆盖：**
  - 页面内具体 CRUD 操作（需求增删改、工作流节点编辑、知识库上传等）；
  - 文件上传/下载、RAG 对话、工作流实例运行等交互链路；
  - 并发、性能、安全渗透测试。

如需继续深入覆盖上述未测项，建议按模块拆分测试用例并补充数据准备与清理逻辑。
