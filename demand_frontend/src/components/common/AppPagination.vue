<script setup lang="ts">
/**
 * 分页条公共组件：统一各列表页 el-pagination 的布局与回调。
 * 优先配合 usePagedList 使用。
 */
withDefaults(defineProps<{
  total: number
  pageNum: number
  pageSize: number
  layout?: string
  pageSizes?: number[]
  /** 仅当总数超过单页容量时才显示分页条 */
  hideOnSinglePage?: boolean
}>(), {
  layout: 'total, sizes, prev, pager, next',
  pageSizes: () => [10, 20, 50, 100],
  hideOnSinglePage: false,
})

const emit = defineEmits<{
  (e: 'update:pageNum', page: number): void
  (e: 'update:pageSize', size: number): void
  (e: 'change'): void
}>()

function onCurrentChange(page: number) {
  emit('update:pageNum', page)
  emit('change')
}

function onSizeChange(size: number) {
  emit('update:pageSize', size)
  emit('change')
}
</script>

<template>
  <el-pagination
    :current-page="pageNum"
    :page-size="pageSize"
    :total="total"
    :layout="layout"
    :page-sizes="pageSizes"
    :hide-on-single-page="hideOnSinglePage"
    @current-change="onCurrentChange"
    @size-change="onSizeChange"
  />
</template>
