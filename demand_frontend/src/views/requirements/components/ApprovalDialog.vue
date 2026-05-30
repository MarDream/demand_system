<template>
  <el-dialog
    v-model="dialogVisible"
    title="审核操作"
    width="480px"
    :close-on-click-modal="false"
    @closed="handleReset"
  >
    <p class="approval-dialog-tip">提交到下一节点前，请补充审核信息。</p>
    <div class="approval-dialog-rate">
      <span class="approval-dialog-label">评分</span>
      <el-rate v-model="localRating" :max="5" />
    </div>
    <el-input
      v-model="localComment"
      type="textarea"
      :rows="4"
      placeholder="请输入审核意见（选填）"
      maxlength="1000"
      show-word-limit
    />
    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleConfirm">确认提交</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'

const props = defineProps<{
  visible: boolean
  rating: number
  comment: string
  loading: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'update:rating': [value: number]
  'update:comment': [value: string]
  confirm: []
  cancel: []
  reset: []
}>()

const localRating = computed({
  get: () => props.rating,
  set: (val) => emit('update:rating', val),
})

const localComment = computed({
  get: () => props.comment,
  set: (val) => emit('update:comment', val),
})

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

function handleConfirm() {
  emit('confirm')
}

function handleCancel() {
  emit('cancel')
}

function handleReset() {
  emit('reset')
}
</script>

<style scoped>
.approval-dialog-tip {
  margin: 0 0 16px;
  color: #606266;
  font-size: 13px;
}

.approval-dialog-rate {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.approval-dialog-label {
  color: #606266;
  font-size: 14px;
}
</style>
