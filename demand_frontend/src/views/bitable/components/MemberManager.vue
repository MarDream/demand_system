<template>
  <el-dialog
    :model-value="visible"
    title="协作成员管理"
    width="600px"
    @close="emit('close')"
  >
    <div class="member-manager">
      <!-- 成员列表 -->
      <div class="member-list">
        <el-table :data="members" style="width: 100%" v-loading="loading">
          <el-table-column prop="userName" label="用户名" width="160">
            <template #default="{ row }">
              <div class="member-name">
                <el-avatar :size="28" :src="row.avatar">{{ (row.userName || '?')[0] }}</el-avatar>
                <span>{{ row.userName || '未知用户' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="角色" width="140">
            <template #default="{ row }">
              <el-select
                v-model="row.role"
                size="small"
                :disabled="row.role === 'owner'"
                @change="handleRoleChange(row)"
              >
                <el-option label="拥有者" value="owner" disabled />
                <el-option label="管理员" value="admin" />
                <el-option label="编辑者" value="editor" />
                <el-option label="评论者" value="commenter" />
                <el-option label="查看者" value="viewer" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button
                link
                type="danger"
                size="small"
                :disabled="row.role === 'owner'"
                @click="handleRemove(row.userId)"
              >
                移除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 添加成员 -->
      <div class="member-add">
        <div class="member-add__title">添加成员</div>
        <div class="member-add__form">
          <el-select
            v-model="newUserId"
            filterable
            placeholder="选择用户"
            style="flex: 1;"
            size="small"
            @focus="loadUsersIfNeeded"
          >
            <el-option
              v-for="user in availableUsers"
              :key="user.id"
              :label="user.realName || user.username"
              :value="user.id"
            />
          </el-select>
          <el-select
            v-model="newRole"
            placeholder="角色"
            style="width: 120px;"
            size="small"
          >
            <el-option label="管理员" value="admin" />
            <el-option label="编辑者" value="editor" />
            <el-option label="评论者" value="commenter" />
            <el-option label="查看者" value="viewer" />
          </el-select>
          <el-button
            type="primary"
            size="small"
            :loading="adding"
            :disabled="!newUserId"
            @click="handleAdd"
          >
            添加
          </el-button>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { resolveErrorMessage } from '@/utils/error'
import {
  listBaseMembers,
  addBaseMember,
  updateBaseMemberRole,
  removeBaseMember,
} from '@/api/modules/bitable'
import { getFilterUsers } from '@/api/modules/user'
import type { BitableBaseMember, MemberRole } from '@/types/bitable'

const props = defineProps<{
  visible: boolean
  baseId: number | null
}>()

const emit = defineEmits<{
  close: []
}>()

const members = ref<BitableBaseMember[]>([])
const allUsers = ref<Array<{ id: number; username: string; realName: string }>>([])
const loading = ref(false)
const adding = ref(false)
const newUserId = ref<number | null>(null)
const newRole = ref<MemberRole>('viewer')
const usersLoaded = ref(false)

// 排除已是成员的用户
const availableUsers = computed(() => {
  const memberIds = new Set(members.value.map((m) => m.userId))
  return allUsers.value.filter((u) => !memberIds.has(u.id))
})

watch(
  () => [props.visible, props.baseId] as const,
  async ([visible, baseId]) => {
    if (visible && baseId != null) {
      await loadMembers(baseId)
    } else {
      members.value = []
    }
  }
)

async function loadMembers(baseId: number) {
  loading.value = true
  try {
    const res = await listBaseMembers(baseId)
    if (Array.isArray(res)) {
      members.value = res
    } else if (res && typeof res === 'object' && 'data' in res) {
      members.value = (res as { data: BitableBaseMember[] }).data ?? []
    } else {
      members.value = []
    }
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '加载成员失败'))
  } finally {
    loading.value = false
  }
}

async function loadUsersIfNeeded() {
  if (usersLoaded.value) return
  try {
    const res = await getFilterUsers()
    if (Array.isArray(res)) {
      allUsers.value = res
    } else if (res && typeof res === 'object' && 'data' in res) {
      allUsers.value = (res as { data: Array<{ id: number; username: string; realName: string }> }).data ?? []
    } else {
      allUsers.value = []
    }
    usersLoaded.value = true
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '加载用户列表失败'))
  }
}

async function handleRoleChange(row: BitableBaseMember) {
  if (props.baseId == null) return
  try {
    await updateBaseMemberRole(props.baseId, row.userId, row.role)
    ElMessage.success('角色已更新')
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '更新角色失败'))
    // 回滚：重新加载
    if (props.baseId != null) await loadMembers(props.baseId)
  }
}

async function handleRemove(userId: number) {
  if (props.baseId == null) return
  try {
    await ElMessageBox.confirm('确定移除该成员吗？', '移除确认', {
      confirmButtonText: '移除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await removeBaseMember(props.baseId, userId)
    members.value = members.value.filter((m) => m.userId !== userId)
    ElMessage.success('已移除成员')
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(resolveErrorMessage(e, '移除失败'))
    }
  }
}

async function handleAdd() {
  if (props.baseId == null || newUserId.value == null) return

  adding.value = true
  try {
    await addBaseMember(props.baseId, {
      userId: newUserId.value,
      role: newRole.value,
    })
    ElMessage.success('添加成功')
    newUserId.value = null
    newRole.value = 'viewer'
    await loadMembers(props.baseId)
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '添加失败'))
  } finally {
    adding.value = false
  }
}
</script>

<style scoped lang="scss">
.member-manager {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.member-list {
  :deep(.el-table) {
    .el-table__cell {
      padding: 8px 0;
    }
  }
}

.member-name {
  display: flex;
  align-items: center;
  gap: 8px;
}

.member-add {
  padding-top: 12px;
  border-top: 1px solid var(--color-border);

  .member-add__title {
    font-size: var(--font-size-sm);
    font-weight: var(--font-weight-semibold);
    color: var(--color-text-primary);
    margin-bottom: 8px;
  }

  .member-add__form {
    display: flex;
    gap: 8px;
    align-items: center;
  }
}
</style>