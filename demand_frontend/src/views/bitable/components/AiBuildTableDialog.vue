<template>
  <el-dialog
    :model-value="visible"
    title="AI 建表"
    width="600px"
    @close="handleClose"
  >
    <div class="ai-build-dialog">
      <el-input
        v-model="description"
        type="textarea"
        placeholder="请描述你想创建的数据表，例如：一个客户信息管理表，包含客户名称、联系方式、行业、签约状态等"
        :rows="4"
        maxlength="1000"
        show-word-limit
      />

      <div v-if="loading" class="ai-loading">
        <el-skeleton :rows="6" animated />
      </div>

      <div v-else-if="result" class="ai-result">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="表名">{{ result.tableName }}</el-descriptions-item>
          <el-descriptions-item label="说明">{{ result.tableDescription }}</el-descriptions-item>
        </el-descriptions>

        <el-table :data="result.fields" stripe style="margin-top: 12px">
          <el-table-column prop="name" label="字段名" width="160" />
          <el-table-column prop="fieldType" label="类型" width="120" />
          <el-table-column prop="description" label="说明" />
        </el-table>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button v-if="!result" type="primary" :loading="loading" @click="handlePreview">
        AI 生成
      </el-button>
      <el-button v-else type="success" :loading="confirming" @click="handleConfirm">
        确认创建
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { resolveErrorMessage } from '@/utils/error'
import { previewBuildTable, confirmBuildTable, type AiBuildTableResult } from '@/api/modules/bitableAi'

const props = defineProps<{
  visible: boolean
  baseId: number
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'created', tableId: number): void
}>()

const description = ref('')
const loading = ref(false)
const confirming = ref(false)
const result = ref<AiBuildTableResult | null>(null)

async function handlePreview() {
  if (!description.value.trim()) {
    ElMessage.warning('请输入表描述')
    return
  }
  loading.value = true
  result.value = null
  try {
    result.value = await previewBuildTable(description.value)
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, 'AI 生成失败，请重试'))
  } finally {
    loading.value = false
  }
}

async function handleConfirm() {
  if (!result.value) return
  confirming.value = true
  try {
    const tableId = await confirmBuildTable(props.baseId, result.value)
    ElMessage.success('数据表创建成功')
    emit('created', tableId)
    handleClose()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '创建失败，请重试'))
  } finally {
    confirming.value = false
  }
}

function handleClose() {
  description.value = ''
  result.value = null
  emit('close')
}
</script>

<style scoped>
.ai-build-dialog {
  min-height: 200px;
}

.ai-loading {
  margin-top: 16px;
}

.ai-result {
  margin-top: 16px;
}
</style>
