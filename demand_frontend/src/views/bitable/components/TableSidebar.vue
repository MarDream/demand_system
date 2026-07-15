<template>
  <div class="table-sidebar">
    <div class="table-sidebar__header">
      <span class="table-sidebar__title">数据表</span>
      <el-button link class="table-sidebar__add" @click="showCreateInput = !showCreateInput">
        <el-icon><Plus /></el-icon>
      </el-button>
    </div>

    <!-- 新建表输入框 -->
    <div v-if="showCreateInput" class="table-sidebar__create">
      <el-input
        ref="newTableInputRef"
        v-model="newTableName"
        placeholder="输入表名"
        size="small"
        @keyup.enter="handleCreate"
        @keyup.escape="cancelCreate"
      >
        <template #append>
          <el-button size="small" type="primary" @click="handleCreate">
            <el-icon><Check /></el-icon>
          </el-button>
        </template>
      </el-input>
    </div>

    <!-- 表列表 -->
    <el-menu
      :default-active="String(activeTableId)"
      class="table-sidebar__menu"
      @select="handleSelect"
    >
      <el-menu-item
        v-for="table in tables"
        :key="table.id"
        :index="String(table.id)"
        class="table-sidebar__item"
      >
        <el-icon class="table-sidebar__item-icon"><Document /></el-icon>
        <span class="table-sidebar__item-name">{{ table.name }}</span>
        <el-button
          link
          class="table-sidebar__item-delete"
          @click.stop="handleDelete(table)"
        >
          <el-icon><Delete /></el-icon>
        </el-button>
      </el-menu-item>
    </el-menu>

    <el-empty v-if="!tables.length" description="暂无数据表" :image-size="60" />
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Check, Document } from '@element-plus/icons-vue'
import type { BitableTable } from '@/types/bitable'

const props = defineProps<{
  tables: BitableTable[]
  activeTableId: number | null
}>()

const emit = defineEmits<{
  select: [tableId: number]
  create: [name: string]
  delete: [tableId: number]
}>()

const showCreateInput = ref(false)
const newTableName = ref('')
const newTableInputRef = ref<HTMLInputElement | null>(null)

watch(showCreateInput, async (val) => {
  if (val) {
    await nextTick()
    newTableInputRef.value?.focus()
  }
})

function handleSelect(index: string) {
  const id = Number(index)
  if (!Number.isNaN(id)) {
    emit('select', id)
  }
}

function handleCreate() {
  const name = newTableName.value.trim()
  if (!name) {
    ElMessage.warning('请输入表名')
    return
  }
  emit('create', name)
  newTableName.value = ''
  showCreateInput.value = false
}

function cancelCreate() {
  newTableName.value = ''
  showCreateInput.value = false
}

function handleDelete(table: BitableTable) {
  ElMessageBox.confirm(`确定删除数据表「${table.name}」吗？此操作将删除表内所有数据且不可恢复。`, '删除确认', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    emit('delete', table.id)
  }).catch(() => {})
}
</script>

<style scoped lang="scss">
.table-sidebar {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--color-surface);

  .table-sidebar__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 16px 8px;
    font-weight: var(--font-weight-semibold);
    font-size: var(--font-size-sm);
    color: var(--color-text-primary);
  }

  .table-sidebar__add {
    padding: 2px;
  }

  .table-sidebar__create {
    padding: 0 12px 8px;
  }

  .table-sidebar__menu {
    border-right: none;
    flex: 1;
    overflow-y: auto;
  }

  .table-sidebar__item {
    display: flex;
    align-items: center;
    height: 36px;
    line-height: 36px;
    padding-right: 8px;

    .table-sidebar__item-icon {
      margin-right: 6px;
      font-size: 14px;
    }

    .table-sidebar__item-name {
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .table-sidebar__item-delete {
      opacity: 0;
      transition: opacity 0.2s ease;
      padding: 2px;
    }

    &:hover .table-sidebar__item-delete {
      opacity: 1;
    }
  }
}
</style>