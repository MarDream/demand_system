# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

需求管理系统 (Demand System) - 一站式需求管理工具，覆盖从需求收集到上线验收的全生命周期。

### Technology Stack

| Layer | Technology |
|-------|------------|
| Frontend | Vue 3 + TypeScript + Vite + Element Plus + Pinia |
| Backend | Spring Boot 3.2 + MyBatis-Plus + Spring Security |
| Database | MySQL 8.0 |
| Cache | Redis |
| MQ | RabbitMQ |
| File Storage | MinIO |
| Search | Elasticsearch |

### Directory Structure

```
demand_system/
├── demand_frontend/          # Vue3 Web端 (端口517x)
├── demand_backend/            # Spring Boot后端 (端口8081)
├── database/                 # 数据库初始化脚本
├── scripts/                  # Docker Compose配置
└── docs/                    # PRD文档
```

## Development Commands

### Frontend (demand_frontend/)

```bash
npm install                    # 安装依赖
npm run dev                    # 开发服务器 (localhost:517x)
npm run build                  # 生产构建
npm run preview                # 预览构建结果
```

### Backend (demand_backend/)

```bash
mvn compile                   # 编译
mvn package -DskipTests       # 构建JAR (跳过测试)
mvn spring-boot:run           # 开发模式启动
java -jar target/demand-system-1.0.0-SNAPSHOT.jar  # 生产启动
```

### Infrastructure (scripts/)

```bash
docker-compose -f scripts/docker-compose.yml up -d   # 启动所有基础设施
docker ps                                              # 查看运行中的容器
```

**容器端口映射**：
- MySQL: 3306
- Redis: 6379
- RabbitMQ: 5672 (AMQP), 15672 (管理界面)
- MinIO: 9000 (API), 9001 (控制台)
- Elasticsearch: 9200

## Critical Configuration

### 基础设施认证 (必须与docker-compose.yml一致)

**RabbitMQ**: `admin / admin`
**MinIO**: `admin / admin123456`

配置文件: `demand_backend/src/main/resources/application-dev.yml`

### JWT Configuration

- Secret: 需256位以上
- Access Token有效期: 2小时 (7200000ms)
- Refresh Token有效期: 7天 (604800000ms)

## Architecture Patterns

### Backend (分层结构)

```
module/
├── auth/           # 认证授权
├── user/           # 用户与组织架构
├── project/        # 项目管理
├── requirement/    # 需求管理
├── workflow/       # 工作流引擎 (StateMachine + PermissionEngine)
├── iteration/      # 迭代管理
├── review/         # 评审管理
├── relation/       # 需求关联
├── statistics/     # 统计报表
├── notification/  # 通知中心
└── file/          # 文件服务
```

**每个模块遵循统一结构**: `controller / service / mapper / entity / dto / converter`

### Frontend (Vue3最佳实践)

```
src/
├── api/modules/    # 按业务模块的API定义
├── components/     # 公共组件 (common/requirement/workflow/charts/layout)
├── composables/    # 组合式函数 (useAuth/usePermission/useWorkflow)
├── stores/         # Pinia状态管理
├── views/          # 页面视图
└── types/          # TypeScript类型定义
```

### Security Architecture

- JWT Token认证 (Spring Security + 自定义JwtAuthenticationFilter)
- RBAC权限控制 (@PreAuthorize注解)
- 数据权限: 项目级隔离
- Token刷新: 前端自动处理401并刷新

### Workflow Engine

核心组件位于 `workflow/engine/`:
- **StateMachine**: 状态流转执行器，含乐观锁并发控制
- **PermissionEngine**: 权限校验引擎，支持角色/用户/动态指定人

## Code Conventions

### Backend

- Service层承载所有业务逻辑，Controller仅做参数校验和调用
- 使用MyBatis-Plus的LambdaQueryWrapper进行类型安全查询
- 使用@Version字段实现乐观锁
- 软删除: 使用deleted_at字段，0=已删除，null=正常
- 用户ID: 必须从SecurityUtils.getCurrentUserId()获取，禁止硬编码

### Frontend

- 使用Composition API + `<script setup>`
- API返回类型统一使用泛型 `Promise<T>`
- 组件命名: 公共组件以`App`或业务前缀开头
- 工具函数: 放utils/目录，不依赖Vue实例

## API Standards

- Base Path: `/api/v1/`
- 统一响应格式: `{ code, message, data }`
- 分页格式: `{ list, total, pageNum, pageSize }`
- 认证: `Authorization: Bearer <token>`
- Swagger文档: `/swagger-ui.html`

### Key API Endpoints

| Resource | Endpoints |
|----------|-----------|
| /requirements | GET, POST, GET/:id, PUT/:id, DELETE/:id |
| /workflow | GET/states, POST/transition, GET/versions |
| /iterations | GET, POST, PUT/:id, POST/:id/requirements |
| /auth | POST/login, POST/refresh, GET/currentUser |
