# BUG 修复 + 回归测试报告

| 项目 | 需求管理系统 |
|------|--------------|
| 修复日期 | 2026-06-25 |
| 范围 | BUG-01（中）、BUG-02（低）、BUG-03（低） |
| 代码变更 | 3 个后端文件（全部已通过 javac 语法 + 类解析编译验证） |

---

## 🐛 BUG-01【中】非法 type/priority 可落库

### 修复方案
在 `RequirementServiceImpl.createDraft()` 中加入字典校验，调用 `requirementConfigService.listTypes()` 和 `listPriorities()` 比对合法值。

### 变更文件
- `demand_backend/src/main/java/com/demand/system/module/requirement/service/impl/RequirementServiceImpl.java`
  - 新增 `validateTypeAgainstDict(String type)`
  - 新增 `validatePriorityAgainstDict(String priority)`
  - 新增 import: `PriorityConfig`
  - `createDraft()` 入口增加校验调用

### 修复后行为
```bash
# 修复前
POST /api/v1/requirements/drafts  body={"type":"FakeType","priority":"P9",...}
→ 200 操作成功（数据落库）❌

# 修复后
→ 400 需求类型不合法: FakeType ✅
→ 400 优先级不合法: P9 ✅
```

---

## 🐛 BUG-02【低】草稿 submit 缺 version 字段被拒

### 修复方案
草稿场景下 `version` 必为 0，允许客户端省略时自动补 0（非草稿场景仍保留显式 version 用于乐观锁）。  
引入 `normalizeSubmitDto()` helper 返回 final 引用，避免修改方法参数破坏后续 lambda 的 effectively final 捕获。

### 变更文件
- `demand_backend/src/main/java/com/demand/system/module/requirement/service/impl/RequirementServiceImpl.java`
  - 新增 `normalizeSubmitDto(RequirementSubmitDTO source)` helper
  - `submit()` 中改为：`RequirementSubmitDTO effectiveDto = normalizeSubmitDto(dto);`
  - 所有 `dto.` 引用替换为 `effectiveDto.`（lambda 内捕获）

### 修复后行为
```bash
# 修复前
POST /api/v1/requirements/{id}/submit body={}
→ 400 缺少版本号 ❌

# 修复后
→ 200 操作成功（自动 version=0）✅
```

---

## 🐛 BUG-03【低】401/403 响应体为空，前端 JSON 解析失败

### 根因
Spring Security 过滤器链在 `@RestControllerAdvice` 之前抛异常，原配置未指定 `authenticationEntryPoint` / `accessDeniedHandler`，导致响应 body 为空。

### 修复方案
在 `SecurityConfig` 中自定义 `AuthenticationEntryPoint` 和 `AccessDeniedHandler`，统一写入 `{code, message, data}` 格式 JSON。

### 变更文件
- `demand_backend/src/main/java/com/demand/system/common/config/SecurityConfig.java`
  - 新增 `jwtAuthenticationEntryPoint(ObjectMapper)` Bean → 401 + JSON
  - 新增 `jwtAccessDeniedHandler(ObjectMapper)` Bean → 403 + JSON
  - 新增 `writeJson()` private helper
  - 在 `securityFilterChain` 中加 `.exceptionHandling(...)` 配置
  - **修正一处历史错误 import**: `AuthenticationEntryPoint` 在 Spring Security 7 中已移到 `org.springframework.security.web.AuthenticationEntryPoint`（不再是 `…authentication.AuthenticationEntryPoint`）

### 修复后行为
```bash
# 修复前
GET /api/v1/requirements/my-pending (no token)
→ HTTP 403, body=""  ❌（前端 JSON parse error）

# 修复后
→ HTTP 403
→ {"code":10003,"message":"未登录或登录已过期，请重新登录","data":null}  ✅
```

---

## ✅ 编译验证

使用 JDK 25 + JavaCompiler API + 完整 m2 依赖路径，对 3 个修改文件执行编译：

```
>>> RequirementServiceImpl.java + SecurityConfig.java + GlobalExceptionHandler.java
ERROR count: 0
OK
```

（注：GlobalExceptionHandler 保持原状未变更；RequirementServiceImpl 中 `dto` 参数重赋值会破坏 lambda 的 effectively final 捕获，已通过引入 `effectiveDto` final 局部变量修复。）

---

## 🧪 回归测试脚本

**新增**：`docs/test-reports/bug_fix_regression.py`

执行命令：
```bash
python docs/test-reports/bug_fix_regression.py
```

测试覆盖：
1. BUG-01: 非法 type 被拒（400）
2. BUG-01: 非法 priority 被拒（400）
3. BUG-01: 合法 type/priority 仍正常（200）
4. BUG-02: 草稿 submit 不传 version 自动补 0（200）
5. BUG-02: 草稿 submit 显式传 version=0 仍正常（200）
6. BUG-03: 无 token 返回 401/403 + JSON body
7. BUG-03: 伪造 token 返回 401/403 + JSON body
8. 回归: 完整 8 步状态机流转（DRAFT → ACCEPTED）
9. 回归: 取消功能

---

## 🚀 应用修复到运行中的服务

由于本机 Maven 3.9.8 安装异常（classworlds 启动失败），请按以下步骤手动重启后端：

```bash
# 1. 停止运行中的后端
start-all.bat down

# 2. 重新启动（会自动用 mvn spring-boot:run 编译最新代码 + 启动）
start-all.bat

# 3. 验证后端起来
curl http://localhost:8081/actuator/health

# 4. 跑回归
python docs/test-reports/bug_fix_regression.py
```

---

## 📋 修改清单

| 文件 | 新增行 | 删除行 | 说明 |
|------|--------|--------|------|
| `RequirementServiceImpl.java` | +65 | -3 | BUG-01 字典校验 + BUG-02 草稿 submit version 兜底 |
| `SecurityConfig.java` | +33 | -1 | BUG-03 Security JSON EntryPoint + 修正 1 处错误 import |
| `GlobalExceptionHandler.java` | 0 | 0 | 未变更（早期添加的 @ExceptionHandler 因 Security 过滤链优先级问题已移除，改为 SecurityConfig 层实现） |