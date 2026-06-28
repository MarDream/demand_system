# ADR-001: 将需求流转评分改为可选

## 状态
✅ Accepted (2026-06-25)

## 背景

需求管理系统在工作流节点流转时要求用户对审批环节进行 1-5 星评分。当前实现存在以下问题：

1. **价值链断裂**：系统收集了评分数据，但没有任何统计、分析或报表功能使用这些数据
2. **操作负担**：每次流转都强制要求评分，用户不理解评分的目的和价值
3. **数据质量隐患**：缺乏反馈回路，用户可能随意评分或一律打满分
4. **标准缺失**：在"待分析→待确认"等节点评分缺乏明确的评价标准和评价主体定义

**技术现状：**
```
RequirementApprovalEvaluation
├── rating (Integer 1-5)          ✅ 存储
├── content (String)               ✅ 存储  
├── attachments (List)             ✅ 存储
└── 统计分析功能                   ❌ 完全缺失
```

**代码位置：**
- `WorkflowEngineService.validateApprovalEvaluation()` — 强制校验 rating 必填
- `WorkflowCountersignService.submitCountersign()` — 会签评分校验
- `RequirementApprovalEvaluation` 实体 — 数据存储

## 决策

将评分从**必填**改为**可选**，同时保留数据结构和校验逻辑的完整性。

**具体改动：**

1. **工作流引擎** (`WorkflowEngineService.java`)
   ```java
   // 修改前
   if (rating == null || rating < 1 || rating > 5) {
       throw new BusinessException(400, "审批环节需选择 1-5 星评价");
   }
   
   // 修改后
   if (rating != null && (rating < 1 || rating > 5)) {
       throw new BusinessException(400, "评分必须在 1-5 星之间");
   }
   ```

2. **会签服务** (`WorkflowCountersignService.java`)
   - 已经是可选校验，无需修改（仅校验合法性）

3. **数据库和实体**
   - 保持 `requirement_approval_evaluations` 表结构不变
   - `rating` 字段保持 `Integer` 可空类型
   - 保留未来扩展能力

## 后果

### ✅ 变得更容易的事

1. **降低操作负担**
   - 用户不再被迫对不理解的环节进行评分
   - 减少"为了评而评"的无效数据

2. **降低认知负担**
   - 新用户不需要理解评分标准就能完成流转
   - 避免"评分是否影响流转结果"的困惑

3. **保留扩展性**
   - 数据结构完整保留
   - 未来需要时可以快速启用评分分析功能
   - 可以逐步补全：评分标准 → 统计分析 → 仪表盘展示

### ⚠️ 变得更难的事

1. **数据稀疏性**
   - 大部分流转可能不会填写评分
   - 未来做统计分析时可能样本不足

2. **标准缺失延续**
   - 未明确"什么时候应该评分"
   - 未定义评价标准和评价主体

### 🔄 未来改进方向（当需要时）

如果评分功能确有价值，需要补全以下能力：

1. **明确评分语义**
   - 定义评价主体：产品经理？需求方？
   - 定义评价对象：需求质量？响应速度？交付效果？
   - 在不同节点定义不同的评分标准

2. **构建统计分析**
   ```java
   // 新增统计接口
   RequirementRatingStatisticsVO getRequirementRatingStatistics(
       Long projectId, 
       LocalDate startDate, 
       LocalDate endDate
   );
   ```

3. **形成反馈回路**
   - 仪表盘展示评分趋势
   - 低于 3 星的需求自动标记复盘
   - 评分纳入迭代回顾会议
   - 评分作为质量改进的输入

## 相关决策

- 未来如需启用评分分析，建议创建 ADR-00X 记录具体的统计需求和实现方案

## 参考资料

- `WorkflowEngineService.java:824` — 评分校验逻辑
- `RequirementApprovalEvaluation.java` — 评分数据模型
- E2E 测试报告显示评分校验为 1-5 星范围
