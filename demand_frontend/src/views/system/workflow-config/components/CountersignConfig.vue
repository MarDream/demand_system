<template>
  <div class="countersign-config">
    <el-form-item label="启用会签">
      <el-switch :model-value="enabled" @update:model-value="emit('update:enabled', $event)" />
    </el-form-item>
    <template v-if="enabled">
      <el-form-item label="会签策略">
        <el-radio-group :model-value="strategy" @update:model-value="emit('update:strategy', $event)">
          <el-radio value="ALL">全部通过</el-radio>
          <el-radio value="ANY">任一通过</el-radio>
          <el-radio value="MAJORITY">多数通过</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="会签人选择">
        <el-radio-group :model-value="mode" @update:model-value="emit('update:mode', $event)">
          <el-radio value="FIXED">固定会签人</el-radio>
          <el-radio value="DYNAMIC">动态（按处理人配置）</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="mode === 'FIXED'" label="选择会签人">
        <el-select
          :model-value="approvers"
          multiple
          filterable
          :filter-method="handleFilter"
          placeholder="可搜索选择其他会签人"
          style="width: 100%"
          @update:model-value="onApproversChange"
        >
          <el-option
            v-for="option in candidateOptions"
            :key="option.key"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <div class="countersign-hint">
          默认包含「需求提出人」，可继续搜索选择其他用户。
        </div>
      </el-form-item>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

/**
 * 会签人配置组件
 *
 * 约定：
 * - 特殊占位 ID CREATOR_PLACEHOLDER_ID = -1 表示"需求提出人"。
 *   在工作流执行时，后端会将其动态替换为当前需求的 creatorId。
 * - 首次打开会签配置时，approvers 默认包含「需求提出人」占位。
 * - 用户可继续搜索选择其他具体用户加入会签人列表。
 */

const CREATOR_PLACEHOLDER_ID = -1
const CREATOR_PLACEHOLDER_LABEL = '需求提出人'

const props = defineProps<{
  enabled: boolean
  strategy: 'ALL' | 'ANY' | 'MAJORITY'
  mode: 'FIXED' | 'DYNAMIC'
  approvers: number[]
  users: Array<{ id: number; realName?: string; username?: string }>
}>()

const emit = defineEmits<{
  'update:enabled': [value: boolean]
  'update:strategy': [value: 'ALL' | 'ANY' | 'MAJORITY']
  'update:mode': [value: 'FIXED' | 'DYNAMIC']
  'update:approvers': [value: number[]]
}>()

const searchKeyword = ref('')

const isCreatorPlaceholder = (id: number) => id === CREATOR_PLACEHOLDER_ID

const candidateOptions = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  const realUsers = props.users ?? []
  const filteredUsers = keyword
    ? realUsers.filter(user => {
        const name = (user.realName || user.username || '').toLowerCase()
        return name.includes(keyword)
      })
    : realUsers

  const creatorOption = {
    key: `creator-${CREATOR_PLACEHOLDER_ID}`,
    value: CREATOR_PLACEHOLDER_ID,
    label: CREATOR_PLACEHOLDER_LABEL,
  }

  return [
    creatorOption,
    ...filteredUsers
      .filter(user => !isCreatorPlaceholder(user.id))
      .map(user => ({
        key: `user-${user.id}`,
        value: user.id,
        label: user.realName || user.username || `用户#${user.id}`,
      })),
  ]
})

const handleFilter = (keyword: string) => {
  searchKeyword.value = keyword
}

const onApproversChange = (next: number[]) => {
  // 始终保留「需求提出人」默认项，同时允许追加具体用户
  const valid = [CREATOR_PLACEHOLDER_ID]
  next
    .filter((id) => !isCreatorPlaceholder(id) && (props.users ?? []).some(user => user.id === id))
    .forEach((id) => {
      if (!valid.includes(id)) {
        valid.push(id)
      }
    })
  emit('update:approvers', valid)
}
</script>

<style scoped>
.countersign-hint {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  line-height: 1.4;
}
</style>
