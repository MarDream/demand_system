<template>
  <div class="position-management">
    <!-- Filter Bar -->
    <div class="filter-bar">
      <el-input
        v-model="searchText"
        placeholder="搜索岗位名称..."
        clearable
        style="width: 240px"
        prefix-icon="Search"
      />
      <el-button type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>
        新增岗位
      </el-button>
    </div>

    <!-- Position Table -->
    <div class="table-container" v-loading="loading">
      <el-table
        :data="filteredPositions"
        row-key="id"
        class="position-table"
      >
        <el-table-column prop="name" label="岗位名称" min-width="200">
          <template #default="{ row }">
            <div class="position-name-cell">
              <el-icon class="position-icon"><User /></el-icon>
              <span>{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="code" label="岗位编码" width="150">
          <template #default="{ row }">
            {{ row.code || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="level" label="职级" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.level" type="warning" size="small">L{{ row.level }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200">
          <template #default="{ row }">
            {{ row.description || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" align="center">
          <template #default="{ row }">
            {{ row.sortOrder || 0 }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="岗位名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入岗位名称" />
        </el-form-item>
        <el-form-item label="岗位编码">
          <el-input v-model="form.code" placeholder="请输入岗位编码" />
        </el-form-item>
        <el-form-item label="职级">
          <el-input-number v-model="form.level" :min="1" :max="99" style="width: 100%" />
        </el-form-item>
        <el-form-item label="归属区域">
          <el-tree-select
            v-model="form.regionId"
            :data="regionTree"
            :props="{ label: 'name', value: 'id' }"
            placeholder="请选择归属区域"
            clearable
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="归属部门">
          <el-tree-select
            v-model="form.departmentId"
            :data="departmentTree"
            :props="{ label: 'name', value: 'id' }"
            placeholder="请选择归属部门"
            clearable
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入岗位描述" />
        </el-form-item>
        <el-form-item label="菜单权限">
          <el-tree
            ref="menuTreeRef"
            :data="menuTree"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            show-checkbox
            :default-checked-keys="form.menuPermissions"
            check-strictly
            style="max-height: 240px; overflow-y: auto; border: 1px solid #dcdfe6; border-radius: 4px; padding: 8px; width: 100%"
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
import { User, Plus } from '@element-plus/icons-vue'
import {
  getPositionList,
  createPosition,
  updatePosition,
  deletePosition,
  getRegionTree,
  getDepartmentTree,
} from '@/api/modules/organization'
import { getAllMenus } from '@/api/modules/menu'
import type { Position, Region, Department } from '@/types/user'

// State
const loading = ref(false)
const searchText = ref('')
const positions = ref<Position[]>([])
const regionTree = ref<Region[]>([])
const departmentTree = ref<Department[]>([])
const menuTree = ref<any[]>([])
const menuTreeRef = ref<any>()

// Dialog
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  code: '',
  level: 1,
  description: '',
  regionId: null as number | null,
  departmentId: null as number | null,
  menuPermissions: [] as number[],
  sortOrder: 0,
})

const dialogTitle = computed(() => (isEdit.value ? '编辑岗位' : '新增岗位'))

const formRules: FormRules = {
  name: [{ required: true, message: '请输入岗位名称', trigger: 'blur' }],
}

// Computed
const filteredPositions = computed(() => {
  if (!searchText.value) return positions.value

  const search = searchText.value.toLowerCase()
  return positions.value.filter((pos) =>
    pos.name.toLowerCase().includes(search) ||
    (pos.code && pos.code.toLowerCase().includes(search))
  )
})

// Fetch Data
async function fetchPositions() {
  loading.value = true
  try {
    const res = await getPositionList() as any
    positions.value = res || []
  } catch {
    ElMessage.error('加载岗位数据失败')
  } finally {
    loading.value = false
  }
}

// Dialog Handlers
function openCreateDialog() {
  isEdit.value = false
  editId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEditDialog(position: any) {
  isEdit.value = true
  editId.value = position.id
  form.name = position.name
  form.code = position.code || ''
  form.level = position.level || 1
  form.description = position.description || ''
  form.regionId = position.regionId || null
  form.departmentId = position.departmentId || null
  form.menuPermissions = position.menuPermissions || []
  form.sortOrder = position.sortOrder ?? 0
  dialogVisible.value = true
}

function resetForm() {
  form.name = ''
  form.code = ''
  form.level = 1
  form.description = ''
  form.regionId = null
  form.departmentId = null
  form.menuPermissions = []
  form.sortOrder = 0
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  const checkedKeys = menuTreeRef.value?.getCheckedKeys() || []

  submitting.value = true
  try {
    const payload: any = {
      name: form.name,
      code: form.code || null,
      level: form.level,
      description: form.description || null,
      regionId: form.regionId || null,
      departmentId: form.departmentId || null,
      menuPermissions: checkedKeys,
      sortOrder: form.sortOrder,
    }

    if (isEdit.value) {
      await updatePosition(editId.value!, payload)
    } else {
      await createPosition(payload)
    }

    ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
    dialogVisible.value = false
    fetchPositions()
  } catch {
    // error handled by interceptor
  } finally {
    submitting.value = false
  }
}

async function handleDelete(position: Position) {
  try {
    await ElMessageBox.confirm(`确定要删除岗位"${position.name}"吗？`, '删除确认', {
      type: 'warning',
    })
    await deletePosition(position.id)
    ElMessage.success('删除成功')
    fetchPositions()
  } catch {
    // cancelled or error
  }
}

async function loadOrgData() {
  try {
    const [regions, departments, menus] = await Promise.all([
      getRegionTree(),
      getDepartmentTree(),
      getAllMenus(),
    ])
    regionTree.value = (regions as any) || []
    departmentTree.value = (departments as any) || []
    menuTree.value = buildMenuTree((menus as any) || [])
  } catch (error) {
    console.error('加载组织架构数据失败:', error)
  }
}

function buildMenuTree(flatList: any[]): any[] {
  const map = new Map<number, any>()
  const roots: any[] = []
  for (const item of flatList) {
    map.set(item.id, { ...item, children: [] })
  }
  for (const item of flatList) {
    const node = map.get(item.id)
    if (!node) continue
    if (item.parentId && map.has(item.parentId)) {
      map.get(item.parentId).children.push(node)
    } else {
      roots.push(node)
    }
  }
  return roots
}

// Init
onMounted(() => {
  fetchPositions()
  loadOrgData()
})
</script>

<style scoped>
.position-management {
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

.table-container {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  overflow: auto;
}

.position-table {
  width: 100%;
}

.position-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.position-icon {
  color: #e6a23c;
  font-size: 16px;
}
</style>
