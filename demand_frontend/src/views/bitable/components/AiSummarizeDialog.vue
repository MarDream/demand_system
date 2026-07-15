<template>
  <el-dialog
    :model-value="visible"
    title="AI 自动摘要"
    width="500px"
    @close="handleClose"
  >
    <div class="ai-summarize-dialog">
      <el-form label-width="100px">
        <el-form-item label="源文本字段">
          <el-select
            v-model="selectedSourceFieldId"
            placeholder="选择要摘要的文本字段"
            style="width: 100%"
          >
            <el-option
              v-for="field in textFields"
              :key="field.id"
              :label="field.name"
              :value="field.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="目标字段名">
          <el-input
            v-model="targetFieldName"
            placeholder="输入新的摘要字段名称"
          />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button
        type="primary"
        :loading="summarizing"
        :disabled="!selectedSourceFieldId || !targetFieldName"
        @click="handleSummarize"
      >
        生成摘要
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { resolveErrorMessage } from '@/utils/error'
import { summarize } from '@/api/modules/bitableAi'
import type { BitableField } from '@/types/bitable'

const props = defineProps<{
  visible: boolean
  tableId: number
  fields: BitableField[]
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'completed'): void
}>()

const selectedSourceFieldId = ref<number | undefined>()
const targetFieldName = ref('')
const summarizing = ref(false)

const textFields = computed(() =>
  props.fields.filter(f => f.fieldType === 'text' || f.fieldType === 'ai_text')
)

async function handleSummarize() {
  if (!selectedSourceFieldId.value || !targetFieldName.value.trim()) {
    ElMessage.warning('请选择源字段并输入目标字段名')
    return
  }
  summarizing.value = true
  try {
    await summarize(props.tableId, selectedSourceFieldId.value, targetFieldName.value)
    ElMessage.success('AI 摘要生成完成')
    emit('completed')
    handleClose()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, 'AI 摘要生成失败，请重试'))
  } finally {
    summarizing.value = false
  }
}

function handleClose() {
  selectedSourceFieldId.value = undefined
  targetFieldName.value = ''
  emit('close')
}
</script>

<style scoped>
.ai-summarize-dialog {
  min-height: 120px;
}
</style>
