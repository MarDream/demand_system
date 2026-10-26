# demand_system
需求管理平台，用于软件开发管理过程中需求的生命周期管理

## 项目结构

```
demand_system/
├── demand_frontend/          # Vue3 Web端（Vite + Element Plus）
├── demand_h5/                # 手机 H5 端（Vite + Vant 4，复用后端 /api/v1 接口，端口 5175）
├── demand_backend/           # Spring Boot 3.2 后端（MyBatis-Plus）
├── database/                 # MySQL 初始化脚本
├── scripts/                  # 基础设施与一键脚本
└── docs/                     # PRD 等文档
```

## 数据库初始化

`database/init.sql` 是**唯一入口**：在空库上执行一次，即可建出全部表结构与基础数据
（字典 / 权限 / 角色 / 需求类型 / 工作流模板等），无需再执行任何迁移脚本。

```bash
docker exec -i mysql mysql -uroot -padmin123 \
  -e "CREATE DATABASE IF NOT EXISTS demand_system DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
docker exec -i mysql mysql -uroot -padmin123 --default-character-set=utf8mb4 \
  demand_system < database/init.sql
```

- 执行顺序：① 建表 → ② 补列/补索引 → ③ 初始化数据（全部 `INSERT IGNORE` 幂等）→ ④ 收尾。
- 脚本内含 `DROP TABLE IF EXISTS`，**在已有库上执行会清空数据**，请仅用于全新部署。
- 模型配置（`llm_providers` / `llm_models`）含密钥，不入库初始化，部署后请在
  「系统配置 → 模型配置」中录入。

## 本地开发（推荐）

**唯一启动入口：`start-all.bat`**

### 1) 一键全量启动（基础设施 + 后端 + 前端）

```bash
start-all.bat
```

> 自动拉起 MySQL / Redis / RabbitMQ / MinIO / Elasticsearch / Milvus / kkFileView，
> 等待关键容器健康，启动后端（端口 8081）和前端（端口 5170）。

### 2) 常用子命令

```bash
start-all.bat check      # 健康检查（不启动应用）
start-all.bat down       # 停止所有 Docker 容器
start-all.bat e2e        # 全链路 E2E（依赖 + 后端 + 前端 + Playwright）
start-all.bat contract   # 导出 OpenAPI 契约到 contracts/openapi/
```

后端 OpenAPI：`http://localhost:8081/v3/api-docs`
MinIO 控制台：`http://localhost:9001`（admin / admin123456）
RabbitMQ 管理：`http://localhost:15672`（admin / admin）

## 前端构建与部署

两个前端各自独立构建，产物分别在 `demand_frontend/dist`（PC）与 `demand_h5/dist`（H5）：

```bash
cd demand_frontend && npm run build     # vue-tsc → vite build → 产物断言
cd demand_h5       && npm run build     # vite build → 产物断言
```

### ⚠️ 产物断言：dev / 内网地址不许进生产

`npm run build` 会扫产物，一旦**业务代码**里出现 `localhost:端口`、`127.0.0.1:端口`
或指向私网 IP 的绝对 URL，**直接构建失败**（实现见 `tools/wb-dev-address-guard.mjs`）。

> 为什么要有这道闸：2026-10-12 线上故障 —— `useBitableWebSocket.ts` 的兜底值
> `ws://localhost:8081` 被原样打进产物，而 `.env.production` 没设 `VITE_WS_URL`，
> 于是用户浏览器去连**用户自己的机器** → 协同表格 `ERR_CONNECTION_REFUSED`。

**铁律：前端地址的兜底值只能是「相对路径」或「同源推导」。**

```ts
const WS_BASE = import.meta.env.VITE_WS_URL
  || `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}`
// dev 走 vite 的 /ws 代理、生产走 nginx 的 location /ws/，同源两头都通
```

- 后端地址同理：`VITE_API_BASE_URL` 写 `/api`，不要写 `http://localhost:8081/api`。
- 要改 dev 后端地址，用 `VITE_API_PROXY_TARGET`（只在 dev server 生效，**不进产物**）。
- 生产环境变量规范见 `demand_frontend/.env.production.template`
  （`.env.production` 本身被 `.gitignore` 排除，不入库）。
- 校验已构建 / 已部署目录：`node tools/check-dist.mjs /home/www/demand/pc`
- 守卫自身自测：`node tools/selftest-guard.mjs`
- 临时放行（**仅限本地验证，勿用于部署**）：`ALLOW_DEV_ADDR=1 npm run build`
- ⚠️ **不要全量 grep `localhost`**：第三方库自带（`vendor-CTXUQT0Q.js` 里有 3 处），
  守卫按「模块来源」区分业务 / 第三方，只判业务代码，避免误报。

## 跨域白名单：HTTP CORS 与 WebSocket 只有一份

后端 **HTTP CORS**（`CorsConfig`）与 **WebSocket 握手 Origin 校验**（`BitableWebSocketConfig`）
读的是**同一个属性** `cors.allowed-origins`，线上由环境变量 `CORS_ALLOWED_ORIGINS` 注入：

```yaml
# /data2/demand-system/docker-compose.yml
CORS_ALLOWED_ORIGINS: "http://dg.gov.demand.cn,http://19.106.101.220,https://demand.zxmeng.asia"
```

- 加域名 = 两个门同时放开；漏域名 = 两个门同时拒。**改完必须 `docker compose up -d`（recreate）**。
- 启动日志会打印实际生效的 Origin 列表，别靠猜：
  `WebSocket /ws/bitable/{baseId} 允许的握手 Origin（与 cors.allowed-origins 同源，共 N 个）: [...]`
- ⚠️ **历史坑（2026-10-12 线上故障）**：WS 原本自己写死
  `setAllowedOrigins("http://localhost:5170", "http://127.0.0.1:5170")`，
  于是公网与内网入口握手全部 **403**，而普通接口因为 CORS 配的是另一份白名单反而是好的。
  **判据：两份白名单必然分叉 —— 只要"同一个东西配了两处"，迟早出事。**
- 区分两个 403（都叫 403，但不是一个门）：
  | 响应体 | 来源 |
  |---|---|
  | **空** | WebSocket 的 `OriginHandshakeInterceptor` |
  | `Invalid CORS request` | Spring 的 `CorsFilter` |

## 全量集成测试

### 后端集成测试（Testcontainers，需要本机可用 Docker）

```bash
cd demand_backend
mvn verify
```

### 前端 E2E（Playwright）

```bash
cd demand_frontend
npm run test:e2e
```

### 一键全链路 E2E（起依赖 + 起后端 + 起前端 + 跑测试）

```bash
start-all.bat e2e
```

### 导出接口契约（OpenAPI）

```bash
start-all.bat contract
```
<!-- DREAMFIELD_README_HEADER_END -->