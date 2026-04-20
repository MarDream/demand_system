# 需求管理系统 - 产品需求说明书 (PRD)

> 版本：v1.0
> 日期：2026-04-19
> 参考：TAPD 需求管理解决方案

---

## 目录

1. [产品概述](#1-产品概述)
2. [用户角色与权限](#2-用户角色与权限)
3. [需求层级模型](#3-需求层级模型)
4. [核心功能模块](#4-核心功能模块)
5. [数据模型设计](#5-数据模型设计)
6. [非功能需求](#6-非功能需求)
7. [API 设计概要](#7-api-设计概要)
8. [技术选项与技术架构](#8-技术选项与技术架构)
9. [实施路线图](#9-实施路线图)

---

## 1. 产品概述

### 1.1 产品定位

本系统是一款面向软件研发团队的**一站式需求管理工具**，覆盖从需求收集、分析、评审、开发到上线验收的全生命周期。旨在解决传统需求管理中的三大痛点：

| 痛点 | 解决方案 |
|------|----------|
| 需求流程不明确导致协作效率低下 | 自定义工作流引擎，状态机驱动流转 |
| 需求时常变更导致重复返工 | 变更历史追踪 + 影响范围分析 |
| 信息不畅通导致沟通成本增加 | 多维关联 + 实时通知 + 可视化报表 |

### 1.2 目标用户群体

| 角色 | 核心使用场景 |
|------|-------------|
| 业务人员 | 通过平台创建、编辑业务需求，反馈业务诉求 |
| 产品经理 | 需求收集、拆解、优先级排序、评审组织 |
| 项目经理 | 迭代规划、进度跟踪、资源协调 |
| 开发人员 | 需求实现、状态更新、关联代码提交 |
| 测试人员 | 需求验收标准确认、关联测试用例 |
| 团队管理者 | 数据统计、效能分析、决策支持 |

### 1.3 核心价值主张

- **全流程覆盖**：需求收集 → 分解 → 评审 → 规划 → 实施 → 验收
- **灵活可配置**：自定义字段、工作流、模板、视图
- **可视化协作**：故事墙、燃尽图、甘特图、仪表盘
- **深度关联**：需求与缺陷、用例、文档、代码的全链路追溯

### 1.4 终端支持

本系统支持 **Web端** 和 **小程序端** 双终端，覆盖不同使用场景。

| 终端 | 定位 | 典型使用场景 |
|------|------|-------------|
| **Web端** | 主力工作台 | 需求详细编辑、工作流配置、可视化报表、甘特图排期、迭代规划 |
| **小程序端** | 移动协同 | 快速创建需求、审批流转、进度查看、消息通知处理、出差/通勤场景 |

#### 1.4.1 Web端功能范围

- 需求全生命周期管理（创建/编辑/流转/关联）
- 可视化流程引擎配置
- 自定义字段/模板/视图配置
- 迭代管理与发布计划
- 需求评审（多人协作评审）
- 全部统计报表与自定义仪表盘
- 甘特图/燃尽图/累计流图等高级可视化
- 项目设置与系统管理
- 需求批量导入导出

#### 1.4.2 小程序端功能范围

| 功能模块 | 支持能力 |
|----------|----------|
| 需求创建 | 快速创建需求（标题+描述+优先级+负责人），支持语音输入 |
| 需求查看 | 需求列表、详情查看、关联数据查看 |
| 需求编辑 | 基础字段编辑、状态流转、添加评论 |
| 审批处理 | 审批列表、审批意见填写、通过/拒绝/转交 |
| 消息通知 | 实时消息推送、@提醒、审批超时提醒 |
| 迭代查看 | 迭代列表、迭代内需求查看 |
| 统计概览 | 个人待办统计、项目进度概览 |
| 扫码关联 | 扫描二维码快速关联需求与缺陷/用例 |

**不支持**（仅限 Web 端）：可视化流程引擎配置、甘特图/燃尽图等高级可视化、批量导入导出、项目设置与系统管理、自定义字段配置。

#### 1.4.3 双端同步

| 同步策略 | 说明 |
|----------|------|
| 数据实时同步 | Web端与小程序端操作实时同步至云端，延迟 < 1s |
| 离线能力 | 小程序端支持离线草稿（本地缓存），网络恢复后自动同步 |
| 消息推送 | 小程序订阅消息推送，Web端站内信 + 可选邮件通知 |

#### 1.4.4 技术选型建议

| 终端 | 技术方案 | 说明 |
|------|----------|------|
| Web端 | Vue 3 + TypeScript + Vite + Element Plus / Ant Design Vue | 响应式布局，支持主流浏览器 |
| 小程序端 | 微信小程序原生 或 uni-app（推荐，可同时覆盖微信/支付宝/钉钉小程序） | 统一代码库，多端发布 |
| 后端 API | 统一 RESTful API，Web端与小程序端共用 | 接口鉴权采用 JWT Token |

---

## 2. 用户角色与权限

### 2.1 角色定义

| 角色 | 标识 | 描述 |
|------|------|------|
| 系统管理员 | `admin` | 系统全局管理，用户/项目/权限配置 |
| 业务人员 | `business_user` | 通过平台创建、编辑业务需求，反馈业务诉求 |
| 产品经理 | `product_owner` | 需求全生命周期管理，评审主导 |
| 项目经理 | `project_manager` | 迭代规划，进度跟踪，资源协调 |
| 开发人员 | `developer` | 需求实现，状态更新，工时填报 |
| 测试人员 | `tester` | 需求验收，用例关联，缺陷提交 |
| 观察者 | `observer` | 只读查看，不参与编辑 |

### 2.2 权限矩阵

| 权限 | admin | business_user | product_owner | project_manager | developer | tester | observer |
|------|-------|---------------|---------------|-----------------|-----------|--------|----------|
| 创建需求 | ✅ | ✅ (仅自己创建) | ✅ | ✅ | ✅ | ❌ | ❌ |
| 编辑需求 | ✅ | 仅自己创建的 | ✅ | ✅ | 仅分配给自己的 | ❌ | ❌ |
| 删除需求 | ✅ | 仅自己创建且未流转 | ✅ | ❌ | ❌ | ❌ | ❌ |
| 需求评审 | ✅ | ❌ | ✅ (主导) | ✅ | ✅ | ✅ | ❌ |
| 迭代管理 | ✅ | ❌ | ✅ | ✅ (主导) | ❌ | ❌ | ❌ |
| 工作流配置 | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| 自定义字段 | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| 统计报表 | ✅ | 仅自己创建的 | ✅ | ✅ | 仅个人 | 仅个人 | ✅ |
| 系统设置 | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |

### 2.3 用户组织架构

系统中每个人员均有所在区域、部门/公司、岗位职称/角色的定义，用于需求权限分配、数据隔离、统计维度等场景。

#### 2.3.1 组织架构维度

| 维度 | 标识 | 说明 | 示例 |
|------|------|------|------|
| 区域 | `region` | 用户所在地理区域/业务区域 | 华东区、华南区、华北区、海外区 |
| 部门/公司 | `department` | 用户所属部门或子公司 | 产品研发部、市场部、XX分公司 |
| 岗位职称 | `position` | 用户的岗位职称/职级 | 高级工程师、产品专家、部门经理 |
| 系统角色 | `role` | 用户在系统中的操作角色（见 2.1） | product_owner、developer 等 |

**说明**：系统角色（role）决定用户"能做什么操作"，组织架构（region/department/position）决定用户"能看到什么数据"。

#### 2.3.2 组织架构树

```
公司总部
├── 华东区
│   ├── 上海分公司
│   │   ├── 产品研发部
│   │   │   ├── 前端组（岗位：前端工程师）
│   │   │   ├── 后端组（岗位：后端工程师）
│   │   │   └── 产品组（岗位：产品经理）
│   │   └── 市场部
│   └── 杭州分公司
│       └── 技术中心
├── 华南区
│   └── 深圳分公司
│       └── 产品研发部
└── 华北区
    └── 北京总部
        ├── 架构组（岗位：架构师）
        └── 运维部（岗位：运维工程师）
```

#### 2.3.3 组织架构在需求管理中的应用

| 场景 | 应用方式 |
|------|----------|
| 数据权限隔离 | 业务人员只能查看本区域/本部门创建的需求 |
| 需求自动路由 | 业务人员创建需求后，自动流转到所属区域的产品经理 |
| 审批链路 | 按组织架构自动确定审批人（如"部门负责人审批"） |
| 统计维度 | 按区域/部门/岗位统计需求数量、完成率、时长等 |
| 通知范围 | 状态变更时通知同部门相关人员 |
| 动态指定人 | 工作流节点按"同部门产品经理"等规则动态确定操作人 |

---

---

## 3. 需求层级模型

### 3.1 层级结构

```
Epic (史诗)
  └─ Feature (特性)
       └─ User Story (用户故事)
            └─ Task (任务)
```

| 层级 | 粒度 | 描述 | 典型周期 |
|------|------|------|----------|
| Epic | 最大 | 基于产品战略方向，可独立使用的产品模块 | 季度/半年 |
| Feature | 中等 | 主要产品功能，迭代和版本的主要目标 | 月/季度 |
| User Story | 小 | 从用户角度描述的独立功能点 | 迭代内 |
| Task | 最小 | 具体执行任务，可分配给个人 | 天/周 |

### 3.2 用户故事标准格式

```
作为 [角色]，我希望 [功能]，以便 [价值]
```

### 3.3 拆解规则

- 父需求可拆解为多个子需求/任务
- 子需求状态汇总后自动更新父需求状态
- 父需求优先级默认传递至子需求
- 支持跨层级的需求关联（依赖、阻塞、相关）

### 3.4 层级间数据继承

| 字段 | 继承规则 |
|------|----------|
| 所属项目 | 子级继承父级 |
| 所属迭代 | 子级可独立设置，默认继承 |
| 优先级 | 子级默认继承，可单独调整 |
| 标签 | 子级继承父级标签，可追加 |
| 负责人 | 不继承，需单独指定 |

---

## 4. 核心功能模块

### 4.1 需求管理

#### 4.1.1 需求创建

- **快速创建**：标题 + 描述 + 优先级 + 负责人
- **模板创建**：预置模板（功能需求、优化需求、Bug修复、技术债务等）
- **批量创建**：Excel/CSV 导入
- **API 创建**：通过开放 API 自动创建

#### 4.1.2 需求基本信息

| 字段 | 类型 | 必填 | 描述 |
|------|------|------|------|
| 需求标题 | 单行文本 | ✅ | 简洁描述需求内容 |
| 需求描述 | 富文本 | ✅ | 详细说明，支持图片/表格/代码块 |
| 需求类型 | 下拉列表 | ✅ | 功能/优化/Bug/技术债务/运营 |
| 优先级 | 下拉列表 | ✅ | P0-紧急 / P1-高 / P2-中 / P3-低 |
| 状态 | 系统管理 | ✅ | 由工作流驱动 |
| 负责人 | 人名输入 | ✅ | 当前处理人 |
| 创建人 | 系统自动 | ✅ | 创建者 |
| 创建时间 | 系统自动 | ✅ | 创建时间戳 |
| 更新时间 | 系统自动 | ✅ | 最后更新时间戳 |
| 所属迭代 | 关联选择 | ❌ | 关联的迭代 |
| 所属模块 | 关联选择 | ❌ | 需求分类 |
| 截止日期 | 日期 | ❌ | 期望完成时间 |
| 估算工时 | 数值 | ❌ | 预估工作量（人时/人天） |
| 实际工时 | 数值 | 系统计算 | 实际消耗工时 |
| 标签 | 多选标签 | ❌ | 自定义标签 |
| 附件 | 文件上传 | ❌ | 关联文档、原型图等 |

#### 4.1.3 需求列表与视图

- **自定义列表视图**：选择显示字段、列顺序、列宽
- **过滤与搜索**：多条件组合过滤，全文搜索
- **排序**：支持多字段排序
- **视图保存**：保存常用视图配置，快速切换
- **视图类型**：
  - 列表视图（默认）
  - 看板视图（基于状态分组）
  - 甘特图视图（时间轴）
  - 表格视图（类 Excel 编辑）

#### 4.1.4 需求分类

- 支持多级分类树，如：
  ```
  前端
    ├── APP端
    ├── Web端
    └── 小程序端
  后端
    ├── API服务
    ├── 数据处理
    └── 基础设施
  运营
    ├── 活动需求
    └── 数据需求
  ```

#### 4.1.5 需求导入/导出

| 操作 | 支持格式 | 说明 |
|------|----------|------|
| 导入 | Excel (.xlsx)、CSV | 支持字段映射，预览校验 |
| 导出 | Excel (.xlsx)、CSV、PDF | 支持当前视图导出 |

#### 4.1.6 需求变更历史

- 记录每次变更：字段名、旧值、新值、操作人、操作时间
- 支持变更对比视图
- 支持变更回滚（需权限）

---

### 4.2 需求工作流

#### 4.2.0 可视化流程引擎

**核心能力**：通过可视化拖拽画布定义需求生命周期流程，每个节点支持精细化权限与操作配置。

**可视化编辑器**：
- **拖拽画布**：从左到右的流程图编辑器，支持拖拽添加节点、拖拽连线创建流转路径
- **节点类型**：
  - **状态节点**：代表需求所处的状态（如"开发中"、"测试中"）
  - **审批节点**：代表需要人工审批的环节（如"评审通过"）
  - **自动节点**：代表自动执行的动作（如"自动分配迭代"、"发送通知"）
  - **条件分支节点**：根据条件判断走不同流转路径（如"优先级=P0 → 走紧急通道"）
  - **并行节点**：多个分支同时执行（如"技术评审"与"业务评审"并行）
- **连线规则**：支持标注流转条件、流转名称、流转图标
- **实时校验**：画布实时检测流程完整性（孤立节点、死循环、无出口等）
- **版本管理**：流程配置支持版本化，可回滚到历史版本
- **模板市场**：预置常用流程模板（敏捷开发流程、瀑布流程、自定义流程）

**节点级精细化权限控制**：

每个流程节点可独立配置以下权限：

| 权限维度 | 配置项 | 说明 |
|----------|--------|------|
| **操作权限** | 可操作角色 | 哪些角色可以在此节点执行操作 |
| | 可操作人 | 精确到具体用户（如"仅张三可审批"） |
| | 动态指定人 | 按规则动态确定操作人（如"需求创建人"、"负责人"、"负责人上级"、"同部门产品经理"） |
| **字段权限** | 字段可见性 | 在此节点哪些字段可见/隐藏 |
| | 字段可编辑性 | 在此节点哪些字段可编辑/只读/必填 |
| | 字段级校验 | 在此节点对字段值的额外校验规则 |
| **按钮权限** | 可选操作按钮 | 在此节点显示哪些操作按钮（通过/拒绝/打回/转交/加签） |
| | 按钮可见条件 | 按钮的显示条件（如"仅当关联缺陷数=0时显示通过按钮"） |
| **流转权限** | 可流转目标 | 从此节点可以流转到哪些后续节点 |
| | 流转条件 | 触发流转的前置条件表达式 |
| | 强制流转 | 是否允许跳过此节点 |
| **通知权限** | 通知规则 | 进入/离开此节点时通知谁（邮件/Webhook/企微/飞书） |
| | 超时通知 | 节点停留超过N小时自动通知 |
| **数据权限** | 数据可见范围 | 在此节点能看到需求的哪些关联数据（关联缺陷/用例/代码） |
| | 附件操作权限 | 在此节点是否允许上传/下载/删除附件 |

**节点配置示例**：

```
节点：「技术评审」
├── 操作权限
│   ├── 可操作角色：技术负责人、架构师
│   └── 动态指定人：需求所属模块的技术负责人
├── 字段权限
│   ├── 可见字段：全部
│   ├── 可编辑字段：技术评审意见、技术方案、风险等级
│   └── 必填字段：技术评审意见
├── 按钮权限
│   ├── 显示按钮：[通过] [拒绝] [打回修改] [转交]
│   └── 通过条件：关联的技术风险评估 ≠ 高
├── 流转权限
│   ├── 通过 → 开发中（条件：评审意见不为空）
│   ├── 拒绝 → 已拒绝
│   └── 打回 → 待评审（条件：自动通知创建人）
├── 通知规则
│   ├── 进入节点：通知需求创建人 + 负责人
│   └── 超时通知：停留超过24小时通知项目经理
└── 数据权限
    ├── 可见关联：关联的代码提交、技术方案文档
    └── 附件操作：允许上传评审附件，不允许删除已有附件
```

#### 4.2.1 状态机模型

**典型状态流转**：

```
[新建] → [待评审] → [评审中] → [已通过] → [开发中] → [测试中] → [已上线] → [已验收]
   │         │          │          │          │          │          │
   │         ↓          ↓          ↓          ↓          ↓          ↓
   │      [已拒绝] ← [评审不通过]  [重新评审]   [打回]     [测试不通过]  [验收不通过]
   │
   └→ [已取消]
```

#### 4.2.2 工作流配置项

> 以下配置项均通过可视化流程引擎的节点属性面板完成配置，同时也支持 JSON 批量导入导出。

| 配置项 | 说明 |
|--------|------|
| 状态定义 | 自定义状态名称、颜色、分组 |
| 流转规则 | 定义状态间的流转路径（允许/禁止） |
| 流转条件 | 触发流转的前置条件（如：必须填写验收标准） |
| 角色权限 | 各状态下的可操作角色 |
| 必填字段 | 流转到特定状态时必须填写的字段 |
| 自动流转 | 满足条件时自动流转到下一状态 |
| 通知规则 | 状态变更时通知相关人 |

#### 4.2.3 流转规则示例

| 当前状态 | 可流转至 | 操作人 | 必填字段 |
|----------|----------|--------|----------|
| 新建 | 待评审、已取消 | 创建人、产品经理 | - |
| 待评审 | 评审中、已取消 | 产品经理 | - |
| 评审中 | 已通过、已拒绝 | 评审人 | 评审意见 |
| 已通过 | 开发中 | 项目经理 | 所属迭代 |
| 开发中 | 测试中、打回 | 开发人员 | 开发说明 |
| 测试中 | 已上线、测试不通过 | 测试人员 | 测试报告 |
| 已上线 | 已验收、验收不通过 | 产品经理 | 验收结论 |
| 已验收 | - | - | - (终态) |

#### 4.2.4 自动化流转规则

通过"触发条件 + 执行条件"实现：

| 触发条件 | 执行条件 | 示例 |
|----------|----------|------|
| 状态变更为"已通过" | 自动分配到指定迭代 | 评审通过的需求自动进入当前迭代 |
| 关联缺陷全部关闭 | 自动流转到"已上线" | 所有关联缺陷修复后自动标记上线 |
| 开发完成 + 代码合并 | 自动流转到"测试中" | 代码合并后自动通知测试 |

---

### 4.3 需求评审

#### 4.3.1 评审流程模板

| 模板类型 | 适用场景 | 流程节点 |
|----------|----------|----------|
| 正常评审 | 常规需求 | 提交评审 → 初审 → 技术评审 → 评审结论 |
| 快速评审 | 小优化/紧急修复 | 提交评审 → 评审结论 |
| 联合评审 | 跨团队大需求 | 提交评审 → 多部门并行评审 → 评审汇总 → 评审结论 |

#### 4.3.2 评审管理

| 功能 | 说明 |
|------|------|
| 创建评审 | 选择需求、评审人、评审模板、评审时间 |
| 评审意见 | 评审人填写评审意见（通过/不通过/需修改） |
| 评审结论 | 汇总所有评审意见，自动生成结论 |
| 评审记录 | 完整记录评审过程，支持追溯 |
| 评审统计 | 评审通过率、平均评审时长等 |

#### 4.3.3 评审意见

| 字段 | 类型 | 说明 |
|------|------|------|
| 评审结果 | 单选 | 通过 / 不通过 / 需修改后重审 |
| 评审意见 | 富文本 | 具体评审意见 |
| 修改建议 | 富文本 | 需要修改的内容 |
| 评审人 | 人名 | 评审者 |
| 评审时间 | 日期 | 评审完成时间 |

---

### 4.4 迭代与计划管理

#### 4.4.1 迭代管理

| 功能 | 说明 |
|------|------|
| 创建迭代 | 迭代名称、开始/结束时间、目标、负责人 |
| 迭代池 | 未分配的需求统一管理 |
| 需求分配 | 拖拽或批量分配需求到迭代 |
| 容量管理 | 设置迭代容量（人天），校验是否超容 |
| 迭代状态 | 未开始 / 进行中 / 已完成 / 已关闭 |

#### 4.4.2 迭代可视化

| 视图 | 说明 |
|------|------|
| 故事墙/看板 | 按状态分列展示需求卡片，支持拖拽流转 |
| 燃尽图 | 剩余工作量随时间变化趋势，判断是否能按期完成 |
| 甘特图 | 需求在时间轴上的分布，查看排期是否合理 |
| 累计流图 | 各状态需求数量的时间变化，识别瓶颈环节 |

#### 4.4.3 发布计划

- 将多个迭代组合为一个发布计划
- 跟踪发布计划的整体进度
- 发布评审（关联 4.3 评审模块）
- 发布记录与回顾

---

### 4.5 需求关联

#### 4.5.1 关联类型

| 关联类型 | 方向 | 说明 |
|----------|------|------|
| 父子关系 | 单向 | 父需求 → 子需求 |
| 依赖关系 | 单向 | 需求A 依赖 需求B（B先完成） |
| 阻塞关系 | 单向 | 需求A 阻塞 需求B |
| 相关关系 | 双向 | 需求A 与 需求B 相关 |
| 关联缺陷 | 双向 | 需求 ↔ 缺陷 |
| 关联用例 | 双向 | 需求 ↔ 测试用例 |
| 关联文档 | 单向 | 需求 → 文档/原型 |
| 关联代码 | 单向 | 需求 → 代码提交/PR |

#### 4.5.2 关联管理

- 创建关联：搜索并选择目标工作项
- 关联列表：在需求详情页展示所有关联
- 关联影响分析：修改需求时提示关联项
- 关联阻断校验：防止循环依赖

---

### 4.6 统计与报表

#### 4.6.1 统计维度

| 报表名称 | 维度 | 说明 |
|----------|------|------|
| 需求分布统计 | 状态/类型/优先级/负责人/模块 | 各维度下需求数量占比 |
| 需求时长统计 | 各状态停留时长 | 识别流转瓶颈 |
| 需求燃烧图 | 时间 | 已完成/总需求数量随时间变化 |
| 需求累计流图 | 时间+状态 | 各状态需求数量累计变化 |
| 需求关联统计 | 关联类型 | 各类关联关系数量统计 |
| 评审通过率 | 时间+评审人 | 评审通过/总评审数 |
| 迭代完成率 | 迭代 | 计划需求 vs 实际完成 |
| 人效统计 | 人员 | 各人员处理需求数量与时长 |

#### 4.6.2 自定义仪表盘

- 拖拽式布局，自由添加/排列报表组件
- 支持仪表盘模板保存与共享
- 定时生成报表快照
- 支持导出 PDF/图片

---

### 4.7 自定义配置

#### 4.7.1 自定义字段

| 字段类型 | 说明 | 示例 |
|----------|------|------|
| 单行文本 | 短文本输入 | 需求编号 |
| 多行文本 | 长文本，支持富文本 | 详细描述 |
| 下拉列表 | 单选，预设选项 | 优先级、类型 |
| 多选列表 | 多选，预设选项 | 涉及端、技术栈 |
| 单选框 | 二选一或多选一 | 是否紧急 |
| 复选框 | 多选 | 测试环境 |
| 人名输入 | 选择项目成员 | 负责人、评审人 |
| 日期 | 日期选择器 | 截止日期 |
| 数值 | 数字输入 | 估算工时 |
| 级联多选 | 层级选择 | 模块分类 |

**字段配置能力**：
- 按状态显示不同字段（如：开发中显示开发说明字段，测试中显示测试报告字段）
- 字段显示顺序自定义
- 字段级权限控制（只读/必填/隐藏）
- 默认值设置

#### 4.7.2 需求模板

- 预置模板：功能需求、优化需求、Bug 修复、技术债务
- 自定义模板：选择字段、设置默认值、指定工作流
- 模板分类管理

#### 4.7.3 项目设置

| 配置项 | 说明 |
|--------|------|
| 项目基本信息 | 名称、描述、头像、成员 |
| 需求设置 | 字段、工作流、模板、分类 |
| 迭代设置 | 迭代命名规则、容量单位 |
| 通知设置 | 邮件/Webhook/企业微信/飞书/钉钉 |
| 权限设置 | 角色权限矩阵自定义 |

---

## 5. 数据模型设计

### 5.1 核心表结构

#### 5.1.1 需求表 (`requirements`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| project_id | BIGINT | NOT NULL, INDEX | 所属项目 |
| parent_id | BIGINT | NULL, INDEX | 父需求ID（NULL表示顶级） |
| creator_id | BIGINT | NOT NULL | 创建人 |
| assignee_id | BIGINT | NULL | 负责人 |
| title | VARCHAR(500) | NOT NULL | 标题 |
| description | LONGTEXT | NULL | 描述（富文本） |
| type | VARCHAR(50) | NOT NULL | 类型 |
| priority | VARCHAR(20) | NOT NULL | 优先级 |
| status | VARCHAR(50) | NOT NULL, INDEX | 状态 |
| module_id | BIGINT | NULL, INDEX | 所属模块分类 |
| iteration_id | BIGINT | NULL, INDEX | 所属迭代 |
| estimated_hours | DECIMAL(10,2) | NULL | 估算工时 |
| actual_hours | DECIMAL(10,2) | NULL | 实际工时 |
| due_date | DATE | NULL | 截止日期 |
| order_num | INT | DEFAULT 0 | 排序号 |
| created_at | DATETIME | NOT NULL | 创建时间 |
| updated_at | DATETIME | NOT NULL | 更新时间 |
| deleted_at | DATETIME | NULL, INDEX | 软删除时间 |

#### 5.1.2 需求自定义字段值表 (`requirement_custom_field_values`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 主键 |
| requirement_id | BIGINT | NOT NULL, INDEX | 需求ID |
| field_id | BIGINT | NOT NULL, INDEX | 字段ID |
| value_text | TEXT | NULL | 文本值 |
| value_number | DECIMAL | NULL | 数值值 |
| value_date | DATE | NULL | 日期值 |
| value_user_ids | JSON | NULL | 用户ID列表 |

#### 5.1.3 自定义字段配置表 (`custom_fields`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 主键 |
| project_id | BIGINT | NOT NULL | 所属项目 |
| name | VARCHAR(100) | NOT NULL | 字段名称 |
| field_type | VARCHAR(50) | NOT NULL | 字段类型 |
| options | JSON | NULL | 选项列表（下拉/多选等） |
| required | BOOLEAN | DEFAULT FALSE | 是否必填 |
| visible_statuses | JSON | NULL | 可见状态列表 |
| default_value | TEXT | NULL | 默认值 |
| sort_order | INT | DEFAULT 0 | 显示顺序 |

#### 5.1.4 工作流状态表 (`workflow_states`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 主键 |
| project_id | BIGINT | NOT NULL | 所属项目 |
| name | VARCHAR(100) | NOT NULL | 状态名称 |
| color | VARCHAR(20) | NULL | 状态颜色 |
| is_final | BOOLEAN | DEFAULT FALSE | 是否终态 |
| sort_order | INT | DEFAULT 0 | 显示顺序 |

#### 5.1.5 工作流流转规则表 (`workflow_transitions`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 主键 |
| project_id | BIGINT | NOT NULL | 所属项目 |
| from_state_id | BIGINT | NOT NULL | 起始状态 |
| to_state_id | BIGINT | NOT NULL | 目标状态 |
| allowed_roles | JSON | NULL | 允许操作的角色 |
| required_fields | JSON | NULL | 必填字段列表 |
| conditions | JSON | NULL | 流转条件 |

#### 5.1.5a 工作流版本表 (`workflow_versions`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 主键 |
| project_id | BIGINT | NOT NULL | 所属项目 |
| version | INT | NOT NULL | 版本号 |
| name | VARCHAR(200) | NOT NULL | 版本名称 |
| definition | JSON | NOT NULL | 流程定义（节点、连线、条件） |
| is_active | BOOLEAN | DEFAULT FALSE | 是否为当前启用版本 |
| creator_id | BIGINT | NOT NULL | 创建人 |
| created_at | DATETIME | NOT NULL | 创建时间 |

#### 5.1.5b 工作流节点权限表 (`workflow_node_permissions`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 主键 |
| workflow_version_id | BIGINT | NOT NULL | 所属流程版本 |
| node_id | VARCHAR(100) | NOT NULL | 节点ID（流程定义中的节点标识） |
| allowed_roles | JSON | NULL | 可操作角色 |
| allowed_users | JSON | NULL | 可操作用户（精确到人） |
| assignee_rule | VARCHAR(100) | NULL | 动态指定人规则 |
| visible_fields | JSON | NULL | 可见字段列表 |
| editable_fields | JSON | NULL | 可编辑字段列表 |
| required_fields | JSON | NULL | 必填字段列表 |
| available_actions | JSON | NULL | 可用操作按钮 |
| action_conditions | JSON | NULL | 按钮显示条件 |
| notification_rules | JSON | NULL | 通知规则 |
| timeout_hours | INT | NULL | 超时通知时长（小时） |
| data_permissions | JSON | NULL | 数据可见范围 |
| attachment_permissions | JSON | NULL | 附件操作权限 |

#### 5.1.6 需求关联表 (`requirement_relations`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 主键 |
| source_id | BIGINT | NOT NULL, INDEX | 源需求ID |
| target_id | BIGINT | NOT NULL, INDEX | 目标需求ID |
| relation_type | VARCHAR(50) | NOT NULL | 关联类型 |
| created_at | DATETIME | NOT NULL | 创建时间 |

#### 5.1.7 需求变更历史表 (`requirement_history`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 主键 |
| requirement_id | BIGINT | NOT NULL, INDEX | 需求ID |
| operator_id | BIGINT | NOT NULL | 操作人 |
| field_name | VARCHAR(100) | NOT NULL | 变更字段 |
| old_value | TEXT | NULL | 旧值 |
| new_value | TEXT | NULL | 新值 |
| created_at | DATETIME | NOT NULL | 变更时间 |

#### 5.1.8 迭代表 (`iterations`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 主键 |
| project_id | BIGINT | NOT NULL, INDEX | 所属项目 |
| name | VARCHAR(200) | NOT NULL | 迭代名称 |
| description | TEXT | NULL | 描述 |
| start_date | DATE | NOT NULL | 开始日期 |
| end_date | DATE | NOT NULL | 结束日期 |
| capacity | DECIMAL(10,2) | NULL | 容量（人天） |
| status | VARCHAR(50) | NOT NULL | 状态 |
| creator_id | BIGINT | NOT NULL | 创建人 |
| created_at | DATETIME | NOT NULL | 创建时间 |

#### 5.1.9 评审记录表 (`reviews`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 主键 |
| requirement_id | BIGINT | NOT NULL, INDEX | 需求ID |
| reviewer_id | BIGINT | NOT NULL | 评审人 |
| result | VARCHAR(50) | NOT NULL | 评审结果 |
| comment | TEXT | NULL | 评审意见 |
| suggestions | TEXT | NULL | 修改建议 |
| reviewed_at | DATETIME | NULL | 评审时间 |

#### 5.1.10 项目表 (`projects`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 主键 |
| name | VARCHAR(200) | NOT NULL | 项目名称 |
| description | TEXT | NULL | 描述 |
| creator_id | BIGINT | NOT NULL | 创建人 |
| status | VARCHAR(50) | NOT NULL | 项目状态 |
| created_at | DATETIME | NOT NULL | 创建时间 |

#### 5.1.11 项目成员表 (`project_members`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 主键 |
| project_id | BIGINT | NOT NULL | 项目ID |
| user_id | BIGINT | NOT NULL | 用户ID |
| role | VARCHAR(50) | NOT NULL | 角色 |
| joined_at | DATETIME | NOT NULL | 加入时间 |

#### 5.1.12 用户表 (`users`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 主键 |
| username | VARCHAR(100) | NOT NULL, UNIQUE | 用户名 |
| real_name | VARCHAR(100) | NOT NULL | 真实姓名 |
| email | VARCHAR(200) | NULL, UNIQUE | 邮箱 |
| phone | VARCHAR(20) | NULL | 手机号 |
| avatar | VARCHAR(500) | NULL | 头像URL |
| status | VARCHAR(20) | NOT NULL | 状态（激活/停用） |
| created_at | DATETIME | NOT NULL | 创建时间 |
| updated_at | DATETIME | NOT NULL | 更新时间 |

#### 5.1.13 区域表 (`regions`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 主键 |
| name | VARCHAR(100) | NOT NULL | 区域名称 |
| parent_id | BIGINT | NULL, INDEX | 父区域ID（支持多级区域） |
| code | VARCHAR(50) | NULL, UNIQUE | 区域编码 |
| sort_order | INT | DEFAULT 0 | 排序号 |

#### 5.1.14 部门表 (`departments`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 主键 |
| name | VARCHAR(200) | NOT NULL | 部门/公司名称 |
| parent_id | BIGINT | NULL, INDEX | 父部门ID（支持多级部门树） |
| region_id | BIGINT | NULL, INDEX | 所属区域ID |
| code | VARCHAR(50) | NULL, UNIQUE | 部门编码 |
| type | VARCHAR(50) | NULL | 类型（公司/部门/组） |
| sort_order | INT | DEFAULT 0 | 排序号 |

#### 5.1.15 岗位职称表 (`positions`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 主键 |
| name | VARCHAR(100) | NOT NULL | 岗位职称名称 |
| code | VARCHAR(50) | NULL, UNIQUE | 岗位编码 |
| level | INT | NULL | 职级等级 |
| description | TEXT | NULL | 岗位描述 |

#### 5.1.16 用户组织关系表 (`user_organizations`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 主键 |
| user_id | BIGINT | NOT NULL, UNIQUE | 用户ID |
| region_id | BIGINT | NULL, INDEX | 所属区域ID |
| department_id | BIGINT | NULL, INDEX | 所属部门ID |
| position_id | BIGINT | NULL, INDEX | 岗位职称ID |
| system_role | VARCHAR(50) | NOT NULL | 系统角色（关联 2.1 角色定义） |
| manager_id | BIGINT | NULL | 直属上级ID |
| effective_date | DATE | NOT NULL | 生效日期（支持历史追溯） |

---

## 6. 非功能需求

### 6.1 性能要求

| 指标 | 要求 |
|------|------|
| 列表加载 | 1000条以内数据加载时间 < 1s |
| 搜索响应 | 搜索结果返回时间 < 500ms |
| 并发用户 | 支持 500+ 并发用户 |
| 数据量 | 单项目支持 10万+ 需求记录 |
| 小程序端列表加载 | 移动端列表加载时间 < 500ms（移动端网络环境） |
| 小程序端首屏加载 | 小程序首屏渲染时间 < 1.5s |

### 6.1.1 小程序端专项性能要求

### 6.2 安全要求

- RBAC 权限控制，接口级权限校验
- 数据隔离：项目级数据隔离
- 操作审计：关键操作留痕
- XSS/CSRF 防护
- SQL 注入防护
- **小程序端专项**：
  - 微信登录采用 `wx.login` + 临时 code 换 token，不暴露用户敏感信息
  - 小程序请求采用 HTTPS，Token 存放于 Storage 加密存储
  - 接口防重放攻击（timestamp + nonce 校验）
  - 小程序端敏感操作（删除需求、审批拒绝等）需二次确认

### 6.3 可用性要求

- 系统可用性 ≥ 99.9%
- 支持数据备份与恢复
- 软删除机制，支持数据恢复

### 6.4 集成能力

| 集成对象 | 集成方式 | 说明 |
|----------|----------|------|
| 企业微信 | Webhook / API | 消息通知、需求同步 |
| 飞书 | Webhook / API | 消息通知、文档关联 |
| 钉钉 | Webhook / API | 消息通知 |
| Git 平台 | Webhook | 代码提交关联需求 |
| CI/CD | Webhook / API | 构建状态关联需求 |

---

## 7. API 设计概要

### 7.1 需求 CRUD API

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/v1/requirements` | 获取需求列表（支持分页、过滤、排序） |
| GET | `/api/v1/requirements/:id` | 获取需求详情 |
| POST | `/api/v1/requirements` | 创建需求 |
| PUT | `/api/v1/requirements/:id` | 更新需求 |
| DELETE | `/api/v1/requirements/:id` | 删除需求（软删除） |
| POST | `/api/v1/requirements/:id/restore` | 恢复已删除需求 |
| GET | `/api/v1/requirements/:id/history` | 获取变更历史 |

### 7.2 工作流 API

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/v1/projects/:id/workflow/states` | 获取工作流状态列表 |
| GET | `/api/v1/projects/:id/workflow/transitions` | 获取流转规则 |
| POST | `/api/v1/requirements/:id/transition` | 执行状态流转 |
| GET | `/api/v1/projects/:id/workflow/versions` | 获取流程版本列表 |
| POST | `/api/v1/projects/:id/workflow/versions` | 创建流程版本（可视化编辑器保存） |
| PUT | `/api/v1/workflow/versions/:id` | 更新流程版本 |
| POST | `/api/v1/workflow/versions/:id/activate` | 启用指定版本 |
| GET | `/api/v1/workflow/versions/:id/nodes/:nodeId/permissions` | 获取节点权限配置 |
| PUT | `/api/v1/workflow/versions/:id/nodes/:nodeId/permissions` | 更新节点权限配置 |
| POST | `/api/v1/workflow/versions/:id/validate` | 校验流程完整性（孤立节点/死循环检测） |

### 7.3 评审 API

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/v1/requirements/:id/reviews` | 获取评审列表 |
| POST | `/api/v1/requirements/:id/reviews` | 创建评审 |
| PUT | `/api/v1/reviews/:id` | 更新评审意见 |
| POST | `/api/v1/requirements/:id/reviews/conclude` | 生成评审结论 |

### 7.4 迭代 API

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/v1/projects/:id/iterations` | 获取迭代列表 |
| POST | `/api/v1/projects/:id/iterations` | 创建迭代 |
| PUT | `/api/v1/iterations/:id` | 更新迭代 |
| POST | `/api/v1/iterations/:id/requirements` | 批量分配需求到迭代 |
| GET | `/api/v1/iterations/:id/burndown` | 获取燃尽数据 |

### 7.5 关联 API

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/v1/requirements/:id/relations` | 获取关联列表 |
| POST | `/api/v1/requirements/:id/relations` | 创建关联 |
| DELETE | `/api/v1/requirements/:id/relations/:relId` | 删除关联 |

### 7.6 统计 API

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/v1/projects/:id/stats/distribution` | 获取需求分布统计 |
| GET | `/api/v1/projects/:id/stats/duration` | 获取需求时长统计 |
| GET | `/api/v1/projects/:id/stats/burndown` | 获取燃烧图数据 |
| GET | `/api/v1/projects/:id/stats/cfd` | 获取累计流图数据 |

### 7.7 自定义字段 API

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/v1/projects/:id/custom-fields` | 获取自定义字段列表 |
| POST | `/api/v1/projects/:id/custom-fields` | 创建自定义字段 |
| PUT | `/api/v1/custom-fields/:id` | 更新自定义字段 |
| DELETE | `/api/v1/custom-fields/:id` | 删除自定义字段 |

### 7.8 用户与组织架构 API

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/v1/users` | 获取用户列表（支持按区域/部门/角色过滤） |
| GET | `/api/v1/users/:id` | 获取用户详情（含组织架构信息） |
| POST | `/api/v1/users` | 创建用户 |
| PUT | `/api/v1/users/:id` | 更新用户信息 |
| GET | `/api/v1/regions` | 获取区域树 |
| POST | `/api/v1/regions` | 创建区域 |
| PUT | `/api/v1/regions/:id` | 更新区域 |
| GET | `/api/v1/departments` | 获取部门树 |
| POST | `/api/v1/departments` | 创建部门 |
| PUT | `/api/v1/departments/:id` | 更新部门 |
| GET | `/api/v1/positions` | 获取岗位职称列表 |
| POST | `/api/v1/positions` | 创建岗位职称 |
| PUT | `/api/v1/user-organizations/:userId` | 更新用户组织关系 |
| GET | `/api/v1/users/:id/department-members` | 获取同部门成员 |
| GET | `/api/v1/users/:id/subordinates` | 获取下级成员列表 |

---

## 8. 技术选项与技术架构

### 8.1 前端技术栈

#### 8.1.1 Web端

| 层级 | 技术选型 | 版本 | 说明 |
|------|----------|------|------|
| 框架 | Vue 3 | ^3.4 | Composition API + `<script setup>` |
| 语言 | TypeScript | ^5.3 | 全量类型覆盖 |
| 构建工具 | Vite | ^5.x | 开发服务器 + 生产构建 |
| UI组件库 | Element Plus | ^2.x | 企业级组件库，与 Vue 3 深度适配 |
| 路由 | Vue Router | ^4.x | 前端路由，支持动态路由懒加载 |
| 状态管理 | Pinia | ^2.x | Vue 3 官方推荐状态管理 |
| HTTP 客户端 | Axios | ^1.x | 请求拦截、Token 自动刷新、错误重试 |
| 可视化图表 | ECharts | ^5.x | 燃尽图、累计流图、甘特图、仪表盘 |
| 流程图编辑器 | LogicFlow / X6 (AntV) | — | 可视化流程引擎画布 |
| 富文本编辑器 | TinyMCE / WangEditor | — | 需求描述、评审意见编辑 |
| 表格组件 | vxe-table | ^4.x | 虚拟滚动、大数据量表格 |
| CSS 预处理 | SCSS | — | 样式变量、Mixin |
| 代码规范 | ESLint + Prettier | — | 统一代码风格 |
| 包管理 | pnpm | ^9.x | 依赖管理 |

**项目目录结构**（`demand_frontend`）：

```
demand_frontend/
├── public/                 # 静态公共资源
├── src/
│   ├── api/                # API 请求模块（按业务模块拆分）
│   │   ├── modules/        # 按模块拆分的 API 定义
│   │   │   ├── requirement.ts
│   │   │   ├── workflow.ts
│   │   │   ├── iteration.ts
│   │   │   ├── review.ts
│   │   │   ├── user.ts
│   │   │   ├── project.ts
│   │   │   ├── relation.ts
│   │   │   ├── statistics.ts
│   │   │   ├── file.ts
│   │   │   └── notification.ts
│   │   ├── request.ts      # Axios 实例配置
│   │   └── index.ts        # API 统一导出
│   ├── assets/             # 静态资源（图片、图标、字体）
│   ├── components/         # 公共组件
│   │   ├── common/         # 通用组件
│   │   │   ├── AppButton/          # 权限按钮组件
│   │   │   ├── AppDialog/          # 统一弹窗组件
│   │   │   ├── AppSearch/          # 搜索组件
│   │   │   ├── AppPagination/      # 分页组件
│   │   │   ├── AppUpload/          # 文件上传组件
│   │   │   └── EmptyState/         # 空状态组件
│   │   ├── requirement/    # 需求相关组件
│   │   │   ├── RequirementCard/    # 需求卡片
│   │   │   ├── RequirementForm/    # 需求表单
│   │   │   ├── RequirementList/    # 需求列表
│   │   │   ├── RequirementDetail/  # 需求详情
│   │   │   └── RequirementFilter/  # 需求过滤器
│   │   ├── workflow/       # 流程引擎组件
│   │   │   ├── FlowCanvas/         # 流程画布
│   │   │   ├── FlowNode/           # 流程节点组件
│   │   │   ├── FlowEdge/           # 流程连线组件
│   │   │   ├── FlowToolbar/        # 流程工具栏
│   │   │   ├── NodeConfigPanel/    # 节点配置面板
│   │   │   └── PermissionConfig/   # 权限配置面板
│   │   ├── charts/         # 图表组件
│   │   │   ├── BurndownChart/      # 燃尽图
│   │   │   ├── CfdChart/           # 累计流图
│   │   │   ├── GanttChart/         # 甘特图
│   │   │   └── StatCard/           # 统计卡片
│   │   └── layout/         # 布局组件
│   │       ├── SideMenu/           # 侧边菜单
│   │       ├── Breadcrumb/         # 面包屑导航
│   │       └── Header/             # 顶部导航
│   ├── composables/        # 组合式函数（hooks）
│   │   ├── useAuth.ts            # 认证相关
│   │   ├── usePermission.ts      # 权限校验
│   │   ├── useWorkflow.ts        # 工作流操作
│   │   ├── useRequirement.ts     # 需求操作
│   │   ├── useTable.ts           # 表格通用逻辑
│   │   └── useNotification.ts    # 通知相关
│   ├── directives/         # 自定义指令
│   │   ├── permission.ts         # v-permission 权限指令
│   │   ├── button.ts             # v-button 按钮权限指令
│   │   └── loading.ts            # v-loading 加载指令
│   ├── layouts/            # 布局组件
│   │   ├── DefaultLayout.vue     # 主布局（侧边栏+内容区）
│   │   ├── BlankLayout.vue       # 空白布局（登录页等）
│   │   └── FullscreenLayout.vue  # 全屏布局（甘特图等）
│   ├── router/             # 路由配置
│   │   ├── index.ts              # 路由实例
│   │   ├── routes.ts             # 路由定义
│   │   └── guards.ts             # 路由守卫（权限拦截）
│   ├── stores/             # Pinia 状态管理
│   │   ├── modules/        # 按模块拆分的 store
│   │   │   ├── user.ts             # 用户状态
│   │   │   ├── requirement.ts      # 需求状态
│   │   │   ├── workflow.ts         # 工作流状态
│   │   │   ├── iteration.ts        # 迭代状态
│   │   │   ├── project.ts          # 项目状态
│   │   │   └── app.ts              # 全局状态（侧边栏、主题等）
│   │   └── index.ts              # store 统一导出
│   ├── styles/             # 全局样式
│   │   ├── variables.scss          # SCSS 变量
│   │   ├── mixins.scss             # SCSS 混入
│   │   ├── global.scss             # 全局样式
│   │   └── themes/                 # 主题样式
│   ├── types/              # TypeScript 类型定义
│   │   ├── api.d.ts                # API 响应类型
│   │   ├── requirement.d.ts        # 需求相关类型
│   │   ├── workflow.d.ts           # 工作流相关类型
│   │   ├── user.d.ts               # 用户相关类型
│   │   ├── iteration.d.ts          # 迭代相关类型
│   │   ├── review.d.ts             # 评审相关类型
│   │   ├── statistics.d.ts         # 统计相关类型
│   │   └── common.d.ts             # 通用类型
│   ├── utils/              # 工具函数
│   │   ├── auth.ts                 # Token 管理
│   │   ├── permission.ts           # 权限校验
│   │   ├── format.ts               # 数据格式化
│   │   ├── validate.ts             # 表单校验
│   │   ├── storage.ts              # 本地存储
│   │   └── date.ts                 # 日期工具
│   ├── views/              # 页面视图（按功能模块组织）
│   │   ├── login/                  # 登录页
│   │   │   ├── LoginPage.vue
│   │   │   └── components/
│   │   ├── dashboard/              # 仪表盘
│   │   │   ├── DashboardPage.vue
│   │   │   └── components/
│   │   ├── requirements/           # 需求管理
│   │   │   ├── RequirementListPage.vue
│   │   │   ├── RequirementDetailPage.vue
│   │   │   ├── RequirementCreatePage.vue
│   │   │   └── components/
│   │   ├── workflow/               # 工作流配置
│   │   │   ├── WorkflowConfigPage.vue
│   │   │   ├── FlowDesignerPage.vue
│   │   │   └── components/
│   │   ├── iterations/             # 迭代管理
│   │   │   ├── IterationListPage.vue
│   │   │   ├── IterationDetailPage.vue
│   │   │   └── components/
│   │   ├── reviews/                # 评审管理
│   │   │   ├── ReviewListPage.vue
│   │   │   ├── ReviewDetailPage.vue
│   │   │   └── components/
│   │   ├── statistics/             # 统计报表
│   │   │   ├── StatisticsPage.vue
│   │   │   └── components/
│   │   └── settings/               # 系统设置
│   │       ├── ProjectSettingsPage.vue
│   │       ├── UserManagePage.vue
│   │       ├── OrgManagePage.vue
│   │       └── components/
│   ├── App.vue
│   └── main.ts
├── .env                    # 环境变量
├── .env.development        # 开发环境变量
├── .env.production         # 生产环境变量
├── index.html
├── vite.config.ts          # Vite 配置
├── tsconfig.json           # TypeScript 配置
├── package.json
├── pnpm-lock.yaml
└── README.md
```

**前端目录设计规范**：

| 目录 | 职责 | 规范 |
|------|------|------|
| `api/modules/` | 按业务模块拆分 API 请求函数 | 一个模块一个文件，统一返回 `Promise<Result<T>>` |
| `components/` | 公共组件，跨页面复用 | 组件名以 `App` 或业务前缀开头，独立目录，包含 `index.vue` + 子组件 |
| `composables/` | 组合式函数（hooks） | 以 `use` 开头，纯逻辑，不包含 UI |
| `directives/` | 自定义指令 | 权限指令（`v-permission`）、按钮指令（`v-button`） |
| `stores/modules/` | 按业务模块拆分 Pinia store | 一个模块一个文件，避免单文件臃肿 |
| `types/` | TypeScript 类型定义 | 与后端 DTO 对应，由 OpenAPI 工具生成 + 手动补充 |
| `views/` | 页面视图 | 一个页面一个目录，页面内私有组件放 `components/` 子目录 |
| `utils/` | 纯工具函数 | 不依赖 Vue 实例，可独立测试 |

#### 8.1.2 小程序端

| 层级 | 技术选型 | 版本 | 说明 |
|------|----------|------|------|
| 框架 | uni-app (Vue 3) | ^3.x | 一套代码多端发布（微信/支付宝/钉钉） |
| 语言 | TypeScript | ^5.3 | — |
| UI 组件库 | uView Plus / uni-ui | — | 移动端组件库 |
| HTTP 客户端 | uni.request 封装 | — | 统一请求拦截器 |
| 状态管理 | Pinia | ^2.x | 与 Web 端统一状态管理方案 |
| 富文本 | mp-html | — | 小程序端渲染富文本内容 |
| 扫码 | uni.scanCode | — | 微信原生扫码 API |
| 语音输入 | 微信同声传译插件 | — | 语音转文字 |
| 订阅消息 | uni.requestSubscribeMessage | — | 微信订阅消息推送 |
| 本地存储 | uni.setStorage (AES加密) | — | 离线草稿、Token 存储 |

**小程序端项目目录结构**（`demand_frontend/src-mini`）：

```
demand_frontend/src-mini/
├── pages/                # 页面
│   ├── index/            # 首页（待办 + 统计概览）
│   │   └── index.vue
│   ├── requirements/     # 需求管理
│   │   ├── list.vue      # 需求列表
│   │   ├── detail.vue    # 需求详情
│   │   └── create.vue    # 创建需求
│   ├── approval/         # 审批中心
│   │   ├── list.vue      # 审批列表
│   │   └── detail.vue    # 审批详情
│   ├── message/          # 消息中心
│   │   └── list.vue
│   ├── iteration/        # 迭代查看
│   │   ├── list.vue
│   │   └── detail.vue
│   └── mine/             # 个人中心
│       └── index.vue
├── components/           # 小程序专用组件
│   ├── ReqCard/          # 需求卡片
│   ├── ApprovalBar/      # 审批操作栏
│   └── StatOverview/     # 统计概览
├── api/                  # API 请求模块（与 Web 端共用类型）
│   ├── request.ts        # uni.request 封装
│   └── modules/          # 按模块拆分
├── stores/               # Pinia 状态
│   ├── modules/
│   └── index.ts
├── utils/                # 工具函数
│   ├── auth.ts
│   ├── format.ts
│   └── storage.ts        # 加密本地存储
├── types/                # 类型定义（与 Web 端共用）
├── static/               # 静态资源
├── uni_modules/          # uni-app 插件
├── App.vue
├── main.ts
├── pages.json            # 页面配置
├── manifest.json         # 应用配置
└── uni.scss              # 全局样式
```

#### 8.1.3 共享代码包

| 共享内容 | 实现方式 |
|----------|----------|
| API 类型定义 | 独立 `packages/types` 包，Web 端与小程序端共用 |
| 业务工具函数 | 独立 `packages/utils` 包，数据格式化、权限校验等 |
| 接口契约 | OpenAPI/Swagger 自动生成 TypeScript 类型 |

#### 8.1.4 项目根目录结构（Monorepo）

```
demand_system/                          # 项目根目录
├── demand_frontend/                    # 前端项目（Web端 + 小程序端）
│   ├── public/
│   ├── src/                            # Web 端源码
│   ├── src-mini/                       # 小程序端源码
│   ├── packages/                       # 共享包
│   │   ├── types/                      # 共享类型定义
│   │   └── utils/                      # 共享工具函数
│   ├── vite.config.ts
│   └── package.json
├── demand_backend/                     # 后端项目
│   ├── src/
│   ├── resources/
│   └── pom.xml
├── docs/                               # 项目文档
│   └── PRD-需求管理系统.md
├── scripts/                            # 构建/部署脚本
│   ├── build-frontend.sh
│   ├── build-backend.sh
│   ├── deploy.sh
│   └── docker-compose.yml
├── database/                           # 数据库脚本
│   ├── init.sql
│   └── migration/
└── README.md
```

### 8.2 后端技术栈

| 层级 | 技术选型 | 版本 | 说明 |
|------|----------|------|------|
| 语言 | Java | 17 (LTS) | 长期支持版本 |
| 框架 | Spring Boot | ^3.2 | 快速构建 RESTful API |
| 安全认证 | Spring Security + JWT | — | Token 认证、权限拦截 |
| ORM | MyBatis-Plus | ^3.5 | 简化 CRUD、分页、逻辑删除 |
| 数据库 | MySQL | 8.0 | 主库，InnoDB 引擎 |
| 缓存 | Redis | 7.x | 会话缓存、热点数据、分布式锁 |
| 消息队列 | RabbitMQ / RocketMQ | — | 异步通知、自动化流转触发 |
| 搜索引擎 | Elasticsearch | 8.x | 需求全文检索、高级搜索 |
| 对象存储 | MinIO / 阿里云 OSS | — | 附件文件存储 |
| 文档预览 | OnlyOffice / LibreOffice | — | Word/Excel 在线预览 |
| 工作流引擎 | 自研（基于状态机） | — | 可视化流程引擎后端 |
| API 文档 | SpringDoc (OpenAPI 3) | — | 自动生成 API 文档 |
| 日志 | Logback + ELK | — | 集中式日志管理 |
| 定时任务 | XXL-JOB | — | 分布式定时任务（超时通知等） |
| 构建工具 | Maven | ^3.9 | 依赖管理与构建 |

**后端项目目录结构**（`demand_backend`）：

```
demand_backend/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/com/demand/system/
    │   │   ├── DemandSystemApplication.java  # 启动类
    │   │   │
    │   │   ├── common/                       # 公共模块
    │   │   │   ├── config/                   # 配置类
    │   │   │   │   ├── SecurityConfig.java   # Spring Security 配置
    │   │   │   │   ├── MyBatisPlusConfig.java
    │   │   │   │   ├── RedisConfig.java
    │   │   │   │   ├── WebMvcConfig.java
    │   │   │   │   ├── OpenApiConfig.java    # Swagger 配置
    │   │   │   │   ├── MinioConfig.java
    │   │   │   │   └── MqConfig.java
    │   │   │   ├── constant/                 # 常量定义
    │   │   │   │   ├── ApiConstants.java
    │   │   │   │   ├── RedisConstants.java
    │   │   │   │   ├── WorkflowConstants.java
    │   │   │   │   └── PermissionConstants.java
    │   │   │   ├── enums/                    # 枚举定义
    │   │   │   │   ├── RequirementStatus.java
    │   │   │   │   ├── RequirementType.java
    │   │   │   │   ├── Priority.java
    │   │   │   │   ├── ReviewResult.java
    │   │   │   │   ├── IterationStatus.java
    │   │   │   │   └── WorkflowNodeType.java
    │   │   │   ├── exception/                # 异常处理
    │   │   │   │   ├── BusinessException.java
    │   │   │   │   ├── WorkflowException.java
    │   │   │   │   ├── PermissionException.java
    │   │   │   │   └── GlobalExceptionHandler.java  # 全局异常拦截
    │   │   │   ├── result/                   # 统一响应
    │   │   │   │   ├── Result.java
    │   │   │   │   ├── PageResult.java
    │   │   │   │   └── ErrorCode.java
    │   │   │   └── utils/                    # 通用工具
    │   │   │       ├── JwtUtils.java         # JWT 工具
    │   │   │       ├── DateUtils.java
    │   │   │       ├── FileUtils.java
    │   │   │       ├── AesUtils.java
    │   │   │       └── ExpressionParser.java  # 条件表达式解析
    │   │   │
    │   │   ├── module/                       # 业务模块（按模块分包）
    │   │   │   │
    │   │   │   ├── auth/                     # 认证授权模块
    │   │   │   │   ├── controller/
    │   │   │   │   │   └── AuthController.java
    │   │   │   │   ├── service/
    │   │   │   │   │   ├── AuthService.java
    │   │   │   │   │   └── impl/AuthServiceImpl.java
    │   │   │   │   ├── dto/
    │   │   │   │   │   ├── LoginRequest.java
    │   │   │   │   │   ├── WxLoginRequest.java
    │   │   │   │   │   └── TokenResponse.java
    │   │   │   │   └── security/
    │   │   │   │       ├── JwtAuthenticationFilter.java
    │   │   │   │       └── WxLoginProvider.java
    │   │   │   │
    │   │   │   ├── user/                     # 用户与组织架构模块
    │   │   │   │   ├── controller/
    │   │   │   │   │   ├── UserController.java
    │   │   │   │   │   ├── RegionController.java
    │   │   │   │   │   ├── DepartmentController.java
    │   │   │   │   │   └── PositionController.java
    │   │   │   │   ├── service/
    │   │   │   │   ├── mapper/
    │   │   │   │   ├── entity/
    │   │   │   │   └── dto/
    │   │   │   │
    │   │   │   ├── project/                  # 项目管理模块
    │   │   │   │   ├── controller/
    │   │   │   │   ├── service/
    │   │   │   │   ├── mapper/
    │   │   │   │   ├── entity/
    │   │   │   │   └── dto/
    │   │   │   │
    │   │   │   ├── requirement/              # 需求管理模块
    │   │   │   │   ├── controller/
    │   │   │   │   │   ├── RequirementController.java
    │   │   │   │   │   ├── RequirementHistoryController.java
    │   │   │   │   │   └── RequirementImportExportController.java
    │   │   │   │   ├── service/
    │   │   │   │   │   ├── RequirementService.java
    │   │   │   │   │   ├── RequirementHistoryService.java
    │   │   │   │   │   └── impl/
    │   │   │   │   ├── mapper/
    │   │   │   │   │   ├── RequirementMapper.java
    │   │   │   │   │   └── RequirementHistoryMapper.java
    │   │   │   │   ├── entity/
    │   │   │   │   │   ├── Requirement.java
    │   │   │   │   │   ├── RequirementHistory.java
    │   │   │   │   │   └── CustomFieldValue.java
    │   │   │   │   ├── dto/
    │   │   │   │   │   ├── RequirementCreateDTO.java
    │   │   │   │   │   ├── RequirementUpdateDTO.java
    │   │   │   │   │   └── RequirementQueryDTO.java
    │   │   │   │   └── converter/
    │   │   │   │       └── RequirementConverter.java
    │   │   │   │
    │   │   │   ├── workflow/                 # 工作流引擎模块
    │   │   │   │   ├── engine/               # 流程引擎核心
    │   │   │   │   │   ├── WorkflowEngine.java        # 引擎入口
    │   │   │   │   │   ├── StateMachine.java          # 状态机
    │   │   │   │   │   ├── PermissionEngine.java      # 权限引擎
    │   │   │   │   │   ├── RuleEngine.java            # 规则引擎
    │   │   │   │   │   ├── TransitionExecutor.java    # 流转执行器
    │   │   │   │   │   ├── ConditionEvaluator.java    # 条件求值器
    │   │   │   │   │   └── Validator/
    │   │   │   │   │       └── WorkflowValidator.java  # 流程完整性校验
    │   │   │   │   ├── controller/
    │   │   │   │   │   ├── WorkflowConfigController.java
    │   │   │   │   │   ├── WorkflowTransitionController.java
    │   │   │   │   │   └── WorkflowVersionController.java
    │   │   │   │   ├── service/
    │   │   │   │   ├── mapper/
    │   │   │   │   ├── entity/
    │   │   │   │   │   ├── WorkflowVersion.java
    │   │   │   │   │   ├── WorkflowState.java
    │   │   │   │   │   ├── WorkflowTransition.java
    │   │   │   │   │   ├── WorkflowNodePermission.java
    │   │   │   │   │   └── WorkflowTransitionRecord.java
    │   │   │   │   └── dto/
    │   │   │   │       ├── WorkflowDefinitionDTO.java
    │   │   │   │       ├── NodeConfigDTO.java
    │   │   │   │       ├── TransitionRequest.java
    │   │   │   │       └── TransitionResponse.java
    │   │   │   │
    │   │   │   ├── iteration/                # 迭代管理模块
    │   │   │   │   ├── controller/
    │   │   │   │   ├── service/
    │   │   │   │   ├── mapper/
    │   │   │   │   ├── entity/
    │   │   │   │   └── dto/
    │   │   │   │
    │   │   │   ├── review/                   # 评审管理模块
    │   │   │   │   ├── controller/
    │   │   │   │   ├── service/
    │   │   │   │   ├── mapper/
    │   │   │   │   ├── entity/
    │   │   │   │   └── dto/
    │   │   │   │
    │   │   │   ├── relation/                 # 需求关联模块
    │   │   │   │   ├── controller/
    │   │   │   │   ├── service/
    │   │   │   │   ├── mapper/
    │   │   │   │   ├── entity/
    │   │   │   │   └── dto/
    │   │   │   │
    │   │   │   ├── statistics/               # 统计报表模块
    │   │   │   │   ├── controller/
    │   │   │   │   ├── service/
    │   │   │   │   ├── mapper/
    │   │   │   │   └── dto/
    │   │   │   │
    │   │   │   ├── notification/             # 通知中心模块
    │   │   │   │   ├── controller/
    │   │   │   │   ├── service/
    │   │   │   │   ├── consumer/             # MQ 消费者
    │   │   │   │   ├── sender/               # 消息发送器
    │   │   │   │   └── template/             # 消息模板
    │   │   │   │
    │   │   │   └── file/                     # 文件服务模块
    │   │   │       ├── controller/
    │   │   │       ├── service/
    │   │   │       └── storage/              # 存储策略（MinIO/OSS）
    │   │   │
    │   │   └── scheduler/                    # 定时任务
    │   │       ├── TimeoutNotifyTask.java    # 超时通知任务
    │   │       ├── ReportSnapshotTask.java   # 报表快照任务
    │   │       └── DataArchiveTask.java      # 数据归档任务
    │   │
    │   └── resources/
    │       ├── application.yml               # 主配置文件
    │       ├── application-dev.yml           # 开发环境配置
    │       ├── application-prod.yml          # 生产环境配置
    │       ├── mapper/                       # MyBatis XML 映射文件
    │       │   ├── RequirementMapper.xml
    │       │   ├── WorkflowMapper.xml
    │       │   └── ...
    │       ├── db/
    │       │   └── migration/                # 数据库迁移脚本（Flyway）
    │       └── logback-spring.xml            # 日志配置
    │
    └── test/
        └── java/com/demand/system/
            ├── common/
            ├── module/
            │   ├── auth/
            │   ├── requirement/
            │   └── workflow/
            └── integration/
```

**后端模块内部统一结构**：每个业务模块（`auth`、`requirement`、`workflow` 等）遵循以下统一目录规范：

```
模块名/
├── controller/     # REST API 控制器（仅做参数校验、调用 service、返回结果）
├── service/        # 业务接口定义
│   └── impl/       # 业务实现
├── mapper/         # MyBatis Mapper 接口
├── entity/         # 数据库实体类（对应表结构）
├── dto/            # 数据传输对象（请求参数、响应数据）
└── converter/      # DTO ↔ Entity 转换器（可选，仅在此模块需要时创建）
```

**规范说明**：
- `controller` 禁止编写业务逻辑，仅做参数校验与 service 调用
- `service/impl` 承载全部业务逻辑
- `mapper` 对应 MyBatis 接口，XML 放在 `resources/mapper/` 下
- `dto` 按操作拆分（如 `RequirementCreateDTO`、`RequirementUpdateDTO`、`RequirementQueryDTO`），不使用万能 Map
- 跨模块调用通过 `service` 层接口，不直接调用 `mapper`

### 8.3 系统架构

#### 8.3.1 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                        客户端层                               │
│  ┌──────────────────┐              ┌──────────────────────┐  │
│  │   Web端 (Vue3)   │              │  小程序端 (uni-app)  │  │
│  │  Element Plus    │              │  uView Plus          │  │
│  └────────┬─────────┘              └──────────┬───────────┘  │
│           │                                    │              │
│           └────────────────┬───────────────────┘              │
│                            │ HTTPS / RESTful API              │
├────────────────────────────┼─────────────────────────────────┤
│                        网关层                                  │
│              ┌─────────────┴─────────────┐                    │
│              │   Nginx / Spring Cloud    │                    │
│              │   Gateway (可选)          │                    │
│              │  - 路由转发                │                    │
│              │  - 负载均衡                │                    │
│              │  - 限流熔断                │                    │
│              │  - HTTPS 终止              │                    │
│              └─────────────┬─────────────┘                    │
├────────────────────────────┼─────────────────────────────────┤
│                        服务层                                  │
│              ┌─────────────┴─────────────┐                    │
│              │   Spring Boot 应用         │                    │
│              │  ┌─────────────────────┐  │                    │
│              │  │  Spring Security    │  │                    │
│              │  │  (JWT认证 + RBAC)   │  │                    │
│              │  └─────────────────────┘  │                    │
│              │  ┌─────────────────────┐  │                    │
│              │  │  业务服务层           │  │                    │
│              │  │  (需求/工作流/迭代等) │  │                    │
│              │  └─────────────────────┘  │                    │
│              │  ┌─────────────────────┐  │                    │
│              │  │  流程引擎核心         │  │                    │
│              │  │  (状态机 + 规则引擎)  │  │                    │
│              │  └─────────────────────┘  │                    │
│              │  ┌─────────────────────┐  │                    │
│              │  │  通知服务             │  │                    │
│              │  │  (MQ + Webhook)      │  │                    │
│              │  └─────────────────────┘  │                    │
│              └─────────────┬─────────────┘                    │
├────────────────────────────┼─────────────────────────────────┤
│                        数据层                                  │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐  │
│  │  MySQL   │  │  Redis   │  │   ES     │  │  MinIO/OSS   │  │
│  │  主数据库 │  │  缓存    │  │  全文检索│  │  对象存储     │  │
│  └──────────┘  └──────────┘  └──────────┘  └──────────────┘  │
├──────────────────────────────────────────────────────────────┤
│                        外部集成层                              │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐  │
│  │ 企业微信  │  │   飞书   │  │   钉钉   │  │   Git/CI-CD  │  │
│  └──────────┘  └──────────┘  └──────────┘  └──────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

#### 8.3.2 关键架构设计决策

| 决策点 | 方案 | 理由 |
|--------|------|------|
| 工作流引擎 | 自研状态机 | 需要深度定制节点权限，开源引擎（如 Flowable）学习成本高且不易深度集成 |
| 前后端分离 | 完全分离 | Web 端与小程序端共用 API，便于后续扩展 |
| 数据库选型 | MySQL 8.0 | 团队熟悉、生态成熟、JSON 字段支持自定义字段存储 |
| 全文检索 | Elasticsearch | 需求标题/描述/评论全文搜索、高亮显示、模糊匹配 |
| 缓存策略 | Redis | 会话管理、热点需求缓存、分布式锁（防并发流转） |
| 文件存储 | MinIO（私有部署）/ OSS（云部署） | 兼容 S3 协议，可按部署环境切换 |
| 小程序框架 | uni-app | 一套代码多端发布，Vue 3 技术栈统一 |
| API 风格 | RESTful + OpenAPI 3 规范 | 标准化、自动生成类型定义、便于前端联调 |

#### 8.3.3 认证与鉴权架构

```
┌─────────┐          ┌──────────────┐          ┌──────────────┐
│ Web端    │          │  小程序端     │          │  第三方系统   │
└────┬────┘          └──────┬───────┘          └──────┬───────┘
     │                      │                         │
     │  POST /auth/login    │  POST /auth/wx-login    │  API Key
     │  {username,password} │  {code}                 │
     ▼                      ▼                         ▼
┌────────────────────────────────────────────────────────────┐
│                    Spring Security                         │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  AuthenticationFilter                                │  │
│  │  - Web端：用户名密码 → JWT                           │  │
│  │  - 小程序端：wx.code → 微信API → 用户信息 → JWT      │  │
│  │  - 第三方：API Key → 权限校验                        │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  AuthorizationFilter (RBAC)                          │  │
│  │  - 接口级权限校验（@PreAuthorize）                     │  │
│  │  - 数据权限校验（同部门/同区域/项目隔离）                │  │
│  │  - 工作流节点权限校验（节点级字段/按钮权限）             │  │
│  └──────────────────────────────────────────────────────┘  │
│                           │                                 │
│                           ▼                                 │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  JWT Token (有效期 2h，Refresh Token 7d)             │  │
│  │  Payload: userId, roles, regionId, deptId, position  │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────┘
```

#### 8.3.4 工作流引擎架构

```
┌───────────────────────────────────────────────────────┐
│                    可视化流程编辑器                      │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  │
│  │ 拖拽画布 │→│ 节点配置 │→│ 连线配置 │→│ 权限配置 │  │
│  └─────────┘  └─────────┘  └─────────┘  └─────────┘  │
│                        │                               │
│                        ▼                               │
│              ┌───────────────────┐                      │
│              │  流程定义 (JSON)   │                      │
│              │  { nodes, edges,  │                      │
│              │    permissions }  │                      │
│              └────────┬──────────┘                      │
│                       │ 保存                            │
└───────────────────────┼───────────────────────────────┘
                        ▼
┌───────────────────────────────────────────────────────┐
│                    流程引擎核心                         │
│  ┌─────────────────────────────────────────────────┐  │
│  │  StateMachine (状态机)                           │  │
│  │  - 加载流程定义 JSON                             │  │
│  │  - 校验流转合法性（from → to 是否存在）            │  │
│  │  - 执行前置条件检查                               │  │
│  │  - 触发流转后动作（通知、自动操作）                 │  │
│  └─────────────────────────────────────────────────┘  │
│  ┌─────────────────────────────────────────────────┐  │
│  │  PermissionEngine (权限引擎)                      │  │
│  │  - 加载节点权限配置                               │  │
│  │  - 校验操作权限（角色/用户/动态指定人）              │  │
│  │  - 校验字段权限（可见/可编辑/必填）                 │  │
│  │  - 校验按钮权限（显示条件）                         │  │
│  └─────────────────────────────────────────────────┘  │
│  ┌─────────────────────────────────────────────────┐  │
│  │  RuleEngine (规则引擎)                            │  │
│  │  - 解析流转条件表达式                              │  │
│  │  - 执行自动化规则（触发条件 → 执行动作）             │  │
│  │  - 集成消息队列异步执行                           │  │
│  └─────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────┘
```

### 8.4 部署架构

#### 8.4.1 开发环境

| 组件 | 部署方式 | 说明 |
|------|----------|------|
| 前端 | Vite Dev Server | 热更新开发 |
| 后端 | Spring Boot 本地启动 | — |
| MySQL | Docker Compose | — |
| Redis | Docker Compose | — |

#### 8.4.2 生产环境

| 组件 | 部署方式 | 说明 |
|------|----------|------|
| 前端 | Nginx 静态托管 | CDN 加速 |
| 小程序端 | 微信开发者工具提交审核 | 微信服务器托管 |
| 后端 | Docker + Kubernetes / 阿里云 ECS | 容器化部署 |
| MySQL | 主从复制 | 阿里云 RDS / 自建 |
| Redis | 集群模式 | 阿里云 Redis / 自建 |
| Elasticsearch | 集群模式 | 3节点起步 |
| MinIO | 分布式集群 | 4节点起步 |
| Nginx | 反向代理 + 负载均衡 | SSL 证书管理 |

---

## 9. 实施路线图

### 阶段一：MVP（最小可用版本）- 8周

| 周次 | Web端交付内容 | 小程序端交付内容 |
|------|---------------|-----------------|
| 第1-2周 | 项目搭建、用户认证、项目/成员管理 | — |
| 第3-4周 | 需求 CRUD、基本字段、需求列表/详情 | — |
| 第5周 | 父子需求拆解、需求分类 | — |
| 第6周 | 固定工作流（不可配置）、状态流转 | 小程序项目初始化、登录授权 |
| 第7周 | 迭代管理、需求分配 | 小程序需求列表、详情查看 |
| 第8周 | 基础统计报表、Bug 修复 | 小程序快速创建需求、消息通知 |

### 阶段二：核心功能完善 - 8周

| 周次 | 交付内容 |
|------|----------|
| 周次 | Web端交付内容 | 小程序端交付内容 |
|------|---------------|-----------------|
| 第9-10周 | 可视化工作流引擎（拖拽画布、节点配置、版本管理） | 小程序需求编辑（基础字段）、状态流转操作 |
| 第11-12周 | 节点级精细化权限控制（字段/按钮/流转/通知/数据权限） | 小程序审批处理（通过/拒绝/转交/意见填写） |
| 第11-12周 | 自定义字段系统 | 小程序迭代查看 |
| 第13周 | 需求评审模块 | 小程序统计概览（个人待办、项目进度） |
| 第14周 | 需求关联系统 | 小程序扫码关联需求 |
| 第15周 | 看板/故事墙视图 | 小程序离线草稿功能 |
| 第16周 | 燃尽图、累计流图 | 双端联调、小程序性能优化 |

### 阶段三：高级功能 - 6周

| 第26周 | 灰度发布、正式上线 |

| 周次 | Web端交付内容 | 小程序端交付内容 |
|------|---------------|-----------------|
| 第17-18周 | 甘特图视图 | 小程序需求评论与 @提醒 |
| 第19周 | 需求模板与批量导入导出 | — |
| 第20周 | 自定义仪表盘 | 小程序自定义仪表盘（简化版） |
| 第21周 | 第三方集成（企微/飞书/钉钉） | 小程序订阅消息模板配置 |
| 第22周 | 自动化流转规则 | 小程序语音输入创建需求 |

### 阶段四：优化与扩展 - 4周

| 周次 | 交付内容 |
|------|----------|
| 第23-24周 | 性能优化、大数据量处理、小程序端专项性能调优 |
| 第25周 | API 开放平台 |
| 第26周 | 灰度发布、正式上线（Web端 + 小程序端同步发布） |

**总计：26周（约6个月）**
