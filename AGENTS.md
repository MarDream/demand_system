# AGENTS.md

本文件定义本仓库内 Codex/Agent 的工作规则。优先遵守用户明确指令；没有明确指令时按本文执行。不得覆盖、回滚或删除用户已有改动。

## 项目概览

需求管理系统（Demand System），包含前端、后端、数据库脚本、部署脚本和项目文档。

核心目录：

```text
demand_frontend/   Vue 3 + TypeScript + Vite + Element Plus + Pinia
demand_backend/    Java 17 + Spring Boot 3.2 + MyBatis-Plus + Spring Security
database/          数据库初始化、迁移、验证脚本
scripts/           Docker Compose 与运维脚本
docs/              设计、接口、实施文档
```

依赖服务：MySQL、Redis、RabbitMQ、MinIO、Elasticsearch、Milvus。

## 常用命令

前端：

```bash
cd demand_frontend
npm install
npm run dev
npm run build
npm run test:e2e
```

后端：

```bash
cd demand_backend
mvn compile
mvn test
mvn verify
mvn package -DskipTests
mvn spring-boot:run
```

基础设施：

```bash
docker-compose -f scripts/docker-compose.yml up -d
docker ps
```

关键端口：后端 `8081`，MySQL `3306`，Redis `6379`，RabbitMQ `5672/15672`，MinIO `9000/9001`，Elasticsearch `9200`，Milvus `19530`。

## 启动与中间件处理

- 启动前先检查项目所需端口是否被占用；若目标端口被占用，直接结束占用该端口的进程，再启动目标服务。
- 处理端口冲突时优先释放默认端口占用，不要通过擅自修改项目默认端口绕过，除非用户明确要求。
- 遇到 MySQL、Redis、RabbitMQ、MinIO、Elasticsearch、Milvus 等中间件连接异常时，先检查配置、账密、服务状态、容器状态、网络连通性和日志。
- 未经用户明确允许，不得移除、卸载、重装、重建、清空数据目录或替换任何开发环境中间件实例。
- 处理开发环境问题时，以 `scripts/docker-compose.yml`、`demand_backend/src/main/resources/application-dev.yml`、`demand_frontend/.env.development` 等现有配置为准，记住各中间件的账密和部署方式，不得随机猜测、批量尝试或擅自改写。

## 配置与敏感信息

- 后端开发配置：`demand_backend/src/main/resources/application-dev.yml`。
- 前端开发环境：`demand_frontend/.env.development`。
- 本地基础设施账号以 `scripts/docker-compose.yml` 为准。
- 数据库脚本只维护 `database/init.sql`；不要新增迁移、临时、验证或测试 SQL 文件。
- 不要新增真实 API Key、邮箱密码、数据库密码或生产 Token。
- 不要把已有敏感值复制到新文件、日志、文档或最终回复中。
- 示例配置优先写入 `.template` 文件，并使用占位符。

## 后端规则

模块位于 `demand_backend/src/main/java/com/demand/system/module/`。每个业务模块优先使用：

```text
controller / service / service/impl / mapper / entity / dto / converter
```

必须遵守：

- Controller 只做参数校验、权限注解和调用 Service。
- Service 承载业务逻辑、事务边界和业务权限校验。
- Mapper 只负责数据访问。
- 用户身份从 `SecurityUtils.getCurrentUserId()` 或认证上下文获取，禁止硬编码。
- 查询优先使用 MyBatis-Plus `LambdaQueryWrapper`。
- 软删除字段 `deleted_at` 的约定是 `0=未删除，1=已删除`。
- 使用 `@TableLogic`、`@Version` 时必须与数据库字段约定一致。
- 鉴权基于 Spring Security + JWT + RBAC；不得绕过项目、组织或业务归属的数据隔离。
- 不要把 401/403 改成 200 响应。

工作流相关逻辑集中在 `workflow/engine/`，修改状态流转、审批、权限时必须同时检查兼容性、审计记录和异常路径。

## 前端规则

源码位于 `demand_frontend/src/`。

必须遵守：

- 使用 Vue 3 Composition API 和 `<script setup lang="ts">`。
- API 调用放在 `src/api/modules/`，不要散落在页面组件中。
- HTTP 返回类型必须显式声明 TypeScript 类型。
- 跨页面共享状态使用 Pinia；页面局部状态留在组件内。
- 工具函数放 `src/utils/`，不得依赖 Vue 组件实例。
- 权限判断复用 `src/composables/usePermission.ts` 和 `src/directives/permission.ts`。
- 路由权限和登录态逻辑放在 `src/router/guards.ts`。
- UI 遵循 Element Plus 和现有样式，不随意引入新 UI 库。

## API 规则

- Base Path：`/api/v1/`
- 认证头：`Authorization: Bearer <token>`
- 统一响应：`{ code, message, data }`
- 分页响应：`{ list, total, pageNum, pageSize }`
- Swagger：`/swagger-ui.html`

新增或修改 API 时同步检查：

- 后端 Controller、DTO、Service。
- 前端 `src/api/modules/`。
- 前端 `src/types/`。
- 相关页面、表单、列表和权限控制。

## 工作流程

执行任务时：

1. 先阅读相关代码，不凭目录名猜实现。
2. 检查工作区状态，识别用户已有改动。
3. 只修改与任务直接相关的文件。
4. 沿用现有代码风格、命名、异常处理和响应结构。
5. 文档按主题整合到 `docs/`，同类内容维护在同一份文档中；不要新增实施总结、文件清单、临时说明等冗余文档。
6. 修改后运行与变更范围匹配的最小验证命令。
7. 最终说明改了什么、验证了什么、还剩什么风险。

禁止：

- 未经用户明确要求，不得使用 `git reset --hard`、`git checkout -- <file>` 或等价操作。
- 不得删除未确认用途的文件。
- 不得进行与任务无关的大重构。
- 不得提交构建产物、日志、临时文件、真实密钥或 `.omx/` 本地状态。

## 验证要求

按变更范围选择最小验证：

| 变更范围 | 验证命令 |
| --- | --- |
| 仅文档 | 检查 Markdown 内容准确性 |
| 前端代码 | `cd demand_frontend && npm run build` |
| 前端交互 | `cd demand_frontend && npm run test:e2e` |
| 后端普通代码 | `cd demand_backend && mvn test` |
| 后端编译级变更 | `cd demand_backend && mvn compile` |
| 后端集成、数据库、MQ、MinIO、Milvus | `cd demand_backend && mvn verify` |
| Docker Compose 或基础设施配置 | `docker-compose -f scripts/docker-compose.yml config` |

如果验证因环境、权限、网络或依赖服务不可用而未执行，最终回复必须明确说明原因。
