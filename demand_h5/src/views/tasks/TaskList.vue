<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  getMyPending,
  getMyFollows,
  getMyDone,
  getList,
  type Requirement,
} from '@/api/requirement'
import { statusColor, statusLabel, priorityInfo, typeLabel, formatRelative } from '@/utils/format'

const router = useRouter()

type TabKey = 'pending' | 'follows' | 'done' | 'all'
const activeTab = ref<TabKey>((sessionStorage.getItem('h5_task_tab') as TabKey) || 'pending')
const keyword = ref('')

function switchTab(name: string | number) {
  activeTab.value = name as TabKey
  sessionStorage.setItem('h5_task_tab', activeTab.value)
  loadPage(true)
}

const tabMeta: Record<TabKey, { title: string; loader: (params: Record<string, unknown>) => Promise<{ list: Requirement[]; total: number }> }> = {
  pending: { title: '待我处理', loader: (p) => getMyPending(p as never) },
  follows: { title: '我关注的', loader: (p) => getMyFollows(p as never) },
  done: { title: '已办结', loader: (p) => getMyDone(p as never) },
  all: { title: '全部需求', loader: (p) => getList(p as never) },
}

const list = ref<Requirement[]>([])
const refreshing = ref(false)
const loading = ref(false)
const finished = ref(false)
const error = ref(false)
const pageNum = ref(1)
const PAGE_SIZE = 15

const total = ref(0)

let fetching = false

async function loadPage(reset = false) {
  if (fetching) return
  fetching = true
  if (reset) {
    pageNum.value = 1
    finished.value = false
  }
  loading.value = true
  loading.value = true
  try {
    const meta = tabMeta[activeTab.value]
    const result = await meta.loader({
      keyword: keyword.value || undefined,
      pageNum: pageNum.value,
      pageSize: PAGE_SIZE,
    })
    const rows = result.list || []
    total.value = result.total ?? rows.length
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
    fetching = false
  }
}

function onRefresh() {
  loadPage(true)
}

function onSearch() {
  loadPage(true)
}

function goDetail(item: Requirement) {
  router.push(`/requirement/${item.id}`)
}

const tabTitle = computed(() => tabMeta[activeTab.value].title)

function toDraftTag(item: Requirement) {
  return item.isDraft === 1 || item.isDraft === true
}
</script>

<template>
  <div class="page page--tabbar">
    <van-nav-bar :title="tabTitle" fixed placeholder safe-area-inset-top>
      <template #right>
        <span class="nav-total" v-if="total > 0">共 {{ total }} 条</span>
      </template>
    </van-nav-bar>

    <van-search
      v-model="keyword"
      placeholder="搜索需求标题 / 编号"
      shape="round"
      @search="onSearch"
      @clear="onSearch"
    />

    <van-tabs v-model:active="activeTab" sticky @change="switchTab">
      <van-tab v-for="(meta, key) in tabMeta" :key="key" :title="meta.title" :name="key">
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
              class="req-card"
              @click="goDetail(item)"
            >
              <div class="req-card__top">
                <span class="req-card__no" v-if="item.requirementNo">#{{ item.requirementNo }}</span>
                <span
                  class="req-card__tag"
                  :style="{ color: priorityInfo(item.priority).color, borderColor: priorityInfo(item.priority).color }"
                >
                  {{ priorityInfo(item.priority).label }}
                </span>
                <span class="req-card__tag">{{ typeLabel(item.type) }}</span>
                <span v-if="toDraftTag(item)" class="req-card__tag req-card__tag--draft">草稿</span>
                <van-icon
                  v-if="item.followed"
                  name="star"
                  class="req-card__follow"
                  color="#FFCD32"
                />
              </div>
              <div class="req-card__title">{{ item.title }}</div>
              <div class="req-card__bottom">
                <span
                  class="req-card__status"
                  :style="{ background: statusColor(item.status) + '1a', color: statusColor(item.status) }"
                >
                  {{ statusLabel(item.status) }}
                </span>
                <span class="req-card__meta" v-if="item.creatorName">{{ item.creatorName }}</span>
                <span class="req-card__meta req-card__meta--time">{{ formatRelative(item.updatedAt) }}</span>
              </div>
            </div>
            <van-empty
              v-if="!loading && finished && list.length === 0"
              description="暂无数据"
            />
          </van-list>
        </van-pull-refresh>
      </van-tab>
    </van-tabs>
  </div>
</template>

<style scoped>
.nav-total {
  font-size: 12px;
  color: #969799;
}

.req-card {
  margin: 10px 12px 0;
  padding: 12px 14px;
  background: #fff;
  border-radius: 10px;
}

.req-card__top {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.req-card__no {
  font-size: 12px;
  color: #969799;
  font-family: monospace;
}

.req-card__tag {
  font-size: 11px;
  padding: 1px 6px;
  border: 1px solid #dcdee0;
  border-radius: 4px;
  color: #969799;
}

.req-card__tag--draft {
  color: #ff976a;
  border-color: #ff976a;
}

.req-card__follow {
  margin-left: auto;
  font-size: 14px;
}

.req-card__title {
  margin: 8px 0;
  font-size: 15px;
  font-weight: 600;
  color: #323233;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.req-card__bottom {
  display: flex;
  align-items: center;
  gap: 10px;
}

.req-card__status {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
}

.req-card__meta {
  font-size: 12px;
  color: #969799;
}

.req-card__meta--time {
  margin-left: auto;
}
</style>
