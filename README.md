<!--
╔══════════════════════════════════════════════════════════════════════╗
║  DreamSeed 种梦计划 — AI创造者大赛  官方 README 模板                ║
║                                                                      ║
║  使用说明：                                                          ║
║  1. 将本模板放在参赛仓库根目录 README.md 的顶部                       ║
║  2. 头图使用 DreamField 官方公开活动图片地址                         ║
║  3. 请保留 DREAMFIELD_README_HEADER_START / END 标识                 ║
║  4. 分割线以下供创作者自由编写项目内容                               ║
╚══════════════════════════════════════════════════════════════════════╝
-->

<!-- DREAMFIELD_README_HEADER_START -->

<p align="center">
  <a href="https://www.dreamfield.top">
    <img src="https://www.dreamfield.top/dream-field/contest-readme/assets/dreamseed-readme-banner.png" alt="DreamSeed 种梦计划参赛作品" width="100%" />
  </a>
</p>

# demand_system
需求管理平台，用于软件开发管理过程中需求的生命周期管理

## 项目结构

```
demand_system/
├── demand_frontend/          # Vue3 Web端（Vite + Element Plus）
├── demand_backend/           # Spring Boot 3.2 后端（MyBatis-Plus）
├── database/                 # MySQL 初始化脚本
├── scripts/                  # 基础设施与一键脚本
└── docs/                     # PRD 等文档
```

## 本地开发（推荐）

### 1) 启动基础设施（MySQL / Redis / RabbitMQ / MinIO / ES）

```bash
bash scripts/compose-up.sh
```

> MinIO 会自动创建 bucket：`demand-system`

### 2) 启动后端

```bash
cd demand_backend
mvn spring-boot:run
```

后端端口：`8081`，OpenAPI：`http://localhost:8081/v3/api-docs`

### 3) 启动前端

```bash
cd demand_frontend
npm install
npm run dev
```

前端端口：`5176`

## 全量集成测试（后端 + E2E + 一键全链路）

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

### 一键全链路（起依赖 + 起后端 + 起前端 + 跑 E2E）

```bash
bash scripts/e2e.sh
```

### 导出接口契约（OpenAPI）

```bash
bash scripts/contract.sh
```
<!-- DREAMFIELD_README_HEADER_END -->