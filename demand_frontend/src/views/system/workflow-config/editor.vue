<template>
  <div class="workflow-editor-page">
    <el-card shadow="never" class="editor-card">
      <template #header>
        <div class="editor-header">
          <div class="header-left">
            <el-button @click="goBack">
              <el-icon><ArrowLeft /></el-icon>
              返回
            </el-button>
            <div class="title-info">
              <h2>{{ workflowEditorTitle }}</h2>
              <div class="scope-tag">适用范围：{{ workflowScopeLabel }}</div>
              <template v-if="currentVersion || !isViewMode">
                <span v-if="isViewMode" class="version-tag">
                  V{{ currentVersion?.version }} - {{ currentVersion?.name }}
                  <span v-if="currentVersion?.knowledgeBaseName" class="knowledge-tag">
                    <el-icon><FolderOpened /></el-icon>
                    {{ currentVersion.knowledgeBaseName }}
                  </span>
                </span>
                <div v-else class="version-editor">
                  <div class="version-input-group">
                    <div class="version-number-input">
                      <span class="version-prefix">V</span>
                      <el-input
                        v-model="versionForm.version"
                        placeholder="1.0.0"
                        maxlength="20"
                      />
                    </div>
                    <div v-if="versionMetaHint" class="version-meta-hint" :class="versionMetaHint.type">
                      {{ versionMetaHint.message }}
                    </div>
                  </div>
                  <div class="version-input-group">
                    <el-input
                      v-model="versionForm.name"
                      class="version-name-input"
                      maxlength="50"
                      show-word-limit
                      placeholder="请输入版本名称"
                    />
                  </div>
                  <div class="version-input-group knowledge-binding-group">
                    <label class="knowledge-binding-label">关联知识库</label>
                    <el-select
                      v-model="versionForm.knowledgeBaseId"
                      placeholder="流转附件自动入库"
                      clearable
                      filterable
                      class="knowledge-binding-select"
                    >
                      <el-option
                        v-for="kb in knowledgeBases"
                        :key="kb.id"
                        :label="kb.name"
                        :value="kb.id"
                      />
                    </el-select>
                    <el-tooltip content="绑定后，此工作流流转过程中上传的附件将自动解析入库到所选知识库，用于文档中心知识问答" placement="top">
                      <el-icon class="knowledge-binding-help"><QuestionFilled /></el-icon>
                    </el-tooltip>
                  </div>
                  <div class="version-input-group evaluation-binding-group">
                    <label class="evaluation-binding-label">评价状态</label>
                    <el-switch v-model="versionForm.approvalEvaluationEnabled" />
                    <span class="evaluation-binding-desc">{{ versionForm.approvalEvaluationEnabled ? '审批时显示评分（必填）' : '审批时不显示评分' }}</span>
                    <el-tooltip content="开启后，使用该工作流的工单在审批时需要填写评分；关闭则隐藏评分功能" placement="top">
                      <el-icon class="evaluation-binding-help"><QuestionFilled /></el-icon>
                    </el-tooltip>
                  </div>
                </div>
              </template>
            </div>
          </div>
          <div class="header-right">
            <AppButton v-if="!isViewMode" permission="button:workflow:update" @click="handleSave" :loading="saving">
              <el-icon><DocumentCopy /></el-icon>
              {{ ['approved', 'inactive', 'rejected'].includes(currentVersion?.activationStatus ?? '') && !configDirty ? '保存名称' : '保存草稿' }}
            </AppButton>
            <AppButton v-if="!isViewMode && (!['approved', 'inactive', 'rejected'].includes(currentVersion?.activationStatus ?? '') || configDirty)" type="primary" permission="button:workflow:update" @click="handleSubmit" :loading="submitting">
              <el-icon><Check /></el-icon>
              提交审核
            </AppButton>
          </div>
        </div>
        <div v-if="['approved', 'inactive', 'rejected'].includes(currentVersion?.activationStatus ?? '') && !isViewMode" class="approved-edit-warning">
          <el-icon><WarningFilled /></el-icon>
          当前版本{{ currentVersion?.activationStatus === 'approved' ? '已审核通过' : currentVersion?.activationStatus === 'inactive' ? '已停用' : '审核被拒绝' }}。修改节点或连线配置后保存，版本将回退为草稿状态，需重新提交审核。仅修改版本名称则保持原状态。
        </div>
      </template>

      <div class="editor-container">
        <!-- 左侧工具栏 -->
        <div class="toolbar-left">
          <div class="toolbar-title">节点类型</div>
          <div class="node-palette">
            <div
              v-for="node in nodeTypes"
              :key="node.type"
              class="palette-node"
              :class="[node.type, { 'is-readonly': isViewMode }]"
              @mousedown.prevent="handleNodeDragStart(node)"
            >
              <div class="node-icon">{{ node.icon }}</div>
              <div class="node-label">{{ node.label }}</div>
            </div>
          </div>

          <div class="toolbar-section help-section">
            <div class="toolbar-title">绘制说明</div>
            <ol>
              <li>按住节点类型拖到画布。</li>
              <li>从节点锚点拖向目标节点创建连线。</li>
              <li>点击节点或连线，在右侧配置。</li>
              <li>完成后保存草稿，再提交审核。</li>
            </ol>
          </div>

          <div class="toolbar-section">
            <div class="toolbar-title">操作</div>
            <div class="toolbar-actions">
              <el-button class="toolbar-action-btn" @click="handleZoomIn">
                <el-icon><ZoomIn /></el-icon>
                放大
              </el-button>
              <el-button class="toolbar-action-btn" @click="handleZoomOut">
                <el-icon><ZoomOut /></el-icon>
                缩小
              </el-button>
              <el-button class="toolbar-action-btn" @click="handleResetZoom">
                <el-icon><Refresh /></el-icon>
                重置
              </el-button>
              <el-tooltip content="查看模式不可编辑" placement="bottom" :disabled="!isViewMode">
                <span>
                  <AppButton
                    class="toolbar-action-btn"
                    @click="handleFormatLayout"
                    permission="button:workflow:update"
                    :disabled="isViewMode"
                  >
                    <el-icon><Grid /></el-icon>
                    格式化排版
                  </AppButton>
                </span>
              </el-tooltip>
              <el-tooltip content="查看模式不可编辑" placement="bottom" :disabled="!isViewMode">
                <span>
                  <AppButton class="toolbar-action-btn" @click="handleClearCanvas" type="danger" permission="button:workflow:update" :disabled="isViewMode">
                    <el-icon><Delete /></el-icon>
                    清空画布
                  </AppButton>
                </span>
              </el-tooltip>
            </div>
          </div>
        </div>

        <!-- 中间画布区域 -->
        <div class="canvas-container">
          <div ref="logicFlowContainer" class="logicflow-container"></div>
        </div>

        <!-- 右侧配置面板 -->
        <el-drawer
          v-model="drawerVisible"
          :title="drawerTitle"
          direction="rtl"
          :size="drawerSize"
          :before-close="handleDrawerClose"
          class="config-drawer"
        >
          <!-- 拖拽调整手柄 -->
          <div
            class="drawer-resize-handle"
            :class="{ active: isResizing }"
            @mousedown="onResizeStart"
          >
            <div class="resize-bar"></div>
          </div>
          <div v-if="selectedNode" class="node-config-panel">
            <el-form :model="nodeForm" label-width="100px" label-position="top" :disabled="isViewMode">
              <el-form-item label="节点名称">
                <el-input v-model="nodeForm.nodeName" placeholder="请输入节点名称" />
              </el-form-item>

              <el-form-item label="节点类型">
                <el-tag>{{ getNodeTypeLabel(nodeForm.nodeType) }}</el-tag>
              </el-form-item>

              <!-- 显示可流转的下个节点名称 -->
              <el-form-item v-if="nextNodeNames.length > 0" label="可流转节点">
                <el-select
                  :model-value="selectedNextNode"
                  placeholder="请选择目标节点"
                  clearable
                  @update:model-value="onNextNodeChange"
                >
                  <el-option
                    v-for="name in nextNodeNames"
                    :key="name"
                    :label="name"
                    :value="name"
                  />
                </el-select>
              </el-form-item>

              <el-form-item label="绑定节点状态">
                <el-select v-model="nodeForm.nodeStatusCode" placeholder="请选择节点状态" clearable filterable>
                  <el-option
                    v-for="status in nodeStatusOptions"
                    :key="status.code"
                    :label="status.name"
                    :value="status.code"
                  />
                </el-select>
              </el-form-item>

              <el-form-item v-if="nodeForm.nodeType !== 'end'" label="节点规则">
                <div class="node-rule-grid">
                  <el-checkbox v-model="nodeForm.allowCancel">允许取消</el-checkbox>
                  <el-checkbox v-if="showProjectRequiredCheckbox" v-model="nodeForm.projectRequired">
                    项目必选
                  </el-checkbox>
                  <el-checkbox v-model="nodeForm.requireAttachment">
                    必须上传附件
                  </el-checkbox>
                  <el-checkbox v-if="nodeForm.nodeType === 'approval'" v-model="nodeForm.allowModifyType">
                    允许变更工单类型
                  </el-checkbox>
                  <!-- 需求详情页按钮可见性开关：勾选显示、不勾选隐藏（历史节点缺省显示） -->
                  <el-checkbox v-model="nodeForm.allowEdit">
                    允许编辑
                  </el-checkbox>
                  <el-checkbox v-model="nodeForm.allowSplit">
                    允许拆分子需求
                  </el-checkbox>
                  <el-checkbox
                    v-if="nodeForm.nodeType !== 'start'"
                    v-model="nodeForm.allowReject"
                  >
                    允许驳回
                  </el-checkbox>
                </div>
              </el-form-item>

              <!-- 消息提醒：进入本节点后向已审批路径 / 实际处理用户推送站内消息 -->
              <el-form-item v-if="nodeForm.nodeType !== 'end'" label="消息提醒">
                <div class="node-notify-panel">
                  <div class="node-notify-row">
                    <el-switch v-model="nodeForm.notifyOnEnter" />
                    <span class="node-notify-label">流转后向相关用户推送站内消息</span>
                    <el-tooltip content="开启后，流转到本节点时自动给指定范围的用户发送站内消息提醒" placement="top">
                      <el-icon class="node-notify-help"><QuestionFilled /></el-icon>
                    </el-tooltip>
                  </div>
                  <template v-if="nodeForm.notifyOnEnter">
                    <el-radio-group v-model="nodeForm.notifyScope" class="node-notify-scope">
                      <el-radio value="PATH_APPROVERS">已审批节点路径上的用户（含创建人）</el-radio>
                      <el-radio value="ACTUAL_HANDLERS">从需求创建到当前节点实际处理过的用户</el-radio>
                    </el-radio-group>
                  </template>
                </div>
              </el-form-item>

              <!-- 评分配置（仅审批节点） -->
              <el-form-item v-if="nodeForm.nodeType === 'approval'" label="节点评价">
                <div class="rating-config-panel">
                  <div class="rating-toggle-row">
                    <el-switch v-model="nodeForm.ratingConfig.enabled" />
                    <span class="rating-toggle-label">启用评分</span>
                    <el-tooltip content="关闭后此节点不展示评价" placement="top">
                      <el-icon class="rating-help"><QuestionFilled /></el-icon>
                    </el-tooltip>
                  </div>
                  <template v-if="nodeForm.ratingConfig.enabled">
                    <div class="rating-required-row">
                      <el-checkbox v-model="nodeForm.ratingConfig.required">设为必填</el-checkbox>
                      <el-checkbox v-model="nodeForm.ratingConfig.showInStatistics">纳入统计</el-checkbox>
                    </div>
                    <el-divider content-position="left">评分维度</el-divider>
                    <div v-for="(dim, idx) in nodeForm.ratingConfig.dimensions" :key="idx" class="rating-dimension-item">
                      <el-input v-model="dim.name" placeholder="维度名称（如：需求质量）" size="small" />
                      <el-input v-model="dim.description" placeholder="评价说明" type="textarea" :rows="2" size="small" />
                      <el-row :gutter="8">
                        <el-col :span="12">
                          <el-input v-model="dim.minLabel" placeholder="1星标签（如：很差）" size="small" />
                        </el-col>
                        <el-col :span="12">
                          <el-input v-model="dim.maxLabel" placeholder="5星标签（如：非常好）" size="small" />
                        </el-col>
                      </el-row>
                      <el-button type="danger" text size="small" @click="removeRatingDimension(idx)">删除维度</el-button>
                    </div>
                    <el-button v-if="nodeForm.ratingConfig.dimensions.length === 0" type="primary" plain size="small" @click="addRatingDimension">
                      + 添加评分维度
                    </el-button>
                    <el-button v-else type="primary" plain size="small" @click="addRatingDimension">
                      + 再添加一个维度
                    </el-button>
                    <div v-if="nodeForm.ratingConfig.dimensions.length === 0" class="rating-tip">
                      留空则使用单一评分模式（1-5星整体评分）
                    </div>
                  </template>
                </div>
              </el-form-item>

              <!-- 审批节点和抄送节点的配置 -->
              <template v-if="nodeForm.nodeType === 'approval' || nodeForm.nodeType === 'cc'">
                <el-form-item v-if="nodeForm.nodeType === 'cc'" label="抄送方式">
                  <el-radio-group v-model="nodeForm.ccMode" class="cc-mode-group">
                    <el-radio value="MESSAGE">站内消息</el-radio>
                    <el-radio value="READ_ONLY_TODO">只读查阅待办</el-radio>
                  </el-radio-group>
                  <div class="cc-mode-tip">
                    站内消息只发送通知；只读查阅待办会出现在“抄送我的”列表中，但不可审批或流转。
                  </div>
                </el-form-item>

                <el-form-item label="处理人类型">
                  <el-select v-model="nodeForm.assigneeType" placeholder="请选择处理人类型">
                    <el-option label="指定用户" value="SPECIFIED_USER" />
                    <el-option label="指定角色" value="SPECIFIED_ROLE" />
                    <el-option label="指定角色组" value="SPECIFIED_ROLE_GROUP" />
                    <el-option label="指定组织" value="SPECIFIED_ORG" />
                    <el-option label="提交人" value="CREATOR" />
                    <el-option label="上一节点处理人" value="PREV_APPROVER" />
                  </el-select>
                </el-form-item>

                <el-form-item v-if="nodeForm.assigneeType === 'SPECIFIED_ROLE'" label="指定角色">
                  <!-- el-tree-select 不消费 el-form 的禁用上下文，查看模式必须显式传 disabled -->
                  <RoleSelect v-model="nodeForm.assigneeRoleId" placeholder="请选择角色" :disabled="isViewMode" />
                </el-form-item>

                <el-form-item v-if="nodeForm.assigneeType === 'SPECIFIED_ROLE_GROUP'" label="指定角色组">
                  <el-select v-model="nodeForm.assigneeRoleGroupId" placeholder="请选择角色组" :loading="assigneeOptionsLoading">
                    <el-option
                      v-for="group in roleGroupSelectOptions"
                      :key="group.id"
                      :label="group.name"
                      :value="group.id"
                      :disabled="group.disabled"
                    />
                  </el-select>
                </el-form-item>

                <el-form-item v-if="nodeForm.assigneeType === 'SPECIFIED_USER'" label="指定用户">
                  <el-select v-model="nodeForm.assigneeUserIds" multiple placeholder="请选择用户" :loading="assigneeOptionsLoading">
                    <el-option
                      v-for="user in userSelectOptions"
                      :key="user.id"
                      :label="user.realName || user.username"
                      :value="user.id"
                      :disabled="user.disabled"
                    />
                  </el-select>
                </el-form-item>

                <el-form-item v-if="nodeForm.assigneeType === 'SPECIFIED_ORG'" label="指定组织">
                  <el-tree-select
                    v-model="nodeForm.assigneeOrgId"
                    :data="orgTreeData"
                    :props="{ label: 'name', value: 'id', children: 'children' }"
                    placeholder="请选择组织节点"
                    :disabled="isViewMode"
                    check-strictly
                    filterable
                    clearable
                  />
                </el-form-item>

                <el-form-item v-if="nodeForm.assigneeType === 'SPECIFIED_ORG'" label="组织层级范围">
                  <el-radio-group v-model="nodeForm.orgScopeType">
                    <el-radio value="current">仅当前层级</el-radio>
                    <el-radio value="include_children">当前层级及子层级</el-radio>
                  </el-radio-group>
                </el-form-item>

                <el-form-item v-if="nodeForm.nodeType === 'approval'" label="超时时间（小时）">
                  <el-input-number
                    v-model="nodeForm.timeoutHours"
                    :min="0"
                    :max="720"
                    placeholder="0表示不限制"
                  />
                </el-form-item>

                <el-form-item v-if="nodeForm.nodeType === 'approval' && nodeForm.timeoutHours" label="超时动作">
                  <el-select v-model="nodeForm.timeoutAction" placeholder="请选择超时动作">
                    <el-option label="自动通过" value="AUTO_APPROVE" />
                    <el-option label="自动拒绝" value="AUTO_REJECT" />
                    <el-option label="转交上级" value="ESCALATE" />
                  </el-select>
                </el-form-item>

                <!-- 会签配置 -->
                <CountersignConfig
                  v-if="nodeForm.nodeType === 'approval'"
                  :enabled="nodeForm.countersignEnabled ?? false"
                  :strategy="nodeForm.countersignStrategy ?? 'ALL'"
                  :mode="nodeForm.countersignMode ?? 'FIXED'"
                  :approvers="nodeForm.countersignApprovers ?? []"
                  :users="allUserList"
                  @update:enabled="handleCountersignEnabledChange"
                  @update:strategy="nodeForm.countersignStrategy = $event"
                  @update:mode="nodeForm.countersignMode = $event"
                  @update:approvers="handleCountersignApproversChange"
                />
              </template>

              <template v-if="nodeForm.nodeType === 'parallel'">
                <ParallelConfig
                  :parallel-type="nodeForm.parallelType"
                  :branches="nodeForm.parallelBranches"
                  :disabled="isViewMode"
                  @update:parallel-type="nodeForm.parallelType = $event as 'AND' | 'OR'"
                  @update:branches="nodeForm.parallelBranches = $event"
                />
              </template>

              <!-- 条件节点配置 -->
              <template v-if="nodeForm.nodeType === 'condition'">
                <ConditionBranchConfig
                  :condition-desc="nodeForm.properties?.conditionDesc || ''"
                  :branches="nodeForm.conditionBranches || []"
                  :disabled="isViewMode"
                  @update:condition-desc="nodeForm.properties = { ...nodeForm.properties, conditionDesc: $event }"
                  @update:branches="nodeForm.conditionBranches = $event"
                />
              </template>

              <!-- 需求动态字段权限 -->
              <template v-if="showFieldPermissions">
                <el-divider content-position="left">
                  <span class="field-perm-divider-title">动态字段权限</span>
                  <el-tooltip
                    content="控制该环节下需求扩展字段的可见 / 可编辑 / 必填范围。全部留空表示不限制（字段默认全部可见可编辑）。"
                    placement="top"
                  >
                    <el-icon class="field-perm-help"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </el-divider>
                <el-table :data="customFieldOptions" size="small" border max-height="320">
                  <el-table-column prop="name" label="字段" min-width="120">
                    <template #default="{ row }">
                      <div class="field-perm-name">
                        <span>{{ row.name }}</span>
                        <el-tag size="small" effect="plain" round>{{ row.fieldType }}</el-tag>
                      </div>
                      <div class="field-perm-code">{{ row.fieldCode }}</div>
                    </template>
                  </el-table-column>
                  <el-table-column label="可见" width="62" align="center">
                    <template #default="{ row }">
                      <el-checkbox
                        :model-value="nodeForm.fieldPermissions?.visible.includes(row.fieldCode)"
                        :disabled="isViewMode"
                        @change="(v: boolean) => toggleFieldPermission('visible', row.fieldCode, v)"
                      />
                    </template>
                  </el-table-column>
                  <el-table-column label="可编辑" width="72" align="center">
                    <template #default="{ row }">
                      <el-checkbox
                        :model-value="nodeForm.fieldPermissions?.editable.includes(row.fieldCode)"
                        :disabled="isViewMode"
                        @change="(v: boolean) => toggleFieldPermission('editable', row.fieldCode, v)"
                      />
                    </template>
                  </el-table-column>
                  <el-table-column label="必填" width="62" align="center">
                    <template #default="{ row }">
                      <el-checkbox
                        :model-value="nodeForm.fieldPermissions?.required.includes(row.fieldCode)"
                        :disabled="isViewMode"
                        @change="(v: boolean) => toggleFieldPermission('required', row.fieldCode, v)"
                      />
                    </template>
                  </el-table-column>
                </el-table>
                <div v-if="!isViewMode" class="field-perm-actions">
                  <el-button link type="primary" size="small" @click="selectAllFieldPermissions('visible')">
                    可见全选
                  </el-button>
                  <el-button link type="primary" size="small" @click="selectAllFieldPermissions('editable')">
                    可编辑全选
                  </el-button>
                  <el-button link type="primary" size="small" @click="selectAllFieldPermissions('required')">
                    必填全选
                  </el-button>
                  <el-button link type="info" size="small" @click="clearFieldPermissions">清空</el-button>
                </div>
              </template>
            </el-form>

            <div v-if="!isViewMode" class="node-config-footer">
              <AppButton type="primary" :icon="Check" permission="button:workflow:update" @click="handleSaveNodeConfig">保存</AppButton>
              <AppButton type="danger" :icon="Delete" permission="button:workflow:update" @click="handleDeleteNode">删除</AppButton>
            </div>
          </div>

          <div v-else-if="selectedEdge" class="edge-config-panel">
            <el-form :model="edgeForm" label-width="100px" label-position="top" :disabled="isViewMode">
              <el-form-item label="连线标签">
                <el-input v-model="edgeForm.label" placeholder="请输入连线标签（可选）" />
              </el-form-item>

              <el-form-item label="条件">
                <ConditionConfig
                  :model-value="edgeConditionModel"
                  :disabled="isViewMode"
                  @update:model-value="onEdgeConditionModelUpdate"
                  @update:expr="edgeForm.conditionExpr = $event"
                />
              </el-form-item>
              <el-form-item label="条件表达式（高级）">
                <el-input
                  v-model="edgeForm.conditionExpr"
                  type="textarea"
                  :rows="2"
                  placeholder="例如：priority == 'P0'"
                />
              </el-form-item>
            </el-form>

            <div v-if="!isViewMode" class="node-config-footer">
              <AppButton type="primary" :icon="Check" permission="button:workflow:update" @click="handleSaveEdgeConfig">保存</AppButton>
              <AppButton type="danger" :icon="Delete" permission="button:workflow:update" @click="handleDeleteEdge">删除连线</AppButton>
            </div>
          </div>
        </el-drawer>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount, reactive, watch, nextTick } from 'vue'
import { useRouter, useRoute, onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppButton from '@/components/common/AppButton.vue'
import RoleSelect from '@/components/common/RoleSelect.vue'
import CountersignConfig from './components/CountersignConfig.vue'
import ParallelConfig from './components/ParallelConfig.vue'
import ConditionConfig from './components/ConditionConfig.vue'
import ConditionBranchConfig from './components/ConditionBranchConfig.vue'
import {
  ArrowLeft,
  DocumentCopy,
  Check,
  ZoomIn,
  ZoomOut,
  Refresh,
  Grid,
  Delete,
  WarningFilled,
  QuestionFilled,
  FolderOpened
} from '@element-plus/icons-vue'
import LogicFlow from '@logicflow/core'
import '@logicflow/core/dist/index.css'
import { registerCustomNodes } from './logicflow-config'
import {
  GLOBAL_WORKFLOW_PROJECT_ID,
  saveWorkflowConfig,
  submitForApproval,
  validateBeforeSubmit,
  getVersionConfig,
  getVersionHistory,
} from '@/api/modules/workflow-visual'
import { nodeStatusApi, type NodeStatus } from '@/api/modules/workflow-engine'
import { requirementConfigApi } from '@/api/modules/requirementConfig'
import type { CustomFieldDef } from '@/api/modules/requirementConfig'
import * as roleApi from '@/api/modules/role'
import * as userApi from '@/api/modules/user'
import { getAllKnowledgeBases, type KnowledgeBase } from '@/api/modules/knowledge'
import { resolveActiveMenuPath } from '@/utils/menuNavigation'
import { loadOrgTree } from '@/composables/useOrgTree'
import {
  compareWorkflowVersion,
  isWorkflowVersion,
  normalizeWorkflowVersion,
  sameWorkflowVersion,
  suggestNextWorkflowVersion,
} from '@/utils/workflowVersion'
import type {
  WorkflowVersionDTO,
  WorkflowVersionMetaUpdateDTO,
  WorkflowNodeDTO,
  WorkflowEdgeDTO,
  WorkflowConfigDTO,
  ConditionBranch,
  ConditionConfig as ConditionConfigType,
  ConditionRule
} from '@/types/workflow-visual'

const router = useRouter()
const route = useRoute()

const logicFlowContainer = ref<HTMLElement>()
let lf: LogicFlow | null = null

const isViewMode = ref(false)
const isEditMode = ref(false)
const currentVersion = ref<WorkflowVersionDTO>()
const saving = ref(false)
const submitting = ref(false)
const configDirty = ref(false)
const versionHistory = ref<WorkflowVersionDTO[]>([])
const roleList = ref<Array<{ id: number; name: string; code: string }>>([])
const roleTreeSelectOptions = ref<Array<{ groupId: number | null; groupName: string; children: Array<{ id: number; name: string; code: string; isDefault: number }> }>>([])
const roleGroupList = ref<Array<{ id: number; name: string }>>([])
const allUserList = ref<Array<{ id: number; realName: string; username: string }>>([])
const knowledgeBases = ref<KnowledgeBase[]>([])
// 组织树数据：来自共享缓存（useOrgTree 模块级单例）
const orgTreeData = ref<any[]>([])
const assigneeOptionsLoading = ref(false)

const drawerVisible = ref(false)
const drawerTitle = ref('')
/** 抽屉宽度：默认 500px，条件节点自适应加宽到 680px */
const drawerSize = ref('500px')
/** 拖拽调整宽度 */
const isResizing = ref(false)
const MIN_DRAWER_WIDTH = 420
const MAX_DRAWER_WIDTH_RATIO = 0.85
const selectedNode = ref<any>(null)
const selectedEdge = ref<any>(null)
const selectedNextNode = ref<string>('')
const SELECTED_NEXT_NODE_PROPERTY = 'selectedNextNode'

// 选择可流转节点时的处理
const onNextNodeChange = (nodeName: string | null | undefined) => {
  selectedNextNode.value = nodeName || ''
  nodeForm.properties = {
    ...(nodeForm.properties || {}),
    [SELECTED_NEXT_NODE_PROPERTY]: selectedNextNode.value
  }
}
const versionForm = reactive<{
  version: string
  name: string
  knowledgeBaseId: number | null
  approvalEvaluationEnabled: boolean
}>({
  version: '',
  name: '',
  knowledgeBaseId: null,
  approvalEvaluationEnabled: false
})
const nodeStatusOptions = ref<NodeStatus[]>([])

// 节点类型定义
const nodeTypes = [
  { type: 'start', label: '开始', icon: '▶' },
  { type: 'approval', label: '审批', icon: '✓' },
  { type: 'cc', label: '抄送', icon: '📧' },
  { type: 'condition', label: '条件', icon: '◆' },
  { type: 'parallel', label: '并行', icon: '⑂' },
  { type: 'end', label: '结束', icon: '■' }
]

// 计算当前节点可流转的下个节点名称列表
const nextNodeNames = computed(() => {
  if (!lf || !selectedNode.value?.id) return []

  const graphData = lf.getGraphData() as {
    nodes?: Array<{ id?: string; text?: { value: string } }>
    edges?: Array<{ sourceNodeId?: string; targetNodeId?: string }>
  }

  if (!graphData?.edges) return []

  // 找出所有从当前节点出发的边
  const outgoingEdges = (graphData.edges || []).filter(
    (edge) => edge.sourceNodeId === selectedNode.value!.id
  )

  if (outgoingEdges.length === 0) return []

  // 构建节点ID到名称的映射
  const nodeNameMap = new Map<string, string>()
  ;(graphData.nodes || []).forEach((node) => {
    if (node.id) {
      nodeNameMap.set(node.id, node.text?.value || node.id)
    }
  })

  // 获取所有下一个节点的名称
  return outgoingEdges
    .map((edge) => nodeNameMap.get(edge.targetNodeId || ''))
    .filter((name): name is string => !!name)
})

/**
 * 唯一可流转节点所绑定的节点状态。
 * 「可流转节点」只有一个时，用它作为本节点「绑定节点状态」的默认值。
 * 节点名称不做自动填充（由人工填写）。
 */
const soleNextNodeStatusCode = computed(() => {
  if (nextNodeNames.value.length !== 1 || !lf || !selectedNode.value?.id) return ''

  const graphData = lf.getGraphData() as {
    nodes?: Array<{ id?: string; properties?: Record<string, any> }>
    edges?: Array<{ sourceNodeId?: string; targetNodeId?: string }>
  }

  const edge = (graphData?.edges || []).find(
    (item) => item.sourceNodeId === selectedNode.value!.id
  )
  if (!edge) return ''

  const target = (graphData?.nodes || []).find((node) => node.id === edge.targetNodeId)
  const code =
    target?.properties?.nodeStatusCode ?? target?.properties?.properties?.nodeStatusCode
  return typeof code === 'string' ? code : ''
})

// 节点表单
const nodeForm = reactive<Partial<WorkflowNodeDTO> & {
  nodeStatusCode?: string
  allowCancel?: boolean
  projectRequired?: boolean
  requireAttachment?: boolean
  /** 审核节点是否允许在流转时切换需求类型 */
  allowModifyType?: boolean
  /** 需求详情页按钮可见性开关（缺省 true，历史节点不改变既有行为） */
  allowEdit?: boolean
  allowSplit?: boolean
  allowReject?: boolean
  notifyOnEnter?: boolean
  notifyScope?: 'PATH_APPROVERS' | 'ACTUAL_HANDLERS'
  ratingConfig: {
    enabled: boolean
    required: boolean
    showInStatistics: boolean
    dimensions: Array<{ key: string; name: string; description: string; minLabel: string; maxLabel: string }>
  }
  assigneeRoleGroupId?: number
  assigneeOrgId?: number
  orgScopeType?: 'current' | 'include_children'
  countersignEnabled?: boolean
  countersignStrategy?: 'ALL' | 'ANY' | 'MAJORITY'
  countersignMode?: 'FIXED' | 'DYNAMIC'
  countersignApprovers?: number[]
  parallelType?: 'AND' | 'OR'
  parallelBranches?: Array<{ branchId: string; branchName: string; condition: { field?: string; operator?: string; value?: string } }>
  conditionBranches?: ConditionBranch[]
  ccMode?: 'MESSAGE' | 'READ_ONLY_TODO'
  /** 需求动态字段在该节点上的可见 / 可编辑 / 必填范围（存 fieldCode） */
  fieldPermissions?: {
    visible: string[]
    editable: string[]
    required: string[]
  }
}>({
  nodeId: '',
  nodeType: 'approval',
  nodeName: '',
  positionX: 0,
  positionY: 0,
  assigneeType: undefined,
  assigneeRoleId: undefined,
  assigneeRoleGroupId: undefined,
  assigneeOrgId: undefined,
  orgScopeType: 'include_children',
  assigneeUserIds: [],
  timeoutHours: undefined,
  timeoutAction: undefined,
  nodeStatusCode: undefined,
  allowCancel: true,
  projectRequired: false,
  requireAttachment: false,
  allowModifyType: false,
  allowEdit: true,
  allowSplit: true,
  allowReject: true,
  notifyOnEnter: false,
  notifyScope: 'PATH_APPROVERS',
  ratingConfig: {
    enabled: false,
    required: false,
    showInStatistics: true,
    dimensions: []
  },
  properties: {},
  countersignEnabled: false,
  countersignStrategy: 'ALL',
  countersignMode: 'FIXED',
  countersignApprovers: [],
  parallelType: 'AND',
  parallelBranches: [],
  conditionBranches: [],
  ccMode: 'MESSAGE',
  fieldPermissions: { visible: [], editable: [], required: [] }
})

// ------------------------------------------------------------------
// 需求动态字段权限
// ------------------------------------------------------------------

/** 当前流程版本所绑定需求类型下可用的动态字段 */
const customFieldOptions = ref<CustomFieldDef[]>([])
const customFieldsLoading = ref(false)

/** 拉取流程绑定需求类型的动态字段（含项目级全局字段） */
async function loadCustomFieldOptions() {
  const versionId = currentVersion.value?.id
  if (!versionId) {
    customFieldOptions.value = []
    return
  }
  customFieldsLoading.value = true
  try {
    const types = await requirementConfigApi.listTypes() as unknown as any[]
    const bound = (Array.isArray(types) ? types : []).filter(
      (t: any) => t?.workflowVersionId === versionId,
    )
    if (bound.length === 0) {
      customFieldOptions.value = []
      return
    }
    const chunks = await Promise.all(
      bound.map((t: any) =>
        requirementConfigApi.listCustomFields(t.code).catch(() => []),
      ),
    )
    const merged = new Map<string, CustomFieldDef>()
    for (const list of chunks) {
      for (const field of (Array.isArray(list) ? list : []) as CustomFieldDef[]) {
        if (field?.fieldCode && !merged.has(field.fieldCode)) {
          merged.set(field.fieldCode, field)
        }
      }
    }
    customFieldOptions.value = [...merged.values()].sort(
      (a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0),
    )
  } catch {
    customFieldOptions.value = []
  } finally {
    customFieldsLoading.value = false
  }
}

/** 字段权限面板是否可用：仅审批/处理类节点有意义且需绑定了动态字段 */
const showFieldPermissions = computed(
  () => ['approval', 'cc', 'parallel', 'condition'].includes(nodeForm.nodeType || '')
    && customFieldOptions.value.length > 0,
)

function toggleFieldPermission(kind: 'visible' | 'editable' | 'required', fieldCode: string, on: boolean) {
  const fp = nodeForm.fieldPermissions
  if (!fp) return
  const list = fp[kind]
  const idx = list.indexOf(fieldCode)
  if (on && idx === -1) list.push(fieldCode)
  if (!on && idx > -1) list.splice(idx, 1)
  // 可编辑与必填隐式要求可见
  if ((kind === 'editable' || kind === 'required') && on && !fp.visible.includes(fieldCode)) {
    fp.visible.push(fieldCode)
  }
}

/** 全部可见（用于快速初始化） */
function selectAllFieldPermissions(kind: 'visible' | 'editable' | 'required') {
  const fp = nodeForm.fieldPermissions
  if (!fp) return
  fp[kind] = customFieldOptions.value.map((f) => f.fieldCode)
  if (kind !== 'visible') {
    fp.visible = customFieldOptions.value.map((f) => f.fieldCode)
  }
}

function clearFieldPermissions() {
  if (nodeForm.fieldPermissions) {
    nodeForm.fieldPermissions = { visible: [], editable: [], required: [] }
  }
}

function normalizeFieldPermissions(raw: any): { visible: string[]; editable: string[]; required: string[] } {
  const pick = (v: any) => (Array.isArray(v) ? v.map(String).filter(Boolean) : [])
  return {
    visible: pick(raw?.visible),
    editable: pick(raw?.editable),
    required: pick(raw?.required),
  }
}

// 流程版本切换后重新拉取该版本绑定需求类型下的动态字段
watch(
  () => currentVersion.value?.id,
  () => {
    void loadCustomFieldOptions()
  },
  { immediate: true },
)

// 会签配置：需求提出人占位 ID（与 CountersignConfig 中保持一致）
const COUNTERSIGN_CREATOR_PLACEHOLDER_ID = -1

// 用户首次启用会签开关时，若会签人列表为空，自动填入「需求提出人」占位
watch(
  () => nodeForm.countersignEnabled,
  (newVal, oldVal) => {
    if (newVal && !oldVal) {
      ensureDefaultCountersignApprover()
    }
  },
)

function ensureDefaultCountersignApprover() {
  if (!nodeForm.countersignApprovers || nodeForm.countersignApprovers.length === 0) {
    nodeForm.countersignApprovers = [COUNTERSIGN_CREATOR_PLACEHOLDER_ID]
  }
}

function handleCountersignEnabledChange(value: boolean) {
  nodeForm.countersignEnabled = value
  if (value) {
    ensureDefaultCountersignApprover()
  }
}

function handleCountersignApproversChange(value: number[]) {
  nodeForm.countersignApprovers = value
}

// 边表单
const edgeForm = reactive({
  label: '',
  conditionExpr: ''
})

const edgeConditionModel = ref<{ logic: 'AND' | 'OR'; rules: ConditionRule[] }>({
  logic: 'AND',
  rules: [{ field: 'type', operator: 'eq', value: '' }],
})

function onEdgeConditionModelUpdate(val: { logic: string; rules: ConditionRule[]; expr: string }) {
  edgeConditionModel.value = { logic: (val.logic === 'OR' ? 'OR' : 'AND'), rules: val.rules }
  edgeForm.conditionExpr = val.expr || ''
}

const NODE_LAYOUT_SIZE: Record<string, { width: number; height: number }> = {
  start: { width: 80, height: 80 },
  approval: { width: 120, height: 60 },
  cc: { width: 120, height: 60 },
  condition: { width: 80, height: 80 },
  parallel: { width: 80, height: 80 },
  end: { width: 80, height: 80 }
}

const LAYOUT_START_X = 180
const LAYOUT_START_Y = 180
const LAYOUT_COLUMN_GAP = 110
const LAYOUT_ROW_GAP = 130
const LAYOUT_MIN_NODE_VERTICAL_GAP = 34
const LAYOUT_GRID_SIZE = 10

const GATEWAY_TYPES = new Set(['condition', 'parallel'])

/**
 * 网关（条件/并行）菱形尺寸：与 logicflow-config.ts 的 GatewayDiamondModel 同一公式，
 * 长文本回填菱形内部（内接可用宽度 ≈ width × 0.66），布局据此分配列宽，文字不再被连线穿过。
 */
const gatewaySizeOf = (label?: string) => {
  const text = (label ?? '').trim()
  if (!text) return { width: 80, height: 80 }
  return { width: Math.max(80, Math.ceil(text.length * 14 / 0.66) + 40), height: 90 }
}

/** 网关尺寸归一：拖入/改名后调用（渲染数据已直接携带尺寸的场景无需调用） */
const normalizeGatewaySizes = () => {
  if (!lf) return
  ;(lf.graphModel.nodes || []).forEach((model: any) => {
    if (!GATEWAY_TYPES.has(model.type)) return
    const size = gatewaySizeOf(model.text?.value)
    if (model.width !== size.width || model.height !== size.height) {
      try {
        model.resize({ ...size, deltaX: 0, deltaY: 0 })
      } catch {
        // 个别状态下 resize 不可用时跳过，不影响其余节点
      }
    }
  })
}

type LayoutNode = {
  id: string
  type: string
  x: number
  y: number
  text?: string
}

type LayoutEdge = {
  sourceNodeId: string
  targetNodeId: string
}

type LayoutPosition = {
  x: number
  y: number
}

const resolveWorkflowProjectId = (rawValue: unknown) => {
  const parsedValue = Number(rawValue)
  return Number.isFinite(parsedValue) && parsedValue >= 0 ? parsedValue : GLOBAL_WORKFLOW_PROJECT_ID
}

const getNodeSize = (type?: string) => NODE_LAYOUT_SIZE[type || 'approval'] || NODE_LAYOUT_SIZE.approval

const currentProjectId = computed(() => resolveWorkflowProjectId(route.query.projectId || route.params.projectId))
const returnMenuPath = computed(() => resolveActiveMenuPath(route))
const workflowScopeLabel = computed(() => currentProjectId.value === GLOBAL_WORKFLOW_PROJECT_ID ? '全局标准流程' : `项目 ${currentProjectId.value}`)
const workflowEditorTitle = computed(() => {
  const scopeText = currentProjectId.value === GLOBAL_WORKFLOW_PROJECT_ID ? '全局工作流' : '工作流'
  if (isViewMode.value) return `查看${scopeText}`
  if (isEditMode.value) return `编辑${scopeText}`
  return `新建${scopeText}`
})
const nodeDrawerTitle = computed(() => isViewMode.value ? '节点详情' : '节点配置')
const edgeDrawerTitle = computed(() => isViewMode.value ? '连线详情' : '连线配置')
const showProjectRequiredCheckbox = computed(() => !hasProjectRequiredInPredecessors(nodeForm.nodeId))
const roleSelectOptions = computed(() => {
  const options = roleList.value.map(role => ({ ...role, disabled: false }))
  const selectedRoleId = nodeForm.assigneeRoleId
  if (
    selectedRoleId !== undefined &&
    selectedRoleId !== null &&
    !options.some(role => role.id === selectedRoleId)
  ) {
    options.unshift({
      id: selectedRoleId,
      name: assigneeOptionsLoading.value ? '角色加载中...' : '角色不存在或已删除',
      code: '',
      disabled: true
    })
  }
  return options
})
const roleGroupSelectOptions = computed(() => {
  const options = roleGroupList.value.map(group => ({ ...group, disabled: false }))
  const selectedGroupId = nodeForm.assigneeRoleGroupId
  if (
    selectedGroupId !== undefined &&
    selectedGroupId !== null &&
    !options.some(group => group.id === selectedGroupId)
  ) {
    options.unshift({
      id: selectedGroupId,
      name: assigneeOptionsLoading.value ? '角色组加载中...' : '角色组不存在或已删除',
      disabled: true
    })
  }
  return options
})
const userSelectOptions = computed(() => {
  const options = allUserList.value.map(user => ({ ...user, disabled: false }))
  const selectedUserIds = nodeForm.assigneeUserIds || []
  selectedUserIds
    .filter(userId => !options.some(user => user.id === userId))
    .forEach(userId => {
      options.unshift({
        id: userId,
        realName: assigneeOptionsLoading.value ? '用户加载中...' : '用户不存在或已删除',
        username: '',
        disabled: true
      })
    })
  return options
})
const trimmedVersionName = computed(() => versionForm.name.trim())
const duplicatedVersionRecord = computed(() => {
  const normalizedVersion = normalizeWorkflowVersion(versionForm.version)
  const normalizedName = trimmedVersionName.value
  if (!normalizedVersion || !normalizedName) return undefined
  return versionHistory.value.find((item) => {
    if (currentVersion.value?.id && item.id === currentVersion.value.id) return false
    if ((item.name || '').trim() !== normalizedName) return false
    return sameWorkflowVersion(item.version, normalizedVersion)
  })
})
const versionMetaHint = computed(() => {
  if (isViewMode.value) return null
  const trimmedVersion = versionForm.version.trim()
  if (!trimmedVersion && !trimmedVersionName.value) {
    return { type: 'info', message: '支持直接编辑版本号和版本名称' }
  }
  if (!trimmedVersion) {
    return { type: 'warning', message: '版本号不能为空' }
  }
  if (!isWorkflowVersion(trimmedVersion)) {
    return { type: 'warning', message: '版本号格式需为正整数或 1.0.0' }
  }
  if (duplicatedVersionRecord.value) {
    return { type: 'error', message: `工作流“${trimmedVersionName.value}”下版本号 V${normalizeWorkflowVersion(trimmedVersion)} 已存在` }
  }
  if (!trimmedVersionName.value) {
    return { type: 'warning', message: '版本名称不能为空' }
  }
  return { type: 'success', message: '版本信息可保存' }
})

const applyCurrentVersion = (version: WorkflowVersionDTO) => {
  currentVersion.value = {
    ...currentVersion.value,
    ...version,
    config: version.config ?? currentVersion.value?.config
  }
}

const syncVersionForm = (version?: WorkflowVersionDTO) => {
  versionForm.version = normalizeWorkflowVersion(version?.version)
  versionForm.name = version?.name || ''
  versionForm.knowledgeBaseId = version?.knowledgeBaseId ?? null
  versionForm.approvalEvaluationEnabled = version?.approvalEvaluationEnabled ?? false
}

const getDesiredVersionMeta = (): WorkflowVersionMetaUpdateDTO | null => {
  const version = versionForm.version.trim()
  const name = versionForm.name.trim()
  const hasAnyInput = version.length > 0 || name.length > 0 || !!currentVersion.value

  if (!hasAnyInput) {
    return null
  }

  if (!version) {
    ElMessage.warning('请输入版本号')
    return null
  }

  if (!isWorkflowVersion(version)) {
    ElMessage.warning('版本号格式需为正整数或 1.0.0')
    return null
  }

  if (duplicatedVersionRecord.value) {
    ElMessage.warning(`工作流“${name}”下版本号 V${normalizeWorkflowVersion(version)} 已存在，请重新输入`)
    return null
  }

  if (!name) {
    ElMessage.warning('请输入版本名称')
    return null
  }

  return { version, name, knowledgeBaseId: versionForm.knowledgeBaseId, approvalEvaluationEnabled: versionForm.approvalEvaluationEnabled }
}

const syncEditorVersionRoute = async (versionId: number, projectId: number) => {
  const nextVersionId = String(versionId)
  const currentRouteVersionId = route.query.versionId ? String(route.query.versionId) : ''
  const currentProjectId = route.query.projectId ? String(route.query.projectId) : ''
  if (currentRouteVersionId === nextVersionId && currentProjectId === String(projectId) && route.query.mode === 'edit') {
    return
  }

  await router.replace({
    path: route.path,
    query: {
      ...route.query,
      versionId: nextVersionId,
      projectId: String(projectId),
      mode: 'edit'
    }
  })
}

const applySuggestedVersionMeta = () => {
  if (currentVersion.value || isViewMode.value) return
  if (versionForm.version.trim() || trimmedVersionName.value) return

  const latestVersion = [...versionHistory.value]
    .sort((left, right) => compareWorkflowVersion(right.version, left.version))[0]?.version
  const nextVersion = suggestNextWorkflowVersion(latestVersion)
  versionForm.version = nextVersion
  versionForm.name = `草稿版本 v${nextVersion}`
}

const getLayoutGraphData = () => {
  if (!lf) return null

  const graphData = lf.getGraphData() as {
    nodes?: Array<{
      id?: string
      type?: string
      x?: number
      y?: number
      text?: { value?: string }
    }>
    edges?: Array<{
      sourceNodeId?: string
      targetNodeId?: string
    }>
  }

  return {
    nodes: (graphData.nodes || [])
      .filter((node) => !!node.id && !!node.type && typeof node.x === 'number' && typeof node.y === 'number')
      .map((node) => ({
        id: node.id as string,
        type: node.type as string,
        x: node.x as number,
        y: node.y as number,
        text: node.text?.value ?? ''
      })),
    edges: (graphData.edges || [])
      .filter((edge): edge is Required<LayoutEdge> => !!edge.sourceNodeId && !!edge.targetNodeId)
      .map((edge) => ({
        sourceNodeId: edge.sourceNodeId,
        targetNodeId: edge.targetNodeId
      }))
  }
}

const getWorkflowGraphData = () => {
  if (!lf) return null

  return lf.getGraphData() as {
    nodes?: Array<{
      id?: string
      properties?: Record<string, any>
    }>
    edges?: Array<{
      sourceNodeId?: string
      targetNodeId?: string
    }>
  }
}

const hasNodeProjectRequired = (node?: { properties?: Record<string, any> }) => {
  return Boolean(node?.properties?.projectRequired ?? node?.properties?.properties?.projectRequired)
}

const hasProjectRequiredInPredecessors = (nodeId?: string) => {
  if (!nodeId) return false

  const graphData = getWorkflowGraphData()
  if (!graphData) return false

  const nodeMap = new Map((graphData.nodes || []).filter(node => !!node.id).map(node => [node.id as string, node]))
  const parentMap = new Map<string, string[]>()

  ;(graphData.edges || []).forEach((edge) => {
    if (!edge.sourceNodeId || !edge.targetNodeId) return
    const parents = parentMap.get(edge.targetNodeId) || []
    parents.push(edge.sourceNodeId)
    parentMap.set(edge.targetNodeId, parents)
  })

  const visited = new Set<string>()
  const stack = [...(parentMap.get(nodeId) || [])]

  while (stack.length > 0) {
    const currentId = stack.pop()
    if (!currentId || visited.has(currentId)) continue
    visited.add(currentId)

    const currentNode = nodeMap.get(currentId)
    if (hasNodeProjectRequired(currentNode)) {
      return true
    }

    stack.push(...(parentMap.get(currentId) || []))
  }

  return false
}

const normalizeCurrentNodeProjectRequired = () => {
  if (hasProjectRequiredInPredecessors(nodeForm.nodeId)) {
    nodeForm.projectRequired = false
  }
}

// 格式化排版：分层 → 虚拟节点占道 → 重心排序/定位 → 链段拉直 → 碰撞消解
// 目标：主干链水平成直线、分支围绕主干对称、跨层连线走廊不被节点占据
const buildFormattedLayout = (nodes: LayoutNode[], edges: LayoutEdge[]) => {
  const positions = new Map<string, LayoutPosition>()
  if (nodes.length === 0) return positions

  const realNodeMap = new Map(nodes.map(node => [node.id, node]))
  const sizeOf = (id: string) => {
    const node = realNodeMap.get(id)
    if (!node) return { width: 0, height: 0 }
    if (GATEWAY_TYPES.has(node.type)) return gatewaySizeOf(node.text)
    return getNodeSize(node.type)
  }

  // 清理边：去掉未知端点 / 自环 / 重复边
  const edgeKeySet = new Set<string>()
  const cleanEdges: LayoutEdge[] = []
  edges.forEach((edge) => {
    if (!realNodeMap.has(edge.sourceNodeId) || !realNodeMap.has(edge.targetNodeId)) return
    if (edge.sourceNodeId === edge.targetNodeId) return
    const edgeKey = `${edge.sourceNodeId}->${edge.targetNodeId}`
    if (edgeKeySet.has(edgeKey)) return
    edgeKeySet.add(edgeKey)
    cleanEdges.push(edge)
  })

  // 分层：最长路径层赋值（Kahn 拓扑；死锁时强制断环；回边不参与布局）
  const levelMap = new Map<string, number>(nodes.map(node => [node.id, 0]))
  const layoutEdges: LayoutEdge[] = []
  {
    const topoChildrenMap = new Map<string, string[]>(nodes.map(node => [node.id, []]))
    const remainingParents = new Map<string, number>(nodes.map(node => [node.id, 0]))
    cleanEdges.forEach((edge) => {
      topoChildrenMap.get(edge.sourceNodeId)!.push(edge.targetNodeId)
      remainingParents.set(edge.targetNodeId, (remainingParents.get(edge.targetNodeId) || 0) + 1)
    })
    const compareByCanvasId = (leftId: string, rightId: string) => {
      const left = realNodeMap.get(leftId)
      const right = realNodeMap.get(rightId)
      if (!left || !right) return leftId.localeCompare(rightId)
      return left.y - right.y || left.x - right.x || left.id.localeCompare(right.id)
    }
    const queue: string[] = nodes
      .filter(node => (remainingParents.get(node.id) || 0) === 0)
      .map(node => node.id)
    const processed = new Set<string>()
    const processNode = (id: string) => {
      processed.add(id)
      ;(topoChildrenMap.get(id) || []).forEach((childId) => {
        levelMap.set(childId, Math.max(levelMap.get(childId) || 0, (levelMap.get(id) || 0) + 1))
        remainingParents.set(childId, (remainingParents.get(childId) || 0) - 1)
        if ((remainingParents.get(childId) || 0) === 0 && !processed.has(childId)) queue.push(childId)
      })
    }
    while (processed.size < nodes.length) {
      if (queue.length === 0) {
        // 环：挑“未处理父节点最少”的节点强制断环
        const candidates = nodes
          .filter(node => !processed.has(node.id))
          .sort((a, b) => (remainingParents.get(a.id) || 0) - (remainingParents.get(b.id) || 0) || compareByCanvasId(a.id, b.id))
        const candidate = candidates[0]
        if (!candidate) break
        queue.push(candidate.id)
        remainingParents.set(candidate.id, 0)
      }
      queue.sort((leftId, rightId) =>
        Number(realNodeMap.get(rightId)?.type === 'start') - Number(realNodeMap.get(leftId)?.type === 'start')
        || compareByCanvasId(leftId, rightId))
      const id = queue.shift()
      if (!id || processed.has(id)) continue
      processNode(id)
    }
    cleanEdges.forEach((edge) => {
      if ((levelMap.get(edge.targetNodeId) || 0) > (levelMap.get(edge.sourceNodeId) || 0)) {
        layoutEdges.push(edge)
      }
    })
  }

  // 虚拟节点占道：跨层长边在中间层插入虚拟链，预留连线走廊
  const allIds = new Set(nodes.map(node => node.id))
  const expandedEdges: LayoutEdge[] = []
  {
    let virtualSeq = 0
    layoutEdges.forEach((edge) => {
      const span = (levelMap.get(edge.targetNodeId) || 0) - (levelMap.get(edge.sourceNodeId) || 0)
      let prevId = edge.sourceNodeId
      for (let step = 1; step < span; step += 1) {
        const virtualId = `__layout_v${virtualSeq += 1}`
        allIds.add(virtualId)
        levelMap.set(virtualId, (levelMap.get(edge.sourceNodeId) || 0) + step)
        expandedEdges.push({ sourceNodeId: prevId, targetNodeId: virtualId })
        prevId = virtualId
      }
      expandedEdges.push({ sourceNodeId: prevId, targetNodeId: edge.targetNodeId })
    })
  }

  const childrenMap = new Map<string, string[]>([...allIds].map(id => [id, []]))
  const parentsMap = new Map<string, string[]>([...allIds].map(id => [id, []]))
  expandedEdges.forEach((edge) => {
    childrenMap.get(edge.sourceNodeId)!.push(edge.targetNodeId)
    parentsMap.get(edge.targetNodeId)!.push(edge.sourceNodeId)
  })
  const byCanvas = (leftId: string, rightId: string) => {
    const left = realNodeMap.get(leftId)
    const right = realNodeMap.get(rightId)
    if (!left || !right) return leftId.localeCompare(rightId)
    return left.y - right.y || left.x - right.x || left.id.localeCompare(right.id)
  }
  childrenMap.forEach(ids => ids.sort(byCanvas))
  parentsMap.forEach(ids => ids.sort(byCanvas))

  const maxLevel = Math.max(...[...levelMap.values()], 0)
  const levels: string[][] = Array.from({ length: maxLevel + 1 }, () => [])
  allIds.forEach(id => levels[levelMap.get(id) || 0].push(id))

  // 列内排序：重心法消交叉（初始顺序尊重画布现状）
  const posInLevel = new Map<string, number>()
  levels.forEach((col) => {
    col.sort(byCanvas)
    col.forEach((id, index) => posInLevel.set(id, index))
  })
  const neighborBarycenter = (id: string, useParents: boolean) => {
    const neighbors = useParents ? parentsMap.get(id) || [] : childrenMap.get(id) || []
    if (neighbors.length === 0) return posInLevel.get(id) || 0
    return neighbors.reduce((sum, nid) => sum + (posInLevel.get(nid) || 0), 0) / neighbors.length
  }
  for (let sweep = 0; sweep < 4; sweep += 1) {
    const useParents = sweep % 2 === 0
    const levelIndexes = levels.map((_, index) => index)
    if (!useParents) levelIndexes.reverse()
    levelIndexes.forEach((levelIndex) => {
      const col = levels[levelIndex]
      const keyed = col.map((id, index) => ({ id, bary: neighborBarycenter(id, useParents), index }))
      keyed.sort((a, b) => a.bary - b.bary || a.index - b.index)
      keyed.forEach((item, index) => {
        col[index] = item.id
        posInLevel.set(item.id, index)
      })
    })
  }

  // 纵坐标：父均值初始化 + 重心松弛
  const yMap = new Map<string, number>([...allIds].map(id => [id, 0]))
  levels.forEach((col, levelIndex) => {
    if (levelIndex === 0) return
    col.forEach((id) => {
      const parents = parentsMap.get(id) || []
      if (parents.length > 0) {
        yMap.set(id, parents.reduce((sum, pid) => sum + (yMap.get(pid) || 0), 0) / parents.length)
      }
    })
  })

  // 纯链节点（一进一出）按连通段拉直：段内 y 统一为段均值
  const isPureChain = (id: string) => (parentsMap.get(id) || []).length === 1 && (childrenMap.get(id) || []).length === 1
  const segmentIdMap = new Map<string, string>()
  const segmentMembersMap = new Map<string, string[]>()
  {
    const segmentKeyOf = new Map<string, string>()
    allIds.forEach((id) => {
      if (!isPureChain(id) || segmentKeyOf.has(id)) return
      const members = [id]
      segmentKeyOf.set(id, id)
      let cursor = (childrenMap.get(id) || [])[0]
      while (cursor && isPureChain(cursor) && !segmentKeyOf.has(cursor)) {
        segmentKeyOf.set(cursor, id)
        members.push(cursor)
        cursor = (childrenMap.get(cursor) || [])[0]
      }
      cursor = (parentsMap.get(id) || [])[0]
      while (cursor && isPureChain(cursor) && !segmentKeyOf.has(cursor)) {
        segmentKeyOf.set(cursor, id)
        members.unshift(cursor)
        cursor = (parentsMap.get(cursor) || [])[0]
      }
      members.forEach(memberId => segmentIdMap.set(memberId, id))
      segmentMembersMap.set(id, members)
    })
  }
  const flattenChainSegments = () => {
    const sums = new Map<string, number>()
    const counts = new Map<string, number>()
    segmentIdMap.forEach((segmentId, memberId) => {
      sums.set(segmentId, (sums.get(segmentId) || 0) + (yMap.get(memberId) || 0))
      counts.set(segmentId, (counts.get(segmentId) || 0) + 1)
    })
    segmentIdMap.forEach((segmentId, memberId) => {
      yMap.set(memberId, (sums.get(segmentId) || 0) / (counts.get(segmentId) || 1))
    })
  }

  const shareNeighbor = (leftId: string, rightId: string) => {
    const leftParents = new Set(parentsMap.get(leftId) || [])
    if ((parentsMap.get(rightId) || []).some(pid => leftParents.has(pid))) return true
    const leftChildren = new Set(childrenMap.get(leftId) || [])
    if ((childrenMap.get(rightId) || []).some(cid => leftChildren.has(cid))) return true
    return false
  }
  const pairPitch = (leftId: string, rightId: string) => {
    if (!realNodeMap.has(leftId) || !realNodeMap.has(rightId)) return LAYOUT_ROW_GAP
    return shareNeighbor(leftId, rightId)
      ? LAYOUT_ROW_GAP
      : (sizeOf(leftId).height + sizeOf(rightId).height) / 2 + LAYOUT_MIN_NODE_VERTICAL_GAP
  }

  // 列内避让：以“链段/单点”为刚性块上下推挤，保持已拉直的链不弯
  const applyColumnMinGap = () => {
    levels.forEach((col) => {
      if (col.length < 2) return
      const sorted = [...col].sort((a, b) => (yMap.get(a) || 0) - (yMap.get(b) || 0) || byCanvas(a, b))
      const blocks: Array<{ id: string; ids: string[]; y: number }> = []
      sorted.forEach((id) => {
        const blockId = segmentIdMap.get(id) || id
        const last = blocks[blocks.length - 1]
        if (last && last.id === blockId) {
          last.ids.push(id)
        } else {
          blocks.push({ id: blockId, ids: [id], y: yMap.get(id) || 0 })
        }
      })
      if (blocks.length < 2) return
      const blockPitch = (left: { ids: string[] }, right: { ids: string[] }) => {
        let pitch = 0
        left.ids.forEach((leftId) => {
          right.ids.forEach((rightId) => {
            pitch = Math.max(pitch, pairPitch(leftId, rightId))
          })
        })
        return pitch
      }
      const desired = blocks.map(block => block.y)
      const pushDown = () => {
        for (let i = 1; i < blocks.length; i += 1) {
          desired[i] = Math.max(desired[i], desired[i - 1] + blockPitch(blocks[i - 1], blocks[i]))
        }
      }
      const meanBefore = desired.reduce((sum, value) => sum + value, 0) / desired.length
      pushDown()
      const meanAfter = desired.reduce((sum, value) => sum + value, 0) / desired.length
      const drift = meanBefore - meanAfter
      for (let i = 0; i < desired.length; i += 1) desired[i] += drift
      pushDown()
      blocks.forEach((block, index) => {
        block.ids.forEach(id => yMap.set(id, desired[index]))
      })
    })
  }

  const relaxOnce = (reverseLevels: boolean, onlyNonChain = false) => {
    const levelIndexes = levels.map((_, index) => index)
    if (reverseLevels) levelIndexes.reverse()
    levelIndexes.forEach((levelIndex) => {
      ;(levels[levelIndex] || []).forEach((id) => {
        if (onlyNonChain && segmentIdMap.has(id)) return
        const neighbors = [...(parentsMap.get(id) || []), ...(childrenMap.get(id) || [])]
        if (neighbors.length === 0) return
        yMap.set(id, neighbors.reduce((sum, nid) => sum + (yMap.get(nid) || 0), 0) / neighbors.length)
      })
    })
  }

  for (let round = 0; round < 3; round += 1) {
    relaxOnce(false)
    flattenChainSegments()
    applyColumnMinGap()
    relaxOnce(true)
    flattenChainSegments()
    applyColumnMinGap()
  }
  // 收尾：非链节点再对齐邻域重心（链段保持刚性）
  for (let round = 0; round < 2; round += 1) {
    relaxOnce(false, true)
    relaxOnce(true, true)
    flattenChainSegments()
    applyColumnMinGap()
  }
  // 最终拉直后，若仍有同列碰撞，按“整段刚性平移”消解（跨列链段保持直线）
  flattenChainSegments()
  for (let iter = 0; iter < nodes.length; iter += 1) {
    let moved = false
    levels.forEach((col) => {
      const sorted = [...col].sort((a, b) => (yMap.get(a) || 0) - (yMap.get(b) || 0) || byCanvas(a, b))
      for (let i = 1; i < sorted.length; i += 1) {
        const upperId = sorted[i - 1]
        const lowerId = sorted[i]
        const need = pairPitch(upperId, lowerId)
        const gap = (yMap.get(lowerId) || 0) - (yMap.get(upperId) || 0)
        if (gap < need - 0.5) {
          const segmentId = segmentIdMap.get(lowerId)
          const members = (segmentId && segmentMembersMap.get(segmentId)) || [lowerId]
          members.forEach(memberId => yMap.set(memberId, (yMap.get(memberId) || 0) + need - gap))
          moved = true
        }
      }
    })
    if (!moved) break
  }

  // 分叉对称化：出度≥2 的节点，其真实子节点围绕分叉点上下对称分布（纯链整段平移），
  // 分支连线因此获得一致的分流角度，不再一侧远一侧近地歪斜
  {
    const realChildren = new Map<string, string[]>()
    cleanEdges.forEach((edge) => {
      if (!realNodeMap.has(edge.targetNodeId)) return
      const list = realChildren.get(edge.sourceNodeId)
      if (list) list.push(edge.targetNodeId)
      else realChildren.set(edge.sourceNodeId, [edge.targetNodeId])
    })
    realChildren.forEach((children, parentId) => {
      if (children.length < 2) return
      const yv = yMap.get(parentId) || 0
      const ordered = [...children].sort((a, b) => (yMap.get(a) || 0) - (yMap.get(b) || 0) || byCanvas(a, b))
      const spread = Math.max(
        LAYOUT_ROW_GAP,
        ...ordered.map(id => sizeOf(id).height + LAYOUT_MIN_NODE_VERTICAL_GAP),
      )
      ordered.forEach((childId, index) => {
        const targetY = yv + spread * (index - (ordered.length - 1) / 2)
        const segId = segmentIdMap.get(childId)
        const members = (segId && segmentMembersMap.get(segId)) || [childId]
        const delta = targetY - (yMap.get(childId) || 0)
        members.forEach(memberId => yMap.set(memberId, (yMap.get(memberId) || 0) + delta))
      })
    })
    applyColumnMinGap()
  }

  // 横坐标：按层分列（列宽取该层真实节点的最大宽度）
  const columnWidth = levels.map((col) => {
    const realIds = col.filter(id => realNodeMap.has(id))
    if (realIds.length === 0) return 0
    return Math.max(...realIds.map(id => sizeOf(id).width))
  })
  const xOfLevel = levels.map(() => LAYOUT_START_X)
  for (let levelIndex = 1; levelIndex < levels.length; levelIndex += 1) {
    xOfLevel[levelIndex] = xOfLevel[levelIndex - 1]
      + (columnWidth[levelIndex - 1] || 0) / 2 + LAYOUT_COLUMN_GAP + (columnWidth[levelIndex] || 0) / 2
  }

  nodes.forEach((node) => {
    positions.set(node.id, { x: xOfLevel[levelMap.get(node.id) || 0] || LAYOUT_START_X, y: yMap.get(node.id) || 0 })
  })

  // 连通分量：主分量（含 start）保持原位，其余分量依次堆叠到下方
  const parent = new Map<string, string>([...allIds].map(id => [id, id]))
  const find = (id: string): string => {
    let current = id
    while (current !== parent.get(current)) {
      current = parent.get(current) || current
    }
    return current
  }
  expandedEdges.forEach((edge) => {
    const leftRoot = find(edge.sourceNodeId)
    const rightRoot = find(edge.targetNodeId)
    if (leftRoot !== rightRoot) parent.set(leftRoot, rightRoot)
  })
  const startNode = [...nodes].sort((a, b) =>
    Number(b.type === 'start') - Number(a.type === 'start') || a.y - b.y || a.id.localeCompare(b.id))[0]
  const mainRoot = startNode ? find(startNode.id) : null
  const componentGroups = new Map<string, string[]>()
  nodes.forEach((node) => {
    const root = find(node.id)
    const group = componentGroups.get(root) || []
    group.push(node.id)
    componentGroups.set(root, group)
  })
  const groups = [...componentGroups.values()].sort((left, right) => {
    const leftMain = left.some(id => find(id) === mainRoot)
    const rightMain = right.some(id => find(id) === mainRoot)
    if (leftMain !== rightMain) return leftMain ? -1 : 1
    return byCanvas(left[0] || '', right[0] || '')
  })

  let stackBottomY = -Infinity
  groups.forEach((group, groupIndex) => {
    const groupTop = Math.min(...group.map(id => (positions.get(id)?.y || 0) - sizeOf(id).height / 2))
    const groupBottom = Math.max(...group.map(id => (positions.get(id)?.y || 0) + sizeOf(id).height / 2))
    if (groupIndex === 0) {
      stackBottomY = groupBottom
      return
    }
    const shiftY = Math.max(0, stackBottomY + 130 - groupTop)
    if (shiftY > 0) {
      group.forEach((id) => {
        const position = positions.get(id)
        if (!position) return
        positions.set(id, { ...position, y: position.y + shiftY })
      })
    }
    stackBottomY = Math.max(stackBottomY, groupBottom + shiftY)
  })

  // 平移到起始位置并对齐网格
  let minX = Infinity
  let minY = Infinity
  nodes.forEach((node) => {
    const size = getNodeSize(node.type)
    const position = positions.get(node.id)
    if (!position) return
    minX = Math.min(minX, position.x - size.width / 2)
    minY = Math.min(minY, position.y - size.height / 2)
  })
  const offsetX = LAYOUT_START_X - minX
  const offsetY = LAYOUT_START_Y - minY
  const snap = (value: number) => Math.round(value / LAYOUT_GRID_SIZE) * LAYOUT_GRID_SIZE
  positions.forEach((position, id) => {
    positions.set(id, { x: snap(position.x + offsetX), y: snap(position.y + offsetY) })
  })

  return positions
}

const applyEditorEditConfig = () => {
  if (!lf) return

  const editable = !isViewMode.value
  lf.updateEditConfig({
    adjustNodePosition: editable,
    adjustEdge: editable,
    adjustEdgeMiddle: editable,
    adjustEdgeStartAndEnd: editable,
    adjustEdgeStart: editable,
    adjustEdgeEnd: editable,
    textEdit: editable,
    nodeTextEdit: editable,
    edgeTextEdit: editable,
    edgeSelectedOutline: true,
    nodeSelectedOutline: true,
    hideAnchors: false,
    stopScrollGraph: true,
    stopZoomGraph: true
  })
}

const syncSelectedNodePosition = (nodeId: string, position: LayoutPosition) => {
  if (selectedNode.value?.id !== nodeId) return

  selectedNode.value = {
    ...selectedNode.value,
    x: position.x,
    y: position.y
  }
  nodeForm.positionX = position.x
  nodeForm.positionY = position.y
}

// 初始化LogicFlow
const initLogicFlow = () => {
  if (!logicFlowContainer.value) return

  lf = new LogicFlow({
    container: logicFlowContainer.value,
    width: logicFlowContainer.value.offsetWidth,
    height: logicFlowContainer.value.offsetHeight,
    grid: {
      size: 10,
      visible: true,
      type: 'dot'
    },
    keyboard: {
      enabled: true
    },
    stopScrollGraph: true,
    stopZoomGraph: true,
    style: {
      rect: {
        rx: 5,
        ry: 5,
        strokeWidth: 2
      },
      circle: {
        r: 40,
        strokeWidth: 2
      },
      diamond: {
        strokeWidth: 2
      }
    }
  })

  // 注册自定义节点
  registerCustomNodes(lf)
  applyEditorEditConfig()

  // 监听节点点击事件
  lf.on('node:click', ({ data }) => {
    handleNodeClick(data)
  })

  // 监听边点击事件
  lf.on('edge:click', ({ data }) => {
    handleEdgeClick(data)
  })

  // 监听画布点击事件（取消选中）
  lf.on('blank:click', () => {
    drawerVisible.value = false
    selectedNode.value = null
    selectedEdge.value = null
  })

  // 监听节点/连线结构性变更（approved 版本编辑时用于检测配置是否变化）
  lf.on('node:dnd-add', () => { configDirty.value = true })
  lf.on('node:delete', () => { configDirty.value = true })
  lf.on('edge:add', () => { configDirty.value = true })
  lf.on('edge:delete', () => { configDirty.value = true })

  // 渲染初始数据
  lf.render({
    nodes: [],
    edges: []
  })
}

// 处理节点拖拽开始
const handleNodeDragStart = (node: any) => {
  if (isViewMode.value) {
    ElMessage.warning('查看模式下不能编辑')
    return
  }
  if (!lf) return

  const nodeId = `${node.type}_${Date.now()}`
  lf.dnd.startDrag({
    type: node.type,
    text: node.label,
    properties: {
      nodeId,
      nodeType: node.type,
      nodeName: node.label,
      assigneeUserIds: [],
      properties: {}
    }
  })
}

// 评分维度管理
const addRatingDimension = () => {
  if (!nodeForm.ratingConfig) {
    nodeForm.ratingConfig = { enabled: true, required: false, showInStatistics: true, dimensions: [] }
  }
  nodeForm.ratingConfig.dimensions.push({
    key: `dim_${Date.now()}`,
    name: '',
    description: '',
    minLabel: '很差',
    maxLabel: '非常好'
  })
}

const removeRatingDimension = (idx: number) => {
  if (nodeForm.ratingConfig) {
    nodeForm.ratingConfig.dimensions.splice(idx, 1)
  }
}

// 处理节点点击
const handleNodeClick = (data: any) => {
  selectedNode.value = data
  selectedEdge.value = null
  const savedNextNode = data.properties?.[SELECTED_NEXT_NODE_PROPERTY]
  selectedNextNode.value = typeof savedNextNode === 'string' && nextNodeNames.value.includes(savedNextNode) ? savedNextNode : ''
  drawerTitle.value = nodeDrawerTitle.value
  drawerVisible.value = true
  adjustDrawerSize()

  // 填充表单
  Object.assign(nodeForm, {
    nodeId: data.id,
    nodeType: data.type,
    nodeName: data.text?.value || '',
    positionX: data.x,
    positionY: data.y,
    assigneeType: data.properties?.assigneeType,
    assigneeRoleId: data.properties?.assigneeRoleId,
    assigneeRoleGroupId: data.properties?.assigneeRoleGroupId,
    assigneeOrgId: data.properties?.assigneeOrgId,
    orgScopeType: data.properties?.orgScopeType ?? 'include_children',
    assigneeUserIds: data.properties?.assigneeUserIds || [],
    timeoutHours: data.properties?.timeoutHours,
    timeoutAction: data.properties?.timeoutAction,
    nodeStatusCode: data.properties?.nodeStatusCode ?? data.properties?.properties?.nodeStatusCode,
    allowCancel: data.properties?.allowCancel ?? data.properties?.properties?.allowCancel ?? true,
    projectRequired: data.properties?.projectRequired ?? data.properties?.properties?.projectRequired ?? false,
    requireAttachment: data.properties?.requireAttachment ?? data.properties?.properties?.requireAttachment ?? false,
    allowModifyType: data.properties?.allowModifyType ?? data.properties?.properties?.allowModifyType ?? false,
    allowEdit: data.properties?.allowEdit ?? data.properties?.properties?.allowEdit ?? true,
    allowSplit: data.properties?.allowSplit ?? data.properties?.properties?.allowSplit ?? true,
    allowReject: data.properties?.allowReject ?? data.properties?.properties?.allowReject ?? true,
    notifyOnEnter: data.properties?.notifyOnEnter ?? data.properties?.properties?.notifyOnEnter ?? false,
    notifyScope: (() => {
      const scope = data.properties?.notifyScope ?? data.properties?.properties?.notifyScope
      return scope === 'ACTUAL_HANDLERS' ? 'ACTUAL_HANDLERS' : 'PATH_APPROVERS'
    })(),
    ratingConfig: data.properties?.ratingConfig ?? data.properties?.properties?.ratingConfig ?? {
      enabled: false,
      required: false,
      showInStatistics: true,
      dimensions: []
    },
    // 会签配置
    countersignEnabled: data.properties?.countersignEnabled ?? false,
    countersignStrategy: data.properties?.countersignStrategy ?? 'ALL',
    countersignMode: data.properties?.countersignMode ?? 'FIXED',
    countersignApprovers: data.properties?.countersignApprovers || [],
    parallelType: data.properties?.parallelType ?? 'AND',
    parallelBranches: data.properties?.branches || [],
    ccMode: data.properties?.ccMode ?? data.properties?.properties?.ccMode ?? 'MESSAGE',
    fieldPermissions: normalizeFieldPermissions(
      data.properties?.fieldPermissions ?? data.properties?.properties?.fieldPermissions,
    ),
    properties: data.properties || {}
  })

  // 条件节点：收集所有出边构建 conditionBranches
  if (data.type === 'condition' && lf) {
    const graphData = lf.getGraphData() as any
    const nodeNameMap = new Map<string, string>()
    ;(graphData.nodes || []).forEach((n: any) => {
      if (n.id) nodeNameMap.set(n.id, n.text?.value || n.id)
    })
    const outEdges = (graphData.edges || []).filter((e: any) => e.sourceNodeId === data.id)
    nodeForm.conditionBranches = outEdges.map((edge: any) => ({
      edgeId: edge.id,
      targetNodeId: edge.targetNodeId,
      targetNodeName: nodeNameMap.get(edge.targetNodeId) || edge.targetNodeId,
      label: edge.text?.value || '',
      condition: edge.properties?.condition || { logic: 'AND', rules: [] }
    }))
  } else {
    nodeForm.conditionBranches = []
  }

  // 可流转节点只有一个时，「绑定节点状态」默认选中下个节点的状态（节点名称保持人工填写）
  if (!nodeForm.nodeStatusCode && soleNextNodeStatusCode.value) {
    nodeForm.nodeStatusCode = soleNextNodeStatusCode.value
  }

  normalizeCurrentNodeProjectRequired()
}

// 处理边点击
const handleEdgeClick = (data: any) => {
  selectedEdge.value = data
  selectedNode.value = null
  drawerTitle.value = edgeDrawerTitle.value
  drawerVisible.value = true
  adjustDrawerSize()

  // 填充表单
  edgeForm.label = data.text?.value || ''
  edgeForm.conditionExpr = data.properties?.condition?.expr || ''
  // 从边数据还原结构化条件（修复 edgeConditionModel 不持久化 bug）
  const savedCondition = data.properties?.condition
  if (savedCondition?.rules?.length) {
    edgeConditionModel.value = {
      logic: savedCondition.logic === 'OR' ? 'OR' : 'AND',
      rules: savedCondition.rules as ConditionRule[]
    }
  } else {
    edgeConditionModel.value = { logic: 'AND', rules: [{ field: 'type', operator: 'eq', value: '' }] }
  }
}

// 保存节点配置
// 修复 P1：客户端连通性预校验（与服务端 WorkflowConfigServiceImpl.validateConfigStructure 对齐）
// 避免无效工作流被提交，提示用户尽早修正
const validateBeforeSave = (): { valid: boolean; error?: string } => {
  if (!lf) return { valid: false, error: '画布未初始化' }

  const graphData = lf.getGraphData() as any
  const nodes = graphData?.nodes || []
  const edges = graphData?.edges || []

  if (nodes.length === 0) {
    return { valid: false, error: '工作流必须至少包含一个节点' }
  }

  const nodeTypes = new Set<string>()
  const nodeIds = new Set<string>()
  for (const node of nodes) {
    if (node.type) {
      nodeTypes.add(String(node.type).toLowerCase())
    }
    if (node.id) {
      nodeIds.add(String(node.id))
    }
  }
  if (!nodeTypes.has('start')) {
    return { valid: false, error: '工作流必须包含开始节点' }
  }
  if (!nodeTypes.has('end')) {
    return { valid: false, error: '工作流必须包含结束节点' }
  }

  const terminalStates = new Set(['cancelled', 'accepted', 'rejected'])
  for (const edge of edges) {
    const sourceId = edge.sourceNodeId ? String(edge.sourceNodeId) : ''
    const targetId = edge.targetNodeId ? String(edge.targetNodeId) : ''
    if (sourceId && !terminalStates.has(sourceId.toLowerCase()) && !nodeIds.has(sourceId)) {
      return { valid: false, error: `连线引用了不存在的源节点: ${sourceId}` }
    }
    if (targetId && !terminalStates.has(targetId.toLowerCase()) && !nodeIds.has(targetId)) {
      return { valid: false, error: `连线引用了不存在的目标节点: ${targetId}` }
    }
  }
  return { valid: true }
}

const handleSaveNodeConfig = () => {
  if (!lf || !selectedNode.value) return

  normalizeCurrentNodeProjectRequired()
  if (nodeForm.countersignEnabled && nodeForm.countersignMode === 'FIXED') {
    ensureDefaultCountersignApprover()
  }

  const validNextNode = nextNodeNames.value.includes(selectedNextNode.value) ? selectedNextNode.value : ''
  selectedNextNode.value = validNextNode

  const nodeData = {
    id: nodeForm.nodeId,
    type: nodeForm.nodeType,
    x: nodeForm.positionX,
    y: nodeForm.positionY,
    text: nodeForm.nodeName,
    properties: {
      ...nodeForm.properties,
      nodeId: nodeForm.nodeId,
      nodeType: nodeForm.nodeType,
      nodeName: nodeForm.nodeName,
      assigneeType: nodeForm.assigneeType,
      assigneeRoleId: nodeForm.assigneeRoleId,
      assigneeRoleGroupId: nodeForm.assigneeRoleGroupId,
      assigneeOrgId: nodeForm.assigneeOrgId,
      orgScopeType: nodeForm.orgScopeType,
      assigneeUserIds: nodeForm.assigneeUserIds,
      timeoutHours: nodeForm.timeoutHours,
      timeoutAction: nodeForm.timeoutAction,
      nodeStatusCode: nodeForm.nodeStatusCode,
      allowCancel: nodeForm.allowCancel,
      projectRequired: showProjectRequiredCheckbox.value ? nodeForm.projectRequired : false,
      requireAttachment: nodeForm.requireAttachment,
      allowModifyType: nodeForm.nodeType === 'approval' ? nodeForm.allowModifyType : false,
      allowEdit: nodeForm.nodeType === 'end' ? false : nodeForm.allowEdit,
      allowSplit: nodeForm.nodeType === 'end' ? false : nodeForm.allowSplit,
      allowReject: (nodeForm.nodeType === 'start' || nodeForm.nodeType === 'end') ? false : nodeForm.allowReject,
      notifyOnEnter: nodeForm.notifyOnEnter ?? false,
      notifyScope: nodeForm.notifyOnEnter ? (nodeForm.notifyScope || 'PATH_APPROVERS') : undefined,
      ratingConfig: nodeForm.nodeType === 'approval' ? nodeForm.ratingConfig : undefined,
      [SELECTED_NEXT_NODE_PROPERTY]: validNextNode,
      // 会签配置
      countersignEnabled: nodeForm.countersignEnabled,
      countersignStrategy: nodeForm.countersignStrategy,
      countersignMode: nodeForm.countersignMode,
      countersignApprovers: nodeForm.countersignApprovers,
      parallelType: nodeForm.parallelType,
      branches: nodeForm.parallelBranches,
      ccMode: nodeForm.nodeType === 'cc' ? (nodeForm.ccMode || 'MESSAGE') : undefined,
      // 始终回写：面板未展示时保持原值，避免 undefined 覆盖已保存的字段权限
      fieldPermissions: nodeForm.fieldPermissions ?? (nodeForm.properties as any)?.fieldPermissions,
    }
  }

  lf.setProperties(nodeForm.nodeId!, nodeData.properties)
  lf.updateText(nodeForm.nodeId!, nodeForm.nodeName || '')

  // 条件节点：将 conditionBranches 中的条件同步写回各边
  if (nodeForm.nodeType === 'condition' && nodeForm.conditionBranches && lf) {
    for (const branch of nodeForm.conditionBranches) {
      const existingProps = lf.getProperties(branch.edgeId) || {}
      lf.setProperties(branch.edgeId, {
        ...existingProps,
        condition: branch.condition
      })
      if (branch.label) {
        lf.updateText(branch.edgeId, branch.label)
      }
    }
  }

  ElMessage.success('节点配置已保存')
  configDirty.value = true
  drawerVisible.value = false
}

// 保存边配置
const handleSaveEdgeConfig = () => {
  if (!lf || !selectedEdge.value) return

  const edgeProperties = {
    label: edgeForm.label,
    condition: {
      logic: edgeConditionModel.value.logic || 'AND',
      rules: edgeConditionModel.value.rules || [],
      expr: edgeForm.conditionExpr
    }
  }

  lf.setProperties(selectedEdge.value.id, edgeProperties)
  if (edgeForm.label) {
    lf.updateText(selectedEdge.value.id, edgeForm.label)
  }

  ElMessage.success('连线配置已保存')
  configDirty.value = true
  drawerVisible.value = false
}

// 删除节点
const handleDeleteNode = async () => {
  if (!lf || !selectedNode.value) return

  try {
    await ElMessageBox.confirm('确定要删除该节点吗？', '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    lf.deleteNode(selectedNode.value.id)
    drawerVisible.value = false
    selectedNode.value = null
    ElMessage.success('节点已删除')
  } catch {
    // 用户取消
  }
}

// 删除边
const handleDeleteEdge = async () => {
  if (!lf || !selectedEdge.value) return

  try {
    await ElMessageBox.confirm('确定要删除该连线吗？', '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    lf.deleteEdge(selectedEdge.value.id)
    drawerVisible.value = false
    selectedEdge.value = null
    ElMessage.success('连线已删除')
  } catch {
    // 用户取消
  }
}

// 获取节点类型标签
const getNodeTypeLabel = (type?: string) => {
  const node = nodeTypes.find(n => n.type === type)
  return node ? node.label : type
}

// 放大
const handleZoomIn = () => {
  if (!lf) return
  lf.zoom(true)
}

// 缩小
const handleZoomOut = () => {
  if (!lf) return
  lf.zoom(false)
}

// 重置缩放
const handleResetZoom = () => {
  if (!lf) return
  lf.resetZoom()
}

// 格式化排版
const handleFormatLayout = () => {
  if (isViewMode.value) {
    ElMessage.warning('查看模式下不能编辑')
    return
  }
  if (!lf) return

  const graphData = getLayoutGraphData()
  if (!graphData || graphData.nodes.length === 0) {
    ElMessage.warning('画布中暂无可排版的节点')
    return
  }

  const positions = buildFormattedLayout(graphData.nodes, graphData.edges)

  positions.forEach((position, nodeId) => {
    lf!.graphModel.moveNode2Coordinate(nodeId, position.x, position.y, true)
    syncSelectedNodePosition(nodeId, position)
  })

  lf.fitView(60, 80)
  ElMessage.success('已完成格式化排版')
}

// 清空画布
const handleClearCanvas = async () => {
  if (isViewMode.value) {
    ElMessage.warning('查看模式下不能编辑')
    return
  }
  if (!lf) return

  try {
    await ElMessageBox.confirm('确定要清空画布吗？此操作不可恢复。', '确认清空', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    lf.clearData()
    ElMessage.success('画布已清空')
  } catch {
    // 用户取消
  }
}

// 保存草稿
const handleSave = async () => {
  if (!lf) return

  // 修复 P1：保存前客户端连通性校验（与服务端 validateConfigStructure 对齐）
  const clientValidation = validateBeforeSave()
  if (!clientValidation.valid) {
    ElMessage.error(clientValidation.error || '工作流配置不合法')
    return
  }

  // approved 版本修改了节点/连线时的确认提示
  if (currentVersion.value?.activationStatus === 'approved' && configDirty.value) {
    try {
      await ElMessageBox.confirm(
        '当前版本已审核通过，修改节点/连线配置后保存将使版本回退为草稿状态，需重新提交审核。确定继续保存吗？',
        '版本状态变更确认',
        { confirmButtonText: '确定保存', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      return false
    }
  }

  saving.value = true
  try {
    const graphData = lf.getGraphData() as any
    const projectId = currentProjectId.value
    const desiredVersionMeta = getDesiredVersionMeta()
    if ((currentVersion.value || versionForm.version.trim() || versionForm.name.trim()) && !desiredVersionMeta) {
      return false
    }

    // 转换为后端需要的格式
    const config: WorkflowConfigDTO = {
      versionId: currentVersion.value?.id,
      version: desiredVersionMeta?.version,
      versionName: desiredVersionMeta?.name,
      knowledgeBaseId: versionForm.knowledgeBaseId,
      nodes: graphData.nodes.map((node: any) => ({
        nodeId: node.id!,
        nodeType: node.type as any,
        nodeName: node.text?.value || '',
        // 画布坐标缩放/拖拽后会带小数，后端 position_x/position_y 是 int，提交前取整
        // 坐标缺失时保持不提交（undefined），不要用 ?? 0 兜底，否则节点会被静默挪到原点
        positionX: node.x == null ? undefined : Math.round(node.x),
        positionY: node.y == null ? undefined : Math.round(node.y),
        assigneeType: node.properties?.assigneeType,
        assigneeRoleId: node.properties?.assigneeRoleId,
        assigneeRoleGroupId: node.properties?.assigneeRoleGroupId,
        assigneeOrgId: node.properties?.assigneeOrgId,
        assigneeUserIds: node.properties?.assigneeUserIds,
        timeoutHours: node.properties?.timeoutHours == null ? undefined : Math.round(node.properties.timeoutHours),
        timeoutAction: node.properties?.timeoutAction,
        properties: node.properties
      })),
      edges: graphData.edges.map((edge: any) => ({
        edgeId: edge.id!,
        sourceNodeId: edge.sourceNodeId!,
        targetNodeId: edge.targetNodeId!,
        label: edge.text?.value,
        condition: edge.properties?.condition,
        properties: edge.properties
      }))
    }

    const savedVersion = await saveWorkflowConfig(projectId, config)
    if (savedVersion) {
      applyCurrentVersion(savedVersion)
      await loadVersionHistory()
      await syncEditorVersionRoute(savedVersion.id, projectId)
      syncVersionForm(savedVersion)
    }

    // 配置已成功落库，离开画板时不应再提示存在未保存修改。
    // 提交审核会先调用本方法，清除脏状态后随后的路由跳转可直接放行。
    configDirty.value = false
    ElMessage.success('保存成功')
    return true
  } catch (error) {
    return false
  } finally {
    saving.value = false
  }
}

const escapeHtml = (value: unknown) => String(value ?? '')
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')
  .replace(/'/g, '&#39;')

const buildValidationHtml = (issues: Array<{ message: string; severity: string; path?: string; suggestion?: string }>) => {
  const severityLabel: Record<string, string> = {
    error: '错误',
    warning: '警告',
    info: '提示'
  }
  const severityColor: Record<string, string> = {
    error: '#f56c6c',
    warning: '#e6a23c',
    info: '#409eff'
  }
  const visibleIssues = issues.slice(0, 8)
  const items = visibleIssues.map(issue => {
    const severity = issue.severity || 'info'
    const label = severityLabel[severity] || severity
    const color = severityColor[severity] || '#909399'
    const path = issue.path ? `<div style="color:#909399;font-size:12px;margin-top:2px;">位置：${escapeHtml(issue.path)}</div>` : ''
    const suggestion = issue.suggestion ? `<div style="color:#67c23a;font-size:12px;margin-top:2px;">建议：${escapeHtml(issue.suggestion)}</div>` : ''
    return `<li style="margin:8px 0;line-height:1.5;"><strong style="color:${color};">[${escapeHtml(label)}]</strong> ${escapeHtml(issue.message)}${path}${suggestion}</li>`
  }).join('')
  const more = issues.length > visibleIssues.length ? `<p style="margin-top:8px;color:var(--color-text-tertiary);">还有 ${issues.length - visibleIssues.length} 项问题未展示，请按提示逐项检查。</p>` : ''
  return `<div style="text-align:left;"><ol style="padding-left:18px;margin:0;">${items}</ol>${more}</div>`
}

const validateBeforeSubmitAndConfirm = async (): Promise<boolean> => {
  const versionId = currentVersion.value?.id
  if (!versionId) {
    ElMessage.warning('当前工作流版本尚未保存，请先保存后再提交审核')
    return false
  }
  const report = await validateBeforeSubmit(currentProjectId.value, versionId)
  const issues = report?.issues || []
  if (!issues.length) return true

  const errors = issues.filter(issue => issue.severity === 'error')
  const warnings = issues.filter(issue => issue.severity === 'warning')
  const infos = issues.filter(issue => issue.severity === 'info')

  if (errors.length > 0) {
    await ElMessageBox.alert(
      buildValidationHtml(issues),
      `提交前检查未通过：${errors.length} 个错误`,
      {
        confirmButtonText: '我去修复',
        dangerouslyUseHTMLString: true,
        type: 'error',
        customClass: 'workflow-validation-message-box'
      }
    )
    return false
  }

  await ElMessageBox.confirm(
    buildValidationHtml(issues),
    `发现 ${warnings.length} 个警告${infos.length ? `、${infos.length} 个提示` : ''}`,
    {
      confirmButtonText: '继续提交',
      cancelButtonText: '返回检查',
      dangerouslyUseHTMLString: true,
      type: 'warning',
      customClass: 'workflow-validation-message-box'
    }
  )
  return true
}

// 提交审核
const handleSubmit = async () => {
  if (!lf) return

  // 先保存
  const saved = await handleSave()
  if (!saved) return

  submitting.value = true
  try {
    const canSubmit = await validateBeforeSubmitAndConfirm()
    if (!canSubmit) return

    const versionId = currentVersion.value?.id
    if (!versionId) {
      ElMessage.warning('当前工作流版本尚未保存，请先保存后再提交审核')
      return
    }
    await submitForApproval(currentProjectId.value, versionId)

    ElMessage.success('提交审核成功')
    router.push(returnMenuPath.value)
  } catch (error: any) {
    const report = error?.response?.data?.data || error?.data?.data
    if (report?.issues?.length) {
      await ElMessageBox.alert(
        buildValidationHtml(report.issues),
        '提交审核失败：工作流配置存在异常',
        {
          confirmButtonText: '我去修复',
          dangerouslyUseHTMLString: true,
          type: 'error',
          customClass: 'workflow-validation-message-box'
        }
      )
    }
  } finally {
    submitting.value = false
  }
}

// 关闭抽屉
const handleDrawerClose = () => {
  drawerVisible.value = false
  selectedNode.value = null
  selectedEdge.value = null
}

/**
 * 角色树选择器变更处理：当选中叶子节点（角色ID）时才赋值
 */
function handleRoleTreeSelectChange(val: any) {
  // el-tree-select 的 check-strictly 模式下，点击父节点会选中父节点
  // 但我们只需要叶子节点（角色ID），所以需要判断
  if (typeof val === 'number') {
    nodeForm.assigneeRoleId = val
  } else {
    nodeForm.assigneeRoleId = undefined as any
  }
}

// ========== 抽屉拖拽调整宽度 ==========
function onResizeStart(e: MouseEvent) {
  isResizing.value = true
  const startX = e.clientX
  const startWidth = parseInt(drawerSize.value) || 500
  const maxWidth = window.innerWidth * MAX_DRAWER_WIDTH_RATIO

  const onMouseMove = (ev: MouseEvent) => {
    // RTL drawer: width increases when mouse moves left
    const delta = startX - ev.clientX
    const newWidth = Math.min(Math.max(startWidth + delta, MIN_DRAWER_WIDTH), maxWidth)
    drawerSize.value = `${Math.round(newWidth)}px`
  }

  const onMouseUp = () => {
    isResizing.value = false
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
  }

  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
}

// ========== 根据节点/边类型自适应宽度 ==========
function adjustDrawerSize() {
  if (selectedNode.value) {
    const type = selectedNode.value.properties?.nodeType
    if (type === 'condition') {
      drawerSize.value = '680px'
    } else if (type === 'parallel') {
      drawerSize.value = '580px'
    } else {
      drawerSize.value = '500px'
    }
  } else if (selectedEdge.value) {
    drawerSize.value = '520px'
  }
}

// 离开前确认：处理"工作流发生改变，存在有编辑"场景。
// 返回值约定：
//   true  -> 允许离开
//   false -> 留在当前页
// 弹窗提供三个选项：
//   - 保存草稿并离开（先 handleSave，成功才离开）
//   - 不保存直接离开
//   - 取消（关闭弹窗，留在当前页）
const confirmLeaveIfDirty = async (): Promise<boolean> => {
  // 只读模式或没有未保存修改，直接放行
  if (isViewMode.value || !configDirty.value) return true

  try {
    await ElMessageBox.confirm(
      '当前工作流存在未保存的修改，离开前建议先保存草稿。继续离开将丢失这部分修改，是否保存草稿？',
      '存在未保存的修改',
      {
        confirmButtonText: '保存草稿并离开',
        cancelButtonText: '不保存直接离开',
        showClose: true,
        closeOnClickModal: false,
        closeOnPressEscape: false,
        distinguishCancelAndClose: true,
        type: 'warning',
        buttonSize: 'default'
      }
    )
    // 用户点了"保存草稿并离开"
    const saved = await handleSave()
    return saved === true
  } catch (action: any) {
    // distinguishCancelAndClose: 'cancel' = "不保存直接离开"，'close' = 关闭弹窗
    if (action === 'cancel') return true
    return false
  }
}

// 返回
const goBack = async () => {
  const allowed = await confirmLeaveIfDirty()
  if (!allowed) return
  router.push(returnMenuPath.value)
}

// 路由切换守卫：用户在侧边栏点击其他菜单、或者编辑 URL 触发路由变化时拦截
onBeforeRouteLeave(async () => {
  return await confirmLeaveIfDirty()
})

// 浏览器刷新 / 关闭标签页 / 关闭浏览器兜底
const beforeUnloadHandler = (e: BeforeUnloadEvent) => {
  if (isViewMode.value || !configDirty.value) return
  e.preventDefault()
  // 现代浏览器忽略自定义文案，统一显示原生提示
  e.returnValue = ''
}

onMounted(() => {
  window.addEventListener('beforeunload', beforeUnloadHandler)
})

const loadVersionHistory = async () => {
  try {
    versionHistory.value = await getVersionHistory(currentProjectId.value) || []
    applySuggestedVersionMeta()
  } catch {
    versionHistory.value = []
  }
}

// 加载工作流配置
const loadWorkflowConfig = async () => {
  const versionId = route.query.versionId
  const mode = route.query.mode

  isViewMode.value = mode === 'view'
  isEditMode.value = mode === 'edit'
  applyEditorEditConfig()

  if (versionId) {
    try {
      const version = await getVersionConfig(Number(versionId))
      if (version) {
        applyCurrentVersion(version)
        syncVersionForm(version)

        // 渲染配置（网关尺寸随文字自适应，直接随渲染数据传入）
        if (lf && version.config) {
          const graphData = {
            nodes: version.config.nodes.map(node => {
              const gateway = GATEWAY_TYPES.has(node.nodeType)
              const size = gateway ? gatewaySizeOf(node.nodeName) : null
              return {
                id: node.nodeId,
                type: node.nodeType,
                x: node.positionX,
                y: node.positionY,
                text: node.nodeName,
                ...(size ? { width: size.width, height: size.height } : {}),
                properties: {
                  ...(node.properties || {}),
                  assigneeType: node.assigneeType,
                  assigneeRoleId: node.assigneeRoleId,
                  assigneeUserIds: node.assigneeUserIds,
                  timeoutHours: node.timeoutHours,
                  timeoutAction: node.timeoutAction,
                  ccMode: node.properties?.ccMode ?? (node.properties as any)?.properties?.ccMode ?? 'MESSAGE',
                  nodeStatusCode: node.properties?.nodeStatusCode ?? (node.properties as any)?.properties?.nodeStatusCode,
                  requireAttachment: node.properties?.requireAttachment ?? (node.properties as any)?.properties?.requireAttachment ?? false
                }
              }
            }),
            edges: version.config.edges.map(edge => ({
              id: edge.edgeId,
              type: 'polyline',
              sourceNodeId: edge.sourceNodeId,
              targetNodeId: edge.targetNodeId,
              text: edge.label,
              properties: edge
            }))
          }

          lf.render(graphData)
        }
      }
    } catch (error) {
      console.error('加载工作流配置失败', error)
      ElMessage.error('加载配置失败')
    }
  } else {
    applySuggestedVersionMeta()
  }
}

const loadNodeStatuses = async () => {
  try {
    const result = await nodeStatusApi.list() as any
    nodeStatusOptions.value = Array.isArray(result) ? result : (result?.data || [])
  } catch (error) {
    nodeStatusOptions.value = []
  }
}

onMounted(() => {
  initLogicFlow()
  loadNodeStatuses()
  loadVersionHistory()
  loadWorkflowConfig()
  loadRoleAndUserList()
})

async function loadRoleAndUserList() {
  assigneeOptionsLoading.value = true
  try {
    const [rolesRes, roleGroupsRes, roleTreeRes, usersRes, orgTree, kbRes]: any[] = await Promise.all([
      roleApi.getRoleList(),
      roleApi.getRoleGroups(),
      roleApi.getRoleTree(),
      userApi.getUserList({ pageNum: 1, pageSize: 999 }),
      loadOrgTree(),
      getAllKnowledgeBases()
    ])
    roleList.value = (rolesRes?.data ?? rolesRes ?? [])
    roleGroupList.value = (roleGroupsRes?.data ?? roleGroupsRes ?? [])
    // 构建角色树选择器数据
    const roleTree = (roleTreeRes?.data ?? roleTreeRes ?? [])
    roleTreeSelectOptions.value = roleTree.map((node: any) => ({
      groupId: node.groupId,
      groupName: node.groupName,
      isDefault: node.isDefault,
      children: (node.children || []).map((role: any) => ({
        id: role.id,
        name: role.name,
        code: role.code,
        isDefault: role.isDefault,
      }))
    }))
    allUserList.value = (usersRes?.list ?? [])
    orgTreeData.value = orgTree
    knowledgeBases.value = Array.isArray(kbRes) ? kbRes : (kbRes?.data ?? [])
  } catch {
    // ignore
  } finally {
    assigneeOptionsLoading.value = false
  }
}

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', beforeUnloadHandler)
  if (lf) {
    lf.destroy()
    lf = null
  }
})
</script>

<style scoped lang="scss">
.workflow-editor-page {
  height: 100vh;
  padding: 0;
  background: var(--color-fill-secondary);

  .editor-card {
    height: 100%;
    margin: 0;

    :deep(.el-card__body) {
      height: calc(100% - 60px);
      padding: 0;
    }
  }

  .editor-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .header-left {
      display: flex;
      align-items: center;
      gap: 16px;

      .title-info {
        h2 {
          margin: 0;
          font-size: 18px;
          font-weight: 600;
        }

        .scope-tag {
          margin-top: 6px;
          font-size: 13px;
          color: var(--color-muted-text);
        }

        .version-tag {
          font-size: 14px;
          color: var(--color-muted-text);
          margin-left: 8px;
        }

        .version-editor {
          display: flex;
          align-items: flex-start;
          flex-wrap: wrap;
          gap: 12px;
          margin-top: 8px;
        }

        .version-input-group {
          display: flex;
          flex-direction: column;
          gap: 4px;
        }

        .version-number-input {
          display: flex;
          align-items: center;
          gap: 6px;

          .version-prefix {
            font-size: 14px;
            color: var(--color-text-secondary);
            font-weight: 600;
          }

          :deep(.el-input) {
            width: 140px;
          }
        }

        .version-meta-hint {
          font-size: 12px;
          line-height: 1.4;

          &.info {
            color: var(--color-muted-text);
          }

          &.success {
            color: var(--color-success);
          }

          &.warning {
            color: var(--color-warning);
          }

          &.error {
            color: var(--color-danger);
          }
        }

        .version-name-input {
          width: 320px;
        }

        .knowledge-binding-group {
          flex-direction: row;
          align-items: center;
          gap: 6px;
          margin-top: 4px;
        }

        .knowledge-binding-label {
          font-size: 12px;
          color: var(--color-text-secondary);
          white-space: nowrap;
        }

        .knowledge-binding-select {
          width: 200px;

          :deep(.el-input__wrapper) {
            height: 28px;
          }
        }

        .knowledge-binding-help {
          font-size: 14px;
          color: var(--color-muted-text);
          cursor: help;
        }

        .evaluation-binding-group {
          flex-direction: row;
          align-items: center;
          gap: 6px;
          margin-top: 4px;
        }

        .evaluation-binding-label {
          font-size: 12px;
          color: var(--color-text-secondary);
          white-space: nowrap;
        }

        .evaluation-binding-desc {
          font-size: 12px;
          color: var(--el-text-color-placeholder);
        }

        .evaluation-binding-help {
          font-size: 14px;
          color: var(--color-muted-text);
          cursor: help;
        }

        .knowledge-tag {
          display: inline-flex;
          align-items: center;
          gap: 3px;
          margin-left: 8px;
          padding: 2px 8px;
          font-size: 11px;
          color: var(--color-primary);
          background: rgba(37, 99, 235, 0.08);
          border-radius: 4px;

          .el-icon {
            font-size: 12px;
          }
        }
      }
    }

    .header-right {
      display: flex;
      gap: 12px;
    }
  }

  .approved-edit-warning {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 16px;
    margin: 0;
    font-size: 13px;
    line-height: 1.5;
    color: var(--color-warning-text);
    background: var(--color-warning-bg);
    border-top: 1px solid #f3d19e;

    .el-icon {
      flex-shrink: 0;
      font-size: 16px;
    }
  }

  .editor-container {
    display: flex;
    height: 100%;
    background: var(--color-surface);

    .toolbar-left {
      width: 200px;
      border-right: 1px solid var(--color-border);
      padding: 16px;
      overflow-y: auto;

      .toolbar-title {
        font-size: 14px;
        font-weight: 600;
        margin-bottom: 12px;
        color: var(--color-text-primary);
      }

      .node-palette {
        display: flex;
        flex-direction: column;
        gap: 8px;
        margin-bottom: 24px;

        .palette-node {
          display: flex;
          align-items: center;
          gap: 8px;
          padding: 10px;
          border: 1px solid var(--color-border);
          border-radius: 4px;
          cursor: move;
          transition: all 0.3s;

          &:hover {
            border-color: var(--color-accent);
            background: var(--color-info-light);
          }

          &.is-readonly {
            cursor: not-allowed;
            opacity: 0.65;
          }

          &.is-readonly:hover {
            border-color: var(--color-border);
            background: transparent;
          }

          .node-icon {
            font-size: 18px;
          }

          .node-label {
            font-size: 14px;
          }

          &.start {
            border-color: var(--color-success);
            .node-icon { color: var(--color-success); }
          }

          &.approval {
            border-color: var(--color-accent);
            .node-icon { color: var(--color-accent); }
          }

          &.cc {
            border-color: var(--color-warning);
            .node-icon { color: var(--color-warning); }
          }

          &.condition {
            border-color: var(--color-danger);
            .node-icon { color: var(--color-danger); }
          }

          &.end {
            border-color: var(--color-muted-text);
            .node-icon { color: var(--color-muted-text); }
          }
        }
      }

      .toolbar-section {
        margin-top: 24px;
      }

      .toolbar-actions {
        display: flex;
        flex-direction: column;
        gap: 8px;
      }

      .toolbar-action-btn {
        width: 100%;
        margin-left: 0;
        justify-content: center;

        :deep(span) {
          display: inline-flex;
          align-items: center;
          justify-content: center;
          gap: 6px;
          width: 100%;
        }
      }

      .help-section {
        padding: 12px;
        border-radius: 6px;
        background: var(--color-fill-secondary);

        ol {
          margin: 0;
          padding-left: 18px;
          color: var(--color-text-secondary);
          font-size: 12px;
          line-height: 1.7;
        }
      }
    }

    .canvas-container {
      flex: 1;
      position: relative;
      background: var(--color-surface-alt);

      .logicflow-container {
        width: 100%;
        height: 100%;
      }
    }
  }

  .node-config-panel,
  .edge-config-panel {
    padding: 16px;
  }

  .node-rule-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    column-gap: 24px;
    row-gap: 8px;
    width: 100%;

    :deep(.el-checkbox) {
      align-items: center;
      height: 24px;
      margin-right: 0;
      margin-left: 0;
    }

    :deep(.el-checkbox__label) {
      line-height: 24px;
    }
  }

  .node-notify-panel {
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding: 12px 14px;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-md);
    background: var(--color-surface-alt);
    width: 100%;
  }

  .node-notify-row {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    flex-wrap: nowrap;
  }

  .node-notify-label {
    color: var(--color-text-primary);
    font-size: 13px;
    line-height: 20px;
  }

  .node-notify-help {
    color: var(--color-muted-text);
    cursor: help;
  }

  .node-notify-scope {
    display: flex;
    flex-direction: column;
    gap: 8px;

    :deep(.el-radio) {
      margin-right: 0;
      align-items: flex-start;
      min-height: 22px;
    }

    :deep(.el-radio__label) {
      line-height: 1.5;
      padding-left: 6px;
    }
  }

  .field-perm-divider-title {
    font-size: 14px;
    color: var(--el-text-color-primary);
  }

  .field-perm-help {
    margin-left: 4px;
    color: var(--color-muted-text);
    cursor: help;
  }

  .field-perm-name {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .field-perm-code {
    font-size: 12px;
    color: var(--el-text-color-placeholder);
  }

  .field-perm-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
    margin-top: 6px;
  }

  .node-config-footer {
    position: sticky;
    bottom: 0;
    display: flex;
    justify-content: flex-end;
    gap: 10px;
    padding: 10px 16px;
    margin: 16px -16px -16px;
    background: var(--el-bg-color);
    border-top: 1px solid var(--el-border-color-lighter);

    :deep(.el-button) {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
  }
}

/* ===== 抽屉拖拽调整手柄 ===== */
:deep(.config-drawer .el-drawer__body) {
  position: relative;
  overflow-x: hidden;
}

.drawer-resize-handle {
  position: absolute;
  top: 0;
  left: 0;
  width: 6px;
  height: 100%;
  cursor: col-resize;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;

  &:hover,
  &.active {
    background: rgba(64, 158, 255, 0.08);
  }

  .resize-bar {
    width: 2px;
    height: 32px;
    border-radius: 1px;
    background: var(--el-border-color, #dcdfe6);
    transition: background 0.2s;
  }

  &:hover .resize-bar,
  &.active .resize-bar {
    background: var(--el-color-primary, var(--color-primary));
    height: 48px;
  }
}

/* ===== 查看模式禁用控件：不置灰（保持正常观感），鼠标箭头显示「禁止」光标 ===== */
:deep(.config-drawer .el-form) {
  .el-select__wrapper.is-disabled {
    background-color: var(--el-fill-color-blank);
    box-shadow: 0 0 0 1px var(--el-border-color) inset;
    cursor: not-allowed;

    .el-select__selected-item,
    .el-select__placeholder,
    .el-select__placeholder.is-transparent {
      color: var(--el-select-input-color, var(--el-text-color-regular));
    }
  }

  .el-input.is-disabled .el-input__wrapper {
    background-color: var(--el-fill-color-blank);
    box-shadow: 0 0 0 1px var(--el-border-color) inset;
    cursor: not-allowed;
  }

  .el-input.is-disabled .el-input__inner {
    color: var(--el-text-color-regular);
    -webkit-text-fill-color: var(--el-text-color-regular);
    cursor: not-allowed;
  }

  .el-textarea.is-disabled .el-textarea__inner {
    color: var(--el-text-color-regular);
    background-color: var(--el-fill-color-blank);
    box-shadow: 0 0 0 1px var(--el-border-color) inset;
    cursor: not-allowed;
  }

  // 开关：未勾选时保持关闭态原色（勾选态保留 EP 默认，避免勾选开关被画成关闭观感）
  .el-switch.is-disabled:not(.is-checked) .el-switch__core {
    background: var(--el-switch-off-color);
    border-color: var(--el-switch-off-color);
    opacity: 1;
    cursor: not-allowed;
  }

  .el-checkbox__input.is-disabled .el-checkbox__inner {
    background-color: var(--el-fill-color-blank);
    border-color: var(--el-border-color);
    cursor: not-allowed;
  }

  .el-checkbox__input.is-disabled.is-checked .el-checkbox__inner {
    background-color: var(--el-color-primary);
    border-color: var(--el-color-primary);
  }

  .el-checkbox__input.is-disabled.is-checked .el-checkbox__inner::after {
    border-color: #fff;
  }

  .el-checkbox__input.is-disabled + .el-checkbox__label,
  .el-radio__input.is-disabled + .el-radio__label {
    color: var(--el-text-color-regular);
    cursor: not-allowed;
  }

  .el-radio__input.is-disabled .el-radio__inner {
    background: var(--el-fill-color-blank);
    border-color: var(--el-border-color);
    cursor: not-allowed;
  }

  .el-radio__input.is-disabled.is-checked .el-radio__inner {
    background: var(--el-color-primary);
    border-color: var(--el-color-primary);
  }

  .el-radio__input.is-disabled.is-checked .el-radio__inner::after {
    background: #fff;
  }

  .el-button.is-disabled {
    opacity: 1;
    cursor: not-allowed;
  }

  .el-button--primary.is-disabled {
    color: var(--el-color-white);
    background-color: var(--el-color-primary);
    border-color: var(--el-color-primary);
  }

  .el-button--danger.is-disabled {
    color: var(--el-color-danger);
    background-color: var(--el-fill-color-blank);
    border-color: var(--el-color-danger);
  }

  // ConditionConfig 的自定义按钮
  .btn-remove:disabled,
  .btn-add-rule:disabled {
    opacity: 1;
    color: var(--el-text-color-regular);
    border-color: var(--el-border-color);
    background: transparent;
    cursor: not-allowed;
  }
}
</style>
