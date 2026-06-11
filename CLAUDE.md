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
E:\Project\mygit\kkFileView  # kkFileView服务源代码，可直接修改适配预览所需服务
```

## 关键配置

- 基础设施均为Docker容器部署 (MySQL/Redis/RabbitMQ/MinIO/Elasticsearch/Milvus)

### 基础组件账号密码

| 组件 | 端口 | 账号 | 密码 | 说明 |
|------|------|------|------|------|
| MySQL | 3306 | root | admin123 | 数据库: demand_system |
| Redis | 6379 | - | (无密码) | DB 0 |
| RabbitMQ | 5672/15672 | admin | admin | 管理界面: http://localhost:15672 |
| MinIO | 9000/9001 | admin | admin123456 | 控制台: http://localhost:9001 |
| Elasticsearch | 9200/9300 | - | - | 无认证 |
| Milvus | 19530/9091 | - | - | 向量数据库 |

- JWT: 256位+ secret, Access 2h, Refresh 7d
- 配置文件: `demand_backend/src/main/resources/application-dev.yml`，启动先加载记录基础设施对应的访问账号密码信息
## 启动脚本

| 脚本 | 用途 |
|------|------|
| `start-all.bat` | 一键启动前后端+全部 Docker 容器（含 kkfileview） |
| `start-kkfileview.cmd` | 单独拉起 kkfileview 容器（端口8012），用 `docker compose` 方式 |
| `scripts/compose-check.sh` / `compose-check.bat` | 检查全部基础设施 + kkfileview 容器健康状态 |

### 启动命令

```bash
# 方式1: 一键全量启动 (前端5170 + 后端8081 + kkfileview8012 + 全部基础设施)
start-all.bat

# 方式2: 单独启动各服务
# 1. 启动Docker容器（含 kkfileview, 推荐）
docker compose -f scripts/docker-compose.yml up -d

# 2. (可选) 单独拉起 kkfileview 预览服务
start-kkfileview.cmd

# 3. 启动后端 (端口8081)
cd demand_backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 4. 启动前端 (端口5170)
cd demand_frontend && npm run dev
```

### kkFileView 容器化要点
- 镜像：`kkfileview:5.0.1`，由 `E:\Project\mygit\kkFileView\Dockerfile.standalone` 构建
- 内置 CJK 字体（fonts-noto-cjk） + 浏览器原生 PDF viewer 模板（scripts/pdf-direct.ftl）
- PDF 缓存目录走 named volume `kkfileview_file:/opt/kkFileView-5.0.0/file`，避免容器重启后丢缓存
- 如需重建镜像：`cd E:\Project\mygit\kkFileView && ./scripts/build-docker.sh 5.0.1`
- 详见 `.claude/skills/kkfileview-cjk-pdf-rendering-fix.md`

## 端口占用处理

启动服务时若端口被占用，直接杀掉占用进程后重启，不手动排查

## SQL维护
- **Docker MySQL 中文乱码**: 通过 `docker exec mysql mysql` 执行SQL时必须加 `--default-character-set=utf8mb4`，否则中文会乱码

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
