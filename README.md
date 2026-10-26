# demand_system · 需求管理平台

面向软件研发过程的需求全生命周期管理平台。覆盖需求从**提出 → 评审 → 排期 → 迭代开发 → 验收 → 归档**的完整链路，并集成**工作流引擎、知识库检索、AI 智能助手、多维表格、代码仓库管理**等能力，提供 PC 与 H5 双端。

> 技术栈：Spring Boot 4.0.7 / JDK 25 · Vue 3 + Element Plus · Vant 4 · MySQL 8 + Redis + RabbitMQ + MinIO + Milvus

---

## 目录

- [一、功能概览](#一功能概览)
- [二、技术栈](#二技术栈)
- [三、项目结构](#三项目结构)
- [四、快速开始](#四快速开始)
- [五、配置说明](#五配置说明)
- [六、构建与部署](#六构建与部署)
- [七、测试](#七测试)
- [八、开发约定](#八开发约定)
- [九、文档索引](#九文档索引)
- [十、常见问题](#十常见问题)

---

## 一、功能概览

后端共 **23 个业务模块、550+ 个 HTTP 接口**，统一前缀 `/api/v1`。

### 1.1 需求管理（核心）

- **全生命周期流转**：草稿 → 提交 → 评审 → 排期 → 开发 → 验收 → 归档，支持撤回、驳回、重新提交、恢复已删除需求。
- **我的工作台**：按角色视角聚合 —— 我创建的 / 待我处理 / 我关注的 / 抄送我的 / 我已完成的 / 我的草稿。
- **需求层级**：父子需求、关联需求、需求详情批量查询。
- **自定义字段**：字段类型、必填、可见性均可配置，按需求类型差异化呈现。
- **需求模板**：按业务场景预置字段组合，新建时一键套用。
- **审批评价与评分**：节点审批意见、补充说明、评分反馈与评分统计。
- **协作**：评论、附件、变更历史、关注/取消关注、导出。

### 1.2 工作流引擎

- **可视化流程设计**：拖拽式流程编辑器，节点、连线、条件分支可视化配置。
- **审批模式**：会签（多人同时审批）、并行分支、节点状态跟踪。
- **流转控制**：动态计算下一节点（`next-nodes`），支持条件跳转。
- **版本管理**：流程版本发布与回溯；流程复制、导入导出。
- **迁移能力**：流程迁移与迁移计划，支持存量需求批量切换流程。

### 1.3 迭代与项目

- **迭代管理**：迭代规划、需求挂载、进度跟踪。
- **项目管理**：项目维度组织需求与迭代。

### 1.4 多维表格（Bitable）

一个内嵌的类 Airtable 数据协作模块，**25 个 Controller**，是系统中体量最大的子系统：

- **数据模型**：多维表格 → 数据表 → 字段 → 记录，支持字段类型扩展与**公式字段**。
- **视图**：表格视图、视图分享、视图权限、仪表盘视图。
- **表单**：表单发布、公开填单（无需登录）、表单收集。
- **自动化**：触发器 + 动作编排；**Webhook** 对外事件推送。
- **开放能力**：OpenAPI + API Key 鉴权，供第三方系统读写。
- **AI 能力**：表格内 AI 辅助（字段生成、内容处理）。
- **协作**：评论、操作日志、导入导出、模板、Base 级角色权限。

### 1.5 知识库与语义检索

- **知识库与文档管理**：多知识库、文档上传与解析、正文索引。
- **语义检索**：Embedding 向量化（Milvus）+ 重排（Reranker），支持按范围检索与全局检索。
- **公开分享**：知识文档可生成分享链接对外只读访问。

### 1.6 AI 能力

- **智能助手**：右下角常驻，支持流式输出、页面上下文问答、思维链/任务过程展示、引用来源溯源、Token 用量统计。
- **Agent 工具调用（function calling）**：复杂问题自动拆解为子任务，按需调用工具 ——

  | 工具 | 作用 |
  |---|---|
  | 知识库检索 | 语义检索知识文档 |
  | 需求检索 | 按条件查需求 |
  | 数据查询（NL2SQL） | 自然语言转只读 SQL 查业务数据 |
  | 联网搜索 | 外部资料补充 |
  | 页面跳转 | 定位到系统内页面 |
  | 创建需求草稿 | 从对话直接生成需求 |
  | 反问用户 | 信息不足时追问 |

- **危险操作二次确认**：创建/变更类工具调用前需用户确认。
- **NL2SQL 数据问答**：自然语言直接问业务数据。
- **多模型管理**：Provider / Model / **按功能点**（`llm_applications`）分别指定模型，可对接多家大模型。

### 1.7 组织与权限

- **RBAC**：用户、角色、菜单、按钮级权限。
- **组织架构**：多级组织（`SysOrg`）。
- **邀请机制**：邀请码加入、加入申请审批。
- **个人设置**：资料、密码、偏好。

### 1.8 文件与预览

- 文件上传存储（MinIO）、在线预览（kkFileView，支持 Office/PDF 等）、文档分享链接。

### 1.9 通知

- 站内通知中心，RabbitMQ 异步投递（`notification.*` 路由键），避免阻塞主流程。

### 1.10 统计与看板

- 首页仪表盘、需求统计、评分统计等聚合视图。

### 1.11 代码仓库集成

- Git 平台配置（多平台）、代码仓库纳管、**保护规则集**校验。

### 1.12 双端支持

| 端 | 技术 | 能力 |
|---|---|---|
| PC | Vue 3 + Element Plus | 全功能 |
| H5 | Vue 3 + Vant 4 | 需求列表/详情、任务、通知、我的、**AI 助手** |

---

## 二、技术栈

| 层次 | 技术 | 版本 |
|---|---|---|
| 后端框架 | Spring Boot | 4.0.7 |
| 运行时 | JDK | 25 |
| ORM | MyBatis-Plus | — |
| 安全 | Spring Security | — |
| 数据库 | MySQL | 8.x |
| 缓存 | Redis | — |
| 消息队列 | RabbitMQ | — |
| 对象存储 | MinIO | — |
| 向量库 | Milvus | — |
| 文件预览 | kkFileView | — |
| PC 前端 | Vue 3.4 / Vite / TypeScript | — |
| UI 组件 | Element Plus 2.5 | — |
| 状态 / 路由 | Pinia 2.1 / Vue Router 4.3 | — |
| 图表 | ECharts 5.5 | — |
| H5 | Vue 3.4 / Vant 4.9 | — |
| 构建 | Maven 3.9+ / Node 20+ | — |

---

## 三、项目结构

```
demand_system/
├── demand_backend/            # Spring Boot 4.0.7 后端（JDK 25，端口 8081）
│   └── src/main/java/com/demand/system/module/
│       ├── requirement/       # 需求（核心）
│       ├── workflow/          # 工作流引擎
│       ├── iteration/ project/# 迭代 / 项目
│       ├── bitable/           # 多维表格
│       ├── knowledge/         # 知识库 + 语义检索
│       ├── assistant/         # 智能助手 + Agent 工具调用
│       ├── llm/               # 模型配置
│       ├── nl2sql/            # 自然语言查数据
│       ├── auth/ user/ rbac/ organization/ invitation/   # 认证与权限
│       ├── file/ preview/     # 文件与预览
│       ├── notification/      # 通知
│       ├── statistics/        # 统计
│       ├── git/               # 代码仓库集成
│       └── hr/ meta/ relation/ columnconfig/             # 人事记录、元数据、关联、列配置
├── demand_frontend/           # PC 端（Vite + Element Plus，dev 端口 5170）
├── demand_h5/                 # H5 端（Vite + Vant 4，dev 端口 5175，部署在 /h5/）
├── database/
│   └── init.sql               # 数据库唯一初始化入口
├── tools/                     # 两端共享的构建守卫（必须入库）
├── docs/                      # 设计文档
├── start-all.bat              # 本地一键启动入口
└── start-claude.bat           # 本地 AI 辅助启动脚本（不入库）
```

---

## 四、快速开始

### 4.1 环境要求

| 依赖 | 版本 |
|---|---|
| JDK | 25 |
| Maven | 3.9+ |
| Node.js | 20+ |
| Docker Desktop | 用于中间件与集成测试 |

### 4.2 数据库初始化

`database/init.sql` 是**唯一入口**：在空库上执行一次即可建出全部表结构与基础数据（字典 / 权限 / 角色 / 需求类型 / 工作流模板等），无需再执行任何迁移脚本。

```bash
docker exec -i mysql mysql -uroot -padmin123 \
  -e "CREATE DATABASE IF NOT EXISTS demand_system DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
docker exec -i mysql mysql -uroot -padmin123 --default-character-set=utf8mb4 \
  demand_system < database/init.sql
```

- 执行顺序：① 建表 → ② 补列/补索引 → ③ 初始化数据（全部 `INSERT IGNORE`，幂等）→ ④ 收尾。
- ⚠️ 脚本内含 `DROP TABLE IF EXISTS`，**在已有库上执行会清空数据**，仅用于全新部署。
- 模型配置（`llm_providers` / `llm_models`）**含密钥，不入库初始化**，部署后请在「系统配置 → 模型配置」中录入。

### 4.3 一键启动

```bash
start-all.bat
```

自动拉起 MySQL / Redis / RabbitMQ / MinIO / Milvus / kkFileView 等中间件，等待关键容器健康后启动后端（8081）与前端（5170）。

常用子命令：

```bash
start-all.bat check      # 只做健康检查，不启动应用
start-all.bat down       # 停止所有 Docker 容器
start-all.bat e2e        # 全链路 E2E（依赖 + 后端 + 前端 + Playwright）
start-all.bat contract   # 导出 OpenAPI 契约到 contracts/openapi/
```

### 4.4 默认端口与入口

| 服务 | 地址 | 备注 |
|---|---|---|
| PC 前端 | http://localhost:5170 | dev server |
| H5 前端 | http://localhost:5175 | dev server |
| 后端 API | http://localhost:8081 | OpenAPI：`/v3/api-docs` |
| MinIO 控制台 | http://localhost:9001 | admin / admin123456（仅本地） |
| RabbitMQ 管理 | http://localhost:15672 | admin / admin（仅本地） |

---

## 五、配置说明

### 5.1 模型配置

大模型不写配置文件，统一在「系统配置 → 模型配置」维护，按**功能点**（`llm_applications`）分别指定使用的模型，例如对话、意图识别、向量化、Agent 规划/执行/检索/分析等。

### 5.2 跨域白名单：HTTP CORS 与 WebSocket 只有一份

后端 **HTTP CORS**（`CorsConfig`）与 **WebSocket 握手 Origin 校验**（`BitableWebSocketConfig`）读的是**同一个属性** `cors.allowed-origins`，线上由环境变量 `CORS_ALLOWED_ORIGINS` 注入：

```yaml
CORS_ALLOWED_ORIGINS: "http://dg.gov.demand.cn,http://19.106.101.220,https://demand.zxmeng.asia"
```

- 加域名 = 两个门同时放开；漏域名 = 两个门同时拒。**改完必须 recreate 容器**。
- 启动日志会打印实际生效的 Origin 列表，别靠猜。
- 区分两个 403（都叫 403，但不是同一个门）：

  | 响应体 | 来源 |
  |---|---|
  | **空** | WebSocket 的 `OriginHandshakeInterceptor` |
  | `Invalid CORS request` | Spring 的 `CorsFilter` |

### 5.3 文件预览地址

kkFileView 有**两个地址**，用途不同，不能混用：

- `kkfileview.base-url`：后端容器视角，服务端调接口用（容器网络地址）。
- `kkfileview.browser-base-url`：**浏览器**访问用，默认 `/kkfileview`（走 nginx 反代）。

> 两者曾共用 `http://kkfileview:8012`，导致 HTTPS 页面里嵌 `http://` 被浏览器按 Mixed Content 拦截，且 `kkfileview` 是 Docker 内部名，浏览器无法解析。

### 5.4 前端环境变量

- 生产变量规范见 `demand_frontend/.env.production.template`；`.env.production` 本身被 `.gitignore` 排除，不入库。
- `VITE_API_BASE_URL` 写 `/api`（相对路径）。
- 要改 dev 后端地址，用 `VITE_API_PROXY_TARGET`（只在 dev server 生效，**不进产物**）。

---

## 六、构建与部署

### 6.1 前端构建

两个前端各自独立构建，产物分别在 `demand_frontend/dist`（PC）与 `demand_h5/dist`（H5）：

```bash
cd demand_frontend && npm run build     # vue-tsc → vite build → 产物断言
cd demand_h5       && npm run build     # vite build → 产物断言（base 已固化为 /h5/）
```

### 6.2 ⚠️ 产物断言：dev / 内网地址不许进生产

`npm run build` 会扫描产物，一旦**业务代码**里出现 `localhost:端口`、`127.0.0.1:端口` 或指向私网 IP 的绝对 URL，**直接构建失败**（实现见 `tools/wb-dev-address-guard.mjs`）。

> **为什么有这道闸**：2026-10-12 线上故障 —— `useBitableWebSocket.ts` 的兜底值 `ws://localhost:8081` 被原样打进产物，而 `.env.production` 没设 `VITE_WS_URL`，于是用户浏览器去连**用户自己的机器** → 协同表格 `ERR_CONNECTION_REFUSED`。

**铁律：前端地址的兜底值只能是「相对路径」或「同源推导」。**

```ts
const WS_BASE = import.meta.env.VITE_WS_URL
  || `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}`
// dev 走 vite 的 /ws 代理、生产走 nginx 的 location /ws/，同源两头都通
```

- 校验已构建 / 已部署目录：`node tools/check-dist.mjs /home/www/demand/pc`
- 守卫自身自测：`node tools/selftest-guard.mjs`
- 临时放行（**仅限本地验证，勿用于部署**）：`ALLOW_DEV_ADDR=1 npm run build`
- ⚠️ **不要全量 grep `localhost`**：第三方库自带（`vendor-*.js` 里有），守卫按「模块来源」区分业务 / 第三方，只判业务代码，避免误报。

### 6.3 后端构建

```bash
cd demand_backend && mvn -DskipTests package
# 产物：target/demand-system-1.0.0-SNAPSHOT.jar
```

### 6.4 部署形态

**一律容器化部署，不在宿主机直接跑 jar。**

- 后端 jar 以 bind mount 方式挂入 JRE 容器，替换 jar 后重启容器即可生效。
- 前端静态产物由 nginx 容器提供，PC 与 H5 分别对应不同 `root` 路径。
- 仓库内**不包含** `docker-compose.yml` / `Dockerfile`，部署编排由运维环境维护。

---

## 七、测试

### 7.1 后端集成测试（Testcontainers，需本机 Docker 可用）

```bash
cd demand_backend && mvn verify
```

### 7.2 前端 E2E（Playwright）

```bash
cd demand_frontend && npm run test:e2e
```

### 7.3 一键全链路 E2E

```bash
start-all.bat e2e
```

### 7.4 导出接口契约（OpenAPI）

```bash
start-all.bat contract
```

---

## 八、开发约定

1. **同一个东西不要配两处。** 两份白名单必然分叉 —— 只要"同一个东西配了两处"，迟早出事（CORS / WebSocket 的 403 就是这么来的）。
2. **地址兜底值只能是相对路径或同源推导**，绝不写 `localhost` / 内网 IP（见 6.2）。
3. **`tools/` 必须入库。** 它是两个前端共享的构建依赖（`vite.config.ts` 与 `package.json` 均引用 `../tools/`），缺失会导致换机器后 `npm run build` 直接失败。不要把它挪进某个子项目，也不要加进 `.gitignore`。
4. **数据库变更手工执行。** 项目未引入 Flyway/Liquibase，表结构变更需在 `database/init.sql` 同步维护，并在已部署环境执行对应 `ALTER`（脚本内已附注释形式）。
5. **生产镜像固定版本号**，不要用 `:latest` 浮动标签（容器创建即冻结版本，长期不察会漏掉升级）。

---

## 九、文档索引

| 文档 | 内容 |
|---|---|
| `docs/ai-operation-assistant-design.md` | AI 运维助手设计 |
| `docs/bitable-feature-design.md` | 多维表格功能设计 |
| `docs/bitable-unfinished-feature-implementation-plan.md` | 多维表格待实现功能规划 |
| `docs/code-management-design.md` | 代码管理设计 |
| `docs/nl2sql-assistant-design.md` | NL2SQL 助手设计 |
| `docs/requirement-body-ai-retrieval-design.md` | 需求正文 AI 检索设计 |
| `docs/task-requirement-list-optimization.md` | 任务需求列表优化 |
| `docs/knowledge-center-landing-audit.md` | 知识中心落地评审 |

---

## 十、常见问题

**Q：前端 `npm run build` 报 `Could not resolve "../tools/wb-dev-address-guard.mjs"`？**
A：`tools/` 没拉到。确认它已被 git 跟踪（`git ls-files tools/`），不要重新加进 `.gitignore`。

**Q：H5 部署后白屏？**
A：H5 的 `base` 必须是 `/h5/`。该值已固化在 `demand_h5/vite.config.ts` 中，不要用命令行 `--base` 覆盖，也不要漏配 nginx 的 `location /h5/`。

**Q：接口 403，但 CORS 看着没问题？**
A：先看响应体区分是哪个门 —— 空响应体是 WebSocket 的 Origin 校验，`Invalid CORS request` 才是 Spring 的 CorsFilter。两者共用 `cors.allowed-origins`，改完记得 recreate 容器。

**Q：需求列表报 `Unknown column 'tasks'`？**
A：已部署环境的表结构落后于 `init.sql`，按脚本内注释的 `ALTER` 语句补齐对应列。

**Q：模型调用全部失败？**
A：模型配置（含密钥）不入库初始化，需在「系统配置 → 模型配置」中录入 Provider、Model，并为各功能点（`llm_applications`）绑定模型。
