<template>
  <PageContainer title="项目管理" class="project-page">
    <TableCard class="project-table-card">
      <template #toolbar>
        <Toolbar>
          <template #left>
            <el-form :inline="true" :model="queryParams" class="project-filter-form">
              <el-form-item label="项目名称" class="project-filter-form__item project-filter-form__item--name">
                <el-input v-model="queryParams.name" placeholder="输入项目名称" clearable />
              </el-form-item>
              <el-form-item label="状态" class="project-filter-form__item project-filter-form__item--status">
                <el-select v-model="queryParams.status" placeholder="全部" clearable>
                  <el-option label="进行中" value="active" />
                  <el-option label="已截止" value="expired" />
                </el-select>
              </el-form-item>
              <el-form-item class="project-filter-form__actions">
                <el-button type="primary" @click="handleSearch">搜索</el-button>
                <el-button @click="handleReset">重置</el-button>
              </el-form-item>
            </el-form>
          </template>
          <template #right>
            <el-tooltip content="列表字段设置">
              <el-button link :icon="Setting" @click="openColumnConfig" />
            </el-tooltip>
            <input ref="importInputRef" type="file" accept=".xlsx,.xls" style="display: none" @change="handleImportFileChange" />
            <AppButton permission="button:project:template" @click="handleDownloadTemplate">
              <el-icon><Download /></el-icon> 模板
            </AppButton>
            <AppButton permission="button:project:import" @click="triggerImport">
              <el-icon><Plus /></el-icon> 导入
            </AppButton>
            <AppButton permission="button:project:export" @click="handleExport">
              <el-icon><Download /></el-icon> 导出
            </AppButton>
            <AppButton type="primary" permission="button:project:create" @click="handleCreate">
              <el-icon><Plus /></el-icon> 新建项目
            </AppButton>
          </template>
        </Toolbar>
      </template>

      <template #table>
        <el-table :data="projectList" v-loading="loading" border>
        <el-table-column v-if="isColumnVisible('name')" prop="name" label="项目名称" min-width="200" show-overflow-tooltip />
        <el-table-column v-if="isColumnVisible('team')" prop="team" label="归属团队" min-width="120">
          <template #default="{ row }">{{ row.team || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="isColumnVisible('leaderId')" label="负责人" min-width="100">
          <template #default="{ row }">{{ getLeaderName(row.leaderId) }}</template>
        </el-table-column>
        <el-table-column v-if="isColumnVisible('contactPhone')" prop="contactPhone" label="联系电话" min-width="130" />
        <el-table-column v-if="isColumnVisible('startDate')" label="开始时间" min-width="120">
          <template #default="{ row }">{{ row.startDate || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="isColumnVisible('endDate')" label="结束时间" min-width="120">
          <template #default="{ row }">{{ row.endDate || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="isColumnVisible('status')" prop="status" label="状态" min-width="90">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row)">{{ getStatusLabel(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="isColumnVisible('createdAt')" label="创建时间" min-width="160">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column v-if="isColumnVisible('operations')" label="操作" min-width="100" fixed="right">
          <template #default="{ row }">
            <AppButton type="primary" link size="small" permission="button:project:update" @click="handleEdit(row)">
              <el-icon><EditPen /></el-icon>
            </AppButton>
            <AppButton type="danger" link size="small" permission="button:project:delete" @click="handleDelete(row)">
              <el-icon><Delete /></el-icon>
            </AppButton>
          </template>
        </el-table-column>
        </el-table>
      </template>

      <template #pagination>
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </template>
    </TableCard>

    <!-- Create/Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑项目' : '新建项目'"
      width="600px"
      class="settings-form-dialog"
      @close="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="项目名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入项目名称" />
        </el-form-item>
        <el-form-item label="归属公司" prop="companyId">
          <el-select
            v-model="form.companyId"
            placeholder="请选择归属公司"
            clearable
            style="width: 100%"
            @change="handleCompanyChange"
          >
            <el-option
              v-for="item in companyOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="归属团队" prop="team">
          <el-select
            v-model="form.team"
            :placeholder="teamPlaceholder"
            :disabled="teamFieldLocked"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="item in teamOptions"
              :key="`${form.companyId}-${item.value}`"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人" prop="leaderId">
          <el-select v-model="form.leaderId" placeholder="请选择负责人" clearable filterable style="width: 100%">
            <el-option v-for="u in userList" :key="u.id" :label="u.realName || u.username" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间" prop="startDate">
          <el-date-picker
            v-model="form.startDate"
            type="date"
            placeholder="请选择开始日期"
            style="width: 100%"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item label="结束时间" prop="endDate">
          <el-date-picker
            v-model="form.endDate"
            type="date"
            placeholder="请选择截止日期"
            style="width: 100%"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="form.contactPhone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="请输入项目描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-permission="isEdit ? 'button:project:update' : 'button:project:create'" type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <ColumnConfigDialog
      v-model="showColumnConfig"
      :column-groups="columnGroups"
      :draft-selected-columns="draftSelectedColumns"
      :draft-column-keys="draftColumnKeys"
      @update:draft-column-keys="draftColumnKeys = $event"
      @remove="removeDraftColumn"
      @save="saveColumns"
    />
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Download, EditPen, Delete, Setting } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { saveAs } from 'file-saver'
import { exportToExcel } from '@/utils/excel'
import * as projectApi from '@/api/modules/project'
import { getOrgTree } from '@/api/modules/organization'
import type { OrgNode } from '@/types/user'
import * as userApi from '@/api/modules/user'
import type { ProjectImportFailure, ProjectImportResult } from '@/types/project'
import PageContainer from '@/components/common/PageContainer.vue'
import TableCard from '@/components/common/TableCard.vue'
import Toolbar from '@/components/common/Toolbar.vue'
import AppButton from '@/components/common/AppButton.vue'
import ColumnConfigDialog from '@/components/common/ColumnConfigDialog.vue'
import { useColumnConfig, type ColumnDef } from '@/composables/useColumnConfig'
import { formatDate } from '@/utils/format'

// ── 列表字段设置 ──
const projectAllColumns: ColumnDef[] = [
  { key: 'name', label: '项目名称', group: '基础字段', minWidth: 200 },
  { key: 'team', label: '归属团队', group: '基础字段', minWidth: 120 },
  { key: 'leaderId', label: '负责人', group: '人员', minWidth: 100 },
  { key: 'contactPhone', label: '联系电话', group: '基础字段', minWidth: 130 },
  { key: 'startDate', label: '开始时间', group: '基础字段', minWidth: 120 },
  { key: 'endDate', label: '结束时间', group: '基础字段', minWidth: 120 },
  { key: 'status', label: '状态', group: '状态信息', minWidth: 90 },
  { key: 'createdAt', label: '创建时间', group: '人员', minWidth: 160 },
  { key: 'operations', label: '操作', minWidth: 100 },
]
const projectDefaultKeys = ['name', 'team', 'leaderId', 'contactPhone', 'startDate', 'endDate', 'status', 'createdAt', 'operations']

const {
  showColumnConfig,
  openColumnConfig,
  saveColumns,
  loadColumnConfig,
  columnGroups,
  draftSelectedColumns,
  draftColumnKeys,
  visibleColumns,
  removeDraftColumn,
} = useColumnConfig({
  pageKey: 'project_list',
  columns: projectAllColumns,
  defaultKeys: projectDefaultKeys,
})

function isColumnVisible(key: string) {
  return visibleColumns.value.some((c) => c.key === key)
}

const loading = ref(false)
const submitting = ref(false)
const projectList = ref<any[]>([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const orgTree = ref<OrgNode[]>([])
const companyOptions = ref<Array<{ label: string; value: number }>>([])
const userList = ref<any[]>([])
const userMap = computed(() => {
  const map = new Map<number, any>()
  userList.value.forEach(u => map.set(u.id, u))
  return map
})
const selectedCompanyNode = computed(() => findOrgNodeById(orgTree.value, form.companyId))
const actualTeamOptions = computed(() => {
  const companyNode = selectedCompanyNode.value
  if (!companyNode) return [] as Array<{ label: string; value: string }>
  return collectDescendantTeams(companyNode).map(item => ({
    label: item.name,
    value: item.name,
  }))
})
const teamOptions = computed(() => {
  if (actualTeamOptions.value.length > 0) return actualTeamOptions.value
  if (!selectedCompanyNode.value?.name) return [] as Array<{ label: string; value: string }>
  return [{ label: selectedCompanyNode.value.name, value: selectedCompanyNode.value.name }]
})
const teamFieldLocked = computed(() => !form.companyId || actualTeamOptions.value.length === 0)
const teamPlaceholder = computed(() => {
  if (!form.companyId) return '请先选择归属公司'
  if (actualTeamOptions.value.length === 0) return '当前公司下暂无团队，默认使用公司名称'
  return '请选择归属团队'
})

const queryParams = reactive({
  name: '',
  status: '',
})

const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const importInputRef = ref<HTMLInputElement>()

const form = reactive({
  name: '',
  description: '',
  companyId: null as number | null,
  team: '',
  leaderId: null as number | null,
  startDate: null as string | null,
  endDate: null as string | null,
  contactPhone: '',
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
}

function getStatusType(row: any) {
  if (row.status === 'expired' || (row.endDate && new Date(row.endDate) < new Date())) return 'info'
  if (row.status === 'active') return 'success'
  return 'info'
}

function getStatusLabel(row: any) {
  if (row.status === 'expired' || (row.endDate && new Date(row.endDate) < new Date())) return '已截止'
  if (row.status === 'active') return '进行中'
  return row.status
}

function getLeaderName(leaderId?: number | null) {
  if (!leaderId) return '-'
  const user = userMap.value.get(leaderId)
  return user ? (user.realName || user.username || '-') : '-'
}

function findOrgNodeById(nodes: OrgNode[], targetId: number | null): OrgNode | null {
  if (!targetId) return null
  for (const node of nodes) {
    if (node.id === targetId) return node
    if (node.children?.length) {
      const matched = findOrgNodeById(node.children, targetId)
      if (matched) return matched
    }
  }
  return null
}

function normalizeArray<T>(value: unknown): T[] {
  if (Array.isArray(value)) return value as T[]
  const data = (value as any)?.data
  if (Array.isArray(data)) return data as T[]
  if (Array.isArray(data?.data)) return data.data as T[]
  return []
}

function extractOrgNodesByType(nodes: OrgNode[], targetType: OrgNode['orgType']): OrgNode[] {
  const result: OrgNode[] = []
  const walk = (items: OrgNode[]) => {
    items.forEach(item => {
      if (item.orgType === targetType) {
        result.push({ ...item, children: undefined })
      }
      if (item.children?.length) {
        walk(item.children)
      }
    })
  }
  walk(nodes)
  return result
}

function toCompanyOptions(nodes: OrgNode[]) {
  return extractOrgNodesByType(nodes, 'company').map(item => ({
    label: item.name,
    value: item.id,
  }))
}

function collectDescendantTeams(companyNode: OrgNode): OrgNode[] {
  const teams: OrgNode[] = []
  const seen = new Set<string>()
  const walk = (node: OrgNode) => {
    if (node.orgType === 'group') {
      const teamName = node.name?.trim()
      if (teamName && !seen.has(teamName)) {
        seen.add(teamName)
        teams.push(node)
      }
    }
    node.children?.forEach(child => walk(child))
  }
  walk(companyNode)
  return teams
}

function resolveTeamValue(companyId: number | null, currentTeam = '', preserveCurrent = false): string {
  const companyNode = findOrgNodeById(orgTree.value, companyId)
  if (!companyNode) return ''

  const teams = collectDescendantTeams(companyNode).map(item => item.name)
  if (teams.length === 0) {
    return companyNode.name || ''
  }

  if (preserveCurrent && currentTeam && teams.includes(currentTeam)) {
    return currentTeam
  }

  return teams[0] || ''
}

function syncTeamWithCompany(preserveCurrent = false) {
  form.team = resolveTeamValue(form.companyId, form.team, preserveCurrent)
}

function handleCompanyChange() {
  syncTeamWithCompany()
}

async function loadOrgData() {
  try {
    const orgRes = await getOrgTree()
    orgTree.value = normalizeArray<OrgNode>(orgRes)
    companyOptions.value = toCompanyOptions(orgTree.value)
    if (form.companyId) {
      syncTeamWithCompany(true)
    }
  } catch (error) {
    orgTree.value = []
    companyOptions.value = []
  }

  try {
    const users = await userApi.getUserList({ pageNum: 1, pageSize: 9999 })
    userList.value = ((users as any)?.list ?? [])
  } catch (error) {
    userList.value = []
  }
}

function handleSearch() {
  pageNum.value = 1
  fetchList()
}

function handleReset() {
  queryParams.name = ''
  queryParams.status = ''
  handleSearch()
}

function handleExport() {
  if (projectList.value.length === 0) {
    ElMessage.warning('当前没有可导出的项目数据')
    return
  }
  const exportData = projectList.value.map((item) => ({
    项目名称: item.name,
    归属团队: item.team || '',
    负责人: getLeaderName(item.leaderId),
    联系电话: item.contactPhone || '',
    开始日期: item.startDate || '',
    截止日期: item.endDate || '',
    状态: getStatusLabel(item),
    创建时间: formatDate(item.createdAt),
    描述: item.description || ''
  }))
  exportToExcel(exportData, '项目列表', '项目列表')
  ElMessage.success('导出成功')
}

async function handleDownloadTemplate() {
  try {
    const blob = await projectApi.downloadProjectTemplate() as Blob
    saveAs(blob, '项目导入模板.xlsx')
  } catch {
    ElMessage.error('下载模板失败')
  }
}

function triggerImport() {
  importInputRef.value?.click()
}

async function handleImportFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    const result = await projectApi.importProjects(file) as ProjectImportResult
    const successCount = result?.successCount ?? 0
    const failCount = result?.failCount ?? 0
    if (failCount > 0 && Array.isArray(result.failures) && result.failures.length > 0) {
      exportImportFailures(result.failures)
      ElMessage.warning(`导入完成：成功 ${successCount} 条，失败 ${failCount} 条，失败明细已导出`)
    } else {
      ElMessage.success(`导入完成：成功 ${successCount} 条，失败 ${failCount} 条`)
    }
    await fetchList()
  } catch {
    ElMessage.error('项目导入失败')
  } finally {
    input.value = ''
  }
}

function exportImportFailures(failures: ProjectImportFailure[]) {
  const exportData = failures.map(item => ({
    行号: item.rowNum,
    项目名称: item.projectName || '',
    失败原因: item.reason || '导入失败'
  }))
  exportToExcel(exportData, '导入失败明细', '项目导入失败明细')
}

async function fetchList() {
  loading.value = true
  try {
    const res: any = await projectApi.getProjectList({
      name: queryParams.name || undefined,
      status: queryParams.status || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    projectList.value = res?.list ?? []
    total.value = res?.total ?? 0
  } catch {
    projectList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleCreate() {
  isEdit.value = false
  editId.value = null
  dialogVisible.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  editId.value = row.id
  form.name = row.name
  form.description = row.description || ''
  form.companyId = row.companyId || null
  form.team = row.team || ''
  syncTeamWithCompany(true)
  form.leaderId = row.leaderId || null
  form.startDate = row.startDate || null
  form.endDate = row.endDate || null
  form.contactPhone = row.contactPhone || ''
  dialogVisible.value = true
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定要删除项目"${row.name}"吗？`, '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await projectApi.deleteProject(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
    // user cancelled or error
  }
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  submitting.value = true
  try {
    const resolvedTeam = resolveTeamValue(form.companyId, form.team, true)
    const payload: any = {
      name: form.name,
      description: form.description,
      companyId: form.companyId,
      team: resolvedTeam || null,
      leaderId: form.leaderId,
      startDate: form.startDate || null,
      endDate: form.endDate || null,
      contactPhone: form.contactPhone || null,
      status: 'active',
    }
    if (isEdit.value && editId.value) {
      await projectApi.updateProject(editId.value, payload)
      ElMessage.success('更新成功')
    } else {
      await projectApi.createProject(payload)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch {
    // error handled by interceptor
  } finally {
    submitting.value = false
  }
}

function resetForm() {
  form.name = ''
  form.description = ''
  form.companyId = null
  form.team = ''
  form.leaderId = null
  form.startDate = null
  form.endDate = null
  form.contactPhone = ''
  formRef.value?.resetFields()
}

onMounted(() => {
  fetchList()
  loadOrgData()
  loadColumnConfig()
})
</script>

<style lang="scss" scoped>
.project-page {
  :deep(.app-table-card__toolbar) {
    margin-bottom: 10px;
  }
}

.project-table-card {
  :deep(.el-card__body) {
    padding: 14px;
  }
}

.project-filter-form {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;

  :deep(.el-form-item) {
    margin: 0;
  }

  :deep(.el-form-item__label) {
    height: 30px;
    padding-right: 6px;
    color: var(--el-text-color-secondary);
    font-size: 12px;
    font-weight: 500;
    line-height: 30px;
    white-space: nowrap;
  }

  :deep(.el-input__wrapper),
  :deep(.el-select__wrapper) {
    min-height: 30px;
    height: 30px;
  }

  :deep(.el-button) {
    height: 30px;
    padding: 0 12px;
    font-size: 12px;
  }
}

.project-filter-form__item--name {
  width: 214px;
}

.project-filter-form__item--status {
  width: 112px;
}

.project-filter-form__actions {
  margin-left: 2px !important;
}

@media (max-width: 768px) {
  .project-filter-form,
  .project-filter-form__item--name,
  .project-filter-form__item--status,
  .project-filter-form__actions {
    width: 100%;
  }
}
</style>
