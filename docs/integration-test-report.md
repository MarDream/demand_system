# 综合运营管理平台 — 全量集成测试异常报告

**测试时间**: 2026-07-13  
**测试账号**: admin / admin123  
**后端地址**: http://127.0.0.1:8081  
**测试方式**: API 直连（Node.js HTTP 客户端）  
**测试脚本**: `demand_frontend/e2e/full-integration-api.cjs`

---

## 一、测试总览

| 指标 | 数值 |
|------|------|
| 覆盖模块 | 15 |
| 测试用例 | 35 |
| ✅ 通过 | 29 |
| ⏭️ 跳过 | 4 |
| ❌ 失败 | 2 |
| **通过率** | **82.9%** (29/35) |

---

## 二、各模块测试结果

| 模块 | 用例数 | 通过 | 跳过 | 失败 | 状态 |
|------|--------|------|------|------|------|
| AUTH 认证 | 5 | 5 | 0 | 0 | ✅ |
| PROJ 项目 | 3 | 3 | 0 | 0 | ✅ |
| REQ 需求管理 | 6 | 3 | 3 | 0 | ⚠️ 部分跳过 |
| ITER 迭代 | 2 | 2 | 0 | 0 | ✅ |
| WF 工作流 | 3 | 3 | 0 | 0 | ✅ |
| KB 知识库 | 3 | 3 | 0 | 0 | ✅ |
| FILE 文件上传 | 1 | 0 | 1 | 0 | ⏭️ |
| USER 用户 | 2 | 2 | 0 | 0 | ✅ |
| NOTIF 通知 | 2 | 2 | 0 | 0 | ✅ |
| RATE 评分统计 | 1 | 0 | 0 | 1 | ❌ |
| LLM 模型配置 | 2 | 2 | 0 | 0 | ✅ |
| BITABLE 多维表格 | 1 | 0 | 0 | 1 | ❌ |
| CFG 需求配置 | 2 | 2 | 0 | 0 | ✅ |
| ORG 组织架构 | 1 | 1 | 0 | 0 | ✅ |
| META 元数据 | 1 | 1 | 0 | 0 | ✅ |

---

## 三、后端异常详情（2 个 BUG）

### BUG-1: [RATE] 评分统计接口 SQL 执行失败

**严重级别**: 🔴 P0 — 功能完全不可用  
**接口**: `GET /v1/statistics/rating/trend`  
**HTTP 状态**: 200（业务 code 5000）  
**错误信息**: `bad SQL grammar []`

**根因分析**:

1. **Controller 路由缺少 `/api` 前缀**  
   - `RequirementRatingStatisticsController` 的 `@RequestMapping("/v1/statistics/rating")`  
   - 其他所有 Controller 都使用 `/api/v1/...` 前缀，唯独此 Controller 缺少 `/api`  
   - 导致前端调用 `/api/v1/statistics/rating/trend` 返回 404（No static resource）

2. **数据库缺少 `rating_dimensions` 列**  
   - 实体 `RequirementApprovalEvaluation.java` 定义了 `ratingDimensions` 字段（JSON 类型，带 `JacksonTypeHandler`）  
   - 但数据库 `requirement_approval_evaluations` 表中**不存在 `rating_dimensions` 列**  
   - MyBatis-Plus 生成 SELECT SQL 时包含此列，MySQL 执行报错 `Unknown column`  
   - 错误被截断显示为 `bad SQL grammar []`

**修复方案**:

```sql
-- 1. 添加缺失的 rating_dimensions 列
ALTER TABLE requirement_approval_evaluations 
ADD COLUMN rating_dimensions JSON DEFAULT NULL COMMENT '维度评分(JSON)' AFTER rating;
```

```java
// 2. 修复 Controller 路由前缀
// 文件: RequirementRatingStatisticsController.java
// 修改前:
@RequestMapping("/v1/statistics/rating")
// 修改后:
@RequestMapping("/api/v1/statistics/rating")
```

**影响范围**: 评分统计页面完全不可用（趋势图、分布图、维度平均、低分需求列表）

---

### BUG-2: [BITABLE] 多维表格 bases 接口 SQL 执行失败

**严重级别**: 🔴 P0 — 功能完全不可用  
**接口**: `GET /api/v1/bitable/bases`  
**HTTP 状态**: 200（业务 code 5000）  
**错误信息**: `bad SQL grammar []`

**根因分析**:

- 数据库中**不存在 `bitable_bases` 表**  
- 迁移脚本 `database/migrations/20260709_01__add_bitable_tables.sql` 已编写但**未执行**  
- MyBatis-Plus 查询 `bitable_bases` 表时，MySQL 返回 `Table doesn't exist`  
- 错误被截断显示为 `bad SQL grammar []`

**修复方案**:

```bash
# 执行迁移脚本创建 bitable 相关表
docker exec -i mysql mysql -uroot -padmin123 demand_system < database/migrations/20260709_01__add_bitable_tables.sql
```

**影响范围**: 多维表格模块完全不可用（创建/查看/编辑表格）

---

## 四、前端异常详情

### 前端路由与后端 API 不一致

| 前端路由 | 后端实际路由 | 问题 |
|----------|-------------|------|
| `/api/v1/statistics/rating/trend` | `/v1/statistics/rating/trend` | Controller 缺少 `/api` 前缀 |
| `/api/v1/workflow/{id}/versions` | `/api/v1/workflows/{id}/versions` | 前端用 `workflow`（单数），后端用 `workflows`（复数） |

### 知识库搜索接口参数不匹配

- 前端发送 `{ query: "..." }`  
- 后端期望 `{ keyword: "..." }`  
- 需统一字段名

---

## 五、跳过用例说明

| 用例 | 跳过原因 | 建议 |
|------|---------|------|
| REQ-004 需求详情 | 创建需求后列表未返回新数据（可能需刷新） | 检查创建接口返回的 ID |
| REQ-005 评审记录 | 依赖 REQ-004 的 reqId | 修复 REQ-004 后自动解除 |
| REQ-006 提交审批 | 依赖 REQ-004 的 reqId | 修复 REQ-004 后自动解除 |
| FILE-001 文件上传 | 测试文件 `public/logo.svg` 不存在 | 准备测试文件或改用动态生成 |

---

## 六、基础设施问题

### Redis 阻塞导致后端假死

**现象**: 后端登录接口超时 10s+ 无响应  
**根因**: `BitableRedisSubscriber` 执行 BLPOP 阻塞 Redis 连接，Lettuce 客户端超时（1分钟），线程池耗尽  
**临时修复**: `CLIENT KILL` 杀掉阻塞的 Redis 客户端  
**永久修复**: 检查 `BitableRedisSubscriber` 的 BLPOP 超时设置，避免无限阻塞

### Spring Boot 端口随机化

**现象**: `mvn spring-boot:run` 绑定端口 13763 而非配置的 8081  
**根因**: 未明确（可能与 Spring Boot 4.0.7 或 Maven 参数传递有关）  
**临时修复**: 启动时添加 `-Dspring-boot.run.arguments="--server.port=8081"`  
**永久修复**: 排查 Maven spring-boot-plugin 参数传递机制

---

## 七、修复优先级建议

| 优先级 | BUG | 预计工时 |
|--------|-----|---------|
| P0 | 执行 bitable 迁移脚本建表 | 5 分钟 |
| P0 | 添加 rating_dimensions 列 | 5 分钟 |
| P0 | 修复 Rating Controller @RequestMapping 前缀 | 2 分钟 |
| P1 | 修复 BitableRedisSubscriber BLPOP 阻塞 | 1 小时 |
| P1 | 统一知识库搜索接口参数名 | 15 分钟 |
| P2 | 排查 Spring Boot 端口随机化问题 | 2 小时 |
| P2 | 准备文件上传测试数据 | 15 分钟 |

---

## 八、测试文件清单

| 文件 | 说明 |
|------|------|
| `demand_frontend/e2e/full-integration-api.cjs` | 全量集成测试脚本 |
| `demand_frontend/test-report.json` | JSON 格式测试报告 |
| `demand_frontend/test-report.html` | HTML 格式测试报告（可视化） |
