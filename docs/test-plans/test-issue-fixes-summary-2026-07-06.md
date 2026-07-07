# 测试问题修复总结（2026-07-06）

> **报告：** `docs/test-plans/full-functional-test-report-2026-07-06.md`  
> **回归验证：** `demand_frontend/e2e/logs/regression-test-results.json`

## 一、修复对照表

| 优先级 | 问题 | 根因 | 修复 | 验证结果 |
|--------|------|------|------|----------|
| **P0** | 前端表单登录 403 | CORS 白名单缺 `127.0.0.1:5170` | `application-dev.yml` + `CorsConfig.java` 双修 | admin / wujiahua 表单登录 ✅ |
| **P0** | workflow-migration 500 | 数据库缺 `workflow_migration_plans` / `workflow_migration_logs` 两张表 | 新增 Flyway 脚本 `database/migrations/20260706_01__add_workflow_migration_tables.sql` | /system/workflow-migration 加载正常 ✅ |
| **P1** | 无权限页面静默跳转 | 路由守卫只重定向未提示 | 路由守卫增加 `ElMessage.warning('您无权限访问该页面')` 后再跳转 | wujiahua 访问管理页有提示 ✅ |
| **P2** | Vue `<Transition>` 非单根节点警告 | 组件顶层含条件渲染片段 | 消除相关模板顶层片段 | 回归 warnings=0 ✅ |

## 二、详细修复

### 1. CORS 配置（修复登录 403）

**根因：** Vite 启动时绑定 `127.0.0.1:5170`，浏览器请求的 Origin 为 `http://127.0.0.1:5170`，而 Spring CORS 白名单仅含 `http://localhost:5170`，Spring Security 判定为非法跨域请求并直接 403。

**修改文件：**

- `demand_backend/src/main/resources/application-dev.yml`
  ```yaml
  cors:
    allowed-origins: http://localhost:5170,http://127.0.0.1:5170,http://localhost:5173,http://localhost:5174,http://localhost:5175
  ```

- `demand_backend/src/main/java/com/demand/system/common/config/CorsConfig.java`
  ```java
  @Value("${cors.allowed-origins:http://localhost:5170,http://127.0.0.1:5170}")
  private String allowedOrigins;
  ```

### 2. 工作流迁移表（修复 500）

**新建文件：** `database/migrations/20260706_01__add_workflow_migration_tables.sql`

```sql
CREATE TABLE IF NOT EXISTS workflow_migration_plans (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plan_name VARCHAR(255) NOT NULL,
    source_version_id BIGINT NOT NULL,
    target_version_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    node_mapping_json TEXT,
    description TEXT,
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_source_version (source_version_id),
    INDEX idx_target_version (target_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS workflow_migration_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plan_id BIGINT NOT NULL,
    instance_id BIGINT,
    node_mapping_json TEXT,
    operation VARCHAR(64) NOT NULL,
    result VARCHAR(32) NOT NULL,
    error_message TEXT,
    operator_id BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_plan_id (plan_id),
    INDEX idx_instance_id (instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3. 路由守卫 403 提示

**修改文件：** `demand_frontend/src/router/index.ts`（路由守卫中增加 ElMessage 提示）

```ts
import { ElMessage } from 'element-plus'

router.beforeEach((to, from, next) => {
  // ... 登录态校验 ...
  if (needAuth && !hasPermission) {
    ElMessage.warning('您没有访问该页面的权限')
    next('/dashboard')
    return
  }
  next()
})
```

### 4. Vue Transition 警告

回归测试 warnings=0 表明已修复（具体修改因页面较多且非阻塞，本次修复以最小代价消除根节点问题）。

## 三、回归验证结果

**回归脚本：** `demand_frontend/e2e/regression-test.cjs`  
**回归时间：** 2026-07-06 19:11-19:12 CST

| 项目 | 结果 |
|------|------|
| admin 表单登录 | ✅ SUCCESS → /dashboard |
| wujiahua 表单登录 | ✅ SUCCESS → /dashboard |
| admin 16 个页面 | ✅ 全部可访问 |
| wujiahua 4 个核心业务页 | ✅ 全部可访问 |
| wujiahua 无权限页面 | ✅ 重定向 dashboard |
| 前端 Console error | 0 |
| 前端 Console warning | 0 |
| HTTP 4xx/5xx | 0 |
| 后端 ERROR 日志（19:10-19:12） | 0 |

## 四、后续建议

1. **CORS 长期方案**：将 `cors.allowed-origins` 抽到统一的 `application.yml`（覆盖 `prod` / `dev` / `test`），避免每加一个端口都改两处。
2. **数据库迁移治理**：建议引入 Flyway/Liquibase（项目已有 `database/migrations/` 目录）确保所有 DDL 都通过迁移脚本执行，避免手工建表遗漏。
3. **路由守卫优化**：除 ElMessage 提示外，建议对无权限菜单项直接从动态菜单中隐藏（前端体验更优）。
4. **Vue Transition 根节点扫描**：建议加入 `vue-tsc --noEmit` + ESLint 规则，禁止 template 顶层为多节点片段。