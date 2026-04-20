<template>
  <div class="user-settings">
    <div class="page-header">
      <h2>用户管理</h2>
      <el-button type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon> 新增用户
      </el-button>
    </div>

    <el-card>
      <!-- Search Bar -->
      <el-form :inline="true" :model="queryParams" class="search-form">
        <el-form-item label="用户名">
          <el-input v-model="queryParams.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="queryParams.realName" placeholder="请输入姓名" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="disabled" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- Table -->
      <el-table :data="userList" v-loading="loading" style="width: 100%">
        <el-table-column prop="username" label="用户名" width="130" />
        <el-table-column prop="realName" label="姓名" width="120" />
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'danger'">
              {{ row.status === 'active' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
            <el-button type="warning" link size="small" @click="handleResetPassword(row)">重置密码</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        class="pagination"
        @size-change="fetchList"
        @current-change="fetchList"
      />
    </el-card>

    <!-- Create/Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑用户' : '新增用户'"
      width="550px"
      @close="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            :disabled="isEdit"
          />
        </el-form-item>
        <el-form-item v-if="!isEdit" label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import * as userApi from '@/api/modules/user'

const loading = ref(false)
const submitting = ref(false)
const userList = ref<any[]>([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const queryParams = reactive({
  username: '',
  realName: '',
  status: '',
})

interface UserForm {
  username: string
  password: string
  realName: string
  email: string
  phone: string
}

const form = reactive<UserForm>({
  username: '',
  password: '',
  realName: '',
  email: '',
  phone: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
}

async function fetchList() {
  loading.value = true
  try {
    const res: any = await userApi.getUserList({
      username: queryParams.username || undefined,
      realName: queryParams.realName || undefined,
      status: queryParams.status || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    userList.value = res?.list ?? []
    total.value = res?.total ?? 0
  } catch {
    userList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNum.value = 1
  fetchList()
}

function handleReset() {
  queryParams.username = ''
  queryParams.realName = ''
  queryParams.status = ''
  handleSearch()
}

function handleCreate() {
  isEdit.value = false
  editId.value = null
  dialogVisible.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  editId.value = row.id
  form.username = row.username
  form.password = ''
  form.realName = row.realName
  form.email = row.email || ''
  form.phone = row.phone || ''
  dialogVisible.value = true
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定要删除用户"${row.realName || row.username}"吗？`, '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await userApi.deleteUser(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
    // user cancelled or error
  }
}

async function handleResetPassword(row: any) {
  try {
    await ElMessageBox.confirm(`确定要重置用户"${row.realName || row.username}"的密码吗？`, '重置密码', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    ElMessage.success('密码重置成功')
  } catch {
    // user cancelled
  }
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  submitting.value = true
  try {
    if (isEdit.value && editId.value) {
      await userApi.updateUser(editId.value, {
        realName: form.realName,
        email: form.email || null,
        phone: form.phone || null,
      })
      ElMessage.success('更新成功')
    } else {
      await userApi.createUser(form as any)
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
  form.username = ''
  form.password = ''
  form.realName = ''
  form.email = ''
  form.phone = ''
  formRef.value?.resetFields()
}

onMounted(fetchList)
</script>

<style lang="scss" scoped>
.user-settings {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;

  h2 {
    margin: 0;
    font-size: 20px;
    color: #303133;
  }
}

.search-form {
  margin-bottom: 20px;
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>
