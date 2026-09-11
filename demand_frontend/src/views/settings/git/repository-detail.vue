<template>
  <PageContainer :title="repository?.name || '仓库详情'" subtitle="仓库基本信息、分支保护、合并请求与操作日志" :show-title="true">
    <template #headerActions>
      <el-button @click="router.push('/settings/git/repositories')">
        <el-icon><ArrowLeft /></el-icon>
        返回列表
      </el-button>
    </template>

    <el-tabs v-model="activeTab" class="detail-tabs">
      <!-- ==================== 基本信息 ==================== -->
      <el-tab-pane label="基本信息" name="info">
        <el-card shadow="never" class="git-card" v-loading="loading">
          <el-descriptions :column="2" border class="info-desc">
            <el-descriptions-item label="仓库名称">
              <span class="repo-name">{{ repository?.name }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="完整路径">
              <span class="mono-text">{{ repository?.fullPath }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="所属平台">
              {{ repository?.platformName || repository?.platformId || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="所属项目">
              {{ repository?.projectName || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="默认分支">
              <span class="mono-text">{{ repository?.defaultBranch || '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="仓库状态">
              <el-tag :type="repoStatusType(repository?.status)" effect="light" size="small">
                {{ repoStatusLabel(repository?.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="SSH 克隆地址" :span="2">
              <span class="clone-row">
                <span class="mono-text">{{ repository?.cloneUrlSsh || '-' }}</span>
                <el-button
                  v-if="repository?.cloneUrlSsh"
                  link
                  type="primary"
                  size="small"
                  :icon="CopyDocument"
                  @click="copyText(repository!.cloneUrlSsh!)"
                >复制</el-button>
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="HTTPS 克隆地址" :span="2">
              <span class="clone-row">
                <span class="mono-text">{{ repository?.cloneUrlHttps || '-' }}</span>
                <el-button
                  v-if="repository?.cloneUrlHttps"
                  link
                  type="primary"
                  size="small"
                  :icon="CopyDocument"
                  @click="copyText(repository!.cloneUrlHttps!)"
                >复制</el-button>
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="描述" :span="2">
              {{ repository?.description || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">
              {{ formatDate(repository?.createdAt) }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-tab-pane>

      <!-- ==================== 分支保护 ==================== -->
      <el-tab-pane label="分支保护" name="protection">
        <div class="toolbar-row">
          <div class="toolbar-left">
            <el-select
              v-model="ruleFilters.enabled"
              placeholder="全部状态"
              clearable
              class="toolbar-select"
              @change="loadRules"
            >
              <el-option label="已启用" :value="1" />
              <el-option label="已停用" :value="0" />
            </el-select>
          </div>
          <div class="toolbar-right">
            <AppButton @click="openApplySetDialog">
              <el-icon><SetUp /></el-icon>
              应用规则集
            </AppButton>
            <AppButton type="primary" @click="openCreateRuleDialog">
              <el-icon><Plus /></el-icon>
              添加规则
            </AppButton>
          </div>
        </div>

        <div v-loading="rulesLoading" class="rule-list">
          <el-empty v-if="!rulesLoading && filteredRules.length === 0" description="暂无分支保护规则" />
          <div v-for="rule in filteredRules" :key="rule.id" class="rule-card">
            <div class="rule-card__head">
              <div class="rule-card__title">
                <el-icon class="rule-icon"><Lock /></el-icon>
                <div>
                  <div class="rule-name">{{ rule.ruleName }}</div>
                  <div class="rule-pattern">
                    分支模式：<span class="mono-text">{{ rule.branchPattern }}</span>
                    <el-tag size="small" effect="plain" class="priority-tag">优先级 {{ rule.priority }}</el-tag>
                  </div>
                </div>
              </div>
              <div class="rule-card__actions">
                <el-switch
                  :model-value="rule.enabled === 1"
                  @change="(val: boolean | string | number) => handleToggleRule(rule, !!val)"
                />
                <el-button link type="primary" :icon="EditPen" @click="openEditRuleDialog(rule)" />
                <el-button link type="danger" :icon="Delete" @click="handleDeleteRule(rule)" />
              </div>
            </div>
            <div class="rule-card__body">
              <template v-for="item in ruleRestrictions(rule)" :key="item.label">
                <el-tag v-if="item.active" :type="item.type" effect="light" round class="restriction-tag">
                  {{ item.label }}
                </el-tag>
              </template>
              <el-tag v-if="rule.minApprovals > 0" type="primary" effect="light" round class="restriction-tag">
                至少 {{ rule.minApprovals }} 人审批
              </el-tag>
              <el-tag
                v-for="u in parseJsonList(rule.whitelistUsers)"
                :key="'u' + u"
                type="success"
                effect="plain"
                round
                class="restriction-tag"
              >白名单：{{ u }}</el-tag>
              <el-tag
                v-for="r in parseJsonList(rule.whitelistRoles)"
                :key="'r' + r"
                type="warning"
                effect="plain"
                round
                class="restriction-tag"
              >角色：{{ r }}</el-tag>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- ==================== 合并请求 ==================== -->
      <el-tab-pane label="合并请求" name="merge-requests">
        <div class="toolbar-row">
          <div class="toolbar-left">
            <el-select v-model="mrFilters.status" placeholder="全部状态" clearable class="toolbar-select" @change="loadMergeRequests">
              <el-option label="待处理" value="OPEN" />
              <el-option label="已通过" value="APPROVED" />
              <el-option label="已合并" value="MERGED" />
              <el-option label="已关闭" value="CLOSED" />
              <el-option label="有冲突" value="CONFLICT" />
            </el-select>
          </div>
          <div class="toolbar-right">
            <AppButton type="primary" @click="openCreateMrDialog">
              <el-icon><Plus /></el-icon>
              创建合并请求
            </AppButton>
          </div>
        </div>

        <el-table
          :data="mergeRequests"
          border
          v-loading="mrLoading"
          :cell-style="{ textAlign: 'center' }"
          :header-cell-style="{ textAlign: 'center' }"
          class="git-table"
        >
          <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="repo-name">{{ row.title }}</span>
            </template>
          </el-table-column>
          <el-table-column label="分支" min-width="200">
            <template #default="{ row }">
              <span class="mono-text">{{ row.sourceBranch }}</span>
              <el-icon class="branch-arrow"><Right /></el-icon>
              <span class="mono-text">{{ row.targetBranch }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" min-width="100">
            <template #default="{ row }">
              <el-tag :type="mrStatusType(row.status)" effect="light" size="small">{{ mrStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="CI 状态" min-width="100">
            <template #default="{ row }">
              <el-tag v-if="row.ciStatus" :type="ciStatusType(row.ciStatus)" effect="plain" size="small">{{ ciStatusLabel(row.ciStatus) }}</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="作者" min-width="110">
            <template #default="{ row }">{{ row.authorName || '-' }}</template>
          </el-table-column>
          <el-table-column label="创建时间" min-width="150">
            <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openMrDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ==================== 操作日志 ==================== -->
      <el-tab-pane label="操作日志" name="audit">
        <div class="toolbar-row">
          <div class="toolbar-left">
            <el-input
              v-model="auditFilters.keyword"
              placeholder="搜索对象 / 详情"
              clearable
              class="toolbar-select toolbar-select--keyword"
              @keyup.enter="loadAuditLogs"
              @clear="loadAuditLogs"
            >
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-select
              v-model="auditFilters.operatorId"
              placeholder="全部操作人"
              clearable
              filterable
              class="toolbar-select"
              @change="loadAuditLogs"
            >
              <el-option v-for="u in userOptions" :key="u.id" :label="u.realName || u.username" :value="u.id" />
            </el-select>
            <el-select v-model="auditFilters.targetType" placeholder="全部对象类型" clearable class="toolbar-select" @change="loadAuditLogs">
              <el-option v-for="t in targetTypeOptions" :key="t" :label="t" :value="t" />
            </el-select>
            <el-select v-model="auditFilters.action" placeholder="全部操作" clearable class="toolbar-select" @change="loadAuditLogs">
              <el-option v-for="a in actionOptions" :key="a" :label="a" :value="a" />
            </el-select>
          </div>
          <div class="toolbar-right">
            <el-button :icon="Refresh" @click="loadAuditLogs">刷新</el-button>
          </div>
        </div>

        <el-table
          :data="auditLogs"
          border
          v-loading="auditLoading"
          :cell-style="{ textAlign: 'center' }"
          :header-cell-style="{ textAlign: 'center' }"
          class="git-table"
        >
          <el-table-column label="时间" min-width="160">
            <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作人" min-width="110">
            <template #default="{ row }">{{ row.operatorName || '-' }}</template>
          </el-table-column>
          <el-table-column prop="targetType" label="对象类型" min-width="110" />
          <el-table-column prop="targetName" label="对象" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.targetName || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" min-width="110">
            <template #default="{ row }">
              <el-tag type="info" effect="plain" size="small">{{ row.action }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="detail" label="详情" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="detail-text">{{ row.detail || '-' }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- ==================== 规则 新增/编辑 弹窗 ==================== -->
    <el-dialog
      v-model="ruleDialog.visible"
      :title="ruleDialog.isEdit ? '编辑分支保护规则' : '添加分支保护规则'"
      width="640px"
      destroy-on-close
    >
      <el-form ref="ruleFormRef" :model="ruleDialog.form" :rules="ruleFormRules" label-width="110px">
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="ruleDialog.form.ruleName" placeholder="例如：主干分支保护" />
        </el-form-item>
        <el-form-item label="分支模式" prop="branchPattern">
          <el-input v-model="ruleDialog.form.branchPattern" placeholder="例如：main、feature/*、^release-.*$" class="mono-input" />
          <div class="form-tip">支持精确匹配或通配符 *，如 main、feature/*</div>
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-input-number v-model="ruleDialog.form.priority" :min="0" :max="999" controls-position="right" />
          <div class="form-tip">数值越小优先级越高，冲突时高优先级规则生效</div>
        </el-form-item>
        <el-form-item label="推送控制">
          <el-checkbox-group v-model="ruleDialog.pushChecks">
            <el-checkbox :value="'forbidPush'">禁止直接推送</el-checkbox>
            <el-checkbox :value="'forbidForcePush'">禁止强制推送</el-checkbox>
            <el-checkbox :value="'forbidDelete'">禁止删除分支</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="审批要求">
          <el-checkbox-group v-model="ruleDialog.approveChecks">
            <el-checkbox :value="'requireMr'">必须走合并请求</el-checkbox>
            <el-checkbox :value="'dismissStaleApprovals'">新推送后撤销旧审批</el-checkbox>
            <el-checkbox :value="'blockSelfApprove'">禁止自审自批</el-checkbox>
            <el-checkbox :value="'requireCodeownerApproval'">需 Codeowner 审批</el-checkbox>
            <el-checkbox :value="'requireThreadResolved'">必须解决评论线程</el-checkbox>
            <el-checkbox :value="'requireCiPass'">必须通过 CI</el-checkbox>
            <el-checkbox :value="'requireUpToDate'">需与目标分支保持同步</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="审批人数" prop="minApprovals">
          <el-input-number v-model="ruleDialog.form.minApprovals" :min="0" :max="20" controls-position="right" />
        </el-form-item>
        <el-form-item label="白名单用户">
          <el-select
            v-model="ruleDialog.whitelistUsers"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入用户"
            style="width: 100%"
          >
            <el-option v-for="u in userOptions" :key="u.id" :label="u.realName || u.username" :value="u.realName || u.username" />
          </el-select>
        </el-form-item>
        <el-form-item label="白名单角色">
          <el-select
            v-model="ruleDialog.whitelistRoles"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入角色"
            style="width: 100%"
          >
            <el-option label="管理员" value="admin" />
            <el-option label="开发者" value="developer" />
            <el-option label="维护者" value="maintainer" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="ruleDialog.visible = false">取消</el-button>
          <AppButton type="primary" :loading="ruleDialog.saving" @click="handleSaveRule">保存</AppButton>
        </div>
      </template>
    </el-dialog>

    <!-- ==================== 规则集应用 弹窗 ==================== -->
    <el-dialog v-model="applySetDialog.visible" title="应用规则集" width="480px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="规则集" required>
          <el-select v-model="applySetDialog.ruleSetId" placeholder="请选择规则集" style="width: 100%">
            <el-option v-for="s in ruleSets" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <div class="import-tip">将所选规则集中的规则应用到当前仓库，覆盖同分支模式的现有规则。</div>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="applySetDialog.visible = false">取消</el-button>
          <AppButton type="primary" :loading="applySetDialog.saving" @click="handleApplySet">确认应用</AppButton>
        </div>
      </template>
    </el-dialog>

    <!-- ==================== 创建合并请求 弹窗 ==================== -->
    <el-dialog v-model="mrDialog.visible" title="创建合并请求" width="560px" destroy-on-close>
      <el-form ref="mrFormRef" :model="mrDialog.form" :rules="mrFormRules" label-width="110px">
        <el-form-item label="源分支" prop="sourceBranch">
          <el-input v-model="mrDialog.form.sourceBranch" placeholder="例如：feature/demo" class="mono-input" />
        </el-form-item>
        <el-form-item label="目标分支" prop="targetBranch">
          <el-input v-model="mrDialog.form.targetBranch" placeholder="例如：main" class="mono-input" />
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="mrDialog.form.title" placeholder="请输入合并请求标题" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="mrDialog.form.description" type="textarea" :rows="3" placeholder="描述变更内容（可选）" />
        </el-form-item>
        <el-form-item label="合并策略" prop="mergeStrategy">
          <el-radio-group v-model="mrDialog.form.mergeStrategy">
            <el-radio value="MERGE">Merge</el-radio>
            <el-radio value="SQUASH">Squash</el-radio>
            <el-radio value="REBASE">Rebase</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="关联需求">
          <el-input-number v-model="mrDialog.form.requirementId" :min="1" controls-position="right" placeholder="需求 ID（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="mrDialog.visible = false">取消</el-button>
          <AppButton type="primary" :loading="mrDialog.saving" @click="handleCreateMr">创建</AppButton>
        </div>
      </template>
    </el-dialog>

    <!-- ==================== 合并请求详情 抽屉 ==================== -->
    <el-drawer v-model="mrDetail.visible" :title="mrDetail.data?.title || '合并请求详情'" size="480px">
      <div v-if="mrDetail.data" class="mr-detail">
        <div class="mr-detail__meta">
          <el-tag :type="mrStatusType(mrDetail.data.status)" effect="light" size="small">{{ mrStatusLabel(mrDetail.data.status) }}</el-tag>
          <el-tag v-if="mrDetail.data.ciStatus" :type="ciStatusType(mrDetail.data.ciStatus)" effect="plain" size="small">CI {{ ciStatusLabel(mrDetail.data.ciStatus) }}</el-tag>
        </div>
        <div class="mr-branch-row">
          <span class="mono-text">{{ mrDetail.data.sourceBranch }}</span>
          <el-icon class="branch-arrow"><Right /></el-icon>
          <span class="mono-text">{{ mrDetail.data.targetBranch }}</span>
        </div>
        <el-descriptions :column="1" border class="mr-desc">
          <el-descriptions-item label="所属仓库">{{ mrDetail.data.repoName || repoId }}</el-descriptions-item>
          <el-descriptions-item label="作者">{{ mrDetail.data.authorName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="合并策略">{{ mrDetail.data.mergeStrategy }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDate(mrDetail.data.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="合并时间">{{ formatDate(mrDetail.data.mergedAt) }}</el-descriptions-item>
          <el-descriptions-item label="描述">
            <span class="mr-desc-text">{{ mrDetail.data.description || '-' }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <div class="mr-actions">
          <el-button
            v-if="mrDetail.data.status === 'OPEN'"
            type="success"
            :icon="Check"
            :loading="mrDetail.loading"
            @click="handleMrAction('approve')"
          >通过</el-button>
          <el-button
            v-if="mrDetail.data.status === 'OPEN'"
            type="warning"
            :icon="CloseBold"
            :loading="mrDetail.loading"
            @click="handleMrAction('request-changes')"
          >请求变更</el-button>
          <el-button
            v-if="mrDetail.data.status === 'OPEN' || mrDetail.data.status === 'APPROVED'"
            type="primary"
            :icon="Promotion"
            :loading="mrDetail.loading"
            @click="handleMrAction('merge')"
          >合并</el-button>
          <el-tag v-if="mrDetail.data.status === 'MERGED'" type="success" effect="dark">已合并</el-tag>
          <el-tag v-if="mrDetail.data.status === 'CLOSED'" type="info" effect="dark">已关闭</el-tag>
          <el-tag v-if="mrDetail.data.status === 'CONFLICT'" type="danger" effect="dark">存在冲突，请先解决</el-tag>
        </div>
      </div>
    </el-drawer>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  ArrowLeft,
  Check,
  CloseBold,
  CopyDocument,
  Delete,
  EditPen,
  Lock,
  Plus,
  Promotion,
  Refresh,
  Right,
  Search,
  SetUp,
} from '@element-plus/icons-vue'
import AppButton from '@/components/common/AppButton.vue'
import PageContainer from '@/components/common/PageContainer.vue'
import { formatDate } from '@/utils/format'
import { getFilterUsers } from '@/api/modules/user'
import type { BranchProtectionRule, GitRepository, MergeRequest, GitAuditLog } from '@/types/git'
import {
  getGitRepository,
  getProtectionRules,
  createProtectionRule,
  updateProtectionRule,
  deleteProtectionRule,
  toggleProtectionRule,
  getRuleSets,
  applyRuleSet,
  getMergeRequests,
  createMergeRequest,
  getMergeRequest,
  approveMergeRequest,
  requestChangesMergeRequest,
  mergeMergeRequest,
  getGitAuditLogs,
} from '@/api/modules/git'

const route = useRoute()
const router = useRouter()
const repoId = Number(route.params.id)

const activeTab = ref('info')
const loading = ref(false)
const repository = ref<GitRepository | null>(null)

// ── 用户下拉（白名单 / 审计筛选） ──
const userOptions = ref<Array<{ id: number; username: string; realName: string }>>([])
async function loadUsers() {
  try {
    const res = await getFilterUsers() as any
    userOptions.value = (res?.data ?? res ?? []) as Array<{ id: number; username: string; realName: string }>
  } catch {
    userOptions.value = []
  }
}

async function loadRepository() {
  loading.value = true
  try {
    const res = await getGitRepository(repoId) as any
    repository.value = (res?.data ?? res) as GitRepository
  } catch {
    ElMessage.error('仓库信息加载失败')
  } finally {
    loading.value = false
  }
}

// ── Tab2 分支保护 ──
const rulesLoading = ref(false)
const rules = ref<BranchProtectionRule[]>([])
const ruleFilters = reactive({ enabled: undefined as number | undefined })

const filteredRules = computed(() => {
  if (ruleFilters.enabled === undefined) return rules.value
  return rules.value.filter(r => r.enabled === ruleFilters.enabled)
})

async function loadRules() {
  rulesLoading.value = true
  try {
    const res = await getProtectionRules(repoId) as any
    rules.value = (res?.data ?? res ?? []) as BranchProtectionRule[]
  } catch {
    ElMessage.error('分支保护规则加载失败')
  } finally {
    rulesLoading.value = false
  }
}

const RULE_PUSH_FIELDS = ['forbidPush', 'forbidForcePush', 'forbidDelete'] as const
const RULE_APPROVE_FIELDS = [
  'requireMr',
  'dismissStaleApprovals',
  'blockSelfApprove',
  'requireCodeownerApproval',
  'requireThreadResolved',
  'requireCiPass',
  'requireUpToDate',
] as const

const ruleRestrictionLabels: Record<string, string> = {
  forbidPush: '禁止直接推送',
  forbidForcePush: '禁止强制推送',
  forbidDelete: '禁止删除分支',
  requireMr: '必须走 MR',
  dismissStaleApprovals: '新推送撤销审批',
  blockSelfApprove: '禁止自审自批',
  requireCodeownerApproval: 'Codeowner 审批',
  requireThreadResolved: '评论线程须解决',
  requireCiPass: '必须通过 CI',
  requireUpToDate: '保持与目标同步',
}

function ruleRestrictions(rule: BranchProtectionRule) {
  const items: Array<{ label: string; active: boolean; type: 'primary' | 'warning' | 'success' }> = []
  for (const f of RULE_PUSH_FIELDS) {
    items.push({ label: ruleRestrictionLabels[f], active: rule[f] === 1, type: 'warning' })
  }
  for (const f of RULE_APPROVE_FIELDS) {
    items.push({ label: ruleRestrictionLabels[f], active: rule[f] === 1, type: 'warning' })
  }
  return items
}

function parseJsonList(raw?: string): string[] {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.map(String) : []
  } catch {
    return []
  }
}

// ── 规则 弹窗 ──
const ruleDialog = reactive({
  visible: false,
  isEdit: false,
  saving: false,
  editingId: 0,
  form: {
    ruleName: '',
    branchPattern: '',
    priority: 100,
    minApprovals: 0,
  },
  pushChecks: [] as string[],
  approveChecks: [] as string[],
  whitelistUsers: [] as string[],
  whitelistRoles: [] as string[],
})

const ruleFormRef = ref<FormInstance>()
const ruleFormRules: FormRules = {
  ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  branchPattern: [{ required: true, message: '请输入分支模式', trigger: 'blur' }],
}

function resetRuleDialog() {
  ruleDialog.form = { ruleName: '', branchPattern: '', priority: 100, minApprovals: 0 }
  ruleDialog.pushChecks = []
  ruleDialog.approveChecks = []
  ruleDialog.whitelistUsers = []
  ruleDialog.whitelistRoles = []
}

function fillRuleDialog(rule: BranchProtectionRule) {
  ruleDialog.form = {
    ruleName: rule.ruleName,
    branchPattern: rule.branchPattern,
    priority: rule.priority,
    minApprovals: rule.minApprovals,
  }
  ruleDialog.pushChecks = RULE_PUSH_FIELDS.filter(f => rule[f] === 1) as unknown as string[]
  ruleDialog.approveChecks = RULE_APPROVE_FIELDS.filter(f => rule[f] === 1) as unknown as string[]
  ruleDialog.whitelistUsers = parseJsonList(rule.whitelistUsers)
  ruleDialog.whitelistRoles = parseJsonList(rule.whitelistRoles)
}

function openCreateRuleDialog() {
  ruleDialog.isEdit = false
  ruleDialog.editingId = 0
  resetRuleDialog()
  ruleDialog.visible = true
}

function openEditRuleDialog(rule: BranchProtectionRule) {
  ruleDialog.isEdit = true
  ruleDialog.editingId = rule.id
  fillRuleDialog(rule)
  ruleDialog.visible = true
}

function buildRulePayload() {
  const payload: Record<string, unknown> = {
    ruleName: ruleDialog.form.ruleName,
    branchPattern: ruleDialog.form.branchPattern,
    priority: ruleDialog.form.priority,
    minApprovals: ruleDialog.form.minApprovals,
  }
  RULE_PUSH_FIELDS.forEach(f => { payload[f] = ruleDialog.pushChecks.includes(f) ? 1 : 0 })
  RULE_APPROVE_FIELDS.forEach(f => { payload[f] = ruleDialog.approveChecks.includes(f) ? 1 : 0 })
  payload.whitelistUsers = JSON.stringify(ruleDialog.whitelistUsers)
  payload.whitelistRoles = JSON.stringify(ruleDialog.whitelistRoles)
  return payload
}

async function handleSaveRule() {
  if (!ruleFormRef.value) return
  try {
    await ruleFormRef.value.validate()
  } catch {
    return
  }
  ruleDialog.saving = true
  try {
    const payload = buildRulePayload()
    if (ruleDialog.isEdit) {
      await updateProtectionRule(ruleDialog.editingId, payload)
      ElMessage.success('规则已更新')
    } else {
      await createProtectionRule(repoId, payload)
      ElMessage.success('规则已创建')
    }
    ruleDialog.visible = false
    await loadRules()
  } catch {
    ElMessage.error(ruleDialog.isEdit ? '更新失败' : '创建失败')
  } finally {
    ruleDialog.saving = false
  }
}

async function handleToggleRule(rule: BranchProtectionRule, enabled: boolean) {
  try {
    await toggleProtectionRule(rule.id, enabled)
    rule.enabled = enabled ? 1 : 0
    ElMessage.success(enabled ? '规则已启用' : '规则已停用')
  } catch {
    ElMessage.error('规则状态切换失败')
  }
}

async function handleDeleteRule(rule: BranchProtectionRule) {
  try {
    await ElMessageBox.confirm(`确定删除规则「${rule.ruleName}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteProtectionRule(rule.id)
    ElMessage.success('已删除')
    await loadRules()
  } catch {
    ElMessage.error('删除失败')
  }
}

// ── 规则集应用 ──
const ruleSets = ref<Array<{ id: number; name: string }>>([])
async function loadRuleSets() {
  try {
    const res = await getRuleSets() as any
    ruleSets.value = (res?.data ?? res ?? []) as Array<{ id: number; name: string }>
  } catch {
    ruleSets.value = []
  }
}

const applySetDialog = reactive({
  visible: false,
  saving: false,
  ruleSetId: undefined as number | undefined,
})

function openApplySetDialog() {
  applySetDialog.ruleSetId = ruleSets.value[0]?.id
  applySetDialog.visible = true
}

async function handleApplySet() {
  if (!applySetDialog.ruleSetId) {
    ElMessage.warning('请选择规则集')
    return
  }
  applySetDialog.saving = true
  try {
    await applyRuleSet(applySetDialog.ruleSetId, [repoId])
    ElMessage.success('规则集已应用')
    applySetDialog.visible = false
    await loadRules()
  } catch {
    ElMessage.error('规则集应用失败')
  } finally {
    applySetDialog.saving = false
  }
}

// ── Tab3 合并请求 ──
const mrLoading = ref(false)
const mergeRequests = ref<MergeRequest[]>([])
const mrFilters = reactive({ status: '' })

async function loadMergeRequests() {
  mrLoading.value = true
  try {
    const res = await getMergeRequests(repoId, { status: mrFilters.status || undefined }) as any
    mergeRequests.value = (res?.data ?? res ?? []) as MergeRequest[]
  } catch {
    ElMessage.error('合并请求列表加载失败')
  } finally {
    mrLoading.value = false
  }
}

const mrDialog = reactive({
  visible: false,
  saving: false,
  form: {
    sourceBranch: '',
    targetBranch: '',
    title: '',
    description: '',
    mergeStrategy: 'MERGE' as MergeRequest['mergeStrategy'],
    requirementId: undefined as number | undefined,
  },
})

const mrFormRef = ref<FormInstance>()
const mrFormRules: FormRules = {
  sourceBranch: [{ required: true, message: '请输入源分支', trigger: 'blur' }],
  targetBranch: [{ required: true, message: '请输入目标分支', trigger: 'blur' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  mergeStrategy: [{ required: true, message: '请选择合并策略', trigger: 'change' }],
}

function openCreateMrDialog() {
  mrDialog.form = {
    sourceBranch: '',
    targetBranch: repository.value?.defaultBranch || 'main',
    title: '',
    description: '',
    mergeStrategy: 'MERGE',
    requirementId: undefined,
  }
  mrDialog.visible = true
}

async function handleCreateMr() {
  if (!mrFormRef.value) return
  try {
    await mrFormRef.value.validate()
  } catch {
    return
  }
  mrDialog.saving = true
  try {
    await createMergeRequest(repoId, { ...mrDialog.form })
    ElMessage.success('合并请求已创建')
    mrDialog.visible = false
    await loadMergeRequests()
  } catch {
    ElMessage.error('创建失败')
  } finally {
    mrDialog.saving = false
  }
}

// ── MR 详情抽屉 ──
const mrDetail = reactive({
  visible: false,
  loading: false,
  data: null as MergeRequest | null,
})

async function openMrDetail(row: MergeRequest) {
  mrDetail.data = row
  mrDetail.visible = true
  try {
    const res = await getMergeRequest(row.id) as any
    mrDetail.data = (res?.data ?? res) as MergeRequest
  } catch {
    // 保留列表行数据
  }
}

async function handleMrAction(action: 'approve' | 'request-changes' | 'merge') {
  if (!mrDetail.data) return
  mrDetail.loading = true
  try {
    if (action === 'approve') {
      await approveMergeRequest(mrDetail.data.id)
      ElMessage.success('已通过')
    } else if (action === 'request-changes') {
      await requestChangesMergeRequest(mrDetail.data.id)
      ElMessage.success('已请求变更')
    } else {
      await mergeMergeRequest(mrDetail.data.id)
      ElMessage.success('合并成功')
    }
    const res = await getMergeRequest(mrDetail.data.id) as any
    mrDetail.data = (res?.data ?? res) as MergeRequest
    await loadMergeRequests()
  } catch {
    ElMessage.error('操作失败')
  } finally {
    mrDetail.loading = false
  }
}

// ── Tab4 审计日志 ──
const auditLoading = ref(false)
const auditLogs = ref<GitAuditLog[]>([])
const auditFilters = reactive({
  keyword: '',
  operatorId: undefined as number | undefined,
  targetType: '',
  action: '',
})

const targetTypeOptions = ['PLATFORM', 'REPOSITORY', 'PROTECTION_RULE', 'MERGE_REQUEST', 'RULE_SET']
const actionOptions = ['CREATE', 'UPDATE', 'DELETE', 'ARCHIVE', 'TEST_CONNECTION', 'APPLY', 'APPROVE', 'REQUEST_CHANGES', 'MERGE']

async function loadAuditLogs() {
  auditLoading.value = true
  try {
    const res = await getGitAuditLogs({
      keyword: auditFilters.keyword || undefined,
      operatorId: auditFilters.operatorId,
      targetType: auditFilters.targetType || undefined,
      action: auditFilters.action || undefined,
    }) as any
    auditLogs.value = (res?.data ?? res ?? []) as GitAuditLog[]
  } catch {
    ElMessage.error('审计日志加载失败')
  } finally {
    auditLoading.value = false
  }
}

// ── 工具 ──
async function copyText(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败')
  }
}

function repoStatusType(status?: GitRepository['status']): 'success' | 'info' | 'danger' {
  if (!status) return 'info'
  if (status === 'ACTIVE') return 'success'
  if (status === 'ARCHIVED') return 'info'
  return 'danger'
}
function repoStatusLabel(status?: GitRepository['status']) {
  if (status === 'ACTIVE') return '活跃'
  if (status === 'ARCHIVED') return '已归档'
  if (status === 'DELETED') return '已删除'
  return '-'
}

function mrStatusType(status: MergeRequest['status']): 'info' | 'primary' | 'success' | 'danger' {
  if (status === 'OPEN') return 'info'
  if (status === 'APPROVED') return 'primary'
  if (status === 'MERGED') return 'success'
  if (status === 'CONFLICT') return 'danger'
  return 'info'
}
function mrStatusLabel(status: MergeRequest['status']) {
  if (status === 'OPEN') return '待处理'
  if (status === 'APPROVED') return '已通过'
  if (status === 'MERGED') return '已合并'
  if (status === 'CLOSED') return '已关闭'
  return '有冲突'
}
function ciStatusType(status: MergeRequest['ciStatus']): 'success' | 'danger' | 'primary' | 'info' {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'primary'
  return 'info'
}
function ciStatusLabel(status: MergeRequest['ciStatus']) {
  if (status === 'SUCCESS') return '成功'
  if (status === 'FAILED') return '失败'
  if (status === 'RUNNING') return '进行中'
  return '等待中'
}

onMounted(() => {
  loadRepository()
  loadUsers()
  loadRuleSets()
  loadRules()
  loadMergeRequests()
  loadAuditLogs()
})
</script>

<style scoped lang="scss">
.detail-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 0;
  }
}

.git-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  margin-top: var(--spacing-md);
  :deep(.el-card__body) {
    padding: var(--spacing-lg);
  }
}

.git-table {
  width: 100%;
  margin-top: var(--spacing-md);
}

.repo-name {
  color: var(--color-text-primary);
  font-weight: 600;
}

.mono-text {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.mono-input :deep(input) {
  font-family: 'SFMono-Regular', Consolas, Menlo, monospace;
}

.info-desc {
  :deep(.el-descriptions__label) {
    width: 150px;
    color: var(--color-text-secondary);
  }
}

.clone-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

/* ── 规则卡片 ── */
.toolbar-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  flex-wrap: wrap;
  margin: var(--spacing-md) 0;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}

.toolbar-select {
  width: 180px;

  &--keyword {
    width: 240px;
  }
}

.rule-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
  min-height: 120px;
}

.rule-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--spacing-md);
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: var(--shadow-card-lift);
  }
}

.rule-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-sm);
}

.rule-card__title {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
}

.rule-icon {
  font-size: 18px;
  color: var(--color-accent);
  margin-top: 2px;
}

.rule-name {
  color: var(--color-text-primary);
  font-weight: 600;
  font-size: 15px;
}

.rule-pattern {
  margin-top: 4px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.priority-tag {
  margin-left: 8px;
}

.rule-card__actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.rule-card__body {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.restriction-tag {
  margin-right: 0;
}

/* ── MR ── */
.branch-arrow {
  margin: 0 4px;
  color: var(--color-text-placeholder);
  vertical-align: middle;
}

/* ── 表单 ── */
.form-tip {
  width: 100%;
  margin-top: 4px;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
}

.import-tip {
  padding: 8px 12px;
  border-radius: var(--radius-md);
  background: var(--color-primary-subtle);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  line-height: 1.5;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-sm);
}

.detail-text {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

/* ── MR 详情 ── */
.mr-detail {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.mr-detail__meta {
  display: flex;
  gap: 6px;
}

.mr-branch-row {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
}

.mr-desc-text {
  white-space: pre-wrap;
  line-height: 1.6;
}

.mr-actions {
  display: flex;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-sm);
}
</style>
