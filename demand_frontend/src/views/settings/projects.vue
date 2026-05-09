<template>
  <PageContainer title="项目管理">
    <TableCard>
      <template #toolbar>
        <Toolbar>
          <template #left>
            <el-form :inline="true" :model="queryParams">
              <el-form-item>
                <el-input v-model="queryParams.name" placeholder="项目名称" clearable style="width:180px" />
              </el-form-item>
              <el-form-item>
                <el-select v-model="queryParams.status" placeholder="状态" clearable style="width:120px">
                  <el-option label="进行中" value="active" />
                  <el-option label="已截止" value="expired" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleSearch">搜索</el-button>
                <el-button @click="handleReset">重置</el-button>
              </el-form-item>
            </el-form>
          </template>
          <template #right>
            <el-button @click="handleExport">
              <el-icon><Download /></el-icon> 导出
            </el-button>
            <el-button type="primary" @click="handleCreate">
              <el-icon><Plus /></el-icon> 新建项目
            </el-button>
          </template>
        </Toolbar>
      </template>

      <template #table>
        <el-table :data="projectList" v-loading="loading" border>
        <el-table-column prop="name" label="项目名称" min-width="180" />
        <el-table-column prop="team" label="归属团队" width="140">
          <template #default="{ row }">{{ row.team || '-' }}</template>
        </el-table-column>
        <el-table-column label="负责人" width="100">
          <template #default="{ row }">{{ row.leaderName || '-' }}</template>
        </el-table-column>
        <el-table-column label="起止时间" width="200">
          <template #default="{ row }">
            <span v-if="row.startDate">{{ row.startDate }} ~ {{ row.endDate || '无截止' }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row)">{{ getStatusLabel(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
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
      @close="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="项目名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入项目名称" />
        </el-form-item>
        <el-form-item label="归属公司" prop="companyId">
          <el-tree-select
            v-model="form.companyId"
            :data="regionTree"
            :props="{ label: 'name', value: 'id' }"
            placeholder="请选择归属公司"
            clearable
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="归属团队" prop="team">
          <el-input v-model="form.team" placeholder="请输入归属团队" />
        </el-form-item>
        <el-form-item label="负责人" prop="leaderId">
          <el-select v-model="form.leaderId" placeholder="请选择负责人" clearable filterable style="width: 100%">
            <el-option v-for="u in userList" :key="u.id" :label="u.realName || u.username" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="起止时间" prop="dateRange">
          <el-date-picker
            v-model="form.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="截止日期"
            style="width: 100%"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="请输入项目描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Download } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import * as projectApi from '@/api/modules/project'
import { getRegionTree } from '@/api/modules/organization'
import * as userApi from '@/api/modules/user'
import PageContainer from '@/components/common/PageContainer.vue'
import TableCard from '@/components/common/TableCard.vue'
import Toolbar from '@/components/common/Toolbar.vue'

const loading = ref(false)
const submitting = ref(false)
const projectList = ref<any[]>([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const regionTree = ref<any[]>([])
const userList = ref<any[]>([])

const queryParams = reactive({
  name: '',
  status: '',
})

const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  description: '',
  companyId: null as number | null,
  team: '',
  leaderId: null as number | null,
  dateRange: null as string[] | null,
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

async function loadOrgData() {
  try {
    const [regions, users] = await Promise.all([
      getRegionTree(),
      userApi.getUserList({ pageNum: 1, pageSize: 9999 }),
    ])
    regionTree.value = (regions as any) || []
    userList.value = ((users as any)?.list ?? [])
  } catch (error) {
    console.error('加载数据失败:', error)
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
  ElMessage.info('导出功能将在 Sprint 3 实现')
}

async function fetchList() {
  loading.value = true
  try {
    const res: any = await projectApi.getProjectList({
      name: queryParams.name || undefined,
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
  form.leaderId = row.leaderId || null
  form.dateRange = (row.startDate && row.endDate) ? [row.startDate, row.endDate] : null
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
    const payload: any = {
      name: form.name,
      description: form.description,
      companyId: form.companyId,
      team: form.team || null,
      leaderId: form.leaderId,
      startDate: form.dateRange?.[0] || null,
      endDate: form.dateRange?.[1] || null,
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
  form.dateRange = null
  formRef.value?.resetFields()
}

onMounted(() => {
  fetchList()
  loadOrgData()
})
</script>

<style lang="scss" scoped></style>
