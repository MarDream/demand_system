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
      </div>

      <!-- 评审列表 -->
      <el-table :data="filteredReviews" border style="width: 100%; margin-top: 16px">
        <el-table-column label="需求ID" width="90">
          <template #default="{ row }">{{ row.requirementId }}</template>
        </el-table-column>
        <el-table-column prop="requirementTitle" label="需求标题" min-width="200" />
        <el-table-column label="评审结果" width="100">
          <template #default="{ row }">
            <el-tag :type="getResultType(row.result)">
              {{ row.result || '未评审' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reviewerName" label="评审人" width="120" />
        <el-table-column label="评审时间" width="180">
          <template #default="{ row }">
            {{ row.reviewedAt || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetail(row)">查看详情</el-button>
            <el-button link type="primary" @click="editReview(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
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
        <el-button type="primary" @click="submitReview">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { updateReview } from '@/api/modules/review'

interface ReviewRecord {
  id: number
  requirementId: number
  requirementTitle: string
  result: string
  reviewerId: number
  reviewerName: string
  comment: string | null
  suggestions: string | null
  reviewedAt: string | null
}

const reviews = ref<ReviewRecord[]>([])
const filterRequirementId = ref('')
const filterResult = ref('')
const detailVisible = ref(false)
const detailMode = ref<'view' | 'edit'>('view')
const currentReview = ref<ReviewRecord | null>(null)
const reviewForm = ref({
  result: '',
  comment: '',
  suggestions: '',
})

const filteredReviews = computed(() => {
  return reviews.value.filter((r) => {
    if (filterRequirementId.value && String(r.requirementId) !== filterRequirementId.value) return false
    if (filterResult.value && r.result !== filterResult.value) return false
    return true
  })
})

const getResultType = (result: string) => {
  const map: Record<string, string> = {
    '通过': 'success',
    '不通过': 'danger',
    '需修改': 'warning',
  }
  return map[result] || 'info'
}

const loadReviews = () => {
  // 模拟数据，API 返回后切换
  reviews.value = [
    { id: 1, requirementId: 1, requirementTitle: '用户登录功能优化', result: '通过', reviewerId: 1, reviewerName: '张三', comment: '功能完善，符合需求', suggestions: null, reviewedAt: '2024-03-20 10:00:00' },
    { id: 2, requirementId: 2, requirementTitle: '数据报表导出功能', result: '需修改', reviewerId: 2, reviewerName: '李四', comment: '导出格式需要调整', suggestions: '增加PDF格式导出选项', reviewedAt: '2024-03-21 14:30:00' },
    { id: 3, requirementId: 3, requirementTitle: '消息通知系统', result: '不通过', reviewerId: 3, reviewerName: '王五', comment: '缺少邮件通知功能', suggestions: '补充邮件、短信通知渠道', reviewedAt: null },
    { id: 4, requirementId: 4, requirementTitle: '权限管理模块', result: '通过', reviewerId: 1, reviewerName: '张三', comment: 'RBAC设计合理', suggestions: null, reviewedAt: '2024-03-22 09:15:00' },
    { id: 5, requirementId: 5, requirementTitle: '搜索功能优化', result: '', reviewerId: 2, reviewerName: '李四', comment: null, suggestions: null, reviewedAt: null },
  ]
}

const viewDetail = (row: ReviewRecord) => {
  currentReview.value = { ...row }
  reviewForm.value = {
    result: row.result,
    comment: row.comment || '',
    suggestions: row.suggestions || '',
  }
  detailMode.value = 'view'
  detailVisible.value = true
}

const editReview = (row: ReviewRecord) => {
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
  } catch {
    ElMessage.success('评审提交成功（模拟）')
  }

  detailVisible.value = false
  loadReviews()
}

onMounted(() => {
  loadReviews()
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
