# CLAUDE.md

需求管理系统 — 需求全生命周期管理工具。

## 技术栈

Vue 3 + TypeScript + Element Plus + Spring Boot 3.2 + MyBatis-Plus + MySQL 8 + Redis + RabbitMQ + MinIO + Elasticsearch

## 目录

```
demand_frontend/    # 前端 (Vite, 端口517x)
demand_backend/     # 后端 (Spring Boot, 端口8081)
database/           # 数据库初始化脚本
scripts/            # Docker Compose
```

## 关键配置

- 基础设施均为Docker容器部署 (MySQL/Redis/RabbitMQ/MinIO/Elasticsearch)
- RabbitMQ: `admin/admin`, MinIO: `admin/admin123456`
- JWT: 256位+ secret, Access 2h, Refresh 7d
- 配置文件: `demand_backend/src/main/resources/application-dev.yml`，启动先加载记录基础设施对应的访问账号密码信息
- **端口占用处理**: 启动服务时若端口被占用，直接杀掉占用进程后重启，不手动排查
- **SQL维护**: 所有SQL变更整合到 database/init.sql，不新增独立SQL文件

## 架构约定

### 后端分层

模块统一结构: `controller / service / mapper / entity / dto / converter`

| 模块 | 说明 |
|------|------|
| auth | 认证授权 |
| requirement | 需求管理 |
| workflow | 工作流 (StateMachine + PermissionEngine, 位于 workflow/engine/) |
| iteration | 迭代管理 |
| review | 评审管理 |
| project | 项目管理 |
| user | 用户组织 |
| relation | 需求关联 |
| statistics | 统计报表 |
| notification | 通知中心 |
| file | 文件服务 |

### 前端结构

```
api/modules/     # 按模块的API定义
components/      # 公共组件
composables/     # 组合式函数 (useAuth/usePermission/useWorkflow)
stores/          # Pinia状态管理
views/           # 页面
types/           # TypeScript类型
```

## 代码规范

- 后端: Service承载业务逻辑, Controller仅校验调用; LambdaQueryWrapper查询; @Version乐观锁; deleted_at软删除; 用户ID从SecurityUtils.getCurrentUserId()获取
- 前端: Composition API + script setup; 组件以App或业务前缀命名; 工具函数放utils/不依赖Vue实例

## API规范

- Base: `/api/v1/`, 响应 `{ code, message, data }`, 分页 `{ list, total, pageNum, pageSize }`
- 认证: `Bearer <token>`, Swagger: `/swagger-ui.html`
