<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { resolveErrorMessage } from '@/utils/error'
import type { MigrationPlanVO, NodeMappingVO, MigrationPreviewVO, MigrationResultDTO } from '@/types/workflow-visual'
import {
  createMigrationPlan,
  listMigrationPlans,
  getMigrationPlan,
  updateNodeMapping,
  previewMigration,
  executeMigration,
  listMigrationLogs,
} from '@/api/modules/workflow-migration'
import { getVersionHistory } from '@/api/modules/workflow-visual'

// ============ 状态 ============

const loading = ref(false)
const executing = ref(false)
const plans = ref<MigrationPlanVO[]>([])
const selectedPlanId = ref<number | null>(null)
const currentPlan = ref<MigrationPlanVO | null>(null)
const previewData = ref<MigrationPreviewVO | null>(null)
const logs = ref<any[]>([])

// 创建表单
const createForm = reactive({
  fromVersionId: null as number | null,
  toVersionId: null as number | null,
  remark: '',
})

// 版本选择列表
const availableVersions = ref<any[]>([])
const projectId = ref<number>(0) // 全局版本 projectId=0

// 步骤
const currentStep = ref(1) // 1:选版本, 2:配映射, 3:预检, 4:执行

// 节点映射编辑
const editableMapping = ref<NodeMappingVO[]>([])

// 状态标签颜色
const statusColors: Record<string, string> = {
  draft: '#909399',
  pending: '#E6A23C',
  executing: '#409EFF',
  completed: '#67C67A',
  failed: '#F56C6C',
}

const statusLabels: Record<string, string> = {
  draft: '草稿',
  pending: '待执行',
  executing: '执行中',
  completed: '已完成',
  failed: '失败',
}

// ============ 方法 ============

async function loadVersions() {
  try {
    const data = await getVersionHistory(projectId.value)
    availableVersions.value = data || []
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '加载版本列表失败'))
  }
}

async function loadPlans() {
  loading.value = true
  try {
    plans.value = await listMigrationPlans(projectId.value) || []
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '加载迁移计划失败'))
  } finally {
    loading.value = false
  }
}

async function handleCreatePlan() {
  if (!createForm.fromVersionId || !createForm.toVersionId) {
    ElMessage.warning('请选择源版本和目标版本')
    return
  }
  if (createForm.fromVersionId === createForm.toVersionId) {
    ElMessage.warning('源版本和目标版本不能相同')
    return
  }

  try {
    const plan = await createMigrationPlan({
      fromVersionId: createForm.fromVersionId!,
      toVersionId: createForm.toVersionId!,
      remark: createForm.remark,
    })
    currentPlan.value = plan
    selectedPlanId.value = plan.id
    editableMapping.value = plan.nodeMapping || []
    currentStep.value = 2
    ElMessage.success('迁移计划已创建')
    await loadPlans()
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '创建迁移计划失败'))
  }
}

async function selectPlan(planId: number) {
  selectedPlanId.value = planId
  try {
    currentPlan.value = await getMigrationPlan(planId)
    editableMapping.value = currentPlan.value?.nodeMapping || []
    previewData.value = null

    // 根据状态设置步骤
    const status = currentPlan.value?.status
    if (status === 'draft') currentStep.value = 2
    else if (status === 'completed' || status === 'failed') currentStep.value = 4
    else currentStep.value = 3

    // 加载日志
    logs.value = await listMigrationLogs(planId) || []
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '加载迁移计划失败'))
  }
}

async function handleSaveMapping() {
  if (!selectedPlanId.value) return

  // 检查是否有未映射且未跳过的节点
  const unmapped = editableMapping.value.filter(m => !m.toNodeId && !m.skipped)
  if (unmapped.length > 0) {
    ElMessage.warning(`仍有 ${unmapped.length} 个节点未配置映射，请处理后再继续`)
    return
  }

  try {
    const mappingData = editableMapping.value.map(m => ({
      fromNodeId: m.fromNodeId,
      toNodeId: m.skipped ? null : m.toNodeId,
      fromNodeName: m.fromNodeName,
      toNodeName: m.toNodeName,
    }))
    const plan = await updateNodeMapping(selectedPlanId.value!, mappingData)
    currentPlan.value = plan
    editableMapping.value = plan.nodeMapping || []
    ElMessage.success('节点映射已保存')
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '保存映射失败'))
  }
}

async function handlePreview() {
  if (!selectedPlanId.value) return

  try {
    previewData.value = await previewMigration(selectedPlanId.value!)
    currentStep.value = 3
    ElMessage.success('预检完成')
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '预检失败'))
  }
}

async function handleExecute() {
  if (!selectedPlanId.value) return

  try {
    await ElMessageBox.confirm(
      `确定要执行迁移计划？将迁移版本 ${currentPlan.value?.fromVersion} → ${currentPlan.value?.toVersion} 的所有在途实例。`,
      '确认执行',
      { type: 'warning', confirmButtonText: '确认执行', cancelButtonText: '取消' },
    )
  } catch {
    return // 用户取消
  }

  executing.value = true
  try {
    const result = await executeMigration(selectedPlanId.value!)
    currentStep.value = 4
    ElMessage.success(result.message || '迁移完成')

    // 刷新计划数据
    await selectPlan(selectedPlanId.value!)
    await loadPlans()
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '执行迁移失败'))
  } finally {
    executing.value = false
  }
}

// 目标版本下拉选项（仅 active 版本）
const toVersionOptions = computed(() => {
  return availableVersions.value.filter(v => v.isActive === 1)
})

// 源版本下拉选项（排除目标版本）
const fromVersionOptions = computed(() => {
  return availableVersions.value.filter(v => v.id !== createForm.toVersionId)
})

onMounted(() => {
  loadVersions()
  loadPlans()
})
</script>

<template>
  <div class="workflow-migration-page">
    <!-- 左栏：迁移计划列表 -->
    <div class="plan-list-panel">
      <div class="panel-header">
        <h3>迁移计划</h3>
      </div>

      <!-- 创建表单 -->
      <div class="create-form">
        <h4>新建迁移计划</h4>
        <el-form label-position="top" size="small">
          <el-form-item label="源版本（旧版本）">
            <el-select v-model="createForm.fromVersionId" placeholder="选择有在途实例的旧版本" filterable style="width: 100%">
              <el-option v-for="v in fromVersionOptions" :key="v.id" :label="v.name + ' (v' + v.version + ')' " :value="v.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="目标版本（当前活跃版本）">
            <el-select v-model="createForm.toVersionId" placeholder="选择目标版本" filterable style="width: 100%">
              <el-option v-for="v in toVersionOptions" :key="v.id" :label="v.name + ' (v' + v.version + ')' " :value="v.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="createForm.remark" placeholder="可选备注" />
          </el-form-item>
          <el-button type="primary" @click="handleCreatePlan" :disabled="!createForm.fromVersionId || !createForm.toVersionId">
            创建计划
          </el-button>
        </el-form>
      </div>

      <!-- 计划列表 -->
      <div class="plan-cards">
        <div v-if="plans.length === 0" class="empty-tip">暂无迁移计划</div>
        <div v-for="plan in plans" :key="plan.id"
             class="plan-card"
             :class="{ selected: selectedPlanId === plan.id }"
             @click="selectPlan(plan.id)">
          <div class="plan-card-header">
            <span class="plan-version-info">v{{ plan.fromVersion }} → v{{ plan.toVersion }}</span>
            <el-tag :color="statusColors[plan.status]" effect="dark" size="small">
              {{ statusLabels[plan.status] || plan.status }}
            </el-tag>
          </div>
          <div class="plan-card-body">
            <span>{{ plan.fromVersionName }} → {{ plan.toVersionName }}</span>
          </div>
          <div class="plan-card-footer">
            在途 {{ plan.totalInstanceCount }} | 已迁移 {{ plan.migratedCount }} | 失败 {{ plan.failedCount }}
          </div>
        </div>
      </div>
    </div>

    <!-- 右栏：计划详情 -->
    <div class="plan-detail-panel">
      <div v-if="!currentPlan" class="empty-tip">请选择或创建迁移计划</div>

      <template v-else>
        <!-- 步骤条 -->
        <el-steps :active="currentStep" finish-status="success" simple class="migration-steps">
          <el-step title="选择版本" />
          <el-step title="配置映射" />
          <el-step title="预检" />
          <el-step title="执行" />
        </el-steps>

        <!-- 步骤 1: 版本信息 -->
        <div v-if="currentStep === 1" class="step-content">
          <el-descriptions :column="2" border size="small" title="版本信息">
            <el-descriptions-item label="源版本">{{ currentPlan.fromVersionName }} (v{{ currentPlan.fromVersion }})</el-descriptions-item>
            <el-descriptions-item label="目标版本">{{ currentPlan.toVersionName }} (v{{ currentPlan.toVersion }})</el-descriptions-item>
            <el-descriptions-item label="在途实例数">{{ currentPlan.totalInstanceCount }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ statusLabels[currentPlan.status] }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 步骤 2: 节点映射 -->
        <div v-if="currentStep === 2 && currentPlan.status === 'draft'" class="step-content">
          <div class="mapping-header">
            <h4>节点映射配置</h4>
            <div class="mapping-actions">
              <el-button type="primary" size="small" @click="handleSaveMapping">保存映射</el-button>
              <el-button size="small" @click="handlePreview" :disabled="editableMapping.filter(m => !m.toNodeId && !m.skipped).length > 0">
                预检
              </el-button>
            </div>
          </div>

          <el-table :data="editableMapping" size="small" border class="mapping-table">
            <el-table-column label="旧节点" width="180">
              <template #default="{ row }">
                <span>{{ row.fromNodeName }}</span>
                <el-tag v-if="row.autoMatched" type="success" size="small" class="match-tag">自动</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="旧节点类型" width="100">
              <template #default="{ row }">
                <el-tag size="small">{{ row.fromNodeType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="映射" width="60" align="center">
              <template #default>
                →
              </template>
            </el-table-column>
            <el-table-column label="新节点" width="200">
              <template #default="{ row }">
                <el-select v-if="!row.skipped" v-model="row.toNodeId" placeholder="选择目标节点" size="small" filterable clearable>
                  <el-option v-for="n in currentPlan?.toVersionNodes || []" :key="n.nodeId" :label="n.nodeName" :value="n.nodeId" />
                </el-select>
                <span v-if="row.skipped" class="skipped-text">已跳过</span>
              </template>
            </el-table-column>
            <el-table-column label="跳过" width="60" align="center">
              <template #default="{ row }">
                <el-checkbox v-model="row.skipped" />
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.toNodeId" type="success" size="small">已映射</el-tag>
                <el-tag v-else-if="row.skipped" type="info" size="small">跳过</el-tag>
                <el-tag v-else type="danger" size="small">未映射</el-tag>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="currentPlan.unmappedNodes?.length > 0" class="warning-box">
            <el-alert type="warning" :closable="false">
              仍有 {{ currentPlan.unmappedNodes.length }} 个节点未配置映射。未映射节点的在途实例将留在旧版本继续执行。
            </el-alert>
          </div>
        </div>

        <!-- 步骤 3: 预检 -->
        <div v-if="currentStep === 3" class="step-content">
          <div v-if="!previewData" class="preview-placeholder">
            <el-button type="primary" @click="handlePreview">执行预检</el-button>
          </div>

          <template v-else>
            <el-descriptions :column="3" border size="small" title="预检结果">
              <el-descriptions-item label="总实例数">{{ previewData.totalInstances }}</el-descriptions-item>
              <el-descriptions-item label="可迁移">{{ previewData.canMigrateCount }}</el-descriptions-item>
              <el-descriptions-item label="需手动处理">{{ previewData.needManualCount }}</el-descriptions-item>
            </el-descriptions>

            <el-table v-if="previewData.items?.length > 0" :data="previewData.items" size="small" border class="preview-table">
              <el-table-column prop="requirementId" label="需求ID" width="100" />
              <el-table-column prop="currentNodeId" label="当前节点ID" width="150" />
              <el-table-column prop="currentNodeName" label="当前节点名称" width="150" />
              <el-table-column label="映射状态" width="120">
                <template #default="{ row }">
                  <el-tag v-if="row.mapped" type="success" size="small">已映射 → {{ row.mappedToNodeName }}</el-tag>
                  <el-tag v-else type="danger" size="small">未映射</el-tag>
                </template>
              </el-table-column>
            </el-table>

            <div class="preview-actions">
              <el-button @click="currentStep = 2">返回修改映射</el-button>
              <el-button type="primary" @click="handleExecute" :loading="executing">
                确认执行迁移
              </el-button>
            </div>
          </template>
        </div>

        <!-- 步骤 4: 执行结果 -->
        <div v-if="currentStep === 4" class="step-content">
          <el-descriptions :column="2" border size="small" title="执行结果">
            <el-descriptions-item label="总实例数">{{ currentPlan.totalInstanceCount }}</el-descriptions-item>
            <el-descriptions-item label="成功">{{ currentPlan.migratedCount }}</el-descriptions-item>
            <el-descriptions-item label="失败">{{ currentPlan.failedCount }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :color="statusColors[currentPlan.status]" effect="dark" size="small">
                {{ statusLabels[currentPlan.status] }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>

          <!-- 迁移日志 -->
          <div v-if="logs.length > 0" class="logs-section">
            <h4>迁移日志</h4>
            <el-table :data="logs" size="small" border>
              <el-table-column prop="requirementId" label="需求ID" width="100" />
              <el-table-column prop="fromNodeName" label="原节点" width="120" />
              <el-table-column prop="toNodeName" label="映射后节点" width="120" />
              <el-table-column prop="migrationStatus" label="状态" width="80">
                <template #default="{ row }">
                  <el-tag v-if="row.migrationStatus === 'success'" type="success" size="small">成功</el-tag>
                  <el-tag v-else type="danger" size="small">失败</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="errorMessage" label="错误信息" min-width="200" />
              <el-table-column prop="createdAt" label="时间" width="180" />
            </el-table>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped lang="scss">
.workflow-migration-page {
  display: flex;
  gap: 24px;
  padding: 20px;
  height: calc(100vh - 60px);
  overflow: hidden;
}

.plan-list-panel {
  width: 320px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow-y: auto;
  background: var(--el-bg-color);
  border-radius: 8px;
  padding: 16px;
}

.plan-detail-panel {
  flex: 1;
  overflow-y: auto;
  background: var(--el-bg-color);
  border-radius: 8px;
  padding: 24px;
}

.panel-header h3 {
  margin: 0;
  font-size: 16px;
  color: var(--el-text-color-primary);
}

.create-form {
  border-bottom: 1px solid var(--el-border-color-lighter);
  padding-bottom: 16px;

  h4 {
    margin: 0 0 12px 0;
    font-size: 14px;
    color: var(--el-text-color-primary);
  }
}

.plan-cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.plan-card {
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  padding: 12px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: var(--el-color-primary-light-5);
  }

  &.selected {
    border-color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
  }

  .plan-card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 6px;

    .plan-version-info {
      font-weight: 500;
      font-size: 13px;
    }
  }

  .plan-card-body {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .plan-card-footer {
    font-size: 11px;
    color: var(--el-text-color-tertiary);
    margin-top: 6px;
  }
}

.empty-tip {
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  padding: 40px 0;
}

.migration-steps {
  margin-bottom: 24px;
}

.step-content {
  .mapping-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;

    h4 {
      margin: 0;
      font-size: 14px;
    }
  }

  .mapping-table {
    margin-bottom: 16px;
  }

  .match-tag {
    margin-left: 4px;
  }

  .skipped-text {
    color: var(--el-text-color-secondary);
  }

  .warning-box {
    margin-top: 12px;
  }
}

.preview-placeholder {
  text-align: center;
  padding: 60px 0;
}

.preview-table {
  margin-top: 16px;
}

.preview-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
}

.logs-section {
  margin-top: 24px;

  h4 {
    margin: 0 0 12px 0;
    font-size: 14px;
  }
}
</style>
