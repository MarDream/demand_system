<template>
  <div class="org-page">
    <div class="page-header">
      <h2>组织架构管理</h2>
    </div>

    <div class="org-container">
      <!-- Left Panel: Organization Tree -->
      <div class="left-panel">
        <div class="panel-header">
          <span class="panel-title">组织架构</span>
          <div class="panel-actions">
            <el-button type="primary" size="small" @click="openCreateDialog(null, 'region')">
              <el-icon><Plus /></el-icon>
            </el-button>
            <el-button size="small" @click="openCreateDialog(null, 'department')">
              <el-icon><Plus /></el-icon>
            </el-button>
            <el-dropdown trigger="click" @command="handleBatchAction">
              <el-button size="small" :disabled="selectedNodes.length === 0">
                <el-icon><MoreFilled /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="delete">批量删除</el-dropdown-item>
                  <el-dropdown-item command="move">批量移动</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>

        <div class="search-box">
          <el-input
            v-model="searchText"
            placeholder="搜索区域或部门..."
            clearable
            size="small"
            prefix-icon="Search"
          />
        </div>

        <div class="tree-wrapper" v-loading="loading">
          <!-- Region & Department Tree -->
          <el-tree
            ref="treeRef"
            :data="treeData"
            :props="treeProps"
            node-key="key"
            :expand-on-click-node="false"
            :filter-node-method="filterNode"
            highlight-current
            show-checkbox
            check-strictly
            @node-click="handleNodeClick"
            @check="handleNodeCheck"
            class="org-tree"
          >
            <template #default="{ node, data }">
              <div class="tree-node-content" :class="data.type">
                <el-icon v-if="data.type === 'region'" class="node-icon region"><OfficeBuilding /></el-icon>
                <el-icon v-else-if="data.type === 'department'" class="node-icon department"><House /></el-icon>
                <span class="node-label">{{ node.label }}</span>
                <span v-if="data.type === 'department' && data.data?.type" class="node-tag">
                  {{ data.data.type }}
                </span>
              </div>
            </template>
          </el-tree>

          <!-- Position Section -->
          <div class="position-section">
            <div class="section-header" @click="positionsExpanded = !positionsExpanded">
              <el-checkbox
                v-if="positionsExpanded && positions.length > 0"
                :model-value="isAllPositionsSelected"
                :indeterminate="isPositionsIndeterminate"
                @click.stop
                @change="toggleSelectAllPositions"
              />
              <el-icon v-else style="width: 14px"></el-icon>
              <el-icon :class="{ rotated: !positionsExpanded }"><ArrowRight /></el-icon>
              <span>岗位管理</span>
              <span class="section-count">{{ positions.length }}</span>
            </div>
            <div v-show="positionsExpanded" class="position-list">
              <div
                v-for="pos in positions"
                :key="pos.id"
                class="position-item"
                :class="{ active: selectedType === 'position' && selectedId === pos.id }"
              >
                <el-checkbox
                  :model-value="selectedPositions.has(pos.id)"
                  @click.stop
                  @change="togglePositionSelection(pos.id)"
                />
                <el-icon class="position-icon"><User /></el-icon>
                <span class="position-name" @click="selectPosition(pos)">{{ pos.name }}</span>
                <span v-if="pos.level" class="position-level">L{{ pos.level }}</span>
              </div>
              <div v-if="positions.length === 0" class="empty-tip">暂无岗位</div>
              <el-button size="small" class="add-position-btn" @click.stop="openCreateDialog(null, 'position')">
                <el-icon><Plus /></el-icon>
                新增岗位
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- Right Panel: Detail View -->
      <div class="right-panel">
        <!-- Empty State -->
        <div v-if="!selectedNode && !selectedPosition" class="empty-state">
          <el-icon class="empty-icon"><Files /></el-icon>
          <p>请从左侧选择要查看的区域、部门或岗位</p>
        </div>

        <!-- Region Detail -->
        <div v-else-if="selectedType === 'region' && selectedNode" class="detail-content">
          <div class="detail-header">
            <div class="detail-title">
              <el-icon class="title-icon region"><OfficeBuilding /></el-icon>
              <span>{{ selectedNode.data.name }}</span>
            </div>
            <div class="detail-actions">
              <el-button @click="openCreateDialog(selectedNode!, 'region')">添加子区域</el-button>
              <el-button @click="openCreateDialog(selectedNode!, 'department')">添加部门</el-button>
              <el-button @click="openEditDialog(selectedNode!)">编辑</el-button>
              <el-button type="danger" @click="handleDelete(selectedNode!)">删除</el-button>
            </div>
          </div>

          <!-- Tabs -->
          <el-tabs v-model="regionActiveTab" class="detail-tabs">
            <el-tab-pane label="基本信息" name="info">
              <el-card class="info-card">
                <div class="info-grid">
                  <div class="info-item">
                    <span class="info-label">区域名称</span>
                    <span class="info-value">{{ selectedNode.data.name }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">区域编码</span>
                    <span class="info-value">{{ (selectedNode.data as any).code || '-' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">上级区域</span>
                    <span class="info-value">{{ getParentRegionName(selectedNode.data as Region) || '无' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">子区域数</span>
                    <span class="info-value">{{ childRegions.length }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">部门数</span>
                    <span class="info-value">{{ regionDeptCount }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">成员数</span>
                    <span class="info-value">{{ regionMemberCount }}</span>
                  </div>
                </div>
              </el-card>

              <el-card class="info-card" v-if="childRegions.length > 0">
                <template #header>
                  <span>子区域</span>
                </template>
                <div class="child-list">
                  <div v-for="r in childRegions" :key="r.id" class="child-item" @click="selectRegion(r)">
                    <el-icon><OfficeBuilding /></el-icon>
                    <span>{{ r.name }}</span>
                    <span class="child-code" v-if="r.code">({{ r.code }})</span>
                  </div>
                </div>
              </el-card>

              <el-card class="info-card">
                <template #header>
                  <span>直接下属部门</span>
                </template>
                <div v-if="childDepts.length > 0" class="dept-table">
                  <el-table :data="childDepts" size="small">
                    <el-table-column prop="name" label="部门名称" />
                    <el-table-column prop="type" label="类型" width="80">
                      <template #default="{ row }">{{ row.type || '-' }}</template>
                    </el-table-column>
                    <el-table-column label="操作" width="80" align="center">
                      <template #default="{ row }">
                        <el-button type="primary" link size="small" @click="selectDept(row)">查看</el-button>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
                <div v-else class="empty-tip">该区域下暂无部门</div>
              </el-card>
            </el-tab-pane>

            <el-tab-pane :label="`成员 (${regionMemberCount})`" name="members">
              <div v-if="regionMembers.length > 0" class="member-table">
                <el-table :data="regionMembers" size="small">
                  <el-table-column prop="realName" label="姓名" />
                  <el-table-column prop="username" label="用户名" />
                  <el-table-column prop="email" label="邮箱" />
                  <el-table-column prop="phone" label="手机号" />
                  <el-table-column prop="status" label="状态" width="80">
                    <template #default="{ row }">
                      <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">
                        {{ row.status === 'active' ? '激活' : '未激活' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
              <div v-else class="empty-tip">该区域下暂无成员</div>
            </el-tab-pane>
          </el-tabs>
        </div>

        <!-- Department Detail -->
        <div v-else-if="selectedType === 'department' && selectedNode" class="detail-content">
          <div class="detail-header">
            <div class="detail-title">
              <el-icon class="title-icon department"><House /></el-icon>
              <span>{{ selectedNode.data.name }}</span>
              <el-tag v-if="(selectedNode.data as any).type" size="small">{{ (selectedNode.data as any).type }}</el-tag>
            </div>
            <div class="detail-actions">
              <el-button @click="openCreateDialog(selectedNode!, 'department')">添加子部门</el-button>
              <el-button @click="openEditDialog(selectedNode!)">编辑</el-button>
              <el-button type="danger" @click="handleDelete(selectedNode!)">删除</el-button>
            </div>
          </div>

          <el-tabs v-model="deptActiveTab" class="detail-tabs">
            <el-tab-pane label="基本信息" name="info">
              <el-card class="info-card">
                <div class="info-grid">
                  <div class="info-item">
                    <span class="info-label">部门名称</span>
                    <span class="info-value">{{ selectedNode.data.name }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">部门编码</span>
                    <span class="info-value">{{ (selectedNode.data as any).code || '-' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">部门类型</span>
                    <span class="info-value">{{ (selectedNode.data as any).type || '-' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">所属区域</span>
                    <span class="info-value">{{ getRegionName((selectedNode.data as any).regionId) || '-' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">上级部门</span>
                    <span class="info-value">{{ getParentDeptName(selectedNode.data as Department) || '无' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">子部门数</span>
                    <span class="info-value">{{ childDepts.length }}</span>
                  </div>
                </div>
              </el-card>

              <el-card class="info-card" v-if="childDepts.length > 0">
                <template #header>
                  <span>子部门</span>
                </template>
                <div class="dept-table">
                  <el-table :data="childDepts" size="small">
                    <el-table-column prop="name" label="部门名称" />
                    <el-table-column prop="type" label="类型" width="80">
                      <template #default="{ row }">{{ row.type || '-' }}</template>
                    </el-table-column>
                    <el-table-column label="操作" width="80" align="center">
                      <template #default="{ row }">
                        <el-button type="primary" link size="small" @click="selectDept(row)">查看</el-button>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
              </el-card>

              <el-card class="info-card">
                <template #header>
                  <span>岗位配置</span>
                </template>
                <div class="position-tags">
                  <el-tag v-for="pos in assignedPositions" :key="pos.id" class="position-tag">
                    {{ pos.name }}
                    <span class="pos-level">L{{ pos.level }}</span>
                  </el-tag>
                  <el-tag v-if="assignedPositions.length === 0" type="info">暂无岗位</el-tag>
                </div>
              </el-card>
            </el-tab-pane>

            <el-tab-pane :label="`成员 (${deptMemberCount})`" name="members">
              <div v-if="deptMembers.length > 0" class="member-table">
                <el-table :data="deptMembers" size="small">
                  <el-table-column prop="realName" label="姓名" />
                  <el-table-column prop="username" label="用户名" />
                  <el-table-column prop="email" label="邮箱" />
                  <el-table-column prop="phone" label="手机号" />
                  <el-table-column prop="status" label="状态" width="80">
                    <template #default="{ row }">
                      <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">
                        {{ row.status === 'active' ? '激活' : '未激活' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
              <div v-else class="empty-tip">该部门下暂无成员</div>
            </el-tab-pane>
          </el-tabs>
        </div>

        <!-- Position Detail -->
        <div v-else-if="selectedType === 'position'" class="detail-content">
          <div class="detail-header">
            <div class="detail-title">
              <el-icon class="title-icon position"><User /></el-icon>
              <span>{{ selectedPosition?.name }}</span>
              <el-tag v-if="selectedPosition?.level" size="small">L{{ selectedPosition.level }}</el-tag>
            </div>
            <div class="detail-actions">
              <el-button @click="openEditPositionDialog()">编辑</el-button>
              <el-button type="danger" @click="handleDeletePosition()">删除</el-button>
            </div>
          </div>

          <el-card class="info-card">
            <template #header>
              <span>岗位信息</span>
            </template>
            <div class="info-grid">
              <div class="info-item">
                <span class="info-label">岗位名称</span>
                <span class="info-value">{{ selectedPosition?.name }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">岗位编码</span>
                <span class="info-value">{{ selectedPosition?.code || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">职级</span>
                <span class="info-value">{{ selectedPosition?.level ? `L${selectedPosition.level}` : '-' }}</span>
              </div>
            </div>
            <div class="info-item full-width">
              <span class="info-label">岗位描述</span>
              <span class="info-value">{{ selectedPosition?.description || '暂无描述' }}</span>
            </div>
          </el-card>

          <el-card class="info-card">
            <template #header>
              <div class="card-header-with-action">
                <span>关联部门 ({{ positionDeptRelations.length }})</span>
                <el-button size="small" type="primary" @click="openDeptSelectorDialog">
                  <el-icon><Plus /></el-icon>
                  编辑关联
                </el-button>
              </div>
            </template>
            <div v-if="positionDeptRelations.length > 0" class="dept-tags">
              <el-tag
                v-for="dept in positionDeptRelations"
                :key="dept.id"
                closable
                @close="removeDeptRelation(dept.id)"
              >
                {{ dept.name }}
              </el-tag>
            </div>
            <div v-else class="empty-tip">暂未关联任何部门</div>
          </el-card>
        </div>
      </div>
    </div>

    <!-- Create/Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
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
              :render-after-expand="false"
            />
          </el-form-item>
        </template>

        <template v-if="dialogType === 'department'">
          <el-form-item label="部门编码">
            <el-input v-model="form.code" placeholder="请输入部门编码" />
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

    <!-- Position-Department Relation Dialog -->
    <el-dialog v-model="deptSelectorVisible" title="编辑岗位关联部门" width="500px">
      <div class="dept-selector">
        <div class="selector-tip">选择可担任此岗位的部门：</div>
        <el-checkbox-group v-model="selectedDeptIds">
          <div v-for="dept in allDeptsForSelector" :key="dept.id" class="dept-option">
            <el-checkbox :value="dept.id">{{ dept.name }}</el-checkbox>
            <span v-if="dept.type" class="dept-type">{{ dept.type }}</span>
          </div>
        </el-checkbox-group>
      </div>
      <template #footer>
        <el-button @click="deptSelectorVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="saveDeptRelations">保存</el-button>
      </template>
    </el-dialog>

    <!-- Batch Move Dialog -->
    <el-dialog v-model="batchMoveVisible" title="批量移动" width="400px">
      <el-form label-width="80px">
        <el-form-item label="目标区域">
          <el-select v-model="batchMoveTarget.regionId" placeholder="选择区域" clearable style="width: 100%" @change="onRegionChange">
            <el-option v-for="r in regions" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标部门">
          <el-select v-model="batchMoveTarget.departmentId" placeholder="选择部门" clearable style="width: 100%">
            <el-option v-for="d in moveTargetDepts" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchMoveVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmBatchMove">确定移动</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { OfficeBuilding, House, User, Plus, ArrowRight, Search, Files, MoreFilled } from '@element-plus/icons-vue'
import {
  getRegionTree, getDepartmentTree, getPositionList, getUserList,
  createRegion, updateRegion, deleteRegion,
  createDepartment, updateDepartment, deleteDepartment,
  createPosition, updatePosition, deletePosition,
} from '@/api/modules/user'
import type { Region, Department, Position, User as UserType, UserQuery } from '@/types/user'

// Types
interface TreeNode {
  key: string
  label: string
  type: 'region' | 'department'
  data: Region | Department
  children?: TreeNode[]
}

interface ParentContext {
  type: 'region' | 'department'
  data: Region | Department
}

interface PositionDeptRelation {
  positionId: number
  departmentId: number
}

// State
const loading = ref(false)
const searchText = ref('')
const treeRef = ref<any>()
const positionsExpanded = ref(true)
const regionActiveTab = ref('info')
const deptActiveTab = ref('info')

// Data
const regions = ref<Region[]>([])
const departments = ref<Department[]>([])
const positions = ref<Position[]>([])
const allUsers = ref<UserType[]>([])
const positionDeptRelations = ref<{ id: number; name: string }[]>([])

// Selection
const selectedType = ref<'region' | 'department' | 'position' | null>(null)
const selectedId = ref<number | null>(null)
const selectedPosition = ref<Position | null>(null)

// Batch Selection
const selectedNodes = ref<TreeNode[]>([])
const selectedPositions = ref<Set<number>>(new Set())

// Dialog
const dialogVisible = ref(false)
const dialogType = ref<'region' | 'department' | 'position'>('region')
const isEdit = ref(false)
const editId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

// Dept Selector Dialog
const deptSelectorVisible = ref(false)
const selectedDeptIds = ref<number[]>([])

// Batch Move Dialog
const batchMoveVisible = ref(false)
const batchMoveTarget = reactive({
  regionId: null as number | null,
  departmentId: null as number | null,
})

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

// Computed: Tree Data
const treeProps = {
  label: 'label',
  children: 'children',
}

const treeData = computed<TreeNode[]>(() => {
  const result: TreeNode[] = []
  for (const region of regions.value) {
    result.push(buildRegionNode(region))
  }
  const rootDepts = departments.value.filter(d => !d.regionId && !d.parentId)
  for (const dept of rootDepts) {
    result.push(buildDeptNode(dept))
  }
  return result
})

function buildRegionNode(region: Region): TreeNode {
  const children: TreeNode[] = []
  if (region.children && region.children.length > 0) {
    for (const child of region.children) {
      children.push(buildRegionNode(child))
    }
  }
  const regionDepts = departments.value.filter(d => d.regionId === region.id && !d.parentId)
  for (const dept of regionDepts) {
    children.push(buildDeptNode(dept))
  }
  return {
    key: `region-${region.id}`,
    label: region.name,
    type: 'region',
    data: region,
    children: children.length > 0 ? children : undefined,
  }
}

function buildDeptNode(dept: Department): TreeNode {
  const children: TreeNode[] = []
  if (dept.children && dept.children.length > 0) {
    for (const child of dept.children) {
      children.push(buildDeptNode(child))
    }
  }
  return {
    key: `dept-${dept.id}`,
    label: dept.name,
    type: 'department',
    data: dept,
    children: children.length > 0 ? children : undefined,
  }
}

// Computed: Selected Node Info
const selectedNode = computed(() => {
  if (!selectedType.value || selectedType.value === 'position') return null
  if (selectedType.value === 'region') return findRegionById(selectedId.value!)
  if (selectedType.value === 'department') return findDepartmentById(selectedId.value!)
  return null
})

const childRegions = computed(() => {
  if (!selectedNode.value || selectedNode.value.type !== 'region') return []
  return (selectedNode.value.data as Region).children || []
})

const childDepts = computed(() => {
  if (!selectedNode.value) return []
  if (selectedNode.value.type === 'region') {
    return departments.value.filter(d => d.regionId === selectedId.value && !d.parentId)
  }
  if (selectedNode.value.type === 'department') {
    return departments.value.filter(d => d.parentId === selectedId.value)
  }
  return []
})

const assignedPositions = computed(() => positions.value)

const allDeptsForSelector = computed(() => {
  return flattenDepts(departments.value)
})

const moveTargetDepts = computed(() => {
  if (!batchMoveTarget.regionId) return []
  return departments.value.filter(d => d.regionId === batchMoveTarget.regionId)
})

// Region Members
const regionMemberCount = computed(() => {
  if (!selectedNode.value || selectedNode.value.type !== 'region') return 0
  return allUsers.value.filter(u => {
    const org = getUserOrg(u.id)
    return org && org.regionId === selectedId.value
  }).length
})

const regionMembers = computed(() => {
  if (!selectedNode.value || selectedNode.value.type !== 'region') return []
  return allUsers.value.filter(u => {
    const org = getUserOrg(u.id)
    return org && org.regionId === selectedId.value
  })
})

const regionDeptCount = computed(() => {
  if (!selectedNode.value || selectedNode.value.type !== 'region') return 0
  const regionId = selectedId.value
  function countDepts(list: Department[]): number {
    let count = 0
    for (const d of list) {
      if (d.regionId === regionId) count++
      if (d.children) count += countDepts(d.children)
    }
    return count
  }
  return countDepts(departments.value)
})

// Dept Members
const deptMemberCount = computed(() => {
  if (!selectedNode.value || selectedNode.value.type !== 'department') return 0
  return allUsers.value.filter(u => {
    const org = getUserOrg(u.id)
    return org && org.departmentId === selectedId.value
  }).length
})

const deptMembers = computed(() => {
  if (!selectedNode.value || selectedNode.value.type !== 'department') return []
  return allUsers.value.filter(u => {
    const org = getUserOrg(u.id)
    return org && org.departmentId === selectedId.value
  })
})

// Batch Selection
const isAllPositionsSelected = computed(() => {
  return positions.value.length > 0 && selectedPositions.value.size === positions.value.length
})

const isPositionsIndeterminate = computed(() => {
  return selectedPositions.value.size > 0 && selectedPositions.value.size < positions.value.length
})

// User Organization Cache
const userOrgMap = ref<Map<number, any>>(new Map())

// Helper functions
function findRegionById(id: number): { type: 'region'; data: Region } | null {
  function search(list: Region[]): Region | null {
    for (const r of list) {
      if (r.id === id) return r
      if (r.children) {
        const found = search(r.children)
        if (found) return found
      }
    }
    return null
  }
  const found = search(regions.value)
  return found ? { type: 'region', data: found } : null
}

function findDepartmentById(id: number): { type: 'department'; data: Department } | null {
  function search(list: Department[]): Department | null {
    for (const d of list) {
      if (d.id === id) return d
      if (d.children) {
        const found = search(d.children)
        if (found) return found
      }
    }
    return null
  }
  const found = search(departments.value)
  return found ? { type: 'department', data: found } : null
}

function getUserOrg(userId: number): any {
  return userOrgMap.value.get(userId)
}

function getRegionName(regionId: number | null): string | null {
  if (!regionId) return null
  const region = findRegionById(regionId)
  return region?.data.name || null
}

function getParentRegionName(region: Region): string | null {
  if (!region.parentId) return null
  const parent = findRegionById(region.parentId)
  return parent?.data.name || null
}

function getParentDeptName(dept: Department): string | null {
  if (!dept.parentId) return null
  const parent = findDepartmentById(dept.parentId)
  return parent?.data.name || null
}

function flattenDepts(list: Department[]): Department[] {
  const result: Department[] = []
  for (const d of list) {
    result.push(d)
    if (d.children) result.push(...flattenDepts(d.children))
  }
  return result
}

// Flatten regions for select
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

const regionTreeSelectData = computed(() => {
  return [{ id: 0, name: '顶级区域', children: regions.value }]
})

const deptTreeSelectData = computed(() => {
  return [{ id: 0, name: '顶级部门', children: departments.value }]
})

// Filter
function filterNode(value: string, data: TreeNode): boolean {
  if (!value) return true
  return data.label.toLowerCase().includes(value.toLowerCase())
}

watch(searchText, (val) => {
  treeRef.value?.filter(val)
})

// Fetch Data
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

    // Fetch all users
    await fetchUsers()
  } catch {
    ElMessage.error('加载组织架构数据失败')
  } finally {
    loading.value = false
  }
}

async function fetchUsers() {
  try {
    const res = await getUserList({ pageNum: 1, pageSize: 1000 }) as any
    if (res?.data?.list) {
      allUsers.value = res.data.list
    }
  } catch {
    console.error('Failed to fetch users')
  }
}

// Selection Handlers
function handleNodeClick(data: TreeNode) {
  selectedType.value = data.type
  selectedId.value = (data.data as any).id
  selectedPosition.value = null
}

function handleNodeCheck(data: TreeNode, checked: any) {
  selectedNodes.value = checked.checkedNodes || []
}

function selectRegion(region: Region) {
  selectedType.value = 'region'
  selectedId.value = region.id
  selectedPosition.value = null
}

function selectDept(dept: Department) {
  selectedType.value = 'department'
  selectedId.value = dept.id
  selectedPosition.value = null
}

function selectPosition(pos: Position) {
  selectedType.value = 'position'
  selectedId.value = pos.id
  selectedPosition.value = pos
  // Load position-department relations
  loadPositionDeptRelations(pos.id)
}

// Position Selection
function togglePositionSelection(id: number) {
  if (selectedPositions.value.has(id)) {
    selectedPositions.value.delete(id)
  } else {
    selectedPositions.value.add(id)
  }
  selectedPositions.value = new Set(selectedPositions.value)
}

function toggleSelectAllPositions(val: boolean) {
  if (val) {
    selectedPositions.value = new Set(positions.value.map(p => p.id))
  } else {
    selectedPositions.value.clear()
    selectedPositions.value = new Set()
  }
}

// Position-Department Relations
function loadPositionDeptRelations(positionId: number) {
  // For now, use mock data - in real app this would call API
  // Check if there's a relation table or if we track this via user_organizations
  const relatedDepts = departments.value.filter(d => {
    // This is a simplified version - in real app you'd query position_department table
    return false
  })
  positionDeptRelations.value = relatedDepts.map(d => ({ id: d.id, name: d.name }))
}

function openDeptSelectorDialog() {
  selectedDeptIds.value = positionDeptRelations.value.map(d => d.id)
  deptSelectorVisible.value = true
}

async function saveDeptRelations() {
  submitting.value = true
  try {
    // In real app, this would call API to update position-department relations
    // For now, update local state
    positionDeptRelations.value = selectedDeptIds.value.map(id => {
      const dept = flattenDepts(departments.value).find(d => d.id === id)
      return { id, name: dept?.name || '' }
    })
    ElMessage.success('保存成功')
    deptSelectorVisible.value = false
  } catch {
    ElMessage.error('保存失败')
  } finally {
    submitting.value = false
  }
}

function removeDeptRelation(deptId: number) {
  positionDeptRelations.value = positionDeptRelations.value.filter(d => d.id !== deptId)
}

// Batch Operations
async function handleBatchAction(command: string) {
  if (command === 'delete') {
    await handleBatchDelete()
  } else if (command === 'move') {
    batchMoveVisible.value = true
  }
}

async function handleBatchDelete() {
  const treeNodes = selectedNodes.value
  const posNodes = Array.from(selectedPositions.value).map(id => {
    return positions.value.find(p => p.id === id)
  }).filter(Boolean)

  const totalCount = treeNodes.length + posNodes.length
  if (totalCount === 0) return

  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${totalCount} 个项目吗？`,
      '批量删除确认',
      { type: 'warning' }
    )

    // Delete tree nodes
    for (const node of treeNodes) {
      if (node.type === 'region') await deleteRegion((node.data as any).id)
      else await deleteDepartment((node.data as any).id)
    }

    // Delete positions
    for (const pos of posNodes) {
      if (pos) await deletePosition(pos.id)
    }

    ElMessage.success('批量删除成功')
    selectedNodes.value = []
    selectedPositions.value = new Set()
    fetchAllData()
  } catch {
    // cancelled or error
  }
}

function onRegionChange() {
  batchMoveTarget.departmentId = null
}

async function confirmBatchMove() {
  // For now, just close the dialog
  // In real app, this would update regionId/departmentId for selected items
  ElMessage.info('批量移动功能开发中')
  batchMoveVisible.value = false
}

// Dialog Handlers
function openCreateDialog(parent: ParentContext | null, type: 'region' | 'department' | 'position') {
  dialogType.value = type
  isEdit.value = false
  editId.value = null

  if (parent) {
    if (parent.type === 'region') {
      form.regionId = (parent.data as Region).id
      form.parentId = null
    } else if (parent.type === 'department') {
      form.parentId = (parent.data as Department).id
      form.regionId = (parent.data as Department).regionId
    }
  } else {
    resetForm()
  }

  dialogVisible.value = true
}

function openEditDialog(node: { type: string; data: any }) {
  dialogType.value = node.type as 'region' | 'department'
  isEdit.value = true
  editId.value = node.data.id
  form.name = node.data.name
  form.code = node.data.code || ''
  form.parentId = node.data.parentId || null
  form.regionId = node.data.regionId || null
  form.type = node.data.type || ''
  dialogVisible.value = true
}

function openEditPositionDialog() {
  if (!selectedPosition.value) return
  dialogType.value = 'position'
  isEdit.value = true
  editId.value = selectedPosition.value.id
  form.name = selectedPosition.value.name
  form.code = selectedPosition.value.code || ''
  form.level = selectedPosition.value.level || 1
  form.description = selectedPosition.value.description || ''
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
      const payload: any = {
        name: form.name,
        parentId: form.parentId || null,
        regionId: form.regionId || null,
        type: form.type || null,
        code: form.code || null,
      }
      if (isEdit.value) {
        payload.id = editId.value
        await updateDepartment(editId.value!, payload)
      } else {
        await createDepartment(payload)
      }
    } else {
      const payload: any = {
        name: form.name,
        code: form.code || null,
        level: form.level,
        description: form.description || null,
      }
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

async function handleDelete(node: { type: string; data: any }) {
  try {
    await ElMessageBox.confirm(`确定要删除"${node.data.name}"吗？`, '删除确认', { type: 'warning' })
    if (node.type === 'region') await deleteRegion(node.data.id)
    else await deleteDepartment(node.data.id)
    ElMessage.success('删除成功')

    if (selectedId.value === node.data.id) {
      selectedType.value = null
      selectedId.value = null
    }

    fetchAllData()
  } catch {
    // cancelled or error
  }
}

async function handleDeletePosition() {
  if (!selectedPosition.value) return
  try {
    await ElMessageBox.confirm(`确定要删除岗位"${selectedPosition.value.name}"吗？`, '删除确认', { type: 'warning' })
    await deletePosition(selectedPosition.value.id)
    ElMessage.success('删除成功')
    selectedType.value = null
    selectedId.value = null
    selectedPosition.value = null
    fetchAllData()
  } catch {
    // cancelled or error
  }
}

// Init
onMounted(() => {
  fetchAllData()
})
</script>

<style scoped>
.org-page {
  padding: 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.page-header {
  margin-bottom: 16px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.org-container {
  display: flex;
  gap: 20px;
  flex: 1;
  overflow: hidden;
}

.left-panel {
  width: 320px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.right-panel {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  overflow-y: auto;
}

.panel-header {
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.panel-title {
  font-weight: 600;
  font-size: 15px;
}

.panel-actions {
  display: flex;
  gap: 4px;
}

.search-box {
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
}

.tree-wrapper {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.org-tree {
  padding: 0 8px;
}

.tree-node-content {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 0;
  flex: 1;
}

.node-icon {
  font-size: 16px;
}

.node-icon.region {
  color: #409eff;
}

.node-icon.department {
  color: #67c23a;
}

.node-label {
  flex: 1;
  font-size: 14px;
}

.node-tag {
  font-size: 12px;
  color: #909399;
  background: #f4f4f5;
  padding: 2px 6px;
  border-radius: 4px;
}

.position-section {
  border-top: 1px solid #f0f0f0;
  margin-top: 8px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 12px 16px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
}

.section-header:hover {
  background: #f5f7fa;
}

.section-header .el-icon {
  transition: transform 0.2s;
}

.section-header .el-icon.rotated {
  transform: rotate(90deg);
}

.section-count {
  margin-left: auto;
  background: #e6e8eb;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
  color: #666;
}

.position-list {
  padding: 0 8px 8px;
}

.position-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
}

.position-item:hover {
  background: #f5f7fa;
}

.position-item.active {
  background: #ecf5ff;
  color: #409eff;
}

.position-icon {
  color: #e6a23c;
}

.position-name {
  flex: 1;
}

.position-level {
  font-size: 12px;
  color: #909399;
}

.add-position-btn {
  width: 100%;
  margin-top: 8px;
  border-style: dashed;
}

.empty-state {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #909399;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
}

.empty-state p {
  font-size: 14px;
}

.detail-content {
  padding: 20px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.detail-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 600;
}

.title-icon {
  font-size: 24px;
}

.title-icon.region {
  color: #409eff;
}

.title-icon.department {
  color: #67c23a;
}

.title-icon.position {
  color: #e6a23c;
}

.detail-actions {
  display: flex;
  gap: 8px;
}

.detail-tabs {
  margin-top: 0;
}

.info-card {
  margin-bottom: 16px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-item.full-width {
  grid-column: 1 / -1;
  margin-top: 8px;
}

.info-label {
  font-size: 13px;
  color: #909399;
}

.info-value {
  font-size: 14px;
  color: #333;
}

.child-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.child-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f9fafb;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
}

.child-item:hover {
  background: #f0f1f2;
}

.child-code {
  color: #909399;
  font-size: 12px;
}

.dept-table {
  margin-top: 8px;
}

.position-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.position-tag {
  display: flex;
  align-items: center;
  gap: 4px;
}

.pos-level {
  font-size: 11px;
  opacity: 0.7;
}

.dept-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.member-table {
  margin-top: 8px;
}

.empty-tip {
  text-align: center;
  color: #909399;
  padding: 20px 0;
  font-size: 14px;
}

.card-header-with-action {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.dept-selector {
  max-height: 400px;
  overflow-y: auto;
}

.selector-tip {
  margin-bottom: 12px;
  color: #666;
  font-size: 14px;
}

.dept-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: 4px;
}

.dept-option:hover {
  background: #f5f7fa;
}

.dept-type {
  font-size: 12px;
  color: #909399;
}
</style>
