<template>
  <el-dialog
    :model-value="visible"
    title="AI 自动分类"
    width="500px"
    @close="handleClose"
  >
    <div class="ai-classify-dialog">
      <el-form label-width="100px">
        <el-form-item label="源文本字段">
          <el-select
            v-model="selectedSourceFieldId"
            placeholder="选择要分类的文本字段"
            style="width: 100%"
            @change="loadPreview"
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
            placeholder="输入新的分类字段名称"
          />
        </el-form-item>

        <el-form-item v-if="previewCount > 0" label="数据预览">
          <span class="preview-text">将分类 {{ previewCount }} 条记录</span>
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button
        type="primary"
        :loading="classifying"
        :disabled="!selectedSourceFieldId || !targetFieldName"
        @click="handleClassify"
      >
        开始分类
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { resolveErrorMessage } from '@/utils/error'
import { classify } from '@/api/modules/bitableAi'
import type { BitableField } from '@/types/bitable'

const props = defineProps<{
  visible: boolean
  tableId: number
  fields: BitableField[]
  recordCount?: number
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'completed'): void
}>()

const selectedSourceFieldId = ref<number | undefined>()
const targetFieldName = ref('')
const classifying = ref(false)
const previewCount = ref(0)

const textFields = computed(() =>
  props.fields.filter(f => f.fieldType === 'text' || f.fieldType === 'ai_text')
)

function loadPreview() {
  previewCount.value = props.recordCount ?? 0
}

async function handleClassify() {
  if (!selectedSourceFieldId.value || !targetFieldName.value.trim()) {
    ElMessage.warning('请选择源字段并输入目标字段名')
    return
  }
  classifying.value = true
  try {
    await classify(props.tableId, selectedSourceFieldId.value, targetFieldName.value)
    ElMessage.success('AI 分类完成')
    emit('completed')
    handleClose()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, 'AI 分类失败，请重试'))
  } finally {
    classifying.value = false
  }
}

function handleClose() {
  selectedSourceFieldId.value = undefined
  targetFieldName.value = ''
  previewCount.value = 0
  emit('close')
}
</script>

<style scoped>
.ai-classify-dialog {
  min-height: 120px;
}

.preview-text {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
