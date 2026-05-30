# 需求管理系统 - 工作流与需求流转优化方案

## 一、现状分析

### 1.1 当前工作流架构

**核心组件：**
- `WorkflowEngineService`: 工作流引擎核心服务
- `WorkflowGraphNavigator`: 图导航器，负责路径解析
- `WorkflowNode`: 节点实体（支持 start/approval/condition/parallel/end）
- `WorkflowEdge`: 连线实体
- `WorkflowInstance`: 工作流实例
- `WorkflowInstanceTransition`: 流转记录

**当前能力：**
✅ 基础流程流转（submit/approve/reject/rollback/cancel）
✅ 节点权限控制（指定用户/角色/角色组/组织）
✅ 条件分支（condition 节点）
✅ 并行网关（parallel 节点）
✅ 审批评价（rating 1-5星 + comment）
✅ 乐观锁防并发冲突

**存在问题：**
❌ 并行分支执行不完善（parallel 节点存在但未充分利用）
❌ 会签功能缺失（无法多人同时审批）
❌ 节点配置依赖 properties JSON（前端需要解析脚本逻辑）
❌ 缺少可视化组件配置界面
❌ 需求模板系统缺失

### 1.2 需求管理现状

**Requirement 实体字段：**
- `description`: LONGTEXT 富文本描述
- `type`: 需求类型（feature/bug/improvement等）
- 无模板绑定机制

**问题：**
❌ 富文本框无模板支持
❌ 不同需求类型无法预设不同结构
❌ 用户填写需求时缺少引导

---

## 二、优化方案设计

### 2.1 多分支并发执行工作流

#### 2.1.1 设计目标
- 支持真正的并行分支（多条路径同时执行）
- 支持分支汇聚（所有分支完成后才能继续）
- 支持分支条件（根据需求属性动态选择分支）

#### 2.1.2 数据库设计

**新增表：workflow_parallel_branches**
```sql
CREATE TABLE `workflow_parallel_branches` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `instance_id` BIGINT UNSIGNED NOT NULL COMMENT '工作流实例ID',
  `parallel_node_id` VARCHAR(100) NOT NULL COMMENT '并行网关节点ID',
  `branch_node_id` VARCHAR(100) NOT NULL COMMENT '分支节点ID',
  `branch_name` VARCHAR(100) NOT NULL COMMENT '分支名称',
  `status` VARCHAR(50) DEFAULT 'pending' COMMENT 'pending/running/completed/skipped',
  `started_at` DATETIME DEFAULT NULL,
  `completed_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_instance_id` (`instance_id`),
  INDEX `idx_parallel_node_id` (`parallel_node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流并行分支执行记录';
```

**WorkflowNode 扩展 properties 字段：**
```json
{
  "parallelType": "AND|OR",  // AND=所有分支完成, OR=任一分支完成
  "branches": [
    {
      "branchId": "branch_1",
      "branchName": "技术评审",
      "condition": {
        "field": "type",
        "operator": "in",
        "value": ["FEATURE", "OPTIMIZATION"]
      }
    },
    {
      "branchId": "branch_2",
      "branchName": "安全评审",
      "condition": {
        "field": "priority",
        "operator": "eq",
        "value": "P0"
      }
    }
  ]
}
```

#### 2.1.3 核心逻辑

**WorkflowParallelBranchService.java**
```java
@Service
public class WorkflowParallelBranchService {
    
    /**
     * 进入并行网关时创建分支记录
     */
    public void initParallelBranches(WorkflowInstance instance, WorkflowNode parallelNode, Requirement requirement) {
        List<BranchConfig> branches = parseBranches(parallelNode);
        for (BranchConfig branch : branches) {
            if (evaluateCondition(branch.getCondition(), requirement)) {
                WorkflowParallelBranch record = new WorkflowParallelBranch();
                record.setInstanceId(instance.getId());
                record.setParallelNodeId(parallelNode.getNodeId());
                record.setBranchNodeId(branch.getBranchId());
                record.setBranchName(branch.getBranchName());
                record.setStatus("pending");
                parallelBranchMapper.insert(record);
            }
        }
    }
    
    /**
     * 检查所有分支是否完成
     */
    public boolean allBranchesCompleted(Long instanceId, String parallelNodeId) {
        List<WorkflowParallelBranch> branches = parallelBranchMapper.selectList(
            new LambdaQueryWrapper<WorkflowParallelBranch>()
                .eq(WorkflowParallelBranch::getInstanceId, instanceId)
                .eq(WorkflowParallelBranch::getParallelNodeId, parallelNodeId)
        );
        return branches.stream().allMatch(b -> "completed".equals(b.getStatus()));
    }
}
```

---

### 2.2 会签功能

#### 2.2.1 设计目标
- 支持多人同时审批（会签）
- 支持会签策略：全部通过/任一通过/多数通过
- 支持动态选择会签人

#### 2.2.2 数据库设计

**新增表：workflow_countersign_records**
```sql
CREATE TABLE `workflow_countersign_records` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `instance_id` BIGINT UNSIGNED NOT NULL COMMENT '工作流实例ID',
  `node_id` VARCHAR(100) NOT NULL COMMENT '会签节点ID',
  `approver_id` BIGINT UNSIGNED NOT NULL COMMENT '审批人ID',
  `status` VARCHAR(50) DEFAULT 'pending' COMMENT 'pending/approved/rejected',
  `rating` TINYINT DEFAULT NULL COMMENT '评分1-5',
  `comment` VARCHAR(1000) DEFAULT NULL COMMENT '审批意见',
  `approved_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_instance_node` (`instance_id`, `node_id`),
  INDEX `idx_approver_id` (`approver_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流会签记录';
```

**WorkflowNode 扩展 properties 字段：**
```json
{
  "countersignEnabled": true,
  "countersignStrategy": "ALL|ANY|MAJORITY",  // 全部通过/任一通过/多数通过
  "countersignApprovers": [1, 2, 3],  // 固定会签人ID列表
  "countersignDynamic": true,  // 是否动态选择会签人
  "countersignMinCount": 2  // 最少会签人数
}
```

#### 2.2.3 核心逻辑

**WorkflowCountersignService.java**
```java
@Service
public class WorkflowCountersignService {
    
    /**
     * 提交会签审批
     */
    @Transactional
    public void submitCountersignApproval(Long instanceId, String nodeId, Long approverId, 
                                          String status, Integer rating, String comment) {
        WorkflowCountersignRecord record = new WorkflowCountersignRecord();
        record.setInstanceId(instanceId);
        record.setNodeId(nodeId);
        record.setApproverId(approverId);
        record.setStatus(status);
        record.setRating(rating);
        record.setComment(comment);
        record.setApprovedAt(LocalDateTime.now());
        countersignMapper.insert(record);
        
        // 检查是否满足流转条件
        if (canProceedAfterCountersign(instanceId, nodeId)) {
            // 触发流转到下一节点
            workflowEngineService.autoTransitionAfterCountersign(instanceId, nodeId);
        }
    }
    
    /**
     * 检查会签是否完成
     */
    public boolean canProceedAfterCountersign(Long instanceId, String nodeId) {
        WorkflowNode node = getNode(nodeId);
        String strategy = (String) node.getProperties().get("countersignStrategy");
        
        List<WorkflowCountersignRecord> records = countersignMapper.selectList(
            new LambdaQueryWrapper<WorkflowCountersignRecord>()
                .eq(WorkflowCountersignRecord::getInstanceId, instanceId)
                .eq(WorkflowCountersignRecord::getNodeId, nodeId)
        );
        
        long approvedCount = records.stream().filter(r -> "approved".equals(r.getStatus())).count();
        long rejectedCount = records.stream().filter(r -> "rejected".equals(r.getStatus())).count();
        long totalCount = records.size();
        
        switch (strategy) {
            case "ALL":
                return approvedCount == totalCount;
            case "ANY":
                return approvedCount > 0;
            case "MAJORITY":
                return approvedCount > totalCount / 2;
            default:
                return false;
        }
    }
}
```

---

### 2.3 可视化组件替代脚本

#### 2.3.1 设计目标
- 前端提供可视化配置界面
- 后端提供标准化的配置结构
- 最小化或消除脚本代码

#### 2.3.2 节点配置可视化组件

**前端组件设计（Vue 3）：**

**1. 处理人配置组件 `AssigneeConfig.vue`**
```vue
<template>
  <el-form-item label="处理人类型">
    <el-select v-model="config.assigneeType">
      <el-option label="指定用户" value="SPECIFIED_USER" />
      <el-option label="指定角色" value="SPECIFIED_ROLE" />
      <el-option label="指定角色组" value="SPECIFIED_ROLE_GROUP" />
      <el-option label="指定组织" value="SPECIFIED_ORG" />
      <el-option label="创建人" value="CREATOR" />
      <el-option label="上一审批人" value="PREV_APPROVER" />
    </el-select>
  </el-form-item>
  
  <el-form-item v-if="config.assigneeType === 'SPECIFIED_USER'" label="选择用户">
    <el-select v-model="config.assigneeUserIds" multiple>
      <el-option v-for="user in users" :key="user.id" :label="user.realName" :value="user.id" />
    </el-select>
  </el-form-item>
  
  <el-form-item v-if="config.assigneeType === 'SPECIFIED_ROLE'" label="选择角色">
    <el-select v-model="config.assigneeRoleId">
      <el-option v-for="role in roles" :key="role.id" :label="role.name" :value="role.id" />
    </el-select>
  </el-form-item>
</template>
```

**2. 条件配置组件 `ConditionConfig.vue`**
```vue
<template>
  <div class="condition-builder">
    <el-form-item label="条件逻辑">
      <el-radio-group v-model="condition.logic">
        <el-radio label="AND">全部满足</el-radio>
        <el-radio label="OR">任一满足</el-radio>
      </el-radio-group>
    </el-form-item>
    
    <div v-for="(rule, index) in condition.rules" :key="index" class="condition-rule">
      <el-select v-model="rule.field" placeholder="选择字段">
        <el-option label="需求类型" value="type" />
        <el-option label="优先级" value="priority" />
        <el-option label="创建人" value="creatorId" />
      </el-select>
      
      <el-select v-model="rule.operator" placeholder="运算符">
        <el-option label="等于" value="eq" />
        <el-option label="不等于" value="ne" />
        <el-option label="包含" value="in" />
        <el-option label="不包含" value="notIn" />
      </el-select>
      
      <el-input v-model="rule.value" placeholder="值" />
      
      <el-button @click="removeRule(index)" icon="Delete" circle />
    </div>
    
    <el-button @click="addRule" icon="Plus">添加条件</el-button>
  </div>
</template>
```

**3. 会签配置组件 `CountersignConfig.vue`**
```vue
<template>
  <el-form-item label="启用会签">
    <el-switch v-model="config.countersignEnabled" />
  </el-form-item>
  
  <template v-if="config.countersignEnabled">
    <el-form-item label="会签策略">
      <el-radio-group v-model="config.countersignStrategy">
        <el-radio label="ALL">全部通过</el-radio>
        <el-radio label="ANY">任一通过</el-radio>
        <el-radio label="MAJORITY">多数通过</el-radio>
      </el-radio-group>
    </el-form-item>
    
    <el-form-item label="会签人选择">
      <el-radio-group v-model="config.countersignMode">
        <el-radio label="FIXED">固定会签人</el-radio>
        <el-radio label="DYNAMIC">动态选择</el-radio>
      </el-radio-group>
    </el-form-item>
    
    <el-form-item v-if="config.countersignMode === 'FIXED'" label="选择会签人">
      <el-select v-model="config.countersignApprovers" multiple>
        <el-option v-for="user in users" :key="user.id" :label="user.realName" :value="user.id" />
      </el-select>
    </el-form-item>
    
    <el-form-item v-if="config.countersignMode === 'DYNAMIC'" label="最少会签人数">
      <el-input-number v-model="config.countersignMinCount" :min="1" />
    </el-form-item>
  </template>
</template>
```

#### 2.3.3 后端标准化配置结构

**WorkflowNodeConfigDTO.java**
```java
public class WorkflowNodeConfigDTO {
    private String nodeType;  // start/approval/condition/parallel/end
    private String nodeName;
    
    // 处理人配置
    private AssigneeConfig assignee;
    
    // 条件配置
    private ConditionConfig condition;
    
    // 会签配置
    private CountersignConfig countersign;
    
    // 并行配置
    private ParallelConfig parallel;
    
    // 超时配置
    private TimeoutConfig timeout;
}

public class AssigneeConfig {
    private String type;  // SPECIFIED_USER/SPECIFIED_ROLE/...
    private List<Long> userIds;
    private Long roleId;
    private Long roleGroupId;
    private Long orgId;
    private String orgScopeType;  // current/children
}

public class ConditionConfig {
    private String logic;  // AND/OR
    private List<ConditionRule> rules;
}

public class ConditionRule {
    private String field;  // type/priority/creatorId
    private String operator;  // eq/ne/in/notIn/gt/lt
    private Object value;
}

public class CountersignConfig {
    private Boolean enabled;
    private String strategy;  // ALL/ANY/MAJORITY
    private String mode;  // FIXED/DYNAMIC
    private List<Long> approvers;
    private Integer minCount;
}

public class ParallelConfig {
    private String type;  // AND/OR
    private List<BranchConfig> branches;
}

public class BranchConfig {
    private String branchId;
    private String branchName;
    private ConditionConfig condition;
}
```

---

### 2.4 需求模板系统

#### 2.4.1 设计目标
- 不同需求类型绑定不同模板
- 模板支持结构化字段定义
- 富文本编辑器自动加载模板内容

#### 2.4.2 数据库设计

**新增表：requirement_templates**
```sql
CREATE TABLE `requirement_templates` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `requirement_type_code` VARCHAR(50) NOT NULL COMMENT '需求类型编码',
  `template_name` VARCHAR(200) NOT NULL COMMENT '模板名称',
  `template_content` JSON NOT NULL COMMENT '模板内容（结构化字段）',
  `is_active` TINYINT DEFAULT 1 COMMENT '是否启用',
  `creator_id` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` TINYINT DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_type_code` (`requirement_type_code`, `deleted_at`),
  INDEX `idx_is_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='需求模板表';
```

**template_content JSON 结构：**
```json
{
  "sections": [
    {
      "sectionId": "title",
      "sectionName": "需求标题",
      "fieldType": "text",
      "required": true,
      "placeholder": "请输入需求标题",
      "maxLength": 200
    },
    {
      "sectionId": "background",
      "sectionName": "历史背景",
      "fieldType": "richtext",
      "required": true,
      "placeholder": "请描述需求的历史背景和产生原因",
      "defaultContent": "<h3>历史背景</h3><p>请填写...</p>"
    },
    {
      "sectionId": "scope",
      "sectionName": "适用范围",
      "fieldType": "richtext",
      "required": true,
      "placeholder": "请描述需求的适用范围",
      "defaultContent": "<h3>适用范围</h3><ul><li>适用系统：</li><li>适用用户：</li></ul>"
    },
    {
      "sectionId": "acceptance",
      "sectionName": "验收标准",
      "fieldType": "richtext",
      "required": true,
      "placeholder": "请列出验收标准",
      "defaultContent": "<h3>验收标准</h3><ol><li>功能验收：</li><li>性能验收：</li></ol>"
    }
  ]
}
```

#### 2.4.3 后端实现

**RequirementTemplateService.java**
```java
@Service
public class RequirementTemplateService {
    
    /**
     * 根据需求类型获取模板
     */
    public RequirementTemplateVO getTemplateByType(String typeCode) {
        RequirementTemplate template = templateMapper.selectOne(
            new LambdaQueryWrapper<RequirementTemplate>()
                .eq(RequirementTemplate::getRequirementTypeCode, typeCode)
                .eq(RequirementTemplate::getIsActive, true)
                .eq(RequirementTemplate::getDeletedAt, 0)
        );
        
        if (template == null) {
            return getDefaultTemplate();
        }
        
        return RequirementTemplateVO.from(template);
    }
    
    /**
     * 保存或更新模板
     */
    @Transactional
    public void saveTemplate(RequirementTemplateSaveDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        
        RequirementTemplate existing = templateMapper.selectOne(
            new LambdaQueryWrapper<RequirementTemplate>()
                .eq(RequirementTemplate::getRequirementTypeCode, dto.getRequirementTypeCode())
                .eq(RequirementTemplate::getDeletedAt, 0)
        );
        
        if (existing != null) {
            existing.setTemplateName(dto.getTemplateName());
            existing.setTemplateContent(dto.getTemplateContent());
            existing.setUpdatedAt(LocalDateTime.now());
            templateMapper.updateById(existing);
        } else {
            RequirementTemplate template = new RequirementTemplate();
            template.setRequirementTypeCode(dto.getRequirementTypeCode());
            template.setTemplateName(dto.getTemplateName());
            template.setTemplateContent(dto.getTemplateContent());
            template.setIsActive(true);
            template.setCreatorId(userId);
            templateMapper.insert(template);
        }
    }
}
```

#### 2.4.4 前端实现

**需求创建页面集成模板**
```vue
<template>
  <el-form :model="form" ref="formRef">
    <el-form-item label="需求类型" prop="type">
      <el-select v-model="form.type" @change="onTypeChange">
        <el-option v-for="type in requirementTypes" :key="type.code" 
                   :label="type.name" :value="type.code" />
      </el-select>
    </el-form-item>
    
    <!-- 动态渲染模板字段 -->
    <div v-for="section in templateSections" :key="section.sectionId">
      <el-form-item :label="section.sectionName" :prop="section.sectionId" 
                    :rules="section.required ? [{ required: true, message: '必填项' }] : []">
        
        <!-- 文本字段 -->
        <el-input v-if="section.fieldType === 'text'" 
                  v-model="form[section.sectionId]"
                  :placeholder="section.placeholder"
                  :maxlength="section.maxLength" />
        
        <!-- 富文本字段 -->
        <rich-text-editor v-if="section.fieldType === 'richtext'"
                          v-model="form[section.sectionId]"
                          :placeholder="section.placeholder"
                          :default-content="section.defaultContent" />
      </el-form-item>
    </div>
  </el-form>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { getRequirementTemplate } from '@/api/modules/requirement'

const form = ref({
  type: '',
  title: '',
  background: '',
  scope: '',
  acceptance: ''
})

const templateSections = ref([])

const onTypeChange = async (typeCode: string) => {
  const template = await getRequirementTemplate(typeCode)
  templateSections.value = template.sections
  
  // 自动填充默认内容
  template.sections.forEach(section => {
    if (section.defaultContent) {
      form.value[section.sectionId] = section.defaultContent
    }
  })
}
</script>
```

---

## 三、实施计划

### 3.1 第一阶段：会签功能（优先级最高）

**工作量：3-5天**

1. 数据库表创建（workflow_countersign_records）
2. 后端服务实现（WorkflowCountersignService）
3. 前端会签配置组件（CountersignConfig.vue）
4. 前端会签审批界面
5. 集成测试

### 3.2 第二阶段：需求模板系统

**工作量：3-5天**

1. 数据库表创建（requirement_templates）
2. 后端模板服务（RequirementTemplateService）
3. 前端模板管理界面
4. 前端需求创建页面集成模板
5. 集成测试

### 3.3 第三阶段：多分支并发执行

**工作量：5-7天**

1. 数据库表创建（workflow_parallel_branches）
2. 后端并行分支服务（WorkflowParallelBranchService）
3. 前端并行配置组件（ParallelConfig.vue）
4. 工作流引擎集成并行逻辑
5. 集成测试

### 3.4 第四阶段：可视化组件优化

**工作量：5-7天**

1. 标准化配置DTO设计
2. 前端配置组件重构（AssigneeConfig/ConditionConfig等）
3. 工作流编辑器界面优化
4. 配置数据序列化/反序列化
5. 集成测试

---

## 四、技术风险与应对

### 4.1 并发控制风险

**风险：** 多分支并发执行可能导致数据竞争

**应对：**
- 使用乐观锁（WorkflowInstance.lockVersion）
- 分支状态独立管理
- 汇聚节点使用分布式锁

### 4.2 会签性能风险

**风险：** 大量会签人可能导致性能问题

**应对：**
- 限制会签人数上限（建议不超过20人）
- 使用异步通知
- 分页查询会签记录

### 4.3 模板兼容性风险

**风险：** 历史需求无模板数据

**应对：**
- 提供默认模板
- 支持模板迁移工具
- 保持向后兼容

---

## 五、验收标准

### 5.1 功能验收

✅ 会签功能：支持全部通过/任一通过/多数通过策略
✅ 需求模板：不同需求类型绑定不同模板
✅ 并行分支：支持多分支并发执行和汇聚
✅ 可视化配置：无需编写脚本即可配置节点

### 5.2 性能验收

✅ 工作流流转响应时间 < 500ms
✅ 会签审批提交响应时间 < 300ms
✅ 模板加载响应时间 < 200ms

### 5.3 易用性验收

✅ 工作流配置界面直观易懂
✅ 需求创建时模板自动加载
✅ 会签审批界面清晰展示审批进度

---

## 六、附录

### 6.1 相关文件清单

**后端新增文件：**
- `WorkflowCountersignService.java`
- `WorkflowParallelBranchService.java`
- `RequirementTemplateService.java`
- `WorkflowCountersignRecord.java`
- `WorkflowParallelBranch.java`
- `RequirementTemplate.java`

**前端新增文件：**
- `views/system/workflow-config/components/CountersignConfig.vue`
- `views/system/workflow-config/components/ParallelConfig.vue`
- `views/system/workflow-config/components/AssigneeConfig.vue`
- `views/system/workflow-config/components/ConditionConfig.vue`
- `views/settings/requirement-templates/index.vue`
- `views/settings/requirement-templates/editor.vue`

**数据库脚本：**
- `database/migrations/workflow_countersign.sql`
- `database/migrations/workflow_parallel_branches.sql`
- `database/migrations/requirement_templates.sql`

---

**方案设计完成时间：** 2026-05-28
**预计总工期：** 16-24 工作日
**建议优先级：** 会签功能 > 需求模板 > 并行分支 > 可视化优化
