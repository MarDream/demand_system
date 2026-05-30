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
          placeholder="请选择会签人"
          @update:model-value="emit('update:approvers', $event)"
        >
          <el-option v-for="user in users" :key="user.id" :label="user.realName || user.username" :value="user.id" />
        </el-select>
      </el-form-item>
    </template>
  </div>
</template>

<script setup lang="ts">
defineProps<{
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
</script>
