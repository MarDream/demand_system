<template>
  <div class="department-management">
    <!-- Filter Bar -->
    <div class="filter-bar">
      <div class="filter-left">
        <el-select
          v-model="filterRegionId"
          placeholder="筛选区域"
          clearable
          style="width: 200px"
          @change="fetchDepartments"
        >
          <el-option
            v-for="region in flatRegions"
            :key="region.id"
            :label="region.name"
            :value="region.id"
          />
        </el-select>
        <el-input
          v-model="searchText"
          placeholder="搜索部门名称..."
          clearable
          style="width: 240px"
          prefix-icon="Search"
        />
      </div>
      <el-button type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>
        新增部门
      </el-button>
    </div>

    <!-- Department Table -->
    <div class="table-container" v-loading="loading">
      <el-table
        :data="filteredDepartments"
        row-key="id"
        default-expand-all
        :tree-props="{ children: 'children' }"
        class="department-table"
      >
        <el-table-column prop="name" label="部门名称" min-width="200">
          <template #default="{ row }">
            <div class="dept-name-cell">
              <el-icon class="dept-icon"><House /></el-icon>
              <span>{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="code" label="部门编码" width="120">
          <template #default="{ row }">
            {{ row.code || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="type" label="类型" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.type" size="small">{{ row.type }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="regionId" label="所属区域" width="150">
          <template #default="{ row }">
            {{ getRegionName(row.regionId) || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="parentId" label="上级部门" width="150">
          <template #default="{ row }">
            {{ getParentDeptName(row) || '无' }}
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" align="center">
          <template #default="{ row }">
            {{ row.sortOrder || 0 }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openCreateDialog(row)">
              添加子部门
            </el-button>
            <el-button type="primary" link size="small" @click="openEditDialog(row)">
              编辑
            </el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Create/Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="部门名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入部门名称" />
        </el-form-item>
        <el-form-item label="部门编码">
          <el-input v-model="form.code" placeholder="请输入部门编码" />
        </el-form-item>
        <el-form-item label="部门类型">
          <el-select v-model="form.type" placeholder="请选择类型" style="width: 100%">
            <el-option label="公司" value="公司" />
            <el-option label="部门" value="部门" />
            <el-option label="组" value="组" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属区域">
          <el-select v-model="form.regionId" placeholder="请选择区域" clearable style="width: 100%">
            <el-option
              v-for="region in flatRegions"
              :key="region.id"
              :label="region.name"
              :value="region.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="上级部门">
          <el-tree-select
            v-model="form.parentId"
            :data="deptTreeSelectData"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            placeholder="顶级部门"
            clearable
            check-strictly
            style="width: 100%"
            :render-after-expand="false"
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" style="width: 100%" />
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
import { ref, reactive, computed, onMounted } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { House, Plus } from '@element-plus/icons-vue'
import {
  getDepartmentTree,
  createDepartment,
  updateDepartment,
  deleteDepartment,
} from '@/api/modules/organization'
import { getRegionTree } from '@/api/modules/organization'
import type { Department, Region } from '@/types/user'

// State
const loading = ref(false)
const searchText = ref('')
const filterRegionId = ref<number | null>(null)
const departments = ref<Department[]>([])
const regions = ref<Region[]>([])

// Dialog
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  code: '',
  type: '',
  regionId: null as number | null,
  parentId: null as number | null,
  sortOrder: 0,
})

const dialogTitle = computed(() => (isEdit.value ? '编辑部门' : '新增部门'))

const formRules: FormRules = {
  name: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
}

// Computed
const flatRegions = computed(() => {
  const result: Region[] = []
  function flatten(list: Region[]) {
    for (const item of list) {
      result.push(item)
      if (item.children) flatten(item.children)
    }
  }
  flatten(regions.value)
  return result
})

const deptTreeSelectData = computed(() => {
  return [{ id: 0, name: '顶级部门', children: departments.value }]
})

const filteredDepartments = computed(() => {
  let result = departments.value

  // Filter by search text
  if (searchText.value) {
    const search = searchText.value.toLowerCase()
    result = filterDepartmentsByName(result, search)
  }

  return result
})

function filterDepartmentsByName(list: Department[], search: string): Department[] {
  const result: Department[] = []
  for (const dept of list) {
    const matches = dept.name.toLowerCase().includes(search)
    const children = dept.children ? filterDepartmentsByName(dept.children, search) : []

    if (matches || children.length > 0) {
      result.push({
        ...dept,
        children: children.length > 0 ? children : dept.children,
      })
    }
  }
  return result
}

// Fetch Data
async function fetchDepartments() {
  loading.value = true
  try {
    const res = await getDepartmentTree() as any
    let depts = res || []

    // Filter by region if selected
    if (filterRegionId.value) {
      depts = filterDepartmentsByRegion(depts, filterRegionId.value)
    }

    departments.value = depts
  } catch {
    ElMessage.error('加载部门数据失败')
  } finally {
    loading.value = false
  }
}

function filterDepartmentsByRegion(list: Department[], regionId: number): Department[] {
  const result: Department[] = []
  for (const dept of list) {
    const matches = dept.regionId === regionId
    const children = dept.children ? filterDepartmentsByRegion(dept.children, regionId) : []

    if (matches || children.length > 0) {
      result.push({
        ...dept,
        children: children.length > 0 ? children : dept.children,
      })
    }
  }
  return result
}

async function fetchRegions() {
  try {
    const res = await getRegionTree() as any
    regions.value = res || []
  } catch {
    ElMessage.error('加载区域数据失败')
  }
}

// Helper
function getRegionName(regionId: number | null): string | null {
  if (!regionId) return null
  const region = flatRegions.value.find((r) => r.id === regionId)
  return region?.name || null
}

function getParentDeptName(dept: Department): string | null {
  if (!dept.parentId) return null

  function findDept(list: Department[], id: number): Department | null {
    for (const d of list) {
      if (d.id === id) return d
      if (d.children) {
        const found = findDept(d.children, id)
        if (found) return found
      }
    }
    return null
  }

  const parent = findDept(departments.value, dept.parentId)
  return parent?.name || null
}

// Dialog Handlers
function openCreateDialog(parent?: Department) {
  isEdit.value = false
  editId.value = null
  resetForm()
  if (parent) {
    form.parentId = parent.id
    form.regionId = parent.regionId
  }
  dialogVisible.value = true
}

function openEditDialog(dept: Department) {
  isEdit.value = true
  editId.value = dept.id
  form.name = dept.name
  form.code = dept.code || ''
  form.type = dept.type || ''
  form.regionId = dept.regionId || null
  form.parentId = dept.parentId || null
  form.sortOrder = dept.sortOrder || 0
  dialogVisible.value = true
}

function resetForm() {
  form.name = ''
  form.code = ''
  form.type = ''
  form.regionId = null
  form.parentId = null
  form.sortOrder = 0
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload: any = {
      name: form.name,
      code: form.code || null,
      type: form.type || null,
      regionId: form.regionId || null,
      parentId: form.parentId || null,
      sortOrder: form.sortOrder,
    }

    if (isEdit.value) {
      payload.id = editId.value
      await updateDepartment(editId.value!, payload)
    } else {
      await createDepartment(payload)
    }

    ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
    dialogVisible.value = false
    fetchDepartments()
  } catch {
    // error handled by interceptor
  } finally {
    submitting.value = false
  }
}

async function handleDelete(dept: Department) {
  try {
    await ElMessageBox.confirm(`确定要删除部门"${dept.name}"吗？`, '删除确认', {
      type: 'warning',
    })
    await deleteDepartment(dept.id)
    ElMessage.success('删除成功')
    fetchDepartments()
  } catch {
    // cancelled or error
  }
}

// Init
onMounted(() => {
  fetchRegions()
  fetchDepartments()
})
</script>

<style scoped>
.department-management {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.filter-left {
  display: flex;
  gap: 12px;
}

.table-container {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  overflow: auto;
}

.department-table {
  width: 100%;
}

.dept-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dept-icon {
  color: #67c23a;
  font-size: 16px;
}
</style>
