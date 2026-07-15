<template>
  <el-dialog
    v-model="internalVisible"
    title="公式编辑器"
    width="640px"
    @close="handleClose"
  >
    <div class="formula-editor">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 12px;"
      >
        使用字段变量名引用其他字段，支持 +、-、*、/ 运算和基本函数。
      </el-alert>

      <el-input
        v-model="formula"
        type="textarea"
        :rows="4"
        placeholder="例如：总价 = 单价 * 数量"
      />

      <div class="formula-fields">
        <div class="formula-fields__title">可用字段变量：</div>
        <div class="formula-fields__list">
          <el-tag
            v-for="field in availableFields"
            :key="field.id"
            class="formula-field-tag"
            @click="insertField(field)"
          >
            {{ field.name }}
          </el-tag>
        </div>
      </div>

      <div class="formula-functions">
        <div class="formula-functions__title">常用函数：</div>
        <div class="formula-functions__list">
          <el-tag
            v-for="fn in functions"
            :key="fn"
            type="info"
            class="formula-field-tag"
            @click="insertFunction(fn)"
          >
            {{ fn }}
          </el-tag>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleConfirm" :disabled="!formula.trim()">确认</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import type { BitableField } from '@/types/bitable'

const props = defineProps<{
  visible: boolean
  formulaText: string
  fields: BitableField[]
}>()

const emit = defineEmits<{
  confirm: [formula: string]
  close: []
}>()

const internalVisible = ref(props.visible)
const formula = ref(props.formulaText)

const functions = ['SUM()', 'AVG()', 'COUNT()', 'MAX()', 'MIN()', 'IF()', 'ROUND()']

const availableFields = ref<BitableField[]>([])

watch(() => props.visible, (v) => {
  internalVisible.value = v
  if (v) {
    formula.value = props.formulaText
    availableFields.value = props.fields.filter(
      (f) => f.fieldType === 'number' || f.fieldType === 'text'
    )
  }
})

function insertField(field: BitableField) {
  const varName = field.name.replace(/\s/g, '_')
  formula.value = (formula.value || '') + varName
}

function insertFunction(fn: string) {
  const base = fn.replace('()', '')
  formula.value = (formula.value || '') + base + '()'
}

function handleConfirm() {
  emit('confirm', formula.value)
  internalVisible.value = false
}

function handleClose() {
  emit('close')
  internalVisible.value = false
}
</script>

<style scoped lang="scss">
.formula-editor {
  width: 100%;
}

.formula-fields,
.formula-functions {
  margin-top: 16px;

  &__title {
    font-size: var(--font-size-sm);
    color: var(--color-text-secondary);
    margin-bottom: 8px;
  }

  &__list {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }
}

.formula-field-tag {
  cursor: pointer;
}
</style>