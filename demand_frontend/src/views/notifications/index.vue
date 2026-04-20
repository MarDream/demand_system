<template>
  <div class="notification-page">
    <div class="page-header">
      <h2>通知中心</h2>
      <el-button type="primary" size="small" @click="handleMarkAllRead" :disabled="list.every(n => n.isRead === 1)">
        全部已读
      </el-button>
    </div>

    <el-card v-loading="loading">
      <div v-if="list.length === 0" class="empty-state">
        <el-empty description="暂无通知" />
      </div>
      <div v-else class="notification-list">
        <div
          v-for="item in list"
          :key="item.id"
          class="notification-item"
          :class="{ unread: item.isRead === 0 }"
          @click="handleClick(item)"
        >
          <div class="item-dot" v-if="item.isRead === 0"></div>
          <div class="item-content">
            <div class="item-header">
              <span class="item-title">{{ item.title }}</span>
              <el-tag size="small" :type="item.type === 'requirement' ? 'primary' : 'info'">
                {{ item.type === 'requirement' ? '需求' : '系统' }}
              </el-tag>
            </div>
            <p class="item-text">{{ item.content }}</p>
            <span class="item-time">{{ item.createdAt }}</span>
          </div>
        </div>
      </div>

      <div class="pagination-wrapper" v-if="total > pageSize">
        <el-pagination
          v-model:current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          @current-change="fetchData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getNotificationList, markAsRead, markAllAsRead } from '@/api/modules/notification'

const router = useRouter()
const loading = ref(false)
const list = ref<any[]>([])
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)

async function fetchData() {
  loading.value = true
  try {
    const res = await getNotificationList({ pageNum: pageNum.value, pageSize: pageSize.value }) as any
    list.value = res.list || res.data?.list || []
    total.value = res.total || res.data?.total || 0
  } catch {
    ElMessage.error('获取通知列表失败')
  } finally {
    loading.value = false
  }
}

async function handleClick(item: any) {
  if (item.isRead === 0) {
    try {
      await markAsRead(item.id)
      item.isRead = 1
    } catch { /* ignore */ }
  }
  if (item.relatedId) {
    router.push({ name: 'RequirementDetail', params: { id: item.relatedId } })
  }
}

async function handleMarkAllRead() {
  try {
    await markAllAsRead()
    list.value.forEach(n => n.isRead = 1)
    ElMessage.success('已全部标记为已读')
  } catch {
    ElMessage.error('操作失败')
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.notification-page {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.notification-item {
  display: flex;
  gap: 12px;
  padding: 16px;
  border-bottom: 1px solid #ebeef5;
  cursor: pointer;
  transition: background 0.2s;
}

.notification-item:hover {
  background: #f5f7fa;
}

.notification-item.unread {
  background: #ecf5ff;
}

.notification-item.unread:hover {
  background: #d9ecff;
}

.item-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #409EFF;
  margin-top: 6px;
  flex-shrink: 0;
}

.item-content {
  flex: 1;
}

.item-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.item-title {
  font-weight: 500;
  font-size: 14px;
}

.item-text {
  margin: 0 0 4px;
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
}

.item-time {
  color: #909399;
  font-size: 12px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
