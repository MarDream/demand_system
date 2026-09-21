<template>
  <div class="public-view-page">
    <div class="public-view-page__card">
      <template v-if="error">
        <el-result icon="warning" :title="error" />
      </template>
      <template v-else>
        <h1 class="public-view-page__title">{{ viewName || '数据视图' }}</h1>

        <el-table :data="rows" border stripe v-loading="loading" style="width: 100%">
          <el-table-column
            v-for="field in fields"
            :key="field.id"
            :label="field.name"
            min-width="140"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ formatCell(row.cells?.[field.id], field) }}
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无数据" :image-size="72" />
          </template>
        </el-table>

        <div class="public-view-page__pager">
          <el-pagination
            layout="prev, pager, next"
            :total="total"
            :page-size="pageSize"
            :current-page="pageNum"
            @current-change="load"
          />
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getPublicViewData } from '@/api/modules/bitable'

const route = useRoute()
const token = String(route.params.token || '')

const error = ref('')
const loading = ref(false)
const viewName = ref('')
const fields = ref<any[]>([])
const rows = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 50

onMounted(() => load(1))

async function load(page: number) {
  loading.value = true
  pageNum.value = page
  try {
    const data = await getPublicViewData(token, { pageNum: page, pageSize })
    viewName.value = (data.viewName as string) || '数据视图'
    fields.value = (data.fields as any[]) || []
    rows.value = (data.records as any[]) || []
    total.value = (data.total as number) || 0
  } catch (e: any) {
    error.value = e?.message || '分享链接无效或已失效'
  } finally {
    loading.value = false
  }
}

function formatCell(cell: any, field: any): string {
  if (!cell) return ''
  if (cell.valueText) return cell.valueText
  if (cell.valueNumber != null) return String(cell.valueNumber)
  if (cell.valueDate) return cell.valueDate
  const json = cell.valueJson
  if (Array.isArray(json)) return json.filter(Boolean).join(', ')
  if (json != null && typeof json === 'object') return JSON.stringify(json)
  if (json != null) return String(json)
  // 单元格为空但字段是数字/日期类型时也显示空
  return field?.fieldType === 'check' || field?.fieldType === 'checkbox' ? '' : ''
}
</script>

<style scoped lang="scss">
.public-view-page {
  min-height: 100vh;
  padding: 40px 24px;
  background: var(--color-background, var(--color-background));
}

.public-view-page__card {
  max-width: 1080px;
  margin: 0 auto;
  background: var(--color-surface, var(--color-surface));
  border-radius: 12px;
  padding: 24px 28px;
  box-shadow: 0 8px 30px rgba(15, 23, 42, 0.08);
}

.public-view-page__title {
  font-size: 20px;
  margin: 0 0 16px;
}

.public-view-page__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
