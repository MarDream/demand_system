# ADR-002: 工作流节点评分功能设计

## 状态
✅ Accepted (2026-06-25)

## 背景

基于 ADR-001 将评分改为可选后，需要补全评分的完整价值链，使其真正产生业务价值。

**现状问题：**
1. 评分数据只存储不使用，缺乏统计分析
2. 所有节点强制统一的评分逻辑，无法按业务场景灵活配置
3. 缺少评分标准定义和评价主体说明
4. 没有反馈回路，用户不知道评分的作用

**设计目标：**
- 在工作流配置时选择哪些节点需要评分
- 为每个节点定义评分维度和标准
- 构建统计分析能力
- 形成数据反馈回路

## 决策

### 1. 工作流节点配置扩展

**数据模型（利用现有 `properties` JSON 字段）：**

```json
// workflow_nodes.properties 新增字段
{
  "ratingConfig": {
    "enabled": true,                    // 是否启用评分
    "required": false,                  // 是否必填（默认可选）
    "dimensions": [                     // 评分维度（多维评分）
      {
        "key": "quality",
        "name": "需求质量",
        "description": "需求描述清晰度、完整性",
        "minLabel": "不清晰",
        "maxLabel": "非常清晰"
      },
      {
        "key": "response_speed",
        "name": "响应速度",
        "description": "从提交到处理的响应时间",
        "minLabel": "太慢",
        "maxLabel": "非常及时"
      }
    ],
    "evaluator": "HANDLER",             // 评价者：HANDLER(处理人)/SUBMITTER(提交人)
    "showInStatistics": true            // 是否纳入统计
  }
}
```

**典型配置场景：**

| 节点类型 | 是否启用评分 | 评分维度 | 评价者 |
|---------|------------|---------|--------|
| 需求分析 | ✅ | 需求质量、响应速度 | 产品经理（处理人） |
| 开发完成 | ✅ | 交付质量、进度准时性 | 测试人员（处理人） |
| 上线验收 | ✅ | 实现效果、稳定性 | 需求提交人 |
| 其他节点 | ❌ | - | - |

### 2. 数据库迁移（兼容现有数据）

```sql
-- 不需要修改表结构，利用 properties JSON 字段
-- 但需要扩展评分记录表支持多维评分

ALTER TABLE requirement_approval_evaluations 
ADD COLUMN rating_dimensions JSON DEFAULT NULL COMMENT '多维评分详情' 
AFTER rating;

-- rating_dimensions 结构示例：
-- {
--   "quality": 4,
--   "response_speed": 5
-- }

-- 兼容性：rating 字段保留作为"整体评分"或"平均分"
```

### 3. 后端服务层改造

#### 3.1 评分校验逻辑

```java
// WorkflowEngineService.java
private void validateApprovalEvaluation(
    WorkflowNode node, 
    Integer rating, 
    Map<String, Integer> ratingDimensions,
    String comment
) {
    Map<String, Object> properties = node.getProperties();
    if (properties == null) return;
    
    Map<String, Object> ratingConfig = (Map<String, Object>) properties.get("ratingConfig");
    if (ratingConfig == null || !Boolean.TRUE.equals(ratingConfig.get("enabled"))) {
        return; // 节点未启用评分，跳过校验
    }
    
    boolean required = Boolean.TRUE.equals(ratingConfig.get("required"));
    List<Map<String, Object>> dimensions = (List<Map<String, Object>>) ratingConfig.get("dimensions");
    
    // 如果配置了多维评分
    if (dimensions != null && !dimensions.isEmpty()) {
        if (required && (ratingDimensions == null || ratingDimensions.isEmpty())) {
            throw new BusinessException(400, "当前节点需要完成评价");
        }
        
        if (ratingDimensions != null) {
            for (Map.Entry<String, Integer> entry : ratingDimensions.entrySet()) {
                Integer score = entry.getValue();
                if (score != null && (score < 1 || score > 5)) {
                    throw new BusinessException(400, "评分必须在 1-5 星之间");
                }
            }
        }
    } else {
        // 传统单一评分
        if (required && rating == null) {
            throw new BusinessException(400, "当前节点需要完成评价");
        }
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new BusinessException(400, "评分必须在 1-5 星之间");
        }
    }
}
```

#### 3.2 统计分析服务

```java
// RequirementStatisticsService.java 新增接口

/**
 * 获取需求评分统计
 */
public RequirementRatingStatisticsVO getRatingStatistics(
    Long projectId, 
    LocalDate startDate, 
    LocalDate endDate
) {
    // 1. 查询评分数据
    // 2. 按维度聚合
    // 3. 计算趋势
    // 4. 识别异常
}

/**
 * 获取低分需求列表（用于改进）
 */
public List<LowRatingRequirementVO> getLowRatingRequirements(
    Long projectId,
    String dimension,
    Integer threshold  // 低于此分数视为低分
) {
    // 返回评分 < threshold 的需求，按时间倒序
}

/**
 * 获取节点平均评分（用于流程优化）
 */
public Map<String, Double> getNodeAverageRatings(
    Long projectId,
    Long workflowVersionId
) {
    // 返回每个节点的平均评分，用于识别流程瓶颈
}
```

#### 3.3 VO 定义

```java
public class RequirementRatingStatisticsVO {
    private Double overallAverage;                    // 总体平均分
    private Map<String, Double> dimensionAverages;    // 各维度平均分
    private List<RatingTrendPoint> trends;            // 评分趋势（按周/月）
    private Map<Integer, Long> distribution;          // 评分分布（1星:10, 2星:20...）
    private List<LowRatingRequirementVO> topLowRated; // Top 10 低分需求
    private Map<String, Double> nodeAverages;         // 各节点平均分
}

public class LowRatingRequirementVO {
    private Long requirementId;
    private String requirementNo;
    private String title;
    private String nodeName;
    private Integer rating;
    private Map<String, Integer> ratingDimensions;
    private String comment;
    private LocalDateTime createdAt;
}
```

### 4. 前端实现

#### 4.1 工作流配置界面

```vue
<!-- 节点配置抽屉中新增评分配置区块 -->
<el-form-item label="评分配置">
  <el-switch v-model="nodeForm.properties.ratingConfig.enabled" />
  
  <template v-if="nodeForm.properties.ratingConfig.enabled">
    <el-checkbox v-model="nodeForm.properties.ratingConfig.required">
      设为必填
    </el-checkbox>
    
    <el-divider>评分维度</el-divider>
    <el-button @click="addDimension" type="primary" plain size="small">
      添加维度
    </el-button>
    
    <div v-for="(dim, idx) in nodeForm.properties.ratingConfig.dimensions" 
         :key="idx" class="dimension-item">
      <el-input v-model="dim.name" placeholder="维度名称（如：需求质量）" />
      <el-input v-model="dim.description" placeholder="评价说明" type="textarea" />
      <el-row :gutter="12">
        <el-col :span="12">
          <el-input v-model="dim.minLabel" placeholder="1星标签" />
        </el-col>
        <el-col :span="12">
          <el-input v-model="dim.maxLabel" placeholder="5星标签" />
        </el-col>
      </el-row>
      <el-button @click="removeDimension(idx)" type="danger" text size="small">
        删除
      </el-button>
    </div>
  </template>
</el-form-item>
```

#### 4.2 流转评分界面

```vue
<!-- 流转时的评分表单（根据节点配置动态渲染） -->
<template v-if="currentNode.properties?.ratingConfig?.enabled">
  <el-divider>节点评价{{ currentNode.properties.ratingConfig.required ? '（必填）' : '' }}</el-divider>
  
  <!-- 多维评分 -->
  <div v-if="currentNode.properties.ratingConfig.dimensions?.length">
    <div v-for="dim in currentNode.properties.ratingConfig.dimensions" 
         :key="dim.key" class="rating-dimension">
      <label>{{ dim.name }}</label>
      <el-tooltip :content="dim.description" placement="top">
        <el-icon><QuestionFilled /></el-icon>
      </el-tooltip>
      <el-rate v-model="transitionForm.ratingDimensions[dim.key]" 
               :texts="[dim.minLabel, '', '', '', dim.maxLabel]"
               show-text />
    </div>
  </div>
  
  <!-- 单一评分（兼容旧模式） -->
  <el-rate v-else v-model="transitionForm.rating" 
           :texts="['很差', '较差', '一般', '较好', '很好']"
           show-text />
  
  <el-input v-model="transitionForm.comment" 
            type="textarea" 
            placeholder="评价说明（可选）" />
</template>
```

#### 4.3 评分统计仪表盘

```vue
<!-- views/statistics/rating-dashboard.vue -->
<template>
  <div class="rating-dashboard">
    <!-- 概览卡片 -->
    <el-row :gutter="16">
      <el-col :span="6">
        <stat-card title="整体平均分" :value="statistics.overallAverage" suffix="星" />
      </el-col>
      <el-col :span="6" v-for="(avg, dim) in statistics.dimensionAverages" :key="dim">
        <stat-card :title="dim" :value="avg" suffix="星" />
      </el-col>
    </el-row>
    
    <!-- 评分趋势图 -->
    <el-card title="评分趋势">
      <v-chart :option="trendChartOption" />
    </el-card>
    
    <!-- 评分分布 -->
    <el-card title="评分分布">
      <v-chart :option="distributionChartOption" />
    </el-card>
    
    <!-- 低分需求列表 -->
    <el-card title="待改进需求（低于3星）">
      <el-table :data="statistics.topLowRated">
        <el-table-column prop="requirementNo" label="需求编号" width="120" />
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="nodeName" label="节点" width="120" />
        <el-table-column prop="rating" label="评分" width="80">
          <template #default="{ row }">
            <el-rate v-model="row.rating" disabled />
          </template>
        </el-table-column>
        <el-table-column prop="comment" label="评价" />
      </el-table>
    </el-card>
    
    <!-- 节点平均分（识别流程瓶颈） -->
    <el-card title="各节点平均评分">
      <v-chart :option="nodeRatingChartOption" />
    </el-card>
  </div>
</template>
```

### 5. 数据使用场景

| 场景 | 数据来源 | 驱动动作 |
|------|---------|---------|
| **迭代回顾** | 本迭代平均评分、低分需求列表 | 讨论改进点 |
| **流程优化** | 各节点平均分、评分趋势 | 识别流程瓶颈，调整工作流 |
| **团队考核** | 个人处理的需求平均分 | 绩效参考（慎用） |
| **质量预警** | 实时低分告警 | 自动创建复盘任务 |
| **产品改进** | 按需求类型统计评分 | 优先优化低分功能 |

### 6. 实施路径

#### Phase 1: 基础能力（1-2 周）
- [x] 数据库迁移：添加 `rating_dimensions` 字段
- [x] 后端校验逻辑改造：支持节点配置读取
- [x] 前端工作流配置界面：评分配置UI

#### Phase 2: 统计分析（2-3 周）
- [x] 统计服务实现：各维度聚合、趋势计算
- [x] 仪表盘开发：趋势图、分布图、低分列表
- [x] API 接口对接

#### Phase 3: 反馈回路（1 周）
- [x] 低分告警：评分<3星自动通知（已接通 NotificationService，通知创建人+负责人）
- [ ] 迭代报告集成：生成评分分析章节（待业务确认报告格式）
- [ ] 导出功能：导出评分数据供复盘使用（待后续迭代）

> 实现状态：Phase 1-3 核心代码已完成，前端 `vite build` 与后端 `mvn compile` 均通过（2026-06-25）。

## 后果

### ✅ 变得更容易的事

1. **灵活配置**
   - 不同节点可独立配置是否评分
   - 支持多维评分，更精准定位问题
   
2. **数据价值释放**
   - 评分数据真正驱动改进决策
   - 可视化展示让数据可理解
   
3. **流程优化依据**
   - 通过节点评分识别瓶颈
   - 低分需求列表指导复盘方向

### ⚠️ 需要注意的事

1. **初期配置成本**
   - 需要为每个工作流节点配置评分维度
   - 需要培训用户理解评分标准

2. **数据质量依赖**
   - 如果用户不认真评分，统计失去意义
   - 需要建立评分文化和反馈机制

3. **性能考虑**
   - 统计查询可能涉及大量历史数据
   - 需要考虑缓存策略和异步计算

## 相关决策

- ADR-001: 将需求流转评分改为可选（基础）
- 未来: 如需引入 AI 自动评分，创建 ADR-003

## 参考资料

- 工作流节点 `properties` JSON 字段
- `requirement_approval_evaluations` 表
- Element Plus Rate 组件文档
