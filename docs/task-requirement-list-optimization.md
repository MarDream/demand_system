# 需求列表查询性能优化 — 开发任务单

## 项目背景
**问题**：需求管理模块 5 个 Tab 页签下列表查询效率缓慢，20 万条记录场景下无法高效查询
**已完成的优化**（上一轮）：N+1 批量化、全文索引、前端请求取消/缓存
**本轮约束**：日检索 50-100 次 | 缓存一致性容忍高（TTL 过期即可） | 深分页存在但较少 | 暂不上 ES

## 预期效果
| 场景 | 当前 | 优化后 |
|------|------|--------|
| 全部需求第1页 | 500-2000ms | 50-150ms |
| 关键词搜索 | 200-500ms | 80-200ms |
| Tab切换 | 200-500ms | 50-150ms |
| 深分页(第5000页) | 5-30s | 0.1-0.3s |
| DB查询次数/请求 | 7+N(≈17) | 2-3 |

---

## 后端任务（BE-01 ~ BE-06）

---

### BE-01: 创建 Caffeine 本地缓存配置类

**优先级**: P1 | **预估**: 30min | **负责人**: 后端高级开发

**描述**：
创建一个通用的 Caffeine 缓存配置类，为用户、组织、visibleOrgIds 提供进程内 L1 缓存支持。

**验收标准**：
- 新建 `UserLocalCache` 类，提供 `getUserById(Long id)` / `putUser(Long id, User user)` 方法
- 新建 `OrgLocalCache` 类，提供 `getOrgById(Long id)` / `putOrg(Long id, SysOrgVO org)` 方法
- 新建 `VisibleOrgCache` 类，提供 `getVisibleOrgIds(Long userId)` / `putVisibleOrgIds(Long userId, List<Long> orgIds)` 方法
- Caffeine 配置：用户 max=5000/TTL=10min，组织 max=2000/TTL=10min，visibleOrgIds max=500/TTL=5min
- 缓存统计开启（`recordStats()`），方便后续监控命中率
- 所有 Cache 使用 `LoadingCache` 模式，miss 时自动触发 L2(Redis) 回源

**文件创建**：
- `demand_backend/src/main/java/com/demand/system/common/cache/UserLocalCache.java`
- `demand_backend/src/main/java/com/demand/system/common/cache/OrgLocalCache.java`
- `demand_backend/src/main/java/com/demand/system/common/cache/VisibleOrgCache.java`

**依赖**：项目已引入 `caffeine 3.1.8`（pom.xml 中存在）

**关键代码参考**（项目已有的 Caffeine 用法）：
```java
// 参考: OrgHierarchyCache.java
private final LoadingCache<Long, List<Long>> cache = Caffeine.newBuilder()
    .maximumSize(10000)
    .expireAfterWrite(Duration.ofMinutes(30))
    .recordStats()
    .build(this::loadDescendantIds);
```

---

### BE-02: 实现 L1(Caffeine) + L2(Redis) 二级缓存集成

**优先级**: P1 | **预估**: 45min | **负责人**: 后端高级开发

**描述**：
在 BE-01 基础上，将 Caffeine L1 与 Redis L2 串联，实现完整的二级缓存读取链路：L1 miss → L2 miss → DB → 写入 L2 → 写入 L1。

**验收标准**：
- `UserLocalCache.getUserById()` 查询链路：Caffeine → Redis(`demand:user:{id}`) → `userMapper.selectById()` → 写回 Redis + Caffeine
- `OrgLocalCache.getOrgById()` 查询链路：Caffeine → Redis(`demand:org:{id}`) → `sysOrgService.getDetail()` → 写回 Redis + Caffeine
- `VisibleOrgCache.getVisibleOrgIds()` 查询链路：Caffeine → Redis(`demand:visibleOrg:{userId}`) → 调用 `resolveVisibleOrgIds()` → 写回 Redis + Caffeine
- Redis 序列化复用已有的 `RedisTemplate<String, Object>`（RedisConfig.java 中已配置 Jackson2JsonRedisSerializer）
- Redis TTL：用户/组织 10min，visibleOrgIds 5min（与 Caffeine 一致）
- 所有 DB 回源操作加 `@Transactional(readOnly = true)` 保护

**文件修改**：
- `demand_backend/src/main/java/com/demand/system/common/cache/UserLocalCache.java`（BE-01 创建的文件）
- `demand_backend/src/main/java/com/demand/system/common/cache/OrgLocalCache.java`
- `demand_backend/src/main/java/com/demand/system/common/cache/VisibleOrgCache.java`

**关键实现**：
```java
// 二级缓存读取模式（以用户为例）
public User getUserById(Long id) {
    // L1: Caffeine
    User cached = userCache.getIfPresent(id);
    if (cached != null) return cached;
    
    // L2: Redis
    String redisKey = "demand:user:" + id;
    Object redisVal = redisTemplate.opsForValue().get(redisKey);
    if (redisVal instanceof User) {
        userCache.put(id, (User) redisVal);
        return (User) redisVal;
    }
    
    // L3: DB
    User user = userMapper.selectById(id);
    if (user != null) {
        redisTemplate.opsForValue().set(redisKey, user, Duration.ofMinutes(10));
        userCache.put(id, user);
    }
    return user;
}
```

**依赖**：BE-01、RedisConfig.java（已有）、UserMapper（已有）、SysOrgService（已有）

---

### BE-03: 改造 batchFillUserNamesAndOrg 和 resolveVisibleOrgIds 使用二级缓存

**优先级**: P1 | **预估**: 45min | **负责人**: 后端高级开发

**描述**：
将 `RequirementServiceImpl` 中的用户/组织查询和 visibleOrgIds 查询切换到二级缓存，消除 N+1 残留。

**验收标准**：
- `batchFillUserNamesAndOrg()` 方法中：
  - `userMapper.selectBatchIds(allUserIds)` 替换为 `userLocalCache.batchGetUsers(allUserIds)`（先批量查缓存，miss 的再批量查 DB 回填）
  - `sysOrgService.getDetail(orgId)` 循环调用替换为 `orgLocalCache.batchGetOrgs(allOrgIds)`
- `resolveVisibleOrgIds()` 方法中：
  - `userMapper.selectById(userId)` 替换为 `userLocalCache.getUserById(userId)`
  - 最终返回的 visibleOrgIds 缓存到 `visibleOrgCache`
- 5 个列表方法（list / listMyDrafts / listMyPending / listMyFollows / listMyDone）均自动受益，无需逐个修改
- 编译通过，无运行时异常

**文件修改**：
- `demand_backend/src/main/java/com/demand/system/module/requirement/service/impl/RequirementServiceImpl.java`
  - 修改 `batchFillUserNamesAndOrg()` 方法（约 L1758-1850）
  - 修改 `resolveVisibleOrgIds()` 方法

**当前代码关键位置**：
```java
// L1776: 用户批量查询 — 改为走缓存
for (User u : userMapper.selectBatchIds(allUserIds)) { ... }

// L1789-1798: 组织逐个查询 — 改为走缓存
for (Long orgId : allOrgIds) {
    SysOrgVO org = sysOrgService.getDetail(orgId);  // ← N+1 残留
    ...
}
```

**依赖**：BE-02

---

### BE-04: 创建 RequirementListVO 和列表查询字段精简

**优先级**: P2 | **预估**: 60min | **负责人**: 后端高级开发

**描述**：
当前列表查询 `SELECT *` 返回 30+ 字段，其中 `description`（数千字）、`ccUserIds`、`attachments`、`creatorRoleCodes` 等列表页不展示。新增精简 VO 和 Mapper 方法，减少 IO 50-70%。

**验收标准**：
- 新建 `RequirementListVO`，仅包含列表页展示的 15 个字段：
  ```
  id, requirementNo, title, type, priority, status, orgId,
  creatorId, assigneeId, opsFollowId, maintFollowId,
  isDraft, createdAt, updatedAt, deletedAt
  ```
  加上 5 个关联填充字段：`creatorName, assigneeName, opsFollowName, maintFollowName, followed`
- **不包含**：description, ccUserIds, attachments, transitionAttachments, workflowInstanceId, nodeStatus, 
  lastSavedAt, startDate, estimatedHours, actualHours, dueDate, analysisCompletedAt, confirmAt, 
  developmentCompletedAt, moduleId, iterationId, orderNum, version, projectId, parentId, departmentId,
  legacyWorkflow, creatorRoleCodes, currentHandlerName, departmentName, childCount, 
  canEdit, canView, canApprove, isParticipant, operationType
- Mapper 新增 `selectListPage` 方法，SQL 只 SELECT 上述 15 个字段
- `RequirementServiceImpl.list()` 方法使用 `selectListPage` 替代 `selectPage`
- 其余 4 个列表方法暂不改（走 `SELECT r.*` 是 Mapper 注解硬编码，改造成本高，优先级低）
- 详情页 `getDetail()` 继续使用完整 `RequirementVO`

**文件创建**：
- `demand_backend/src/main/java/com/demand/system/module/requirement/dto/RequirementListVO.java`

**文件修改**：
- `demand_backend/src/main/java/com/demand/system/module/requirement/mapper/RequirementMapper.java`（新增 `selectListPage` 方法）
- `demand_backend/src/main/java/com/demand/system/module/requirement/service/impl/RequirementServiceImpl.java`（`list()` 方法改用新 Mapper 方法）

**Mapper 方法签名**：
```java
@Select({
    "<script>",
    "SELECT r.id, r.requirement_no, r.title, r.type, r.priority, r.status, r.org_id,",
    "  r.creator_id, r.assignee_id, r.ops_follow_id, r.maint_follow_id,",
    "  r.is_draft, r.created_at, r.updated_at, r.deleted_at",
    "FROM requirements r",
    "WHERE r.deleted_at = 0",
    // ... 与 list() 方法的 wrapper 条件一致，但用原生 SQL
    "ORDER BY r.created_at DESC",
    "</script>"
})
IPage<RequirementListVO> selectListPage(IPage<?> page, @Param("ew") Wrapper<Requirement> wrapper);
```

**注意**：MyBatis-Plus 的 `select()` 方法可以指定查询字段，也可以用 Wrapper 的 `.select()` 来实现，比新增 Mapper 方法更简单：
```java
// 更简单的方案：用 Wrapper 的 select() 方法
wrapper.select(
    Requirement::getId, Requirement::getRequirementNo, Requirement::getTitle,
    Requirement::getType, Requirement::getPriority, Requirement::getStatus,
    Requirement::getOrgId, Requirement::getCreatorId, Requirement::getAssigneeId,
    Requirement::getOpsFollowId, Requirement::getMaintFollowId,
    Requirement::getIsDraft, Requirement::getCreatedAt, Requirement::getUpdatedAt
);
```
**推荐**：先用 Wrapper `.select()` 方案（改动最小），如果后续需要更多定制再改 Mapper。

---

### BE-05: HikariCP 连接池调优 + Async 线程池配置

**优先级**: P3 | **预估**: 20min | **负责人**: 后端高级开发

**描述**：
HikariCP 默认 max=10 不够用（每个列表请求 7+N 次 DB 查询），@Async 用 SimpleAsyncTaskExecutor 无池化。

**验收标准**：
- `application-dev.yml` 和 `application-prod.yml` 新增 HikariCP 配置：
  ```yaml
  spring:
    datasource:
      hikari:
        maximum-pool-size: 25
        minimum-idle: 10
        connection-timeout: 5000
        idle-timeout: 300000
        max-lifetime: 900000
        leak-detection-threshold: 60000
  ```
- 新建 `AsyncConfig` 配置类，注册自定义 `ThreadPoolTaskExecutor` Bean：
  ```java
  @Configuration
  @EnableAsync
  public class AsyncConfig implements AsyncConfigurer {
      @Override
      public Executor getAsyncExecutor() {
          ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
          executor.setCorePoolSize(4);
          executor.setMaxPoolSize(8);
          executor.setQueueCapacity(100);
          executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
          executor.setThreadNamePrefix("demand-async-");
          executor.initialize();
          return executor;
      }
  }
  ```
- 主类 `DemandSystemApplication.java` 上的 `@EnableAsync` 注解移除（转移到 AsyncConfig 中）

**文件修改**：
- `demand_backend/src/main/resources/application-dev.yml`（新增 hikari 配置段）
- `demand_backend/src/main/resources/application-prod.yml`（新增 hikari 配置段）

**文件创建**：
- `demand_backend/src/main/java/com/demand/system/common/config/AsyncConfig.java`

**文件修改**：
- `demand_backend/src/main/java/com/demand/system/DemandSystemApplication.java`（移除 `@EnableAsync`）

**前提**：确认 MySQL `max_connections` ≥ 30（生产环境）

---

### BE-06: 混合分页实现（OFFSET + 游标）

**优先级**: P4 | **预估**: 90min | **负责人**: 后端高级开发

**描述**：
深分页场景（第 100 页后），OFFSET 扫描行数爆炸。实现前端无感知的混合分页策略。

**验收标准**：
- `PageResult<T>` 新增两个字段：`String nextCursor`（游标值）、`Boolean hasMore`（是否有下一页）
- `RequirementListQueryDTO` 新增可选字段：`String cursor`（前端可传，也可不传）
- `RequirementServiceImpl.list()` 方法的分页逻辑：
  - **cursor 为空且 pageNum ≤ 100**：走正常 OFFSET 分页（覆盖 95%+ 场景）
  - **cursor 为空且 pageNum > 100**：自动计算游标值（先查 `(pageNum-1)*pageSize` 条的最大 ID 作为 cursor），然后走 `WHERE id < cursor ORDER BY id DESC LIMIT pageSize`
  - **cursor 不为空**：直接走游标分页 `WHERE id < #{cursor} ORDER BY id DESC LIMIT #{pageSize}+1`（+1 用于判断 hasMore）
- 游标分页模式下 `total` 返回 -1（无法精确计数，前端根据 hasMore 判断）
- Mapper 新增 `selectListByCursor` 方法
- 前端暂不改动，游标逻辑完全由后端处理

**文件修改**：
- `demand_backend/src/main/java/com/demand/system/common/dto/PageResult.java`（新增 nextCursor、hasMore 字段）
- `demand_backend/src/main/java/com/demand/system/module/requirement/dto/RequirementListQueryDTO.java`（新增 cursor 字段）
- `demand_backend/src/main/java/com/demand/system/module/requirement/service/impl/RequirementServiceImpl.java`（`list()` 方法分页逻辑）
- `demand_backend/src/main/java/com/demand/system/module/requirement/mapper/RequirementMapper.java`（新增 `selectListByCursor`）

**关键实现**（Service 层核心逻辑）：
```java
// list() 方法中的分页判断
if (query.getCursor() != null) {
    // 游标分页
    List<Requirement> records = requirementMapper.selectListByCursor(
        Long.parseLong(query.getCursor()), query.getPageSize() + 1, wrapper);
    boolean hasMore = records.size() > query.getPageSize();
    if (hasMore) records = records.subList(0, query.getPageSize());
    // ... 转换为 VO
    String nextCursor = records.isEmpty() ? null 
        : String.valueOf(records.get(records.size() - 1).getId());
    return new PageResult<>(voList, -1L, query.getPageNum(), query.getPageSize(), nextCursor, hasMore);
} else if (query.getPageNum() > 100) {
    // 自动切换游标：查出 offset 位置的最大 ID
    Long cursorId = requirementMapper.selectMaxIdBeforeOffset(
        (query.getPageNum() - 1) * query.getPageSize(), wrapper);
    if (cursorId != null) {
        // 递归调用自身，传入 cursor
        query.setCursor(String.valueOf(cursorId));
        return list(query);
    }
} else {
    // 正常 OFFSET 分页（原逻辑不变）
}
```

**Mapper 新增方法**：
```java
@Select({
    "<script>",
    "SELECT r.id, r.requirement_no, r.title, r.type, r.priority, r.status, r.org_id,",
    "  r.creator_id, r.assignee_id, r.ops_follow_id, r.maint_follow_id,",
    "  r.is_draft, r.created_at, r.updated_at, r.deleted_at",
    "FROM requirements r",
    "WHERE r.deleted_at = 0 AND r.id &lt; #{cursor}",
    // ... wrapper 条件
    "ORDER BY r.id DESC",
    "LIMIT #{limit}",
    "</script>"
})
List<Requirement> selectListByCursor(@Param("cursor") Long cursor, 
                                      @Param("limit") int limit,
                                      @Param("ew") Wrapper<Requirement> wrapper);
```

---

## 前端任务（FE-01 ~ FE-02）

---

### FE-01: 适配混合分页返回值（nextCursor / hasMore）

**优先级**: P4 | **预估**: 30min | **负责人**: 前端高级开发

**描述**：
后端 BE-06 混合分页会在 `PageResult` 中新增 `nextCursor` 和 `hasMore` 字段。前端需适配，但暂不改变用户交互方式。

**验收标准**：
- API 响应类型 `PageResult` 新增可选字段：`nextCursor?: string`、`hasMore?: boolean`
- 当 `total === -1` 时（游标分页模式），分页组件显示"还有更多"替代精确页码
- "加载更多"交互暂不实现，保持翻页方式不变（后端兼容处理）
- 分页组件在 `total === -1` 时不显示总条数

**文件修改**：
- `demand_frontend/src/views/requirements/index.vue`（分页组件适配）
- `demand_frontend/src/api/requirement.ts` 或对应类型定义文件（PageResult 类型更新）

**关键代码位置**：
```vue
<!-- 当前分页组件，需适配 total === -1 的场景 -->
<el-pagination
  v-model:current-page="pagination.pageNum"
  v-model:page-size="pagination.pageSize"
  :total="pagination.total"
  ...
/>
```

---

### FE-02: 列表查询性能验证与微调

**优先级**: P2 | **预估**: 30min | **负责人**: 前端高级开发

**描述**：
后端完成所有优化后，前端做端到端验证和微调。

**验收标准**：
- 5 个 Tab 页签（全部/草稿/待办/已办/关注）列表加载时间 < 300ms（20万条数据）
- Tab 切换时无白屏闪烁（已实现的缓存优先策略验证）
- `refreshViewCounts()` 不阻塞主流程（已实现的异步调用验证）
- AbortController 请求取消正常工作（快速切换 Tab 时无竞态）
- 检查 `batchEnrichAttachmentMeta` 在列表中是否仍有性能影响（若列表不再展示附件列，应从查询中移除）

**文件修改**：
- `demand_frontend/src/views/requirements/index.vue`（根据验证结果微调）

**验证方法**：
1. Chrome DevTools Network 面板查看列表 API 响应时间
2. Vue DevTools 查看组件渲染时间
3. 快速连续切换 5 个 Tab，确认无竞态请求

---

## 任务依赖关系

```
BE-01 (Caffeine缓存类) 
  └→ BE-02 (L1+L2二级缓存集成)
       └→ BE-03 (Service层接入缓存)

BE-04 (字段精简) — 独立，可与 BE-01~03 并行

BE-05 (连接池/线程池) — 独立，可与所有任务并行

BE-06 (混合分页)
  └→ FE-01 (前端适配分页返回值)

FE-02 (端到端验证) — 依赖所有后端任务完成
```

## 建议执行顺序

1. **第一天上午**：BE-01 + BE-05（并行，互不依赖）
2. **第一天下午**：BE-02 → BE-03（串行，有依赖）
3. **第二天上午**：BE-04（字段精简，独立）
4. **第二天下午**：BE-06 → FE-01（串行）
5. **第二天收尾**：FE-02（端到端验证）

## 风险与注意事项

1. **Caffeine + Redis 双写一致性**：采用简单 TTL 过期策略，不保证强一致。用户改名后最多 10 分钟列表才更新（已确认可接受）
2. **游标分页不支持跳页**：第 100 页之后只能"下一页"逐页翻，不支持直接跳到第 5000 页。但深分页场景用户本就是逐页翻，影响极小
3. **MySQL max_connections**：HikariCP max=25 需确认 MySQL 端 max_connections ≥ 30
4. **PageResult 新增字段**：需确认现有前端代码不因新增字段报错（JSON 反序列化新增字段通常安全）
5. **BE-04 字段精简**：Wrapper `.select()` 方案需确认 `BeanUtils.copyProperties` 对 null 字段不报错（当源对象缺少某些字段时）
