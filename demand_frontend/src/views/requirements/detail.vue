<template>
  <div v-loading="loading" class="detail-page">
    <template v-if="detail">
      <!-- Header -->
      <div class="detail-header">
        <div class="header-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ name: 'Requirements' }">需求管理</el-breadcrumb-item>
            <el-breadcrumb-item>需求详情</el-breadcrumb-item>
          </el-breadcrumb>
          <h2 class="detail-title">
            {{ detail.title }}
            <el-tag :type="statusTagType(detail.status)" size="small" style="margin-left: 12px">
              {{ detail.status }}
            </el-tag>
          </h2>
        </div>
        <div class="header-actions">
          <el-button @click="handleEdit">编辑</el-button>
          <el-button type="success" @click="handleSplit">拆分子需求</el-button>
          <el-popconfirm title="确定删除该需求吗？" @confirm="handleDelete">
            <template #reference>
              <el-button type="danger">删除</el-button>
            </template>
          </el-popconfirm>
          <el-select
            v-model="newStatus"
            placeholder="状态流转"
            style="width: 140px; margin-right: 8px"
          >
            <el-option label="待评审" value="待评审" />
            <el-option label="评审中" value="评审中" />
            <el-option label="已通过" value="已通过" />
            <el-option label="开发中" value="开发中" />
            <el-option label="测试中" value="测试中" />
            <el-option label="已上线" value="已上线" />
            <el-option label="已验收" value="已验收" />
          </el-select>
          <el-button type="primary" @click="handleStatusTransition">执行流转</el-button>
        </div>
      </div>

      <!-- Tabs -->
      <el-tabs v-model="activeTab" class="detail-tabs">
        <!-- 基本信息 -->
        <el-tab-pane label="基本信息" name="basic">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="需求类型">{{ detail.type }}</el-descriptions-item>
            <el-descriptions-item label="优先级">
              <el-tag :type="priorityTagType(detail.priority)">{{ detail.priority }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusTagType(detail.status)">{{ detail.status }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="负责人">{{ detail.assigneeName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建人">{{ detail.creatorName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ detail.createdAt }}</el-descriptions-item>
            <el-descriptions-item label="所属迭代">{{ detail.iterationId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="截止日期">{{ detail.dueDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="估算工时">{{ detail.estimatedHours ? detail.estimatedHours + ' 小时' : '-' }}</el-descriptions-item>
            <el-descriptions-item label="实际工时">{{ detail.actualHours ? detail.actualHours + ' 小时' : '-' }}</el-descriptions-item>
            <el-descriptions-item label="描述" :span="2">
              {{ detail.description || '-' }}
            </el-descriptions-item>
          </el-descriptions>

          <!-- 子需求列表 -->
          <div class="children-section">
            <div class="section-header">
              <h3>子需求（{{ children.length }} 个）</h3>
              <el-button type="primary" size="small" @click="handleSplit">+ 拆分子需求</el-button>
            </div>
            <el-table v-if="children.length > 0" :data="children" border size="small">
              <el-table-column label="ID" width="60" align="center">
                <template #default="{ row }">{{ row.id }}</template>
              </el-table-column>
              <el-table-column label="标题" min-width="200">
                <template #default="{ row }">
                  <el-link type="primary" @click="router.push({ name: 'RequirementDetail', params: { id: row.id } })">
                    {{ row.title }}
                  </el-link>
                </template>
              </el-table-column>
              <el-table-column label="类型" width="80" align="center">
                <template #default="{ row }">{{ row.type }}</template>
              </el-table-column>
              <el-table-column label="优先级" width="80" align="center">
                <template #default="{ row }">
                  <el-tag :type="priorityTagType(row.priority)" size="small">{{ row.priority }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="暂无子需求，点击上方按钮进行拆分" :image-size="60" />
          </div>
        </el-tab-pane>

        <!-- 变更历史 -->
        <el-tab-pane label="变更历史" name="history">
          <el-timeline v-if="history.length > 0">
            <el-timeline-item
              v-for="item in history"
              :key="item.id"
              :timestamp="item.createdAt"
              placement="top"
            >
              <el-card>
                <p><strong>{{ item.operatorName || '系统' }}</strong></p>
                <p>
                  <span>{{ item.fieldName }}: </span>
                  <span class="old-value">{{ item.oldValue || '空' }}</span>
                  <span> -> </span>
                  <span class="new-value">{{ item.newValue || '空' }}</span>
                </p>
              </el-card>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无变更历史" />
        </el-tab-pane>

        <!-- 关联需求 -->
        <el-tab-pane label="关联需求" name="relations">
          <el-table :data="relatedRequirements" border>
            <el-table-column label="需求ID" width="80" align="center">
              <template #default="{ row }">{{ row.id }}</template>
            </el-table-column>
            <el-table-column label="需求标题" min-width="200">
              <template #default="{ row }">
                <el-link type="primary" @click="router.push({ name: 'RequirementDetail', params: { id: row.id } })">
                  {{ row.title }}
                </el-link>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="100" align="center">
              <template #default="{ row }">{{ row.type }}</template>
            </el-table-column>
            <el-table-column label="优先级" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="priorityTagType(row.priority)" size="small">{{ row.priority }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="关联类型" width="120" align="center">
              <template #default="{ row }">
                <el-tag>{{ row.relationType || '关联' }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="relatedRequirements.length === 0" description="暂无关联需求" />
        </el-tab-pane>

        <!-- 评论 -->
        <el-tab-pane label="评论" name="comments">
          <div class="comment-section">
            <el-input
              v-model="commentText"
              type="textarea"
              :rows="4"
              placeholder="输入评论内容..."
              maxlength="500"
              show-word-limit
            />
            <div class="comment-actions">
              <el-button type="primary" :disabled="!commentText.trim()" @click="handleComment">
                提交评论
              </el-button>
            </div>
          </div>
          <el-empty v-if="comments.length === 0" description="暂无评论" />
          <div v-for="comment in comments" :key="comment.id" class="comment-item">
            <el-avatar :size="32">{{ comment.userName?.charAt(0) || 'U' }}</el-avatar>
            <div class="comment-content">
              <div class="comment-header">
                <strong>{{ comment.userName || '用户' }}</strong>
                <span class="comment-time">{{ comment.createdAt }}</span>
              </div>
              <p>{{ comment.content }}</p>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { requirementApi } from '@/api'
import type { Requirement, RequirementHistory, RequirementUpdate } from '@/types/requirement'

const route = useRoute()
const router = useRouter()

const id = Number(route.params.id)
const loading = ref(false)
const detail = ref<Requirement | null>(null)
const history = ref<RequirementHistory[]>([])
const relatedRequirements = ref<any[]>([])
const comments = ref<any[]>([])
const children = ref<any[]>([])
const activeTab = ref('basic')
const newStatus = ref('')
const commentText = ref('')

// Fetch detail
async function fetchDetail() {
  loading.value = true
  try {
    const res = await requirementApi.getRequirementById(id)
    detail.value = res
  } catch {
    ElMessage.error('获取需求详情失败')
  } finally {
    loading.value = false
  }
}

// Fetch history
async function fetchHistory() {
  try {
    const res = await requirementApi.getRequirementHistory(id)
    history.value = res
  } catch {
    // history fetch failure is non-critical
  }
}

// Fetch children
async function fetchChildren() {
  try {
    const res = await requirementApi.getRequirementChildren(id)
    children.value = res
  } catch {
    // children fetch failure is non-critical
  }
}

// Tag type helpers
function priorityTagType(priority: string): string {
  const map: Record<string, string> = { P0: 'danger', P1: 'warning', P2: 'info', P3: 'success' }
  return map[priority] || 'info'
}

function statusTagType(status: string): string {
  const map: Record<string, string> = {
    '新建': 'info', '待评审': 'info', '评审中': 'warning', '已通过': 'success',
    '开发中': 'primary', '测试中': 'info', '已上线': 'success', '已验收': 'success',
  }
  return map[status] || 'info'
}

// Handlers
function handleEdit() {
  router.push({ name: 'RequirementCreate', query: { id } })
}

function handleSplit() {
  router.push({ name: 'RequirementCreate', query: { parentId: id } })
}

async function handleDelete() {
  try {
    await requirementApi.deleteRequirement(id)
    ElMessage.success('删除成功')
    router.push({ name: 'Requirements' })
  } catch {
    ElMessage.error('删除失败')
  }
}

async function handleStatusTransition() {
  if (!newStatus.value) {
    ElMessage.warning('请选择目标状态')
    return
  }
  try {
    await requirementApi.updateRequirement(id, { status: newStatus.value, id } as unknown as RequirementUpdate)
    ElMessage.success('状态流转成功')
    fetchDetail()
    newStatus.value = ''
  } catch {
    ElMessage.error('状态流转失败')
  }
}

function handleComment() {
  if (!commentText.value.trim()) return
  ElMessage.info('评论功能开发中')
  commentText.value = ''
}

onMounted(() => {
  fetchDetail()
  fetchHistory()
  fetchChildren()
})
</script>

<style scoped>
.detail-page {
  padding: 20px;
  background: #fff;
  border-radius: 8px;
  min-height: calc(100vh - 100px);
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
  flex-wrap: wrap;
  gap: 12px;
}

.header-left {
  flex: 1;
}

.detail-title {
  margin: 12px 0 0;
  font-size: 20px;
  font-weight: 600;
  display: flex;
  align-items: center;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail-tabs {
  margin-top: 16px;
}

.old-value {
  color: #909399;
  text-decoration: line-through;
}

.new-value {
  color: #409eff;
  font-weight: 500;
}

.comment-section {
  margin-bottom: 24px;
}

.comment-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.comment-item {
  display: flex;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid #ebeef5;
}

.comment-content {
  flex: 1;
}

.comment-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.comment-time {
  color: #909399;
  font-size: 12px;
}

.children-section {
  margin-top: 24px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.section-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}
</style>
