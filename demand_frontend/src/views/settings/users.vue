<template>
  <PageContainer title="用户管理">
    <template #headerActions>
      <el-button type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon> 新增用户
      </el-button>
    </template>

    <FilterCard>
      <el-form :inline="true" :model="queryParams">
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
      </el-form>
      <template #actions>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
      </template>
    </FilterCard>

    <TableCard>
      <template #table>
        <el-table :data="userList" v-loading="loading" border>
        <el-table-column prop="username" label="用户名" width="130" />
        <el-table-column prop="realName" label="姓名" width="120" />
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 'active'"
              :loading="row._statusLoading"
              @change="handleStatusChange(row, $event)"
            />
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
      :title="isEdit ? '编辑用户' : '新增用户'"
      width="600px"
      @close="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
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
          <el-input v-model="form.phone" placeholder="请输入手机号" @change="onPhoneChange" />
        </el-form-item>

        <el-divider content-position="left">组织信息</el-divider>

        <el-form-item label="区域" prop="regionId">
          <el-tree-select
            v-model="form.regionId"
            :data="regionTree"
            :props="{ label: 'name', value: 'id' }"
            placeholder="请选择区域"
            clearable
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="部门" prop="departmentId">
          <el-tree-select
            v-model="form.departmentId"
            :data="departmentTree"
            :props="{ label: 'name', value: 'id' }"
            placeholder="请选择部门"
            clearable
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="岗位" prop="positionId">
          <el-select v-model="form.positionId" placeholder="请选择岗位" clearable style="width: 100%">
            <el-option
              v-for="position in positionList"
              :key="position.id"
              :label="position.name"
              :value="position.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item v-if="isEdit" label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="active">启用</el-radio>
            <el-radio value="disabled">停用</el-radio>
          </el-radio-group>
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
import * as userApi from '@/api/modules/user'
import type { Region, Department, Position } from '@/types/user'
import PageContainer from '@/components/common/PageContainer.vue'
import FilterCard from '@/components/common/FilterCard.vue'
import TableCard from '@/components/common/TableCard.vue'

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

// 组织架构数据
const regionTree = ref<Region[]>([])
const departmentTree = ref<Department[]>([])
const positionList = ref<Position[]>([])

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
  regionId: number | null
  departmentId: number | null
  positionId: number | null
  status: string
}

const form = reactive<UserForm>({
  username: '',
  password: '',
  realName: '',
  email: '',
  phone: '',
  regionId: null,
  departmentId: null,
  positionId: null,
  status: 'active',
})

// 邮箱验证规则
const validateEmail = (rule: any, value: any, callback: any) => {
  if (value && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
    callback(new Error('请输入正确的邮箱格式'))
  } else {
    callback()
  }
}

// 手机号验证规则
const validatePhone = (rule: any, value: any, callback: any) => {
  if (value && !/^1[3-9]\d{9}$/.test(value)) {
    callback(new Error('请输入正确的手机号格式'))
  } else {
    callback()
  }
}

// 用户名校验：仅允许字母、数字、下划线
const validateUsername = (_rule: any, value: string, callback: any) => {
  if (value && !/^[a-zA-Z0-9_]+$/.test(value)) {
    callback(new Error('用户名仅支持字母、数字、下划线'))
  } else {
    callback()
  }
}

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度为3-20个字符', trigger: 'blur' },
    { validator: validateUsername, trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为6-20个字符', trigger: 'blur' },
  ],
  realName: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { max: 50, message: '姓名长度不能超过50个字符', trigger: 'blur' },
  ],
  email: [{ validator: validateEmail, trigger: 'blur' }],
  phone: [{ validator: validatePhone, trigger: 'blur' }],
}

// 加载组织架构数据
async function loadOrgData() {
  try {
    const [regions, departments, positions] = await Promise.all([
      userApi.getRegionTree(),
      userApi.getDepartmentTree(),
      userApi.getPositionList(),
    ])
    regionTree.value = (regions as any) || []
    departmentTree.value = (departments as any) || []
    positionList.value = (positions as any) || []
  } catch (error) {
    console.error('加载组织架构数据失败:', error)
  }
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
    userList.value = (res?.list ?? []).map((item: any) => ({
      ...item,
      _statusLoading: false,
    }))
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
  resetForm()
  dialogVisible.value = true
}

// 手机号变化时自动生成默认密码
function onPhoneChange() {
  if (!isEdit.value && form.username && form.phone && form.phone.length >= 3) {
    form.password = form.username + form.phone.slice(-3)
  }
}

async function handleEdit(row: any) {
  isEdit.value = true
  editId.value = row.id

  // 加载用户详细信息
  try {
    const userDetail: any = await userApi.getUserById(row.id)
    form.username = userDetail.username
    form.password = ''
    form.realName = userDetail.realName
    form.email = userDetail.email || ''
    form.phone = userDetail.phone || ''
    form.status = userDetail.status || 'active'
    form.regionId = userDetail.regionId || null
    form.departmentId = userDetail.departmentId || null
    form.positionId = userDetail.positionId || null
  } catch (error) {
    ElMessage.error('加载用户信息失败')
    return
  }

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
    // TODO: 调用重置密码接口
    ElMessage.success('密码重置成功，新密码为：123456')
  } catch {
    // user cancelled
  }
}

// 状态切换
async function handleStatusChange(row: any, value: boolean) {
  const newStatus = value ? 'active' : 'disabled'
  const statusText = value ? '启用' : '停用'

  try {
    await ElMessageBox.confirm(`确定要${statusText}用户"${row.realName || row.username}"吗？`, '状态切换', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })

    row._statusLoading = true
    await userApi.updateUser(row.id, { status: newStatus })
    row.status = newStatus
    ElMessage.success(`${statusText}成功`)
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(`${statusText}失败`)
    }
  } finally {
    row._statusLoading = false
  }
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  submitting.value = true

  try {
    if (isEdit.value && editId.value) {
      // 编辑用户：更新所有字段
      await userApi.updateUser(editId.value, {
        realName: form.realName,
        email: form.email || null,
        phone: form.phone || null,
        status: form.status,
        regionId: form.regionId,
        departmentId: form.departmentId,
        positionId: form.positionId,
      })
      ElMessage.success('更新成功')
    } else {
      // 创建用户：先创建基本信息
      const createData = {
        username: form.username,
        password: form.password,
        realName: form.realName,
        email: form.email || null,
        phone: form.phone || null,
      }
      const result: any = await userApi.createUser(createData)

      // 如果选择了组织信息，再调用更新接口设置
      const hasOrgInfo = form.regionId || form.departmentId || form.positionId
      if (hasOrgInfo && result?.id) {
        await userApi.updateUser(result.id, {
          regionId: form.regionId,
          departmentId: form.departmentId,
          positionId: form.positionId,
        })
      }

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
  form.regionId = null
  form.departmentId = null
  form.positionId = null
  form.status = 'active'
  formRef.value?.resetFields()
}

onMounted(() => {
  loadOrgData()
  fetchList()
})
</script>

<style lang="scss" scoped></style>
