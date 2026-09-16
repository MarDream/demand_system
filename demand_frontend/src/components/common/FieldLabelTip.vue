<template>
  <span class="field-label-with-tip">
    <slot />
    <el-tooltip placement="top" effect="dark">
      <template #content>
        <span style="white-space: pre-line">{{ tip }}</span>
      </template>
      <el-icon class="field-tip-badge"><QuestionFilled /></el-icon>
    </el-tooltip>
  </span>
</template>

<script setup lang="ts">
import { QuestionFilled } from '@element-plus/icons-vue'

/**
 * 表单字段标签角标：标签文本 + 问号图标，鼠标悬浮显示操作提示。
 * 用于替代表单项下方的长段内联说明，压缩弹窗/表单高度。
 *
 * 用法：<template #label><FieldLabelTip tip="提示内容">字段名</FieldLabelTip></template>
 */
defineProps<{
  /** 悬浮显示的提示内容（支持 \n 换行） */
  tip: string
}>()
</script>

<style scoped>
.field-label-with-tip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.field-tip-badge {
  color: var(--el-color-info, #909399);
  font-size: 14px;
  cursor: help;
  transition: color 0.15s ease;
}

.field-tip-badge:hover {
  color: var(--el-color-primary, #409eff);
}
</style>
