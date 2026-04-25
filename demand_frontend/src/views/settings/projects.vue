<template>
  <PageContainer title="项目管理">
    <TableCard>
      <template #toolbar>
        <Toolbar>
          <template #left />
          <template #right>
            <el-button type="primary" @click="handleCreate">
              <el-icon><Plus /></el-icon> 新建项目
            </el-button>
          </template>
        </Toolbar>
      </template>

      <template #table>
        <el-table :data="projectList" v-loading="loading" border>
        <el-table-column prop="name" label="项目名称" min-width="180" />
        <el-table-column prop="description" label="描述" min-width="240" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'">
              {{ row.status === 'active' ? '启用' : '停用' }}
            </el-tag>
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
      width="500px"
      @close="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="项目名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入项目名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            placeholder="请输入项目描述"
          />
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
import { Plus } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import * as projectApi from '@/api/modules/project'
import PageContainer from '@/components/common/PageContainer.vue'
import TableCard from '@/components/common/TableCard.vue'
import Toolbar from '@/components/common/Toolbar.vue'

const loading = ref(false)
const submitting = ref(false)
const projectList = ref<any[]>([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  description: '',
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
}

async function fetchList() {
  loading.value = true
  try {
    const res: any = await projectApi.getProjectList({
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
    if (isEdit.value && editId.value) {
      await projectApi.updateProject(editId.value, {
        name: form.name,
        description: form.description,
        status: 'active',
      })
      ElMessage.success('更新成功')
    } else {
      await projectApi.createProject({
        name: form.name,
        description: form.description,
      })
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
  formRef.value?.resetFields()
}

onMounted(fetchList)
</script>

<style lang="scss" scoped></style>
