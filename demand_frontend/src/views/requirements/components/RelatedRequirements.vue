<template>
  <div class="extra-section">
    <!-- 关联需求 -->
    <div class="extra-row">
      <div class="extra-row-label">关联需求</div>
      <div class="extra-row-content">
        <el-button size="small" @click="$emit('add')">
          <el-icon><Plus /></el-icon>
          添加
        </el-button>
        <span v-if="relations.length > 0" class="relation-count">已关联 {{ relations.length }} 个</span>
      </div>
    </div>
    <el-table v-if="relations.length > 0" :data="relations" size="small" class="relation-table">
      <el-table-column prop="title" label="标题" min-width="200" />
      <el-table-column v-if="isEditMode" prop="type" label="类型" width="100">
        <template #default="{ row }">
          <el-tag size="small">{{ row.type }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="relationType" label="关联类型" width="120">
        <template #default="{ row }">
          <el-select v-model="row.relationType" size="small" style="width: 100%">
            <el-option label="阻塞" value="blocks" />
            <el-option label="被阻塞" value="blocked_by" />
            <el-option label="包含" value="contains" />
            <el-option label="被包含" value="contained_by" />
            <el-option label="相关" value="relates_to" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="60" align="center">
        <template #default="{ row }">
          <el-button type="danger" link size="small" @click="$emit('remove', row)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-else-if="showEmpty" description="暂无关联需求" :image-size="40" />
  </div>
</template>

<script setup lang="ts">
import { Plus, Delete } from '@element-plus/icons-vue'

export interface EditableRelationItem {
  id: number
  title: string
  type?: string | null
  status?: string | null
  priority?: string | null
  relationType: string
  relationId?: number
}

defineProps<{
  relations: EditableRelationItem[]
  isEditMode?: boolean
  showEmpty?: boolean
}>()

defineEmits<{
  add: []
  remove: [row: EditableRelationItem]
}>()
</script>

<style scoped>
.extra-section {
  margin-top: 8px;
}

.extra-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
}

.extra-row-label {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  white-space: nowrap;
}

.extra-row-content {
  display: flex;
  align-items: center;
  gap: 8px;
}

.relation-count {
  font-size: 13px;
  color: #909399;
}

.relation-table {
  margin-bottom: 4px;
}

.relation-table :deep(.el-table__header-wrapper th) {
  background: #fafafa;
}
</style>
