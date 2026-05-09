<template>
  <div class="region-management">
    <div class="management-container">
      <!-- Left Panel: Tree Structure -->
      <div class="left-panel">
        <div class="panel-header">
          <span class="panel-title">区域列表</span>
          <el-button type="primary" size="small" @click="openCreateDialog(null)">
            <el-icon><Plus /></el-icon>
            新增区域
          </el-button>
        </div>

        <div class="search-box">
          <el-input
            v-model="searchText"
            placeholder="搜索区域..."
            clearable
            size="small"
            prefix-icon="Search"
          />
        </div>

        <div class="tree-wrapper" v-loading="loading">
          <el-tree
            ref="treeRef"
            :data="regions"
            :props="treeProps"
            node-key="id"
            :expand-on-click-node="false"
            :filter-node-method="filterNode"
            highlight-current
            draggable
            @node-click="handleNodeClick"
            @node-drop="handleNodeDrop"
            class="region-tree"
          >
            <template #default="{ node, data }">
              <div class="tree-node-content">
                <el-icon class="node-icon"><OfficeBuilding /></el-icon>
                <span class="node-label">{{ node.label }}</span>
                <span v-if="data.code" class="node-code">{{ data.code }}</span>
              </div>
            </template>
          </el-tree>
        </div>
      </div>

      <!-- Right Panel: Detail View -->
      <div class="right-panel">
        <!-- Empty State -->
        <div v-if="!selectedRegion" class="empty-state">
          <el-icon class="empty-icon"><OfficeBuilding /></el-icon>
          <p>请从左侧选择要查看的区域</p>
        </div>

        <!-- Region Detail -->
        <div v-else class="detail-content">
          <div class="detail-header">
            <div class="detail-title">
              <el-icon class="title-icon"><OfficeBuilding /></el-icon>
              <span>{{ selectedRegion.name }}</span>
            </div>
            <div class="detail-actions">
              <el-button @click="openCreateDialog(selectedRegion)">添加子区域</el-button>
              <el-button @click="openEditDialog(selectedRegion)">编辑</el-button>
              <el-button type="danger" @click="handleDelete(selectedRegion)">删除</el-button>
            </div>
          </div>

          <el-card class="info-card">
            <template #header>
              <span>基本信息</span>
            </template>
            <div class="info-grid">
              <div class="info-item">
                <span class="info-label">区域名称</span>
                <span class="info-value">{{ selectedRegion.name }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">区域编码</span>
                <span class="info-value">{{ selectedRegion.code || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">上级区域</span>
                <span class="info-value">{{ getParentRegionName(selectedRegion) || '无' }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">子区域数</span>
                <span class="info-value">{{ selectedRegion.children?.length || 0 }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">排序</span>
                <span class="info-value">{{ selectedRegion.sortOrder || '-' }}</span>
              </div>
            </div>
          </el-card>

          <el-card class="info-card" v-if="selectedRegion.children && selectedRegion.children.length > 0">
            <template #header>
              <span>子区域</span>
            </template>
            <div class="child-list">
              <div
                v-for="child in selectedRegion.children"
                :key="child.id"
                class="child-item"
                @click="selectRegion(child)"
              >
                <el-icon><OfficeBuilding /></el-icon>
                <span>{{ child.name }}</span>
                <span v-if="child.code" class="child-code">({{ child.code }})</span>
              </div>
            </div>
          </el-card>
        </div>
      </div>
    </div>

    <!-- Create/Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="区域名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入区域名称" />
        </el-form-item>
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
import { ref, reactive, computed, watch, onMounted } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { OfficeBuilding, Plus } from '@element-plus/icons-vue'
import {
  getRegionTree,
  createRegion,
  updateRegion,
  deleteRegion,
} from '@/api/modules/organization'
import type { Region } from '@/types/user'

// State
const loading = ref(false)
const searchText = ref('')
const treeRef = ref<any>()
const regions = ref<Region[]>([])
const selectedRegion = ref<Region | null>(null)

// Dialog
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  code: '',
  parentId: null as number | null,
  sortOrder: 0,
})

const dialogTitle = computed(() => (isEdit.value ? '编辑区域' : '新增区域'))

const formRules: FormRules = {
  name: [{ required: true, message: '请输入区域名称', trigger: 'blur' }],
}

const treeProps = {
  label: 'name',
  children: 'children',
}

const regionTreeSelectData = computed(() => {
  return [{ id: 0, name: '顶级区域', children: regions.value }]
})

// Filter
function filterNode(value: string, data: Region): boolean {
  if (!value) return true
  return data.name.toLowerCase().includes(value.toLowerCase())
}

watch(searchText, (val) => {
  treeRef.value?.filter(val)
})

// Fetch Data
async function fetchRegions() {
  loading.value = true
  try {
    const res = await getRegionTree() as any
    regions.value = res || []
  } catch {
    ElMessage.error('加载区域数据失败')
  } finally {
    loading.value = false
  }
}

// Selection
function handleNodeClick(data: Region) {
  selectedRegion.value = data
}

function selectRegion(region: Region) {
  selectedRegion.value = region
}

// Drag & Drop
function handleNodeDrop(draggingNode: any, dropNode: any, dropType: string) {
  ElMessage.info('拖拽排序功能开发中')
  // TODO: 实现拖拽排序API调用
}

// Helper
function getParentRegionName(region: Region): string | null {
  if (!region.parentId) return null

  function findRegion(list: Region[], id: number): Region | null {
    for (const r of list) {
      if (r.id === id) return r
      if (r.children) {
        const found = findRegion(r.children, id)
        if (found) return found
      }
    }
    return null
  }

  const parent = findRegion(regions.value, region.parentId)
  return parent?.name || null
}

// Dialog Handlers
function openCreateDialog(parent: Region | null) {
  isEdit.value = false
  editId.value = null
  resetForm()
  if (parent) {
    form.parentId = parent.id
  }
  dialogVisible.value = true
}

function openEditDialog(region: Region) {
  isEdit.value = true
  editId.value = region.id
  form.name = region.name
  form.code = region.code || ''
  form.parentId = region.parentId || null
  form.sortOrder = region.sortOrder || 0
  dialogVisible.value = true
}

function resetForm() {
  form.name = ''
  form.code = ''
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
      parentId: form.parentId || null,
      sortOrder: form.sortOrder,
    }

    if (isEdit.value) {
      payload.id = editId.value
      await updateRegion(editId.value!, payload)
    } else {
      await createRegion(payload)
    }

    ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
    dialogVisible.value = false
    fetchRegions()
  } catch {
    // error handled by interceptor
  } finally {
    submitting.value = false
  }
}

async function handleDelete(region: Region) {
  try {
    await ElMessageBox.confirm(`确定要删除区域"${region.name}"吗？`, '删除确认', {
      type: 'warning',
    })
    await deleteRegion(region.id)
    ElMessage.success('删除成功')

    if (selectedRegion.value?.id === region.id) {
      selectedRegion.value = null
    }

    fetchRegions()
  } catch {
    // cancelled or error
  }
}

// Init
onMounted(() => {
  fetchRegions()
})
</script>

<style scoped>
.region-management {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.management-container {
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

.search-box {
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
}

.tree-wrapper {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.region-tree {
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
  color: #409eff;
}

.node-label {
  flex: 1;
  font-size: 14px;
}

.node-code {
  font-size: 12px;
  color: #909399;
  background: #f4f4f5;
  padding: 2px 6px;
  border-radius: 4px;
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
  color: #409eff;
}

.detail-actions {
  display: flex;
  gap: 8px;
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
</style>
