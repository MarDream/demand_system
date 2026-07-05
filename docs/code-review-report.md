# 代码审查报告 - 综合运营管理平台

**审查日期**: 2026-07-05  
**审查范围**: 前端 Vue 3 + TypeScript + Element Plus；后端 Spring Boot  
**审查重点**: 安全、正确性、性能、可维护性、测试覆盖

---

## 一、概览

项目整体代码质量较好，架构分层清晰，前后端职责明确。后端使用 Spring Security + JWT 做认证授权，前端使用 Vue 3 + Composition API + Element Plus 构建管理界面。工作流引擎、RAG 知识库、LLM 模型管理等功能模块都有较为完整的实现。

以下按优先级从高到低列出发现的问题和改进建议。

---

## 二、🔴 阻塞性问题

### B1. CORS 跨域配置过于宽松

**文件**: `demand_backend/src/main/java/com/demand/system/common/config/CorsConfig.java` (第16行)

```java
config.addAllowedOriginPattern("*");
config.addAllowedHeader("*");
```

**问题**: 允许所有域名和所有请求头，在未使用反向代理做同源策略控制的场景下存在 CSRF 攻击风险。

**建议**:
- 生产环境配置明确的白名单域名
- 或通过 Spring Security 的 `cors()` 方法使用更细粒度的控制
- 开发环境可保留，但建议通过 profile 切换

### B2. 删除操作缺少权限校验

**文件**: `demand_backend/src/main/java/com/demand/system/module/requirement/controller/RequirementController.java` (第349行)

```java
@DeleteMapping("/{id}")
public Result<Void> delete(@PathVariable Long id) {
```

**问题**: `delete` 方法缺少 `@PreAuthorize` 注解，任何已登录用户或未登录用户（如果 filter 放行了该路径）都可以删除需求。对比其他删除操作（如 `ProjectController.delete`）都加了权限校验，此处明显是遗漏。

**建议**:
```java
@PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:requirement:delete')")
```

### B3. `getHistory` / `getComments` 等数据查询接口缺少身份校验

**文件**: `RequirementController.java` 第370-381行

```java
@GetMapping("/{id}/history")
public Result<List<Map<String, Object>>> getHistory(@PathVariable Long id) {
    return Result.success(requirementService.getHistory(id));
}
```

**问题**: `getHistory`、`getComments`、`getApprovalEvaluations`、`getChildren`、`getDetailBatch`、`getDetail` 等接口都没有 `@PreAuthorize`。至少需要 `isAuthenticated()`，否则未认证用户可以枚举需求 ID 来获取敏感数据。

**建议**: 为所有数据读取接口添加 `@PreAuthorize("isAuthenticated()")`。

### B4. LLM API Key 在前端传输中存在泄露风险

**文件**: `LlmProviderServiceImpl.java` 第416-418行

```java
private String maskKey(String key) {
    if (key.length() <= 8) return "****";
    return key.substring(0, 3) + "****" + key.substring(key.length() - 4);
}
```

**问题**: API Key 虽然做了 mask（`sk-a***bcd`），但前端仍能拿到 `getApiKey(Long id)` 接口的 API Key 后半部分。且 `list()` 接口会返回 masked key。

**建议**:
- 前端绝不需要完整 API Key，建议 `getApiKey` 接口只返回 mask 后的值或删除该接口
- 后端测试接口直接由后端调用，不需要把 key 传给前端
- 确认 `LlmProviderVO` 中 `apiKey` 字段在前端 `list` 接口中是否真的不返回明文

### B5. 前端 `description` 最大长度后端正反校验不一致

**文件**: `RequirementForm.vue` 第19行
```html
<el-input :rows="4" placeholder="请输入需求描述" maxlength="2000" show-word-limit />
```

**问题**: 富文本编辑器（`create.vue` 使用 IsleEditor）生成的 HTML 包含标签，实际文本长度远超看起来的字符数。MySQL `text` 字段有 65535 字节限制，如果字数过多（尤其是中文 Unicode），可能截断或入库失败。**后端`@Valid`校验需要显式限制 description 长度。**

**建议**:
- 后端 DTO 添加 `@Size(max = 5000)` 限制
- 或在入库前做长度校验

---

## 三、🟡 建议性问题

### S1. N+1 查询 — 版本列表中统计运行中实例数

**文件**: `WorkflowVersionService.java` 第222-229行

```java
for (WorkflowVersion version : versions) {
    Long count = workflowInstanceMapper.selectCount(
            new LambdaQueryWrapper<WorkflowInstance>()
                    .eq(WorkflowInstance::getWorkflowVersionId, version.getId())
                    .eq(WorkflowInstance::getStatus, "running"));
    instanceCountMap.put(version.getId(), count);
}
```

**问题**: 在循环中逐个查询每个版本的运行中实例数，如果版本很多会产生大量 SQL。

**建议**: 使用一次 `GROUP BY` 查询替代：
```java
List<Object[]> counts = workflowInstanceMapper.selectCountByVersionIds(versionIds, "running");
```

### S2. 前端大量 `catch` 中空处理

**文件**: `create.vue`、`RequirementForm.vue` 等

```typescript
} catch {
    // ignore
}
// 或
} catch (error) {
    // ignore
}
```

**问题**: 多处网络请求的 catch 块为空，失败时用户无感知。例如 `loadProjects`、`loadUsers`、`loadOrgTree`、`loadConfig` 等加载途中如果失败，表单可能部分空置或显示错误数据。

**建议**: 至少对关键数据加载添加 ErrorMessage 提示：
```typescript
} catch {
    ElMessage.warning('加载项目列表失败，部分功能不可用')
}
```

### S3. `embed()` 方法中 fallback 机制可能导致部分成功无感知

**文件**: `EmbeddingServiceImpl.java` 第54-62行

```java
for (LlmGatewayConfig.Provider provider : providers) {
    try {
        List<float[]> result = llmGateway.embedWithProvider(provider, texts);
        return result;
    } catch (Exception e) {
        log.warn(...);
    }
}
```

**问题**: 当第一个模型失败、第二个模型成功时，用户不知道降级了。如果第一个模型失败因维度不匹配，而第二个模型维度不同，向量存到 Milvus 可能产生不兼容。

**建议**: 当使用非默认模型成功时，以 warn 级别日志记录，并在返回结果中给调用方可选提示。

### S4. 前端 `ccUsers` 和 `proposerUsers` 筛选逻辑混合部门/组织

**文件**: `create.vue` 第904-910行

```typescript
const sameDepartment = !!referenceUser.departmentId && candidate.departmentId === referenceUser.departmentId
const sameOrg = !!referenceUser.orgId && candidate.orgId === referenceUser.orgId
return sameDepartment || sameOrg
```

**问题**: `departmentId` 和 `orgId` 是两套不同的组织体系，用 `||` 连接意味着只要匹配任一就可见。这可能导致用户看到比预期更广泛的用户列表。

**建议**: 确认`departmentId`和`orgId`的关系——它们是互斥字段还是共存字段？如果是互斥字段，应该只检查当前用户的体系。

### S5. 后端 `RuntimeException` 作为通用异常

**文件**: `LlmProviderServiceImpl.java` 多处

```java
throw new RuntimeException("配置不存在");
throw new RuntimeException("模型不存在");
```

**问题**: 使用 `RuntimeException` 而非 `BusinessException`，会导致全局异常处理器无法正确捕获并返回结构化错误响应，前端可能收到 500 + 不友好的错误页面。

**建议**: 统一使用 `BusinessException`。

### S6. JWT Secret 的来源

**文件**: `JwtUtils.java` 第19行

```java
SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
```

**问题**: `secret` 从参数传入，但需要确认 `secret` 是否为强随机密钥（至少 256-bit），而非简单的字符串。HMAC-SHA256 要求密钥长度至少 32 字节。

**建议**:
- 在 application.yml 中配置一个至少 32 字符的密钥
- 通过 `@Value` 注入
- 使用 `java.security.SecureRandom` 生成密钥

### S7. 重复的节点状态硬编码

**文件**: `requirements/index.vue` 第131-164行

**问题**: "节点状态"和"状态"两个 filter dropdown 使用了几乎相同的硬编码选项列表。当新增/修改状态时，两边需要同步修改，易遗漏。

**建议**: 抽取为一个状态常数组或从后端动态加载。

---

## 四、💭 细节点

### N1. 数据库密码硬编码

**文件**: 检查 application.yml 中的数据库密码。确认非默认密码。

### N2. 前端版本号验证逻辑

**文件**: `workflowVersion.ts` 中的 `isWorkflowVersion` — 应确认正则校验是否覆盖了所有合法版本号格式。

### N3. Minio 配置检查

**文件**: `MinioConfig.java` — 确认 MinIO endpoint、access key 使用环境变量而非硬编码。

### N4. 条件节点分支回退

**文件**: `ConditionConfig.vue` / `ConditionBranchConfig.vue` — 确认条件分支删除后，对应连线是否也会自动清理。

### N5. Dockerfile / 部署配置

项目中未发现 Dockerfile。建议添加容器化部署方案。

---

## 五、良好实践 👍

以下亮点值得持续保持：

1. **导出 Excel 使用 SXSSFWorkbook 流式写入** — 避免大数据量 OOM，设计合理
2. **工作流格式化和布局算法** — 虽然复杂，但处理了 DAG 布局的多种边界情况
3. **Milvus 维度兼容性检查** — `EmbeddingServiceImpl.checkDimensionCompatibility()` 主动告警
4. **动态模型选择架构** — 三个角色统一走数据库，YML 中已移除固定配置，非常干净
5. **前端权限指令** — `v-permission` 和 `hasPermission` 组合使用，前后端双重防护
6. **detail-batch 批量接口** — 减少前端 N+1 请求，提升详情页性能
7. **工作流版本快照策略 (ADR-002)** — 版本隔离 + 显式迁移，设计清晰
8. **使用 `HexFormat.of()` 而非手写字节转十六进制** — Java 17+ 标准 API
9. **会签默认处理人设为创建人** — 直觉合理，减少配置复杂度

---

## 六、测试覆盖

项目仅发现一个测试文件 `AuthIT.java`。建议：

| 优先级 | 测试类型 | 建议覆盖 |
|--------|----------|----------|
| P0 | 工作流引擎 | 多分支流转、条件判断、驳回、会签 |
| P0 | RAG 问答 | 知识库搜索、LLM 调用、流式响应 |
| P1 | 认证授权 | JWT 过期/刷新、权限不足场景 |
| P1 | 需求 CRUD | 创建→提交→审批→流转全流程 |
| P2 | 前端组件 | 关键表单组件（条件配置、节点配置） |

---

## 七、总结

| 类别 | 数量 |
|------|------|
| 🔴 阻塞 (必须修复) | 5 |
| 🟡 建议 (应该修复) | 7 |
| 💭 细节 (可选) | 5 |

**总体评价**: 项目代码架构合理，安全基线已搭建（JWT + Spring Security + 按钮级权限），工作流引擎设计扎实。**最需要优先修复的是 CORS 配置过宽、delete 接口缺权限、数据读取接口缺身份校验** 这三个安全问题。其次是循环中的 N+1 查询优化和前端空 catch 块处理。
