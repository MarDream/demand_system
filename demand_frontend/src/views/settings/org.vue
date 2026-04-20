<template>
  <div class="org-page">
    <div class="page-header">
      <h2>组织架构管理</h2>
    </div>

    <el-card>
      <!-- Toolbar -->
      <div class="toolbar">
        <el-input
          v-model="searchText"
          placeholder="搜索区域/部门/岗位..."
          clearable
          prefix-icon="Search"
          style="width: 300px"
        />
        <div class="toolbar-actions">
          <el-button type="primary" @click="openDialog('region')">新增区域</el-button>
          <el-button @click="openDialog('department')">新增部门</el-button>
          <el-button @click="openDialog('position')">新增岗位</el-button>
        </div>
      </div>

      <!-- Unified Tree -->
      <el-tree
        ref="treeRef"
        :data="orgTree"
        :props="{ label: 'label', children: 'children' }"
        node-key="key"
        default-expand-all
        :expand-on-click-node="false"
        :filter-node-method="filterNode"
        v-loading="loading"
        class="org-tree"
      >
        <template #default="{ node, data }">
          <span class="tree-node">
            <el-icon v-if="data.type === 'region'" style="color: #409EFF"><OfficeBuilding /></el-icon>
            <el-icon v-else-if="data.type === 'department'" style="color: #67C23A"><House /></el-icon>
            <el-icon v-else style="color: #E6A23C"><User /></el-icon>
            <span class="node-label">{{ node.label }}</span>
            <span v-if="data.code" class="node-code">({{ data.code }})</span>
            <el-tag v-if="data.tag" size="small" style="margin-left: 6px">{{ data.tag }}</el-tag>
            <span class="node-actions">
              <el-button v-if="data.type !== 'position-root'" type="primary" link size="small" @click.stop="openDialog(data.type, data)">编辑</el-button>
              <el-button type="danger" link size="small" @click.stop="handleDelete(data)">删除</el-button>
            </span>
          </span>
        </template>
      </el-tree>
    </el-card>

    <!-- Unified Dialog -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="currentRules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入名称" />
        </el-form-item>

        <template v-if="dialogType === 'region'">
          <el-form-item label="区域编码">
            <el-input v-model="form.code" placeholder="请输入区域编码" />
          </el-form-item>
          <el-form-item label="上级区域">
            <el-tree-select
              v-model="form.parentId"
              :data="regionTreeSelectData"
              :props="{ label: 'name', value: 'id', children: 'children' }"
              placeholder="顶级区域"
              clearable
              check-strictly
              style="width: 100%"
            />
          </el-form-item>
        </template>

        <template v-if="dialogType === 'department'">
          <el-form-item label="上级部门">
            <el-tree-select
              v-model="form.parentId"
              :data="deptTreeSelectData"
              :props="{ label: 'name', value: 'id', children: 'children' }"
              placeholder="顶级部门"
              clearable
              check-strictly
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="所属区域">
            <el-select v-model="form.regionId" placeholder="请选择区域" clearable style="width: 100%">
              <el-option v-for="r in flatRegions" :key="r.id" :label="r.name" :value="r.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="form.type" placeholder="请选择类型" style="width: 100%">
              <el-option label="公司" value="公司" />
              <el-option label="部门" value="部门" />
              <el-option label="组" value="组" />
            </el-select>
          </el-form-item>
        </template>

        <template v-if="dialogType === 'position'">
          <el-form-item label="岗位编码">
            <el-input v-model="form.code" placeholder="请输入岗位编码" />
          </el-form-item>
          <el-form-item label="职级">
            <el-input-number v-model="form.level" :min="1" :max="99" style="width: 100%" />
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入描述" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { OfficeBuilding, House, User } from '@element-plus/icons-vue'
import {
  getRegionTree, getDepartmentTree, getPositionList,
  createRegion, updateRegion, deleteRegion,
  createDepartment, updateDepartment, deleteDepartment,
  createPosition, updatePosition, deletePosition,
} from '@/api/modules/user'

interface OrgTreeNode {
  key: string
  label: string
  type: 'region' | 'department' | 'position' | 'position-root'
  id: number
  code?: string
  tag?: string
  children?: OrgTreeNode[]
  rawData?: any
}

const loading = ref(false)
const searchText = ref('')
const treeRef = ref<any>()
const regions = ref<any[]>([])
const departments = ref<any[]>([])
const positions = ref<any[]>([])

// Dialog state
const dialogVisible = ref(false)
const dialogType = ref<'region' | 'department' | 'position'>('region')
const isEdit = ref(false)
const editId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  code: '',
  parentId: null as number | null,
  regionId: null as number | null,
  type: '',
  level: 1,
  description: '',
})

const dialogTitle = computed(() => {
  const typeMap = { region: '区域', department: '部门', position: '岗位' }
  return `${isEdit.value ? '编辑' : '新增'}${typeMap[dialogType.value]}`
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
}
const currentRules = computed(() => formRules)

// Flatten regions for select dropdown
const flatRegions = computed(() => {
  const result: any[] = []
  function flatten(list: any[]) {
    for (const item of list) {
      result.push(item)
      if (item.children) flatten(item.children)
    }
  }
  flatten(regions.value)
  return result
})

const regionTreeSelectData = computed(() => {
  return [{ id: 0, name: '顶级区域', children: regions.value }]
})

const deptTreeSelectData = computed(() => {
  return [{ id: 0, name: '顶级部门', children: departments.value }]
})

// Build unified tree
const orgTree = computed<OrgTreeNode[]>(() => {
  const tree: OrgTreeNode[] = []

  // Build region nodes with nested departments
  for (const region of regions.value) {
    tree.push(buildRegionNode(region))
  }

  // Find root departments not belonging to any region
  const rootDepts = departments.value.filter(d => !d.regionId && !d.parentId)
  for (const dept of rootDepts) {
    tree.push(buildDeptNode(dept))
  }

  // Virtual positions root
  if (positions.value.length > 0) {
    const posNode: OrgTreeNode = {
      key: 'position-root',
      label: '岗位管理',
      type: 'position-root',
      id: 0,
      children: positions.value.map(p => ({
        key: `position-${p.id}`,
        label: p.name,
        type: 'position' as const,
        id: p.id,
        code: p.code,
        tag: p.level ? `L${p.level}` : undefined,
        rawData: p,
      })),
    }
    tree.push(posNode)
  }

  return tree
})

function buildRegionNode(region: any): OrgTreeNode {
  const node: OrgTreeNode = {
    key: `region-${region.id}`,
    label: region.name,
    type: 'region',
    id: region.id,
    code: region.code,
    rawData: region,
    children: [],
  }
  // Attach departments belonging to this region (root level only)
  const regionDepts = departments.value.filter(d => d.regionId === region.id && !d.parentId)
  for (const dept of regionDepts) {
    node.children!.push(buildDeptNode(dept))
  }
  // Attach nested region children
  if (region.children && region.children.length > 0) {
    for (const child of region.children) {
      node.children!.push(buildRegionNode(child))
    }
  }
  return node
}

function buildDeptNode(dept: any): OrgTreeNode {
  const node: OrgTreeNode = {
    key: `dept-${dept.id}`,
    label: dept.name,
    type: 'department',
    id: dept.id,
    code: dept.code,
    tag: dept.type,
    rawData: dept,
    children: [],
  }
  if (dept.children && dept.children.length > 0) {
    for (const child of dept.children) {
      node.children!.push(buildDeptNode(child))
    }
  }
  return node
}

// Search filter
function filterNode(value: string, data: OrgTreeNode): boolean {
  if (!value) return true
  return data.label.toLowerCase().includes(value.toLowerCase())
}

watch(searchText, (val) => {
  treeRef.value?.filter(val)
})

// Fetch all data
async function fetchAllData() {
  loading.value = true
  try {
    const [r, d, p] = await Promise.all([
      getRegionTree() as any,
      getDepartmentTree() as any,
      getPositionList() as any,
    ])
    regions.value = r || []
    departments.value = d || []
    positions.value = p || []
  } catch {
    ElMessage.error('加载组织架构数据失败')
  } finally {
    loading.value = false
  }
}

// Dialog operations
function openDialog(type: 'region' | 'department' | 'position', data?: OrgTreeNode) {
  dialogType.value = type
  if (data && data.type !== 'position-root') {
    isEdit.value = true
    editId.value = data.id
    form.name = data.label
    form.code = data.code || ''
    if (type === 'region') {
      form.parentId = data.rawData?.parentId || null
    } else if (type === 'department') {
      form.parentId = data.rawData?.parentId || null
      form.regionId = data.rawData?.regionId || null
      form.type = data.rawData?.type || ''
    } else if (type === 'position') {
      form.level = data.rawData?.level || 1
      form.description = data.rawData?.description || ''
    }
  } else {
    isEdit.value = false
    editId.value = null
    form.name = ''
    form.code = ''
    form.parentId = null
    form.regionId = null
    form.type = ''
    form.level = 1
    form.description = ''
  }
  dialogVisible.value = true
}

function resetForm() {
  form.name = ''
  form.code = ''
  form.parentId = null
  form.regionId = null
  form.type = ''
  form.level = 1
  form.description = ''
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (dialogType.value === 'region') {
      const payload: any = { name: form.name, code: form.code || null, parentId: form.parentId || null }
      if (isEdit.value) {
        payload.id = editId.value
        await updateRegion(editId.value!, payload)
      } else {
        await createRegion(payload)
      }
    } else if (dialogType.value === 'department') {
      const payload: any = { name: form.name, parentId: form.parentId || null, regionId: form.regionId || null, type: form.type || null }
      if (isEdit.value) {
        await updateDepartment(editId.value!, payload)
      } else {
        await createDepartment(payload)
      }
    } else {
      const payload: any = { name: form.name, code: form.code || null, level: form.level, description: form.description || null }
      if (isEdit.value) {
        await updatePosition(editId.value!, payload)
      } else {
        await createPosition(payload)
      }
    }
    ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
    dialogVisible.value = false
    fetchAllData()
  } catch {
    // error handled by interceptor
  } finally {
    submitting.value = false
  }
}

async function handleDelete(data: OrgTreeNode) {
  if (data.type === 'position-root') return
  try {
    await ElMessageBox.confirm(`确定要删除"${data.label}"吗？`, '删除确认', { type: 'warning' })
    if (data.type === 'region') await deleteRegion(data.id)
    else if (data.type === 'department') await deleteDepartment(data.id)
    else await deletePosition(data.id)
    ElMessage.success('删除成功')
    fetchAllData()
  } catch {
    // cancelled or error
  }
}

onMounted(() => {
  fetchAllData()
})
</script>

<style scoped>
.org-page {
  padding: 20px;
}

.page-header {
  margin-bottom: 16px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
}

.org-tree {
  margin-top: 8px;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  font-size: 14px;
}

.node-label {
  flex-shrink: 0;
}

.node-code {
  color: #909399;
  font-size: 12px;
}

.node-actions {
  display: none;
  margin-left: auto;
}

.tree-node:hover .node-actions {
  display: inline-flex;
}
</style>
