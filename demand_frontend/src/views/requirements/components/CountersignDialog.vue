<template>
  <el-dialog
    v-model="dialogVisible"
    title="会签审批"
    width="500px"
    :close-on-click-modal="false"
  >
    <div v-if="records.length > 0" class="countersign-records">
      <div class="countersign-records-title">会签记录</div>
      <el-table :data="records" size="small" border>
        <el-table-column prop="approverName" label="会签人" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag 
              :type="row.status === 'approved' ? 'success' : row.status === 'rejected' ? 'danger' : 'info'" 
              size="small"
            >
              {{ row.status === 'approved' ? '已通过' : row.status === 'rejected' ? '已驳回' : '待审批' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="rating" label="评分" width="60">
          <template #default="{ row }">
            {{ row.rating ? row.rating + '星' : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="comment" label="意见" min-width="120" show-overflow-tooltip />
      </el-table>
    </div>
    <div v-if="canCountersign" class="countersign-submit">
      <p class="countersign-tip">请对本次会签进行审批操作</p>
      <div class="countersign-rate">
        <span class="countersign-label">评分</span>
        <el-rate v-model="localRating" :max="5" allow-half />
      </div>
      <el-input
        v-model="localComment"
        type="textarea"
        :rows="3"
        placeholder="请输入审批意见（选填）"
        maxlength="500"
        show-word-limit
      />
    </div>
    <div v-else-if="!loading" class="countersign-empty">
      <el-empty description="您不是当前节点的会签人，无需操作" />
    </div>
    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
      <el-button v-if="canCountersign" type="success" @click="handleApprove">通过</el-button>
      <el-button v-if="canCountersign" type="danger" @click="handleReject">驳回</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { CountersignRecord } from '@/api/modules/workflow'

const props = defineProps<{
  visible: boolean
  records: CountersignRecord[]
  canCountersign: boolean
  loading: boolean
  rating: number
  comment: string
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'update:rating': [value: number]
  'update:comment': [value: string]
  approve: []
  reject: []
  close: []
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

function handleApprove() {
  emit('approve')
}

function handleReject() {
  emit('reject')
}

function handleClose() {
  emit('close')
}
</script>

<style scoped>
.countersign-records {
  margin-bottom: 20px;
}

.countersign-records-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-primary);
  margin-bottom: 12px;
}

.countersign-submit {
  padding-top: 16px;
  border-top: 1px solid var(--color-border);
}

.countersign-tip {
  color: var(--color-text-secondary);
  font-size: 14px;
  margin-bottom: 16px;
}

.countersign-rate {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.countersign-label {
  color: var(--color-text-secondary);
  font-size: 14px;
}

.countersign-empty {
  padding: 20px 0;
}
</style>
