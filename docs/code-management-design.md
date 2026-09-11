# 代码管理系统 — 功能设计说明书

> 版本: v1.0  
> 日期: 2026-08-09  
> 作者: UI Designer / 系统架构  
> 参考: GitHub Branch Protection、GitLab Merge Request Approvals、Gitee 保护分支、CNB 权限模型

---

## 目录

1. [概述与目标](#1-概述与目标)
2. [竞品功能矩阵分析](#2-竞品功能矩阵分析)
3. [功能模块设计](#3-功能模块设计)
4. [权限模型设计](#4-权限模型设计)
5. [数据库设计](#5-数据库设计)
6. [API 设计](#6-api-设计)
7. [前端页面设计](#7-前端页面设计)
8. [实施路线图](#8-实施路线图)

---

## 1. 概述与目标

### 1.1 项目背景

当前系统已具备项目管理、需求管理、工作流配置、多维表格等能力，但缺少代码资产的管理能力。团队需要在系统内完成代码仓库的配置管理、分支保护策略、合并审批流程等精细化管理操作，将"需求→开发→代码评审→合并"形成完整闭环。

### 1.2 设计目标

| 目标 | 说明 |
|------|------|
| Git 服务对接 | 支持对接 GitLab / GitHub / Gitee / Gitea 等主流 Git 平台，通过 API Token/SSH Key 连接 |
| 仓库生命周期管理 | 创建、导入、归档、删除仓库，绑定项目 |
| 分支保护策略 | 定义分支匹配规则 + 推送限制 + 合并审批要求 |
| 合并请求管理 | 创建 MR/PR + 审批流程 + 状态检查 + 自动合并 |
| 细粒度权限 | 基于 RBAC 的仓库级、分支级权限分配 |
| 操作审计 | 全链路操作日志，可追溯到人、IP、时间 |

---

## 2. 竞品功能矩阵分析

下表对比 GitHub / GitLab / Gitee / CNB 在代码精细化管理上的核心能力:

| 功能维度 | GitHub Enterprise | GitLab EE | Gitee 企业版 | 本项目目标 |
|----------|:--:|:--:|:--:|:--:|
| **分支保护** |
| 禁止强制推送 | ✅ | ✅ | ✅ | ✅ |
| 禁止删除分支 | ✅ | ✅ | ✅ | ✅ |
| 要求 PR 才能合并 | ✅ | ✅ | ✅ | ✅ |
| 通配符分支匹配 (`release/*`) | ✅ | ✅ | ✅ | ✅ |
| 规则集 (跨仓库复用) | ✅ Rulesets | ❌ 项目级 | ❌ 项目级 | ✅ |
| **代码评审** |
| 最小审批人数 | ✅ | ✅ | ✅ | ✅ |
| CODEOWNERS 审批 | ✅ | ✅ | ✅ | ✅ |
| 驳回过期审批 | ✅ | ✅ | ✅ | ✅ |
| 禁止作者自批 | ✅ | ✅ | ✅ | ✅ |
| 讨论串全部解决 | ✅ | ✅ | ✅ | ✅ |
| **状态检查** |
| CI 通过才能合并 | ✅ | ✅ | ✅ | ✅ |
| 分支更新前合并 | ✅ | ✅ | ✅ | ✅ |
| 自定义状态检查 | ✅ API | ✅ API | ✅ | ✅ |
| **权限模型** |
| RBAC 角色 (Owner/Maintainer/Developer/Reporter) | ✅ | ✅ | ✅ | ✅ |
| 仓库级权限 | ✅ | ✅ | ✅ | ✅ |
| 分支级白名单 | ✅ | ✅ | ✅ | ✅ |
| 组织级规则覆盖 | ✅ | ❌ | ❌ | ✅ |
| **审计** |
| 操作日志 | ✅ | ✅ | ✅ | ✅ |
| 五元组溯源 (人/IP/设备/时间/对象) | ✅ | ✅ | ❌ | ✅ |

---

## 3. 功能模块设计

### 3.1 功能架构总览

```
┌─────────────────────────────────────────────────────┐
│                    代码管理系统                       │
├─────────────┬─────────────┬─────────────┬─────────────┤
│  Git 配置   │  仓库管理   │  分支保护   │  合并审批   │
│  ────────  │  ────────  │  ────────  │  ────────  │
│  平台对接   │  仓库 CRUD  │  保护规则   │  MR/PR 创建 │
│  凭据管理   │  导入仓库   │  通配符匹配 │  审批人指派 │
│  SSH/Token  │  仓库绑定   │  推送控制   │  状态检查   │
│  连接测试   │  归档/删除  │  白名单     │  自动合并   │
├─────────────┼─────────────┼─────────────┼─────────────┤
│  权限管理   │  操作审计   │  统计分析   │  系统集成   │
│  ────────  │  ────────  │  ────────  │  ────────  │
│  RBAC 模型  │  操作日志   │  提交统计   │  需求关联   │
│  项目绑定   │  五元组    │  评审效率   │  工作流触发 │
│  仓库级权限 │  合规审计   │  代码活跃度 │  Webhook   │
└─────────────┴─────────────┴─────────────┴─────────────┘
```

---

### 3.2 Git 服务配置模块

#### 3.2.1 功能描述
提供对接外部 Git 托管平台的能力支持，管理服务地址、认证凭据。

#### 3.2.2 功能清单

| 功能 | 描述 |
|------|------|
| 添加 Git 服务 | 配置 GitLab/GitHub/Gitee/Gitea 等服务 URL + API 端点 |
| 凭据类型 | 支持 Personal Access Token、SSH Key、用户名+密码 三种方式 |
| 连接测试 | 保存前验证凭据是否有效，展示连接状态（绿色连通/红色失败/黄色过期） |
| 默认平台 | 标记一个平台为默认，创建仓库时自动使用 |
| 凭据加密 | 所有 Token/密码使用 AES-256 加密存储 |

#### 3.2.3 数据字段

| 字段 | 类型 | 说明 |
|------|------|------|
| name | varchar(100) | 平台名称（如"公司 GitLab"） |
| platform_type | enum | GITLAB / GITHUB / GITEE / GITEA |
| base_url | varchar(500) | API 基础地址 |
| auth_type | enum | TOKEN / SSH_KEY / PASSWORD |
| credential | text | 加密凭据 |
| is_default | tinyint | 默认平台 |
| status | enum | CONNECTED / DISCONNECTED / EXPIRED |
| last_checked_at | datetime | 最近验证时间 |

---

### 3.3 仓库管理模块

#### 3.3.1 功能描述
管理代码仓库全生命周期，支持创建、导入、绑定项目。

#### 3.3.2 功能清单

| 功能 | 描述 |
|------|------|
| 创建仓库 | 在目标 Git 服务上创建新仓库，同时写入本地元数据 |
| 导入仓库 | 从已有 Git 平台导入仓库信息（需凭据有访问权限） |
| 绑定项目 | 关联到系统内的项目，实现"代码→需求"可追溯 |
| 归档 | 归档仓库使其只读，禁止新提交 |
| 删除 | 删除本地元数据（可选同步删除远端仓库） |
| 查看详情 | 展示仓库基本信息、最近提交、分支列表、贡献者 |

#### 3.3.3 仓库详情页结构

```
┌─ 仓库详情 ────────────────────────────────────────────┐
│ [基本信息] [分支列表] [保护规则] [操作日志]            │
│                                                        │
│ 仓库名: backend-service    Git 服务: 公司 GitLab       │
│ 所属项目: 核心业务平台      默认分支: main              │
│ 最近活跃: 2026-08-09        提交数: 1,247               │
│ 克隆地址: git@gitlab.company.com:platform/backend.git  │
└────────────────────────────────────────────────────────┘
```

---

### 3.4 分支保护规则模块 ⭐核心

#### 3.4.1 功能描述
这是代码精细化管理的核心模块，允许管理员为不同分支定义差异化的保护策略。

#### 3.4.2 规则模型

每条保护规则包含三个层面：

| 层面 | 说明 |
|------|------|
| **匹配层** | 哪些分支被这条规则覆盖（支持通配符 `release/*`、`feature/*`） |
| **限制层** | 对这些分支施加什么限制 |
| **白名单层** | 谁可以绕过限制 |

#### 3.4.3 规则配置项详解

```
┌─ 分支保护规则 ──────────────────────────────────────────┐
│                                                          │
│ 规则名称: [主干分支保护            ]                     │
│ 分支模式: [main, master           ] ← 支持 fnmatch      │
│                                                          │
│ ── 推送控制 ──                                           │
│ [✓] 禁止直接推送 (强制走 MR/PR)                          │
│ [✓] 禁止强制推送 (git push -f)                           │
│ [✓] 禁止删除分支                                         │
│                                                          │
│ ── 合并审批 ──                                           │
│ [✓] 要求合并请求审批 → 最少 [2] 人                       │
│ [✓] 驳回过期审批 (新 commit 后旧审批失效)                │
│ [✓] 禁止作者自己批准                                     │
│ [ ] 要求 CODEOWNERS 审批                                 │
│ [✓] 要求讨论串全部解决                                   │
│                                                          │
│ ── 状态检查 ──                                           │
│ [✓] CI 流水线通过                                       │
│ [✓] 代码扫描通过 (SonarQube)                            │
│ [ ] 分支必须与目标分支同步                               │
│                                                          │
│ ── 白名单 (不受上述限制) ──                              │
│ 用户: [张三] [李四]                                      │
│ 角色: [技术负责人]                                       │
│                                                          │
│ [保存规则]                                               │
└──────────────────────────────────────────────────────────┘
```

#### 3.4.4 规则优先级

多个规则可能匹配同一分支，按以下优先级判定：

1. **精确匹配** > **通配符匹配**
2. 同一精度下，**先创建** > 后创建
3. **白名单** 优先级最高（白名单用户不受限制）

#### 3.4.5 规则集（跨仓库复用）

```
规则集「生产环境标准」
  ├── release/* 保护规则
  ├── hotfix/* 保护规则
  └── 关联仓库: backend-service, frontend-app, api-gateway

规则集「开发环境宽松」
  ├── develop 保护规则
  └── 关联仓库: (全部仓库)
```

---

### 3.5 合并请求管理模块

#### 3.5.1 功能描述
基于分支保护规则，强制执行代码审查流程。

#### 3.5.2 MR/PR 生命周期

```
创建 MR ──→ 指派审批人 ──→ CI 检查 ──→ 代码评审 ──→ 审批通过 ──→ 合并
              │                              │
              │ 审批人拒绝                    │ 冲突/CI 失败
              ↓                              ↓
          打回修改 ←──────────────────────  修复后重新提交
```

#### 3.5.3 功能清单

| 功能 | 描述 |
|------|------|
| 创建 MR | 选择源分支→目标分支，填写标题、描述、关联需求 |
| 指派审批人 | 自动推荐 CODEOWNERS + 手动添加 |
| 审批操作 | 批准 / 请求修改 / 评论 |
| 状态检查联动 | 实时显示 CI 流水线状态（pending/running/success/failed） |
| 冲突检测 | 源分支与目标分支冲突时提示解决 |
| 自动合并 | 所有条件满足时，自动执行 merge |
| Squash/Merge Commit | 支持两种合并策略 |
| 关联需求 | 将 MR 与系统内需求关联 |

---

### 3.6 操作审计模块

#### 3.6.1 审计五元组

参考金融级审计标准，每条日志记录：

| 维度 | 字段 | 示例 |
|------|------|------|
| 时间 | operated_at | 2026-08-09 14:30:00 |
| 操作人 | operator_name / operator_id | 张三 (ID: 42) |
| 来源IP | operator_ip | 192.168.1.100 |
| 操作设备 | user_agent | Chrome/128.0 Windows |
| 操作对象 | target_type + target_id | 仓库:backend-service |
| 操作类型 | action | branch.protect.create |
| 变更内容 | detail (JSON) | {"branch":"main","min_approvals":2} |

#### 3.6.2 审计查询

- 按时间范围筛选
- 按操作人筛选
- 按操作类型筛选
- 按仓库筛选
- CSV/Excel 导出

---

## 4. 权限模型设计

### 4.1 系统级角色

| 角色 | 权限范围 | 典型用户 |
|------|----------|----------|
| **系统管理员** | 管理所有仓库、规则集、审计日志 | 平台运维 |
| **项目管理员** | 管理绑定项目的仓库和保护规则 | 技术负责人 |
| **代码维护者** | 创建 MR、审批 MR、合并代码 | 高级工程师 |
| **开发者** | 创建分支、推送代码（非保护分支）、创建 MR | 普通开发 |
| **只读用户** | 查看代码、查看 MR | QA / PM |

### 4.2 仓库级权限矩阵

| 操作 | Owner | Maintainer | Developer | Reporter |
|------|:-----:|:----------:|:---------:|:--------:|
| 查看仓库 | ✅ | ✅ | ✅ | ✅ |
| 创建分支 | ✅ | ✅ | ✅ | ❌ |
| 推送到非保护分支 | ✅ | ✅ | ✅ | ❌ |
| 推送到保护分支 | ✅ | ✅(白名单) | ❌ | ❌ |
| 创建 MR | ✅ | ✅ | ✅ | ❌ |
| 审批 MR | ✅ | ✅ | ✅ | ❌ |
| 合并 MR | ✅(不受限) | ✅(满足规则) | ❌ | ❌ |
| 配置保护规则 | ✅ | ✅ | ❌ | ❌ |
| 删除仓库 | ✅ | ❌ | ❌ | ❌ |

### 4.3 分支级权限

通过保护规则中的白名单机制，可以：

- 给指定用户/角色开"绿灯"（绕过推送限制）
- 保护分支合并时，指定审批人（CODEOWNERS）

---

## 5. 数据库设计

### 5.1 ER 图（核心表）

```
┌───────────────────┐       ┌───────────────────┐
│  git_platforms     │       │  repositories      │
│───────────────────│       │───────────────────│
│ id (PK)           │──1:N──│ id (PK)           │
│ name              │       │ platform_id (FK)  │
│ platform_type     │       │ project_id (FK)   │
│ base_url          │       │ name              │
│ auth_type         │       │ full_path         │
│ credential (ENC)  │       │ default_branch    │
│ is_default        │       │ clone_url_ssh     │
│ status            │       │ clone_url_https   │
│ created_at        │       │ status            │
└───────────────────┘       │ created_by        │
                            │ created_at        │
                            └──────┬────────────┘
                                   │ 1:N
              ┌────────────────────┼────────────────────┐
              │                    │                    │
     ┌────────▼────────┐  ┌───────▼────────┐  ┌────────▼────────┐
     │ branch_protection│  │ merge_requests │  │ repo_permissions│
     │─────────────────│  │───────────────│  │─────────────────│
     │ id (PK)         │  │ id (PK)        │  │ id (PK)         │
     │ repo_id (FK)    │  │ repo_id (FK)   │  │ repo_id (FK)    │
     │ rule_name       │  │ source_branch  │  │ user_id / role  │
     │ branch_pattern  │  │ target_branch  │  │ permission_level│
     │ forbid_push     │  │ title          │  │ created_at      │
     │ forbid_force    │  │ description    │  └─────────────────┘
     │ require_mr      │  │ author_id      │
     │ min_approvals   │  │ status         │  ┌─────────────────┐
     │ dismiss_stale   │  │ merge_strategy │  │ audit_logs      │
     │ block_self_approve│ │ ci_status      │  │─────────────────│
     │ require_ci      │  │ requirement_id │  │ id (PK)         │
     │ ci_contexts     │  │ merged_at      │  │ operator_id     │
     │ whitelist_users │  │ created_at     │  │ operator_ip     │
     │ whitelist_roles │  └────────────────┘  │ user_agent      │
     │ rule_set_id(FK) │                      │ target_type     │
     │ created_at      │  ┌─────────────────┐ │ target_id       │
     └─────────────────┘  │ rule_sets        │ │ action          │
                          │─────────────────│ │ detail (JSON)   │
                          │ id (PK)         │ │ created_at      │
                          │ name            │ └─────────────────┘
                          │ description     │
                          │ created_at      │
                          └─────────────────┘
```

### 5.2 建表 SQL（核心表）

```sql
-- =====================================================
-- Git 平台配置表
-- =====================================================
DROP TABLE IF EXISTS `git_platforms`;
CREATE TABLE `git_platforms` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL COMMENT '平台名称',
  `platform_type` ENUM('GITLAB','GITHUB','GITEE','GITEA') NOT NULL,
  `base_url` VARCHAR(500) NOT NULL COMMENT 'API基础地址',
  `auth_type` ENUM('TOKEN','SSH_KEY','PASSWORD') NOT NULL DEFAULT 'TOKEN',
  `credential` TEXT NOT NULL COMMENT '加密凭据(AES-256)',
  `is_default` TINYINT DEFAULT 0,
  `status` ENUM('CONNECTED','DISCONNECTED','EXPIRED') DEFAULT 'DISCONNECTED',
  `last_checked_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_type` (`platform_type`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Git平台配置';

-- =====================================================
-- 代码仓库表
-- =====================================================
DROP TABLE IF EXISTS `repositories`;
CREATE TABLE `repositories` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `platform_id` BIGINT UNSIGNED NOT NULL COMMENT '所属Git平台',
  `project_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '绑定项目ID',
  `name` VARCHAR(200) NOT NULL COMMENT '仓库名',
  `full_path` VARCHAR(500) NOT NULL COMMENT '完整路径(含namespace)',
  `description` TEXT DEFAULT NULL,
  `default_branch` VARCHAR(100) DEFAULT 'main',
  `clone_url_ssh` VARCHAR(500) DEFAULT NULL,
  `clone_url_https` VARCHAR(500) DEFAULT NULL,
  `remote_id` VARCHAR(100) DEFAULT NULL COMMENT '远端仓库ID(gitlab project id)',
  `status` ENUM('ACTIVE','ARCHIVED','DELETED') DEFAULT 'ACTIVE',
  `created_by` INT UNSIGNED DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_platform` (`platform_id`),
  INDEX `idx_project` (`project_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码仓库';

-- =====================================================
-- 分支保护规则表 ⭐核心
-- =====================================================
DROP TABLE IF EXISTS `branch_protection_rules`;
CREATE TABLE `branch_protection_rules` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `repo_id` BIGINT UNSIGNED NOT NULL COMMENT '所属仓库ID',
  `rule_set_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '规则集ID(跨仓库复用)',
  `rule_name` VARCHAR(100) NOT NULL COMMENT '规则名称',
  `branch_pattern` VARCHAR(200) NOT NULL COMMENT '分支匹配模式(fnmatch)',
  `priority` INT DEFAULT 0 COMMENT '优先级(越大越优先)',

  -- 推送控制
  `forbid_push` TINYINT DEFAULT 1 COMMENT '禁止直接推送',
  `forbid_force_push` TINYINT DEFAULT 1 COMMENT '禁止强制推送',
  `forbid_delete` TINYINT DEFAULT 1 COMMENT '禁止删除分支',
  `require_mr` TINYINT DEFAULT 1 COMMENT '要求MR/PR',

  -- 合并审批
  `min_approvals` INT DEFAULT 1 COMMENT '最少审批人数',
  `dismiss_stale_approvals` TINYINT DEFAULT 1 COMMENT '驳回过期审批',
  `block_self_approve` TINYINT DEFAULT 1 COMMENT '禁止作者自批',
  `require_codeowner_approval` TINYINT DEFAULT 0 COMMENT 'CODEOWNERS审批',
  `require_thread_resolved` TINYINT DEFAULT 0 COMMENT '讨论串全部解决',

  -- 状态检查
  `require_ci_pass` TINYINT DEFAULT 0 COMMENT '要求CI通过',
  `require_up_to_date` TINYINT DEFAULT 0 COMMENT '分支同步',
  `ci_contexts` VARCHAR(500) DEFAULT NULL COMMENT 'CI检查项(逗号分隔)',

  -- 白名单
  `whitelist_users` TEXT DEFAULT NULL COMMENT '白名单用户ID(JSON数组)',
  `whitelist_roles` TEXT DEFAULT NULL COMMENT '白名单角色(JSON数组)',

  `enabled` TINYINT DEFAULT 1 COMMENT '是否启用',
  `created_by` INT UNSIGNED DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_repo` (`repo_id`),
  INDEX `idx_rule_set` (`rule_set_id`),
  INDEX `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分支保护规则';

-- =====================================================
-- 合并请求表
-- =====================================================
DROP TABLE IF EXISTS `merge_requests`;
CREATE TABLE `merge_requests` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `repo_id` BIGINT UNSIGNED NOT NULL,
  `source_branch` VARCHAR(200) NOT NULL,
  `target_branch` VARCHAR(200) NOT NULL,
  `title` VARCHAR(500) NOT NULL,
  `description` TEXT DEFAULT NULL,
  `author_id` INT UNSIGNED NOT NULL,
  `status` ENUM('OPEN','APPROVED','MERGED','CLOSED','CONFLICT') DEFAULT 'OPEN',
  `merge_strategy` ENUM('MERGE','SQUASH','REBASE') DEFAULT 'MERGE',
  `ci_status` ENUM('PENDING','RUNNING','SUCCESS','FAILED') DEFAULT NULL,
  `requirement_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联需求ID',
  `remote_mr_id` VARCHAR(50) DEFAULT NULL COMMENT '远端MR IID',
  `merged_by` INT UNSIGNED DEFAULT NULL,
  `merged_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_repo` (`repo_id`),
  INDEX `idx_author` (`author_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合并请求';

-- =====================================================
-- 规则集表(跨仓库复用)
-- =====================================================
DROP TABLE IF EXISTS `protection_rule_sets`;
CREATE TABLE `protection_rule_sets` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL,
  `description` TEXT DEFAULT NULL,
  `created_by` INT UNSIGNED DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保护规则集';
```

---

## 6. API 设计

### 6.1 API 概览

| 模块 | 方法 | 端点 | 说明 |
|------|------|------|------|
| **Git平台** | GET | `/api/v1/git/platforms` | 平台列表 |
| | POST | `/api/v1/git/platforms` | 添加平台 |
| | PUT | `/api/v1/git/platforms/{id}` | 编辑平台 |
| | POST | `/api/v1/git/platforms/{id}/test` | 测试连接 |
| | DELETE | `/api/v1/git/platforms/{id}` | 删除平台 |
| **仓库** | GET | `/api/v1/repositories` | 仓库列表(支持筛选) |
| | POST | `/api/v1/repositories` | 创建仓库 |
| | GET | `/api/v1/repositories/{id}` | 仓库详情 |
| | PUT | `/api/v1/repositories/{id}` | 编辑仓库 |
| | POST | `/api/v1/repositories/{id}/archive` | 归档仓库 |
| | DELETE | `/api/v1/repositories/{id}` | 删除仓库 |
| | GET | `/api/v1/repositories/{id}/branches` | 分支列表 |
| **分支保护** | GET | `/api/v1/repositories/{id}/protection/rules` | 保护规则列表 |
| | POST | `/api/v1/repositories/{id}/protection/rules` | 创建规则 |
| | PUT | `/api/v1/protection/rules/{id}` | 编辑规则 |
| | DELETE | `/api/v1/protection/rules/{id}` | 删除规则 |
| | PUT | `/api/v1/protection/rules/{id}/toggle` | 启用/停用 |
| **规则集** | GET | `/api/v1/protection/rule-sets` | 规则集列表 |
| | POST | `/api/v1/protection/rule-sets` | 创建规则集 |
| | POST | `/api/v1/protection/rule-sets/{id}/apply` | 应用到仓库 |
| **合并请求** | GET | `/api/v1/repositories/{id}/merge-requests` | MR列表 |
| | POST | `/api/v1/repositories/{id}/merge-requests` | 创建MR |
| | GET | `/api/v1/merge-requests/{id}` | MR详情 |
| | POST | `/api/v1/merge-requests/{id}/approve` | 审批通过 |
| | POST | `/api/v1/merge-requests/{id}/request-changes` | 请求修改 |
| | POST | `/api/v1/merge-requests/{id}/merge` | 合并 |
| **审计** | GET | `/api/v1/git/audit-logs` | 审计日志列表 |
| | GET | `/api/v1/git/audit-logs/export` | 导出审计日志 |

### 6.2 核心 API 示例

#### 创建分支保护规则

```json
// POST /api/v1/repositories/3/protection/rules
{
  "ruleName": "主干分支保护",
  "branchPattern": "main",
  "forbidPush": true,
  "forbidForcePush": true,
  "forbidDelete": true,
  "requireMr": true,
  "minApprovals": 2,
  "dismissStaleApprovals": true,
  "blockSelfApprove": true,
  "requireCiPass": true,
  "ciContexts": ["sonarqube-scan", "unit-test"],
  "whitelistUsers": [12, 15],
  "whitelistRoles": ["tech_lead"]
}
```

#### 查询分支保护状态

```json
// GET /api/v1/repositories/3/protection/rules
// 响应:
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "ruleName": "主干分支保护",
      "branchPattern": "main",
      "matchedBranches": ["main", "master"],
      "forbidPush": true,
      "minApprovals": 2,
      "enabled": true,
      "createdAt": "2026-08-09T10:00:00"
    },
    {
      "id": 2,
      "ruleName": "发布分支保护",
      "branchPattern": "release/*",
      "matchedBranches": ["release/1.0", "release/2.0"],
      "forbidPush": true,
      "minApprovals": 1,
      "enabled": true
    }
  ]
}
```

---

## 7. 前端页面设计

### 7.1 页面路由

```
/settings/git                     → 代码管理主页面（卡片导航）
/settings/git/platforms           → Git 平台配置
/settings/git/repositories        → 仓库列表
/settings/git/repositories/:id    → 仓库详情
/settings/git/repositories/:id/protection  → 分支保护规则配置
/settings/git/repositories/:id/merge-requests  → 合并请求列表
/settings/git/rule-sets           → 保护规则集管理
```

### 7.2 核心页面线框图

#### 7.2.1 仓库列表页

```
┌─ 代码管理 / 仓库列表 ──────────────────────────────────┐
│ [搜索仓库___] [Git平台▼] [状态▼]    [+ 创建仓库] [导入] │
│                                                        │
│ ┌──────┬────────┬──────────┬──────┬──────┬──────┐     │
│ │ 仓库名│ 所属项目 │ 默认分支 │ MR数  │ 状态  │ 操作  │     │
│ ├──────┼────────┼──────────┼──────┼──────┼──────┤     │
│ │ backend│ 核心业务 │ main     │ 3    │ 活跃  │ ...  │     │
│ │ frontend│ 核心业务│ main    │ 1    │ 活跃  │ ...  │     │
│ │ common │ (未绑定)│ develop  │ 0    │ 归档  │ ...  │     │
│ └──────┴────────┴──────────┴──────┴──────┴──────┘     │
│                                  [分页: 1/3  共24条]    │
└────────────────────────────────────────────────────────┘
```

#### 7.2.2 仓库详情页 — 分支保护规则 Tab

```
┌─ 仓库: backend-service / 分支保护规则 ──────────────────┐
│                                      [+ 添加规则] [规则集▼]│
│                                                        │
│ ┌─ 规则 #1: 主干分支保护 ────────────────── [编辑] [删除] ┐│
│ │ 匹配: main, master                      启用: ⬤      ││
│ │ 禁止推送 ✓  最小审批: 2人  白名单: 张三               ││
│ └──────────────────────────────────────────────────────┘│
│                                                        │
│ ┌─ 规则 #2: 发布分支保护 ────────────────── [编辑] [删除] ┐│
│ │ 匹配: release/*                         启用: ⬤      ││
│ │ 禁止推送 ✓  最小审批: 1人  CI检查: ✓                 ││
│ └──────────────────────────────────────────────────────┘│
│                                                        │
│ ┌─ 规则 #3: 热修复特殊通道 ──────────────── [编辑] [删除] ┐│
│ │ 匹配: hotfix/*                          启用: ○      ││
│ │ 最小审批: 0人 (紧急修复免审)                           ││
│ └──────────────────────────────────────────────────────┘│
└────────────────────────────────────────────────────────┘
```

#### 7.2.3 规则编辑器（弹窗）

```
┌─ 编辑保护规则 ──────────────────────────────────── × │
│                                                      │
│ 规则名称: [主干分支保护             ]                 │
│ 分支模式: [main, master            ] 💡 支持 fnmatch │
│                                                      │
│ ── 推送控制 ──                                       │
│ [✓] 禁止直接推送  [✓] 禁止强制推送  [✓] 禁止删除分支  │
│                                                      │
│ ── 合并审批 ──                                       │
│ 最少审批人数: [2 ▾]                                 │
│ [✓] 驳回过期审批  [✓] 禁止自批  [ ] CODEOWNERS审批    │
│                                                      │
│ ── 状态检查 ──                                       │
│ [✓] CI流水线通过  检查项: [sonarqube, unit-test ▾]    │
│                                                      │
│ ── 白名单 (可绕过以上限制) ──                        │
│ 用户: [张三 ×] [李四 ×]  角色: [技术负责人 ×]        │
│                                                      │
│          [取消]  [保存规则]                           │
└──────────────────────────────────────────────────────┘
```

---

## 8. 实施路线图

### 8.1 分阶段计划

| 阶段 | 内容 | 预计文件数 | 依赖 |
|------|------|:----:|------|
| **Phase 1 — 基础设施** | `git_platforms` + `repositories` 表、Entity、Mapper、API、前端列表页 | ~15 | 无 |
| **Phase 2 — 分支保护** | `branch_protection_rules` + `protection_rule_sets` + 规则编辑器 UI | ~12 | Phase 1 |
| **Phase 3 — 合并请求** | `merge_requests` 表 + MR CRUD + 审批流程 | ~12 | Phase 2 |
| **Phase 4 — 审计日志** | `audit_logs` 表 + 日志查询页 + 五元组 | ~8 | Phase 1 |
| **Phase 5 — 规则集** | 规则集跨仓库应用 + 组织级覆盖 | ~5 | Phase 2 |

### 8.2 优先级建议

| 优先级 | 模块 | 理由 |
|:------:|------|------|
| P0 | Git 平台配置 + 仓库管理 | 一切功能的基础 |
| P0 | 分支保护规则 | 核心价值——精细化管理 |
| P1 | 合并请求 | 保护规则的"消费端" |
| P1 | 操作审计 | 合规要求 |
| P2 | 规则集 | 规模化后的效率优化 |

---

> **文档状态**: 待评审  
> **下一步**: 确认范围后可开始 Phase 1 全栈实施
