<template>
  <PageContainer :title="pageTitle" class="workflow-config-page">
    <div class="workflow-management-page">
      <el-card shadow="never" class="overview-card">
        <div class="overview-header">
          <div>
            <h2>{{ pageTitle }}</h2>
            <p>{{ pageDescription }}</p>
          </div>
          <AppButton type="primary" permission="button:workflow:create" @click="createNewWorkflow">
            <el-icon><Plus /></el-icon>
            新建工作流
          </AppButton>
        </div>

        <div class="overview-metrics">
          <div class="metric-card">
            <div class="metric-value">{{ versions.length }}</div>
            <div class="metric-label">工作流版本</div>
          </div>
          <div class="metric-card success">
            <div class="metric-value">{{ activeVersionCount }}</div>
            <div class="metric-label">启用中版本</div>
          </div>
          <div class="metric-card warning">
            <div class="metric-value">{{ approvalSummary.pending }}</div>
            <div class="metric-label">待审核记录</div>
          </div>
          <div class="metric-card danger">
            <div class="metric-value">{{ approvalSummary.rejected }}</div>
            <div class="metric-label">已拒绝记录</div>
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="management-card">
        <template #header>
          <div class="card-header">
            <div class="header-copy">
              <h3>工作流版本与审核管理</h3>
              <p>支持查看配置、编辑版本、审核处理、启停切换和安全删除。</p>
            </div>
            <el-button @click="reloadAllData">
              <el-icon><Refresh /></el-icon>
              刷新数据
            </el-button>
          </div>
        </template>

        <el-tabs v-model="activeTab" class="management-tabs">
          <el-tab-pane label="版本管理" name="versions">
            <div class="table-toolbar">
              <div class="toolbar-left">
                <el-select v-model="activationFilter" clearable placeholder="启停状态" class="toolbar-control toolbar-control--sm">
                  <el-option label="全部状态" value="" />
                  <el-option label="已启用" value="ACTIVE" />
                  <el-option label="未启用" value="INACTIVE" />
                </el-select>
                <el-select v-model="versionApprovalStatusFilter" clearable placeholder="审核状态" class="toolbar-control toolbar-control--md">
                  <el-option label="全部状态" value="" />
                  <el-option label="草稿" value="DRAFT" />
                  <el-option label="审核中" value="PENDING" />
                  <el-option label="已通过" value="APPROVED" />
                  <el-option label="已拒绝" value="REJECTED" />
                </el-select>
              </div>
              <div class="toolbar-right">
                <AppButton
                  type="success"
                  plain
                  @click="handleImportWorkflow"
                  v-permission="'button:workflow:import'"
                >
                  <el-icon><Upload /></el-icon>
                  导入工作流
                </AppButton>
                <AppButton
                  type="primary"
                  @click="createNewWorkflow"
                  v-permission="'button:workflow:create'"
                >
                  <el-icon><Plus /></el-icon>
                  新建版本
                </AppButton>
                <el-input
                  v-model="versionKeyword"
                  clearable
                  placeholder="搜索版本"
                  class="toolbar-control toolbar-control--keyword"
                />
                <el-tooltip content="列表字段设置">
                  <el-button link :icon="Setting" @click="openVersionColumnConfig" />
                </el-tooltip>
              </div>
            </div>

            <el-table :data="pagedVersions" border v-loading="versionLoading" :cell-style="{ textAlign: 'center' }" :header-cell-style="{ textAlign: 'center' }" class="workflow-table">
              <el-table-column v-if="isVersionColumnVisible('version')" label="版本" min-width="220">
                <template #default="{ row }">
                  <div class="version-name-cell">
                    <div class="version-name-title">{{ row.name }}</div>
                    <div class="version-name-subtitle">V{{ row.version }}</div>
                  </div>
                </template>
              </el-table-column>
              <el-table-column v-if="isVersionColumnVisible('boundTypes')" label="适用工单类型" min-width="180">
                <template #default="{ row }">
                  <template v-if="boundTypeNames[row.id]?.length">
                    <el-tag v-for="name in boundTypeNames[row.id]" :key="name" size="small" effect="light" round class="workflow-tag">{{ name }}</el-tag>
                  </template>
                  <a v-else class="link-hint" @click="goToRequirementConfig(row)">未绑定</a>
                </template>
              </el-table-column>
              <el-table-column v-if="isVersionColumnVisible('projectId')" label="适用范围" min-width="120">
                <template #default="{ row }">
                  <el-tag :type="row.projectId === GLOBAL_WORKFLOW_PROJECT_ID ? 'primary' : 'info'" effect="light" round class="workflow-tag">
                    {{ row.projectId === GLOBAL_WORKFLOW_PROJECT_ID ? '全局流程' : `项目 ${row.projectId}` }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column v-if="isVersionColumnVisible('knowledgeBaseName')" label="知识库" min-width="140">
                <template #default="{ row }">
                  <template v-if="row.knowledgeBaseName">
                    <el-tag type="warning" effect="light" round class="workflow-tag">
                      {{ row.knowledgeBaseName }}
                    </el-tag>
                  </template>
                  <span v-else class="text-secondary">未绑定</span>
                </template>
              </el-table-column>
              <el-table-column v-if="isVersionColumnVisible('isActive')" label="启停状态" min-width="110">
                <template #default="{ row }">
                  <span v-permission="'button:workflow:activate'">
                    <el-tooltip :content="getActivationTooltip(row)" placement="top" :disabled="!isActivationDisabled(row)">
                      <span>
                        <el-switch
                          :model-value="row.isActive === 1"
                          size="small"
                          :disabled="isActivationDisabled(row)"
                          @change="handleToggleActivation(row)"
                          @click.stop
                        />
                      </span>
                    </el-tooltip>
                  </span>
                </template>
              </el-table-column>
              <el-table-column v-if="isVersionColumnVisible('approvalEvaluation')" label="评价状态" min-width="110">
                <template #default="{ row }">
                  <span v-permission="'button:workflow:config'">
                    <el-tooltip content="开启后审批时显示评分（必填）" placement="top" :disabled="!!row.approvalEvaluationEnabled">
                      <span>
                        <el-switch
                          :model-value="!!row.approvalEvaluationEnabled"
                          size="small"
                          @change="handleToggleEvaluation(row)"
                          @click.stop
                        />
                      </span>
                    </el-tooltip>
                  </span>
                </template>
              </el-table-column>
              <el-table-column v-if="isVersionColumnVisible('approvalStatus')" label="审核状态" min-width="110">
                <template #default="{ row }">
                  <el-tag :type="approvalTagType(versionApprovalStatus(row))" effect="light" round class="workflow-tag">
                    {{ approvalStatusLabel(versionApprovalStatus(row)) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column v-if="isVersionColumnVisible('creatorName')" prop="creatorName" label="创建人" min-width="110" />
              <el-table-column v-if="isVersionColumnVisible('createdAt')" label="创建时间" min-width="160">
                <template #default="{ row }">
                  {{ formatDateTime(row.createdAt) }}
                </template>
              </el-table-column>
              <el-table-column v-if="isVersionColumnVisible('latestSubmittedAt')" label="最近提交" min-width="160">
                <template #default="{ row }">
                  {{ row.latestSubmittedAt ? formatDateTime(row.latestSubmittedAt) : '-' }}
                </template>
              </el-table-column>
              <el-table-column v-if="isVersionColumnVisible('activatedAt')" label="启用时间" min-width="160">
                <template #default="{ row }">
                  {{ row.isActive === 1 && row.activatedAt ? formatDateTime(row.activatedAt) : '-' }}
                </template>
              </el-table-column>
              <el-table-column v-if="isVersionColumnVisible('operations')" label="操作" min-width="120" fixed="right">
                <template #default="{ row }">
                  <div class="operation-cell">
                    <el-tooltip content="查看" placement="top">
                      <el-button link type="primary" :icon="View" @click="viewWorkflow(row)" />
                    </el-tooltip>
                    <el-tooltip :content="getEditButtonTooltip(row)" placement="top">
                      <span>
                        <el-button
                          link
                          type="primary"
                          :icon="EditPen"
                          @click="editWorkflow(row)"
                          v-permission="'button:workflow:update'"
                          :disabled="versionApprovalStatus(row) === 'PENDING' || row.isActive === 1"
                        />
                      </span>
                    </el-tooltip>
                    <el-dropdown trigger="click" class="operation-more-dropdown">
                      <el-button link type="primary" :icon="Tools" class="operation-more-button" title="更多操作" aria-label="更多操作" />
                      <template #dropdown>
                        <el-dropdown-menu>
                          <template v-if="canConfig">
                            <el-dropdown-item :icon="CopyDocument" @click="handleCopyWorkflow(row)">
                              复制
                            </el-dropdown-item>
                          </template>
                          <template v-if="canExport && versionApprovalStatus(row) === 'APPROVED'">
                            <el-dropdown-item :icon="Download" @click="handleExportWorkflow(row)">
                              导出
                            </el-dropdown-item>
                          </template>
                          <template v-if="canDelete">
                            <el-dropdown-item divided :icon="Delete" :disabled="row.isActive === 1" @click="handleDeleteVersion(row)">
                              <span style="color: var(--el-color-danger)">删除</span>
                            </el-dropdown-item>
                          </template>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </div>
                </template>
              </el-table-column>
            </el-table>

            <div class="pagination-container">
              <el-pagination
                v-model:current-page="pagination.page"
                v-model:page-size="pagination.size"
                :total="filteredVersions.length"
                :page-sizes="[10, 20, 50, 100]"
                layout="total, sizes, prev, pager, next, jumper"
              />
            </div>
          </el-tab-pane>

          <el-tab-pane label="审核记录" name="approvals">
            <div class="approval-toolbar">
              <div class="toolbar-left">
                <div class="summary-chip pending">待审核 {{ approvalSummary.pending }}</div>
                <div class="summary-chip approved">已通过 {{ approvalSummary.approved }}</div>
                <div class="summary-chip rejected">已拒绝 {{ approvalSummary.rejected }}</div>
              </div>
              <div class="toolbar-right">
                <el-button
                  v-if="canProcessApproval && approvals.length > 0"
                  type="danger"
                  plain
                  @click="handleClearAllApprovals"
                  v-permission="'button:workflow:approve'"
                >
                  <el-icon><Delete /></el-icon>
                  清空全部
                </el-button>
                <el-select v-model="projectFilter" clearable placeholder="项目" class="toolbar-control toolbar-control--md">
                  <el-option
                    v-for="item in projectOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
                <el-input
                  v-model="approvalKeyword"
                  clearable
                  placeholder="搜索项目/版本"
                  class="toolbar-control toolbar-control--keyword"
                />
                <el-select v-model="approvalStatusFilter" placeholder="审核状态" clearable class="toolbar-control toolbar-control--md">
                  <el-option label="全部状态" value="" />
                  <el-option label="待审核" value="PENDING" />
                  <el-option label="已通过" value="APPROVED" />
                  <el-option label="已拒绝" value="REJECTED" />
                </el-select>
              </div>
              <div class="toolbar-right">
                <el-tooltip content="列表字段设置">
                  <el-button link :icon="Tools" @click="openApprovalColumnConfig" />
                </el-tooltip>
              </div>
            </div>

            <el-table v-loading="approvalLoading" :data="pagedApprovals" border class="approval-table workflow-table">
              <el-table-column v-if="isApprovalColumnVisible('task')" label="任务名称" min-width="240">
                <template #default="{ row }">
                  <div class="task-name-cell">
                    <span class="task-name">{{ row.versionName || `V${row.version}` }}</span>
                    <span class="task-project">{{ row.projectName }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column v-if="isApprovalColumnVisible('status')" label="任务状态" min-width="110">
                <template #default="{ row }">
                  <el-tag :type="approvalTagType(row.status)" size="small" effect="light" round class="workflow-tag">
                    {{ approvalStatusLabel(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column v-if="isApprovalColumnVisible('submitter')" label="提交人" min-width="110">
                <template #default="{ row }">{{ row.submitterName || '-' }}</template>
              </el-table-column>
              <el-table-column v-if="isApprovalColumnVisible('submittedAt')" label="提交时间" min-width="160">
                <template #default="{ row }">{{ formatDateTime(row.submittedAt) }}</template>
              </el-table-column>
              <el-table-column v-if="isApprovalColumnVisible('stayDuration')" label="停留时间" min-width="120">
                <template #default="{ row }">
                  {{ getStayDuration(row.submittedAt, row.approvedAt) }}
                </template>
              </el-table-column>
              <el-table-column v-if="isApprovalColumnVisible('approvedAt')" label="结束时间" min-width="160">
                <template #default="{ row }">
                  {{ row.approvedAt ? formatDateTime(row.approvedAt) : '-' }}
                </template>
              </el-table-column>
              <el-table-column v-if="isApprovalColumnVisible('approver')" label="审核人" min-width="110">
                <template #default="{ row }">
                  {{ row.approverName || '-' }}
                </template>
              </el-table-column>
              <el-table-column v-if="isApprovalColumnVisible('comment')" label="审核意见" min-width="180">
                <template #default="{ row }">
                  <el-tooltip v-if="row.comment" :content="row.comment" placement="top">
                    <span class="comment-text">{{ row.comment }}</span>
                  </el-tooltip>
                  <span v-else class="muted-cell">-</span>
                </template>
              </el-table-column>
              <el-table-column v-if="isApprovalColumnVisible('operations')" label="操作" min-width="180" fixed="right">
                <template #default="{ row }">
                  <el-tooltip content="详情" placement="top">
                    <el-button link type="primary" :icon="Document" @click="openDetail(row)" />
                  </el-tooltip>
                  <el-tooltip content="查看配置" placement="top">
                    <el-button link type="primary" :icon="View" @click="handleViewVersion(row)" />
                  </el-tooltip>
                  <template v-if="row.status === 'PENDING' && canProcessApproval">
                    <el-tooltip content="通过" placement="top">
                      <el-button link type="success" :icon="CircleCheck" @click="handleApprove(row)" v-permission="'button:workflow:approve'" />
                    </el-tooltip>
                    <el-tooltip content="拒绝" placement="top">
                      <el-button link type="danger" :icon="CircleClose" @click="handleReject(row)" v-permission="'button:workflow:approve'" />
                    </el-tooltip>
                  </template>
                  <el-tooltip v-else-if="row.status === 'APPROVED'" content="已通过" placement="top">
                    <el-button link type="success" :icon="CircleCheck" disabled />
                  </el-tooltip>
                  <el-tooltip v-else-if="row.status === 'REJECTED'" content="已拒绝" placement="top">
                    <el-button link type="danger" :icon="CircleClose" disabled />
                  </el-tooltip>
                  <el-tooltip v-if="canProcessApproval" content="删除记录" placement="top">
                    <el-button link type="danger" :icon="Delete" @click="handleDeleteApproval(row)" v-permission="'button:workflow:approve'" />
                  </el-tooltip>
                </template>
              </el-table-column>
            </el-table>
            <div class="pagination-container">
              <el-pagination
                v-model:current-page="approvalPagination.page"
                v-model:page-size="approvalPagination.size"
                :total="filteredApprovals.length"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next"
              />
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </div>

    <el-dialog v-model="versionDialogVisible" title="编辑版本信息" width="460px">
      <el-form label-position="top">
        <el-form-item label="版本号">
          <el-input
            v-model="versionDialogForm.version"
            placeholder="例如 1.0.0"
            maxlength="20"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="版本名称">
          <el-input
            v-model="versionDialogForm.name"
            maxlength="50"
            show-word-limit
            placeholder="请输入版本名称"
          />
        </el-form-item>
        <div v-if="versionDialogHint" class="dialog-hint" :class="versionDialogHint.type">
          {{ versionDialogHint.message }}
        </div>
      </el-form>
      <template #footer>
        <el-button @click="versionDialogVisible = false">取消</el-button>
        <AppButton type="primary" :loading="versionSaving" permission="button:workflow:update" @click="handleSaveVersionMeta">保存</AppButton>
      </template>
    </el-dialog>

    <el-dialog
      v-model="processDialogVisible"
      :title="processAction === 'approve' ? '审核通过' : '审核拒绝'"
      width="500px"
      class="process-dialog"
    >
      <div v-if="currentTask" class="process-content">
        <el-descriptions :column="1" border class="task-info">
          <el-descriptions-item label="工作流版本">
            {{ currentTask.versionName || `V${currentTask.version}` }}
          </el-descriptions-item>
          <el-descriptions-item label="提交人">
            {{ currentTask.submitterName }}
          </el-descriptions-item>
          <el-descriptions-item label="提交时间">
            {{ formatDateTime(currentTask.submittedAt) }}
          </el-descriptions-item>
        </el-descriptions>

        <el-form class="comment-form">
          <el-form-item label="适用范围">
            <el-select v-model="processScope" style="width: 100%">
              <el-option
                v-for="item in processScopeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="审核意见" required>
            <el-input
              v-model="processComment"
              type="textarea"
              :rows="4"
              :placeholder="processAction === 'approve' ? '请输入通过意见（可选）' : '请输入拒绝原因'"
              :maxlength="500"
              show-word-limit
            />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="processDialogVisible = false">取消</el-button>
        <AppButton
          :type="processAction === 'approve' ? 'primary' : 'danger'"
          :loading="submitting"
          permission="button:workflow:approve"
          @click="confirmProcess"
        >
          确认{{ processAction === 'approve' ? '通过' : '拒绝' }}
        </AppButton>
      </template>
    </el-dialog>

    <!-- 版本管理 列设置 -->
    <ColumnConfigDialog
      v-model="showVersionColumnConfig"
      :column-groups="versionColumnGroups"
      :draft-selected-columns="versionDraftSelectedColumns"
      :draft-column-keys="versionDraftColumnKeys"
      @update:draft-column-keys="versionDraftColumnKeys = $event"
      @remove="removeVersionDraftColumn"
      @save="saveVersionColumns"
    />

    <!-- 审核记录 列设置 -->
    <ColumnConfigDialog
      v-model="showApprovalColumnConfig"
      :column-groups="approvalColumnGroups"
      :draft-selected-columns="approvalDraftSelectedColumns"
      :draft-column-keys="approvalDraftColumnKeys"
      @update:draft-column-keys="approvalDraftColumnKeys = $event"
      @remove="removeApprovalDraftColumn"
      @save="saveApprovalColumns"
    />

    <el-drawer
      v-model="detailDrawerVisible"
      title="审核详情"
      size="480px"
      direction="rtl"
    >
      <template v-if="detailTask">
        <div class="detail-section">
          <div class="detail-header">
            <div>
              <div class="detail-title">{{ detailTask.versionName || `V${detailTask.version}` }}</div>
              <div class="detail-subtitle">{{ detailTask.projectName || '未命名项目' }}</div>
            </div>
            <el-tag :type="approvalTagType(detailTask.status)" effect="light">
              {{ approvalStatusLabel(detailTask.status) }}
            </el-tag>
          </div>

          <el-descriptions :column="1" border>
            <el-descriptions-item label="版本号">
              V{{ detailTask.version || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="提交人">
              {{ detailTask.submitterName || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="提交时间">
              {{ formatDateTime(detailTask.submittedAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="审核人">
              {{ detailTask.approverName || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="结束时间">
              {{ detailTask.approvedAt ? formatDateTime(detailTask.approvedAt) : '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="审核意见">
              {{ detailTask.comment || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="detail-section">
          <div class="detail-section-title">流程概览</div>
          <div v-loading="detailLoading" class="graph-summary">
            <template v-if="detailVersionConfig">
              <div class="summary-grid">
                <div class="summary-card">
                  <div class="summary-value">{{ detailVersionConfig.nodes.length }}</div>
                  <div class="summary-label">节点数</div>
                </div>
                <div class="summary-card">
                  <div class="summary-value">{{ detailVersionConfig.edges.length }}</div>
                  <div class="summary-label">连线数</div>
                </div>
                <div class="summary-card">
                  <div class="summary-value">{{ nodeTypeCount('approval') }}</div>
                  <div class="summary-label">审批节点</div>
                </div>
                <div class="summary-card">
                  <div class="summary-value">{{ nodeTypeCount('condition') }}</div>
                  <div class="summary-label">条件节点</div>
                </div>
              </div>
              <div class="node-list">
                <div class="detail-section-title minor">节点清单</div>
                <div
                  v-for="node in detailVersionConfig.nodes"
                  :key="node.nodeId"
                  class="node-item"
                >
                  <span class="node-name">{{ node.nodeName }}</span>
                  <el-tag size="small" effect="plain">{{ getNodeTypeLabel(node.nodeType) }}</el-tag>
                </div>
              </div>
            </template>
            <el-empty v-else description="暂无流程详情" :image-size="80" />
          </div>
        </div>

        <div class="drawer-actions">
          <el-button :icon="View" @click="handleViewVersion(detailTask)">查看配置</el-button>
          <el-button
            v-if="detailTask.status === 'PENDING' && canProcessApproval"
            type="primary"
            @click="handleApprove(detailTask)"
            v-permission="'button:workflow:approve'"
          >
            审核通过
          </el-button>
          <el-button
            v-if="detailTask.status === 'PENDING' && canProcessApproval"
            type="danger"
            @click="handleReject(detailTask)"
            v-permission="'button:workflow:approve'"
          >
            审核拒绝
          </el-button>
        </div>
      </template>
    </el-drawer>

    <WorkflowCopyDialog ref="copyDialogRef" :project-id="0" @success="loadVersions" />
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Setting, CircleCheck, CircleClose, Document, Delete, View, EditPen, Tools, CopyDocument, Download, Upload } from '@element-plus/icons-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import AppButton from '@/components/common/AppButton.vue'
import ColumnConfigDialog from '@/components/common/ColumnConfigDialog.vue'
import WorkflowCopyDialog from './components/WorkflowCopyDialog.vue'
import { useColumnConfig, type ColumnDef } from '@/composables/useColumnConfig'
import { formatDate as formatDateTime } from '@/utils/format'
import { resolveActiveMenuPath } from '@/utils/menuNavigation'
import { resolveErrorMessage } from '@/utils/error'
import { isWorkflowVersion, sameWorkflowVersion } from '@/utils/workflowVersion'
import { usePermission } from '@/composables/usePermission'
import {
  GLOBAL_WORKFLOW_PROJECT_ID,
  approveWorkflow,
  clearAllWorkflowApprovals,
  deleteWorkflowApproval,
  deleteWorkflowVersion,
  getVersionConfig,
  getVersionHistory,
  getWorkflowApprovals,
  rejectWorkflow,
  updateWorkflowVersionActivation,
  updateWorkflowVersionMeta,
  validateWorkflowVersion,
  exportWorkflowVersion,
  importWorkflowVersion,
} from '@/api/modules/workflow-visual'
import type {
  WorkflowApprovalDTO,
  WorkflowConfigDTO,
  WorkflowValidationIssue,
  WorkflowVersionDTO,
  WorkflowVersionMetaUpdateDTO,
  WorkflowExportData,
  WorkflowImportResponse,
} from '@/types/workflow-visual'

import { requirementConfigApi, type RequirementType } from '@/api/modules/requirementConfig'

// ── 列表字段设置：版本管理表 ──
const versionAllColumns: ColumnDef[] = [
  { key: 'version', label: '版本', group: '基础字段', minWidth: 200 },
  { key: 'boundTypes', label: '适用工单类型', group: '基础字段', minWidth: 160 },
  { key: 'projectId', label: '适用范围', group: '基础字段', width: 120 },
  { key: 'knowledgeBaseName', label: '知识库', group: '基础字段', minWidth: 140 },
  { key: 'isActive', label: '启停状态', group: '状态信息', width: 120 },
  { key: 'approvalEvaluation', label: '评价状态', group: '状态信息', width: 120 },
  { key: 'approvalStatus', label: '审核状态', group: '状态信息', width: 140 },
  { key: 'creatorName', label: '创建人', group: '人员与时间', width: 140 },
  { key: 'createdAt', label: '创建时间', group: '人员与时间', width: 180 },
  { key: 'latestSubmittedAt', label: '最近提交', group: '人员与时间', width: 180 },
  { key: 'activatedAt', label: '启用时间', group: '人员与时间', width: 180 },
  { key: 'operations', label: '操作', width: 120 },
]
const versionDefaultKeys = ['version', 'boundTypes', 'projectId', 'knowledgeBaseName', 'isActive', 'approvalEvaluation', 'approvalStatus', 'creatorName', 'createdAt', 'operations']

const {
  showColumnConfig: showVersionColumnConfig,
  openColumnConfig: openVersionColumnConfig,
  saveColumns: saveVersionColumns,
  loadColumnConfig: loadVersionColumnConfig,
  columnGroups: versionColumnGroups,
  draftSelectedColumns: versionDraftSelectedColumns,
  draftColumnKeys: versionDraftColumnKeys,
  visibleColumns: versionVisibleColumns,
  removeDraftColumn: removeVersionDraftColumn,
} = useColumnConfig({
  pageKey: 'workflow_version_list',
  columns: versionAllColumns,
  defaultKeys: versionDefaultKeys,
})

function isVersionColumnVisible(key: string) {
  return versionVisibleColumns.value.some((c) => c.key === key)
}

// ── 列表字段设置：审核记录表 ──
const approvalAllColumns: ColumnDef[] = [
  { key: 'task', label: '任务名称', group: '基础字段', minWidth: 240 },
  { key: 'status', label: '任务状态', group: '状态信息', width: 120 },
  { key: 'submitter', label: '提交人', group: '人员与时间', width: 120 },
  { key: 'submittedAt', label: '提交时间', group: '人员与时间', width: 180 },
  { key: 'stayDuration', label: '停留时间', group: '人员与时间', width: 120 },
  { key: 'approvedAt', label: '结束时间', group: '人员与时间', width: 180 },
  { key: 'approver', label: '审核人', group: '人员与时间', width: 120 },
  { key: 'comment', label: '审核意见', group: '状态信息', minWidth: 180 },
  { key: 'operations', label: '操作', width: 180 },
]
const approvalDefaultKeys = ['task', 'status', 'submitter', 'submittedAt', 'stayDuration', 'approvedAt', 'approver', 'comment', 'operations']

const {
  showColumnConfig: showApprovalColumnConfig,
  openColumnConfig: openApprovalColumnConfig,
  saveColumns: saveApprovalColumns,
  loadColumnConfig: loadApprovalColumnConfig,
  columnGroups: approvalColumnGroups,
  draftSelectedColumns: approvalDraftSelectedColumns,
  draftColumnKeys: approvalDraftColumnKeys,
  visibleColumns: approvalVisibleColumns,
  removeDraftColumn: removeApprovalDraftColumn,
} = useColumnConfig({
  pageKey: 'workflow_approval_list',
  columns: approvalAllColumns,
  defaultKeys: approvalDefaultKeys,
})

function isApprovalColumnVisible(key: string) {
  return approvalVisibleColumns.value.some((c) => c.key === key)
}

const route = useRoute()
const router = useRouter()
const { hasPermission } = usePermission()

// 需求类型列表（用于"适用工单类型"列展示）
const requirementTypes = ref<RequirementType[]>([])

// 工作流版本 → 绑定的类型名称映射
const boundTypeNames = computed<Record<number, string[]>>(() => {
  const map: Record<number, string[]> = {}
  for (const type of requirementTypes.value) {
    if (type.workflowVersionId) {
      if (!map[type.workflowVersionId]) {
        map[type.workflowVersionId] = []
      }
      map[type.workflowVersionId].push(type.name)
    }
  }
  return map
})

const versionLoading = ref(false)
const approvalLoading = ref(false)
const versions = ref<WorkflowVersionDTO[]>([])
const approvals = ref<WorkflowApprovalDTO[]>([])
const activeTab = ref<'versions' | 'approvals'>('versions')
const activationFilter = ref('')
const versionApprovalStatusFilter = ref('')
const versionKeyword = ref('')
const approvalStatusFilter = ref('')
const approvalKeyword = ref('')
const approvalPagination = reactive({ page: 1, size: 10 })
const projectFilter = ref<number | undefined>()
const versionDialogVisible = ref(false)
const versionSaving = ref(false)
const editingVersion = ref<WorkflowVersionDTO>()
const processDialogVisible = ref(false)
const currentTask = ref<WorkflowApprovalDTO | null>(null)
const processAction = ref<'approve' | 'reject'>('approve')
const processComment = ref('')
const processScope = ref<number>(GLOBAL_WORKFLOW_PROJECT_ID)
const submitting = ref(false)
const detailDrawerVisible = ref(false)
const detailLoading = ref(false)
const detailTask = ref<WorkflowApprovalDTO | null>(null)
const detailVersionConfig = ref<WorkflowConfigDTO | null>(null)
const copyDialogRef = ref<InstanceType<typeof WorkflowCopyDialog>>()

const versionDialogForm = reactive<WorkflowVersionMetaUpdateDTO>({
  version: '',
  name: '',
})

const pagination = reactive({
  page: 1,
  size: 10,
})

const pageTitle = '工作流配置'
const pageDescription = '维护工作流版本、流程审核和生效状态。'
const canProcessApproval = computed(() => hasPermission('button:workflow:approve'))
const canConfig = computed(() => hasPermission('button:workflow:config'))
const canExport = computed(() => hasPermission('button:workflow:export'))
const canActivate = computed(() => hasPermission('button:workflow:activate'))
const canDelete = computed(() => hasPermission('button:workflow:delete'))
const activeVersionCount = computed(() => versions.value.filter(item => item.isActive === 1).length)
const sourceMenuPath = computed(() => resolveActiveMenuPath(route))
const approvalSummary = computed(() => ({
  pending: approvals.value.filter(item => item.status === 'PENDING').length,
  approved: approvals.value.filter(item => item.status === 'APPROVED').length,
  rejected: approvals.value.filter(item => item.status === 'REJECTED').length,
}))
const projectOptions = computed(() => {
  const projectMap = new Map<number, string>()
  approvals.value.forEach((item) => {
    if (typeof item.projectId === 'number' && item.projectName) {
      projectMap.set(item.projectId, item.projectName)
    }
  })
  return Array.from(projectMap.entries())
    .map(([value, label]) => ({ value, label }))
    .sort((left, right) => left.label.localeCompare(right.label, 'zh-CN'))
})
/** 适用范围选项：始终包含“全局工作流”，再合并已有项目 */
const processScopeOptions = computed(() => {
  const projectEntries = projectOptions.value
    .filter((item) => item.value !== GLOBAL_WORKFLOW_PROJECT_ID)
    .map((item) => ({ value: item.value, label: item.label || `项目 ${item.value}` }))
  return [
    { value: GLOBAL_WORKFLOW_PROJECT_ID, label: '全局工作流' },
    ...projectEntries,
  ]
})
const duplicatedVersion = computed(() => {
  const normalizedVersion = versionDialogForm.version.trim()
  if (!editingVersion.value?.id || !normalizedVersion) return undefined
  return versions.value.find((item) => sameWorkflowVersion(item.version, normalizedVersion) && item.id !== editingVersion.value?.id)
})
const versionDialogHint = computed(() => {
  const trimmedName = versionDialogForm.name.trim()
  const trimmedVersion = versionDialogForm.version.trim()
  if (!trimmedVersion) {
    return { type: 'warning', message: '版本号不能为空' }
  }
  if (!isWorkflowVersion(trimmedVersion)) {
    return { type: 'warning', message: '版本号格式需为正整数或 1.0.0' }
  }
  if (duplicatedVersion.value) {
    return { type: 'error', message: `版本号 V${trimmedVersion} 已存在` }
  }
  if (!trimmedName) {
    return { type: 'warning', message: '版本名称不能为空' }
  }
  return { type: 'success', message: '版本信息可保存' }
})

const filteredVersions = computed(() => {
  const keyword = versionKeyword.value.trim().toLowerCase()
  return versions.value.filter((item) => {
    const approvalStatus = versionApprovalStatus(item)
    const matchesActivation = !activationFilter.value
      || (activationFilter.value === 'ACTIVE' && item.isActive === 1)
      || (activationFilter.value === 'INACTIVE' && item.isActive !== 1)
    const matchesApproval = !versionApprovalStatusFilter.value || approvalStatus === versionApprovalStatusFilter.value
    if (!matchesActivation || !matchesApproval) {
      return false
    }
    if (!keyword) {
      return true
    }
    return `${item.name} V${item.version}`.toLowerCase().includes(keyword)
  })
})

const pagedVersions = computed(() => {
  const start = (pagination.page - 1) * pagination.size
  return filteredVersions.value.slice(start, start + pagination.size)
})

const filteredApprovals = computed(() => {
  return approvals.value.filter((item) => {
    const matchesStatus = !approvalStatusFilter.value || item.status === approvalStatusFilter.value
    const matchesProject = projectFilter.value === undefined || item.projectId === projectFilter.value
    if (!matchesStatus || !matchesProject) {
      return false
    }

    const keyword = approvalKeyword.value.trim().toLowerCase()
    if (!keyword) {
      return true
    }

    const searchText = `${item.projectName || ''} ${item.versionName || ''} V${item.version || ''}`.toLowerCase()
    return searchText.includes(keyword)
  })
})
const pagedApprovals = computed(() => {
  const start = (approvalPagination.page - 1) * approvalPagination.size
  return filteredApprovals.value.slice(start, start + approvalPagination.size)
})

watch(
  () => [route.query.tab, route.query.status, route.query.keyword, route.query.projectId],
  () => {
    const routeTab = Array.isArray(route.query.tab) ? route.query.tab[0] : route.query.tab
    activeTab.value = routeTab === 'approvals' ? 'approvals' : 'versions'

    const routeStatus = Array.isArray(route.query.status) ? route.query.status[0] : route.query.status
    const routeKeyword = Array.isArray(route.query.keyword) ? route.query.keyword[0] : route.query.keyword
    const routeProjectId = Array.isArray(route.query.projectId) ? route.query.projectId[0] : route.query.projectId

    approvalStatusFilter.value = typeof routeStatus === 'string' ? routeStatus : ''
    approvalKeyword.value = typeof routeKeyword === 'string' ? routeKeyword : ''
    projectFilter.value = routeProjectId ? Number(routeProjectId) : undefined
  },
  { immediate: true },
)

watch(
  () => [activationFilter.value, versionApprovalStatusFilter.value, versionKeyword.value],
  () => {
    pagination.page = 1
  },
)
watch(
  () => [approvalStatusFilter.value, approvalKeyword.value, projectFilter.value],
  () => {
    approvalPagination.page = 1
  },
)

function versionApprovalStatus(row: WorkflowVersionDTO) {
  return row.latestApprovalStatus || 'DRAFT'
}

/** 编辑按钮的 tooltip 文案：根据禁用原因动态显示 */
function getEditButtonTooltip(row: WorkflowVersionDTO) {
  if (row.isActive === 1) return '当前工作流启用中，请先停用工作流再进行操作'
  if (versionApprovalStatus(row) === 'PENDING') return '该版本审核中，暂不可编辑'
  return '编辑'
}

/** 启停开关是否禁用 */
function isActivationDisabled(row: WorkflowVersionDTO) {
  return !canActivate.value || (row.isActive !== 1 && versionApprovalStatus(row) !== 'APPROVED')
}

/** 启停开关禁用时的 tooltip 文案 */
function getActivationTooltip(row: WorkflowVersionDTO) {
  if (!canActivate.value) return '暂无启停权限'
  if (row.isActive !== 1 && versionApprovalStatus(row) !== 'APPROVED') return '仅已通过的版本可启用'
  return row.isActive === 1 ? '点击停用' : '点击启用'
}

function approvalStatusLabel(status: string) {
  const map: Record<string, string> = {
    DRAFT: '草稿',
    PENDING: '待审核',
    APPROVED: '已通过',
    REJECTED: '已拒绝',
  }
  return map[status] || status
}

function approvalTagType(status: string) {
  const map: Record<string, string> = {
    DRAFT: 'info',
    PENDING: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger',
  }
  return map[status] || 'info'
}

function getStayDuration(submittedAt?: string, approvedAt?: string): string {
  const start = parseDateTime(submittedAt)
  if (!start) return '-'
  const end = parseDateTime(approvedAt) || Date.now()
  const diffSeconds = Math.floor((end - start) / 1000)
  if (!Number.isFinite(diffSeconds) || diffSeconds < 0) return '-'
  return formatStayDuration(diffSeconds)
}

function parseDateTime(value?: string): number | null {
  if (!value) return null
  const timestamp = new Date(value.replace(' ', 'T')).getTime()
  return Number.isFinite(timestamp) ? timestamp : null
}

function formatStayDuration(seconds: number): string {
  if (seconds < 60) {
    return `${seconds}秒`
  }

  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) {
    return `${minutes}分钟`
  }

  const hours = Math.floor(minutes / 60)
  if (hours < 24) {
    return `${hours}小时${minutes % 60}分钟`
  }

  const days = Math.floor(hours / 24)
  return `${days}天${hours % 24}小时`
}

function getNodeTypeLabel(type?: string) {
  const map: Record<string, string> = {
    start: '开始节点',
    approval: '审批节点',
    cc: '抄送节点',
    condition: '条件节点',
    end: '结束节点',
  }
  return map[type || ''] || type || '未定义'
}

function nodeTypeCount(type: string) {
  return detailVersionConfig.value?.nodes.filter(item => item.nodeType === type).length || 0
}

async function loadVersions() {
  versionLoading.value = true
  try {
    versions.value = await getVersionHistory(GLOBAL_WORKFLOW_PROJECT_ID) || []
    const maxPage = Math.max(1, Math.ceil(filteredVersions.value.length / pagination.size))
    if (pagination.page > maxPage) {
      pagination.page = maxPage
    }
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '加载工作流版本失败'))
    versions.value = []
  } finally {
    versionLoading.value = false
  }
}

async function loadApprovals() {
  approvalLoading.value = true
  try {
    approvals.value = await getWorkflowApprovals() || []
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '加载工作流审核记录失败'))
    approvals.value = []
  } finally {
    approvalLoading.value = false
  }
}

async function loadRequirementTypes() {
  try {
    const res = await requirementConfigApi.listTypes()
    requirementTypes.value = Array.isArray(res) ? res : []
  } catch (error) {
    console.error(error)
    requirementTypes.value = []
  }
}

async function reloadAllData() {
  await Promise.all([loadVersions(), loadApprovals(), loadRequirementTypes()])
}

function createNewWorkflow() {
  router.push({
    path: '/system/workflow-config/editor',
    query: {
      projectId: String(GLOBAL_WORKFLOW_PROJECT_ID),
      sourceMenu: sourceMenuPath.value,
    },
  })
}

function handleCopyWorkflow(version: WorkflowVersionDTO) {
  copyDialogRef.value?.open('copy', version.id)
}

function viewWorkflow(row: WorkflowVersionDTO) {
  router.push({
    path: '/system/workflow-config/editor',
    query: {
      versionId: row.id,
      projectId: String(row.projectId ?? GLOBAL_WORKFLOW_PROJECT_ID),
      mode: 'view',
      sourceMenu: sourceMenuPath.value,
    },
  })
}

function editWorkflow(row: WorkflowVersionDTO) {
  router.push({
    path: '/system/workflow-config/editor',
    query: {
      versionId: row.id,
      projectId: String(row.projectId ?? GLOBAL_WORKFLOW_PROJECT_ID),
      mode: 'edit',
      sourceMenu: sourceMenuPath.value,
    },
  })
}

function focusApprovalHistory(row: WorkflowVersionDTO) {
  activeTab.value = 'approvals'
  approvalStatusFilter.value = versionApprovalStatus(row) === 'DRAFT' ? '' : versionApprovalStatus(row)
  approvalKeyword.value = row.name
  projectFilter.value = row.projectId
  router.replace({
    path: route.path,
    query: {
      ...route.query,
      tab: 'approvals',
      status: approvalStatusFilter.value || undefined,
      keyword: approvalKeyword.value || undefined,
      projectId: row.projectId !== undefined && row.projectId !== null ? String(row.projectId) : undefined,
    },
  })
}

const openVersionMetaDialog = (row: WorkflowVersionDTO) => {
  editingVersion.value = row
  versionDialogForm.version = row.version
  versionDialogForm.name = row.name
  versionDialogVisible.value = true
}

const handleSaveVersionMeta = async () => {
  if (!editingVersion.value) return

  const trimmedVersion = versionDialogForm.version.trim()
  const trimmedName = versionDialogForm.name.trim()
  if (!trimmedVersion) {
    ElMessage.warning('版本号不能为空')
    return
  }
  if (!isWorkflowVersion(trimmedVersion)) {
    ElMessage.warning('版本号格式需为正整数或 1.0.0')
    return
  }
  if (duplicatedVersion.value) {
    ElMessage.warning(`版本号 V${trimmedVersion} 已存在，请重新输入`)
    return
  }
  if (!trimmedName) {
    ElMessage.warning('版本名称不能为空')
    return
  }

  versionSaving.value = true
  try {
    await updateWorkflowVersionMeta(editingVersion.value.id, {
      version: trimmedVersion,
      name: trimmedName,
    })
    ElMessage.success('版本信息已更新')
    versionDialogVisible.value = false
    await loadVersions()
  } finally {
    versionSaving.value = false
  }
}

function formatValidationIssues(issues: WorkflowValidationIssue[]) {
  const errors = issues.filter((item) => item.severity === 'error')
  if (errors.length === 0) {
    return ''
  }
  return errors.map((item) => item.message).join('\n')
}

/** 切换评价状态 */
async function handleToggleEvaluation(row: WorkflowVersionDTO) {
  const enabled = !row.approvalEvaluationEnabled
  try {
    await updateWorkflowVersionMeta(row.id, {
      version: row.version,
      name: row.name,
      approvalEvaluationEnabled: enabled,
    })
    ElMessage.success(enabled ? '评价已开启' : '评价已关闭')
    await loadVersions()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '操作失败'))
  }
}

async function handleToggleActivation(row: WorkflowVersionDTO) {
  const targetActive = row.isActive !== 1

  if (targetActive) {
    if (versionApprovalStatus(row) !== 'APPROVED') {
      ElMessage.warning('请先完成审核并通过后再启用')
      return
    }

    // 检查是否绑定了工单类型
    const hasBoundTypes = boundTypeNames.value[row.id]?.length > 0
    if (!hasBoundTypes) {
      try {
        await ElMessageBox.confirm(
          '该工作流尚未绑定任何工单类型，启用后无法被需求使用。是否前往需求配置绑定工单类型？',
          '工单类型未绑定',
          {
            confirmButtonText: '前往绑定',
            cancelButtonText: '仍然启用',
            type: 'warning',
            distinguishCancelAndClose: true,
          }
        )
        // 用户选择”前往绑定”
        router.push('/settings/requirements')
        return
      } catch (action: unknown) {
        // 用户选择”仍然启用”（cancel），继续启用流程
        // 用户选择关闭（close），中止操作
        if (action === 'close') return
      }
    }

    const issues = (await validateWorkflowVersion(row.id)) || []
    const errorText = formatValidationIssues(issues)
    if (errorText) {
      await ElMessageBox.alert(errorText, '启用前校验未通过', { type: 'error' })
      return
    }
  }

  try {
    await updateWorkflowVersionActivation(row.id, { active: targetActive })
    ElMessage.success(targetActive ? '已启用' : '已停用')
    await reloadAllData()
  } catch (error: unknown) {
    const issues = (error as { data?: WorkflowValidationIssue[] })?.data
    if (Array.isArray(issues) && issues.length > 0) {
      await ElMessageBox.alert(formatValidationIssues(issues), '启用失败', { type: 'error' })
      return
    }
    throw error
  }
}

async function handleDeleteVersion(row: WorkflowVersionDTO) {
  await ElMessageBox.confirm(`确认删除工作流"${row.name}"吗？该操作无法恢复。`, '删除工作流', { type: 'warning' })
  await deleteWorkflowVersion(row.id)
  ElMessage.success('工作流已删除')
  await reloadAllData()
}

/** 点击"未绑定"跳转到需求配置页面绑定工单类型 */
function goToRequirementConfig(_row?: WorkflowVersionDTO) {
  router.push('/settings/requirements')
}

async function openDetail(row: WorkflowApprovalDTO) {
  detailTask.value = row
  detailDrawerVisible.value = true
  detailLoading.value = true
  detailVersionConfig.value = null
  try {
    const version = await getVersionConfig(row.workflowVersionId)
    detailVersionConfig.value = version.config || null
  } catch (error) {
    console.error(error)
    ElMessage.error(resolveErrorMessage(error, '加载流程详情失败'))
  } finally {
    detailLoading.value = false
  }
}

function handleApprove(row: WorkflowApprovalDTO) {
  currentTask.value = row
  processAction.value = 'approve'
  processComment.value = '同意'
  processScope.value = typeof row.projectId === 'number' ? row.projectId : GLOBAL_WORKFLOW_PROJECT_ID
  processDialogVisible.value = true
  detailDrawerVisible.value = false
}

function handleReject(row: WorkflowApprovalDTO) {
  currentTask.value = row
  processAction.value = 'reject'
  processComment.value = ''
  processScope.value = typeof row.projectId === 'number' ? row.projectId : GLOBAL_WORKFLOW_PROJECT_ID
  processDialogVisible.value = true
  detailDrawerVisible.value = false
}

function handleViewVersion(row: WorkflowApprovalDTO) {
  if (!row.workflowVersionId) return
  // 审核记录 tab 跳转时带上 tab 参数，确保返回时回到审核记录列表
  const sourceMenu = activeTab.value === 'approvals'
    ? `${sourceMenuPath.value}?tab=approvals`
    : sourceMenuPath.value
  router.push({
    path: '/system/workflow-config/editor',
    query: {
      versionId: row.workflowVersionId,
      mode: 'view',
      sourceMenu,
    },
  })
}

async function handleDeleteApproval(row: WorkflowApprovalDTO) {
  const label = row.versionName || `V${row.version}`
  try {
    await ElMessageBox.confirm(
      `确定要删除审核记录"${label}"吗？删除后不可恢复。`,
      '删除确认',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
    await deleteWorkflowApproval(row.id)
    ElMessage.success('删除成功')
    await reloadAllData()
  } catch {
    // 用户取消
  }
}

async function handleClearAllApprovals() {
  if (approvals.value.length === 0) {
    ElMessage.info('当前没有审核记录可清空')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要清空全部 ${approvals.value.length} 条审核记录吗？删除后不可恢复。`,
      '清空确认',
      { confirmButtonText: '确定清空', cancelButtonText: '取消', type: 'warning' },
    )
    await clearAllWorkflowApprovals()
    ElMessage.success('已清空全部审核记录')
    await reloadAllData()
  } catch {
    // 用户取消
  }
}

async function confirmProcess() {
  if (!currentTask.value) return
  if (!canProcessApproval.value) {
    ElMessage.warning('当前账号仅可查看审核记录，无法执行审批操作')
    return
  }
  if (processAction.value === 'reject' && !processComment.value.trim()) {
    ElMessage.warning('请输入拒绝原因')
    return
  }

  submitting.value = true
  try {
    if (processAction.value === 'approve') {
      await approveWorkflow(currentTask.value.id, { comment: processComment.value })
      ElMessage.success('审核通过，请在版本列表中手动启用后生效')
    } else {
      await rejectWorkflow(currentTask.value.id, { comment: processComment.value })
      ElMessage.success('已拒绝')
    }
    processDialogVisible.value = false
    await reloadAllData()
    if (currentTask.value?.id) {
      detailTask.value = approvals.value.find(item => item.id === currentTask.value?.id) || null
    }
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '操作失败'))
  } finally {
    submitting.value = false
  }
}

async function handleExportWorkflow(row: WorkflowVersionDTO) {
  if (versionApprovalStatus(row) !== 'APPROVED') {
    ElMessage.warning('只能导出审核通过的工作流')
    return
  }

  try {
    const data = await exportWorkflowVersion(row.id)

    // 生成文件名
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19)
    const safeName = row.name.replace(/[^\w\u4E00-\u9FA5-]/g, '_')
    const filename = `workflow-${safeName}-v${row.version}-${timestamp}.json`

    // 触发下载
    const blob = new Blob([JSON.stringify(data, null, 2)], {
      type: 'application/json',
    })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    link.click()
    URL.revokeObjectURL(url)

    ElMessage.success('工作流已导出')
  }
  catch (error) {
    ElMessage.error(resolveErrorMessage(error, '导出失败'))
    console.error(error)
  }
}

function handleImportWorkflow() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.json'

  input.onchange = async (e: Event) => {
    const file = (e.target as HTMLInputElement).files?.[0]
    if (!file)
      return

    try {
      const text = await file.text()
      const data: WorkflowExportData = JSON.parse(text)

      // 校验数据格式
      if (!data.workflow?.config?.nodes || !data.workflow?.config?.edges) {
        ElMessage.error('导入文件格式不正确')
        return
      }

      // 确认导入
      await ElMessageBox.confirm(
        `确认导入工作流"${data.workflow.name}"吗？\n\n原版本号：V${data.workflow.version}\n导入后将自动分配新的版本号和名称（如有冲突）`,
        '导入确认',
        { type: 'info' },
      )

      // 调用导入接口
      const result = await importWorkflowVersion(data, GLOBAL_WORKFLOW_PROJECT_ID)

      // 显示结果
      let message = `导入成功！\n新版本：V${result.version}\n新名称：${result.name}`
      if (result.conflicts?.nameConflict) {
        message += '\n（已自动重命名避免冲突）'
      }
      ElMessage.success(message)

      // 刷新列表
      await reloadAllData()

      // 跳转到新导入的版本
      const newVersion = versions.value.find(v => v.id === result.versionId)
      if (newVersion) {
        viewWorkflow(newVersion)
      }
    }
    catch (error: any) {
      if (error === 'cancel')
        return

      ElMessage.error(resolveErrorMessage(error, '导入失败'))
      console.error(error)
    }
  }

  input.click()
}

onMounted(() => {
  reloadAllData()
  loadVersionColumnConfig()
  loadApprovalColumnConfig()
})
</script>

<style scoped lang="scss">
.workflow-management-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  flex: 1;
  min-height: 0;
}

.workflow-config-page {
  display: flex;
  flex-direction: column;
  flex: 1;

  :deep(.app-page__content) {
    min-height: 0;
  }
}

.overview-card {
  flex-shrink: 0;
}

.management-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;

  :deep(.el-card__body) {
    padding: 20px;
    flex: 1;
    display: flex;
    flex-direction: column;
  }

  :deep(.el-tabs) {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-height: 0;
  }

  :deep(.el-tabs__content) {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }

  :deep(.el-tab-pane) {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }
}

.overview-header,
.card-header,
.table-toolbar,
.approval-toolbar,
.toolbar-left,
.toolbar-right,
.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}

.table-toolbar,
.approval-toolbar {
  padding: 10px 12px;
  margin-bottom: 10px;
  border: 1px solid var(--color-border, #e5e7eb);
  border-radius: var(--radius-lg, 12px);
  background: var(--color-surface-alt, #f8fafc);
}

.toolbar-control {
  :deep(.el-input__wrapper),
  :deep(.el-select__wrapper) {
    min-height: 30px;
    height: 30px;
  }
}

.toolbar-control--sm {
  width: 132px;
}

.toolbar-control--md {
  width: 148px;
}

.toolbar-control--keyword {
  width: 180px;
  max-width: 220px;
}

.toolbar-right :deep(.el-button),
.toolbar-left :deep(.el-button) {
  height: 30px;
  padding: 0 10px;
  font-size: 12px;
}

.toolbar-right :deep(.el-button.is-link),
.toolbar-left :deep(.el-button.is-link) {
  height: auto;
  padding: 4px;
}

.overview-header {
  align-items: flex-start;
}

.overview-header h2,
.card-header h3 {
  margin: 0;
  color: var(--color-text-primary);
}

.overview-header p,
.card-header p {
  margin: 6px 0 0;
  color: var(--color-muted-text);
  font-size: 13px;
}

.overview-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.metric-card,
.summary-card {
  padding: 16px;
  border-radius: 14px;
  border: 1px solid var(--color-border);
  background: #f8fafc;
}

.metric-card.success {
  background: #f0f9eb;
}

.metric-card.warning {
  background: #fff7e6;
}

.metric-card.danger {
  background: #fef0f0;
}

.metric-value,
.summary-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--color-text-primary);
}

.metric-label,
.summary-label {
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-muted-text);
}

.management-tabs {
  margin-top: -8px;
}

.approval-table {
  flex: 1;
}

.pagination-container {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
  flex-shrink: 0;
}

.summary-chip {
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 13px;
  line-height: 1;
  border: 1px solid transparent;
}

.summary-chip.pending {
  color: #b88230;
  background: #fff7e6;
  border-color: #f3d19e;
}

.summary-chip.approved {
  color: #3f8f53;
  background: #f0f9eb;
  border-color: #b3e19d;
}

.summary-chip.rejected {
  color: #c45656;
  background: #fef0f0;
  border-color: #fab6b6;
}

/* ============================================
   表格样式：紧凑自适应 + 居中对齐 + 行高舒适
   ============================================ */
.workflow-table {
  flex: 1;
  --el-table-border-color: var(--color-border, #e2e8f0);
  --el-table-header-bg-color: var(--color-surface-alt, #f8fafc);
  --el-table-row-hover-bg-color: rgba(59, 130, 246, 0.04);

  :deep(th.el-table__cell) {
    height: 38px;
    padding: 0 12px;
    font-size: 13px;
    font-weight: 600;
    color: var(--color-text-primary);
    background: var(--color-surface-alt, #f8fafc);
    letter-spacing: 0;
  }

  :deep(td.el-table__cell) {
    padding: 8px 12px;
    font-size: 13px;
    color: var(--color-text-primary);
    border-bottom: 1px solid var(--el-table-border-color);
  }

  :deep(.el-table__row) {
    transition: background-color var(--duration-fast, 150ms) ease;
  }

  :deep(.el-table__row:hover > td.el-table__cell) {
    background: rgba(59, 130, 246, 0.04);
  }

  :deep(.cell) {
    word-break: break-word;
  }

  // tag 间距：单行紧凑
  .workflow-tag {
    margin: 2px 4px 2px 0;

    &:last-child {
      margin-right: 0;
    }
  }
}

.muted-cell {
  color: var(--el-text-color-placeholder);
  font-size: 13px;
}

.link-hint {
  color: var(--el-color-primary);
  font-size: 13px;
  cursor: pointer;
  text-decoration: none;
  border-bottom: 1px dashed var(--el-color-primary-light-5);
  transition: color 0.2s, border-color 0.2s;

  &:hover {
    color: var(--el-color-primary-dark-2);
    border-bottom-color: var(--el-color-primary);
  }
}

.task-name-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.version-name-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  text-align: left;
}

.version-name-title {
  font-weight: 500;
  color: var(--color-text-primary);
}

.version-name-subtitle {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.operation-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.operation-more-button {
  padding: 4px;
}

.operation-more-dropdown .el-button {
  font-size: 14px;
}

.task-name {
  font-weight: 600;
  color: var(--color-text-primary);
}

.task-project,
.completed-text,
.comment-text,
.detail-subtitle,
.node-name {
  color: var(--color-text-secondary);
}

.comment-text {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.comment-form {
  margin-top: 20px;
}

.detail-section + .detail-section {
  margin-top: 20px;
}

.detail-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.detail-section-title {
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.detail-section-title.minor {
  margin-top: 16px;
}

.graph-summary {
  min-height: 140px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.node-list {
  margin-top: 4px;
}

.node-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid var(--color-border);
}

.node-item + .node-item {
  margin-top: 8px;
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
}

.dialog-hint {
  font-size: 12px;
  line-height: 1.6;

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

@media (max-width: 960px) {
  .overview-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .overview-metrics,
  .summary-grid {
    grid-template-columns: 1fr;
  }

  .pagination-container {
    justify-content: flex-start;
  }
}
</style>
