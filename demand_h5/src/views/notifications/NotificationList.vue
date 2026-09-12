<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import {
  getNotificationList,
  getUnreadCount,
  markAsRead,
  markAllAsRead,
  type NotificationItem,
} from '@/api/notification'
import { formatRelative } from '@/utils/format'

const router = useRouter()

const list = ref<NotificationItem[]>([])
const refreshing = ref(false)
const loading = ref(false)
const finished = ref(false)
const error = ref(false)
const pageNum = ref(1)
const PAGE_SIZE = 20
const unread = ref(0)

async function loadPage(reset = false) {
  if (reset) {
    pageNum.value = 1
    finished.value = false
  }
  loading.value = true
  try {
    const result = await getNotificationList({ pageNum: pageNum.value, pageSize: PAGE_SIZE })
    const rows = result.list || []
    if (reset) {
      list.value = rows
    } else {
      list.value.push(...rows)
    }
    pageNum.value += 1
    finished.value = rows.length < PAGE_SIZE
    error.value = false
  } catch {
    error.value = true
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

async function refreshUnread() {
  try {
    unread.value = await getUnreadCount()
  } catch {
    unread.value = 0
  }
}

async function onRefresh() {
  await Promise.all([loadPage(true), refreshUnread()])
}

async function onClick(item: NotificationItem) {
  if (!item.isRead) {
    try {
      await markAsRead(item.id)
      item.isRead = 1
      unread.value = Math.max(0, unread.value - 1)
    } catch {
      // ignore
    }
  }
  // 需求类通知跳转到对应需求详情
  if (item.type === 'requirement' && item.relatedId) {
    router.push(`/requirement/${item.relatedId}`)
  }
}

async function onMarkAll() {
  await markAllAsRead()
  showToast('已全部标记为已读')
  list.value.forEach((n) => (n.isRead = 1))
  unread.value = 0
}

onRefresh()
</script>

<template>
  <div class="page page--tabbar">
    <van-nav-bar title="通知中心" fixed placeholder safe-area-inset-top>
      <template #right>
        <span class="nav-action" @click="onMarkAll">全部已读</span>
      </template>
    </van-nav-bar>

    <div class="unread-banner" v-if="unread > 0">
      未读通知 <b>{{ unread }}</b> 条
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list
        v-model:loading="loading"
        v-model:error="error"
        :finished="finished"
        finished-text="没有更多了"
        error-text="加载失败，点击重试"
        @load="() => loadPage(false)"
      >
        <div
          v-for="item in list"
          :key="item.id"
          class="notice-card"
          :class="{ 'notice-card--unread': !item.isRead }"
          @click="onClick(item)"
        >
          <div class="notice-card__head">
            <span class="notice-card__dot" v-if="!item.isRead" />
            <span class="notice-card__title">{{ item.title || '通知' }}</span>
            <span class="notice-card__type">
              {{ item.type === 'requirement' ? '需求' : '系统' }}
            </span>
          </div>
          <div class="notice-card__content pre-wrap">{{ item.content }}</div>
          <div class="notice-card__time">{{ formatRelative(item.createdAt) }}</div>
        </div>
        <van-empty v-if="!loading && finished && list.length === 0" description="暂无通知" />
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<style scoped>
.nav-action {
  font-size: 13px;
  color: #1989fa;
}

.unread-banner {
  margin: 10px 12px 0;
  padding: 10px 14px;
  background: #ecf9ff;
  border-radius: 8px;
  font-size: 13px;
  color: #1989fa;
}

.notice-card {
  margin: 10px 12px 0;
  padding: 12px 14px;
  background: #fff;
  border-radius: 10px;
}

.notice-card--unread {
  background: #f0f7ff;
}

.notice-card__head {
  display: flex;
  align-items: center;
  gap: 6px;
}

.notice-card__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ee0a24;
  flex-shrink: 0;
}

.notice-card__title {
  font-size: 14px;
  font-weight: 600;
  color: #323233;
}

.notice-card__type {
  margin-left: auto;
  font-size: 11px;
  padding: 1px 6px;
  border: 1px solid #dcdee0;
  border-radius: 4px;
  color: #969799;
}

.notice-card__content {
  font-size: 13px;
  color: #646566;
  margin-top: 6px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.notice-card__time {
  font-size: 11px;
  color: #c8c9cc;
  margin-top: 6px;
}
</style>
