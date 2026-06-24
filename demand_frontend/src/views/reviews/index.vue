<template>
  <div class="review-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">评审管理</span>
        </div>
      </template>

      <!-- 筛选 -->
      <div class="filter-bar">
        <el-input
          v-model="filterRequirementId"
          placeholder="按需求ID筛选"
          clearable
          style="width: 200px"
          @clear="loadReviews"
        />
        <el-select
          v-model="filterResult"
          placeholder="按评审结果筛选"
          clearable
          style="width: 150px; margin-left: 12px"
          @change="loadReviews"
        >
          <el-option label="通过" value="通过" />
          <el-option label="不通过" value="不通过" />
          <el-option label="需修改" value="需修改" />
        </el-select>
        <el-button type="primary" style="margin-left: 12px" @click="loadReviews">搜索</el-button>
        <el-tooltip content="列表字段设置" style="margin-left: auto">
          <el-button link :icon="Setting" @click="openColumnConfig" />
        </el-tooltip>
      </div>

      <!-- 评审列表 -->
      <el-table v-loading="loading" :data="reviews" border style="width: 100%; margin-top: 16px">
        <el-table-column v-if="isColumnVisible('requirementId')" label="需求ID" width="90">
          <template #default="{ row }">{{ row.requirementId }}</template>
        </el-table-column>
        <el-table-column v-if="isColumnVisible('requirementTitle')" prop="requirementTitle" label="需求标题" min-width="200" />
        <el-table-column v-if="isColumnVisible('result')" label="评审结果" width="100">
          <template #default="{ row }">
            <el-tag :type="getResultType(row.result)">
              {{ row.result || '未评审' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="isColumnVisible('reviewerName')" prop="reviewerName" label="评审人" width="120" />
        <el-table-column v-if="isColumnVisible('reviewedAt')" label="评审时间" width="180">
          <template #default="{ row }">
            {{ row.reviewedAt || '-' }}
          </template>
        </el-table-column>
        <el-table-column v-if="isColumnVisible('operations')" label="操作" width="100">
          <template #default="{ row }">
            <el-tooltip content="查看详情">
              <el-button v-permission="'button:review:view'" link type="primary" @click="viewDetail(row)"><el-icon><View /></el-icon></el-button>
            </el-tooltip>
            <AppButton link type="primary" permission="button:review:update" @click="editReview(row)"><el-icon><EditPen /></el-icon></AppButton>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        class="review-pagination"
        @size-change="loadReviews"
        @current-change="loadReviews"
      />
    </el-card>

    <!-- 评审详情对话框 -->
    <el-dialog
      v-model="detailVisible"
      :title="detailMode === 'view' ? '评审详情' : '编辑评审'"
      width="600px"
    >
      <el-form :model="reviewForm" label-width="100px">
        <el-descriptions :column="1" border class="mb-4">
          <el-descriptions-item label="需求标题">
            {{ currentReview?.requirementTitle || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="需求ID">
            {{ currentReview?.requirementId || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="评审人">
            {{ currentReview?.reviewerName || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-form-item label="评审结果" v-if="detailMode === 'edit'">
          <el-radio-group v-model="reviewForm.result">
            <el-radio value="通过">通过</el-radio>
            <el-radio value="不通过">不通过</el-radio>
            <el-radio value="需修改">需修改</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="评审结果" v-else>
          <el-tag :type="getResultType(reviewForm.result)">{{ reviewForm.result || '未评审' }}</el-tag>
        </el-form-item>

        <el-form-item label="评审意见">
          <el-input
            v-model="reviewForm.comment"
            type="textarea"
            :rows="4"
            placeholder="请输入评审意见"
            :disabled="detailMode === 'view'"
          />
        </el-form-item>

        <el-form-item label="修改建议">
          <el-input
            v-model="reviewForm.suggestions"
            type="textarea"
            :rows="3"
            placeholder="如需修改，请输入修改建议"
            :disabled="detailMode === 'view'"
          />
        </el-form-item>
      </el-form>

      <template #footer v-if="detailMode === 'edit'">
        <el-button @click="detailVisible = false">取消</el-button>
        <AppButton type="primary" permission="button:review:submit" @click="submitReview">提交</AppButton>
      </template>
    </el-dialog>

    <ColumnConfigDialog
      v-model="showColumnConfig"
      :column-groups="columnGroups"
      :draft-selected-columns="draftSelectedColumns"
      :draft-column-keys="draftColumnKeys"
      @update:draft-column-keys="draftColumnKeys = $event"
      @remove="removeDraftColumn"
      @save="saveColumns"
    />
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { View, EditPen, Setting } from '@element-plus/icons-vue'
import { getReviews, updateReview } from '@/api/modules/review'
import AppButton from '@/components/common/AppButton.vue'
import ColumnConfigDialog from '@/components/common/ColumnConfigDialog.vue'
import { useColumnConfig, type ColumnDef } from '@/composables/useColumnConfig'
import type { Review } from '@/types/review'

const reviewAllColumns: ColumnDef[] = [
  { key: 'requirementId', label: '需求ID', group: '基础字段', width: 90 },
  { key: 'requirementTitle', label: '需求标题', group: '基础字段', minWidth: 200 },
  { key: 'result', label: '评审结果', group: '状态信息', width: 100 },
  { key: 'reviewerName', label: '评审人', group: '人员与时间', width: 120 },
  { key: 'reviewedAt', label: '评审时间', group: '人员与时间', width: 180 },
  { key: 'operations', label: '操作', width: 100 },
]
const reviewDefaultKeys = ['requirementId', 'requirementTitle', 'result', 'reviewerName', 'reviewedAt', 'operations']

const {
  showColumnConfig,
  openColumnConfig,
  saveColumns,
  loadColumnConfig,
  columnGroups,
  draftSelectedColumns,
  draftColumnKeys,
  visibleColumns,
  removeDraftColumn,
} = useColumnConfig({
  pageKey: 'review_list',
  columns: reviewAllColumns,
  defaultKeys: reviewDefaultKeys,
})

function isColumnVisible(key: string) {
  return visibleColumns.value.some((c) => c.key === key)
}

const reviews = ref<Review[]>([])
const filterRequirementId = ref('')
const filterResult = ref('')
const loading = ref(false)
const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0,
})
const detailVisible = ref(false)
const detailMode = ref<'view' | 'edit'>('view')
const currentReview = ref<Review | null>(null)
const reviewForm = ref({
  result: '',
  comment: '',
  suggestions: '',
})

const getResultType = (result: string) => {
  const map: Record<string, string> = {
    '通过': 'success',
    '不通过': 'danger',
    '需修改': 'warning',
  }
  return map[result] || 'info'
}

const loadReviews = async () => {
  loading.value = true
  try {
    const requirementId = Number(filterRequirementId.value)
    const data = await getReviews({
      requirementId: Number.isFinite(requirementId) && requirementId > 0 ? requirementId : undefined,
      result: filterResult.value || undefined,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
    })
    reviews.value = data.list
    pagination.total = data.total
  } catch (error) {
    reviews.value = []
    pagination.total = 0
    ElMessage.error(resolveErrorMessage(error, '评审列表加载失败'))
  } finally {
    loading.value = false
  }
}

const viewDetail = (row: Review) => {
  currentReview.value = { ...row }
  reviewForm.value = {
    result: row.result,
    comment: row.comment || '',
    suggestions: row.suggestions || '',
  }
  detailMode.value = 'view'
  detailVisible.value = true
}

const editReview = (row: Review) => {
  currentReview.value = { ...row }
  reviewForm.value = {
    result: row.result,
    comment: row.comment || '',
    suggestions: row.suggestions || '',
  }
  detailMode.value = 'edit'
  detailVisible.value = true
}

const submitReview = async () => {
  if (!currentReview.value) return

  try {
    await updateReview(currentReview.value.id, reviewForm.value)
    ElMessage.success('评审提交成功')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '评审提交失败'))
    return
  }

  detailVisible.value = false
  await loadReviews()
}

function resolveErrorMessage(error: unknown, fallback: string) {
  const message = (error as any)?.response?.data?.message || (error as any)?.message
  return typeof message === 'string' && message.trim() ? message : fallback
}

onMounted(() => {
  loadReviews()
  loadColumnConfig()
})
</script>

<style scoped lang="scss">
.review-page {
  .card-header {
    .title {
      font-size: 18px;
      font-weight: 600;
    }
  }

  .filter-bar {
    display: flex;
    align-items: center;
  }

  .mb-4 {
    margin-bottom: 16px;
  }
}
</style>
