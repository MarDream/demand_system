<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { useUserStore } from '@/stores/user'
import { logout } from '@/api/auth'

const router = useRouter()
const userStore = useUserStore()

const user = computed(() => userStore.userInfo)

async function handleLogout() {
  try {
    await showConfirmDialog({
      title: '退出登录',
      message: '确定要退出当前账号吗？',
    })
  } catch {
    return
  }
  try {
    await logout()
  } catch {
    // 忽略登出接口异常，本地照常清理
  }
  userStore.reset()
  showToast('已退出登录')
  router.replace('/login')
}
</script>

<template>
  <div class="page page--tabbar">
    <van-nav-bar title="我的" fixed placeholder safe-area-inset-top />

    <div class="profile-card">
      <van-image
        round
        width="56px"
        height="56px"
        :src="user?.avatar || undefined"
      >
        <template v-if="!user?.avatar" #loading>
          <div class="avatar-fallback">{{ (user?.realName || user?.username || '?').slice(0, 1) }}</div>
        </template>
      </van-image>
      <div class="profile-card__info">
        <div class="profile-card__name">{{ user?.realName || user?.username || '-' }}</div>
        <div class="profile-card__account">@{{ user?.username }}</div>
      </div>
    </div>

    <van-cell-group inset style="margin-top: 12px">
      <van-cell title="角色" :value="user?.roleNames?.join('、') || user?.roles?.join('、') || '-'" />
      <van-cell title="邮箱" :value="user?.email || '-'" />
      <van-cell title="手机号" :value="user?.phone || '-'" />
    </van-cell-group>

    <van-cell-group inset style="margin-top: 12px">
      <van-cell title="系统提示" label="移动端支持需求查看、评论、审批流转与通知；多维表格等完整功能请使用 PC 端。" />
    </van-cell-group>

    <div class="logout-wrap">
      <van-button block round type="danger" plain @click="handleLogout">退出登录</van-button>
    </div>
  </div>
</template>

<style scoped>
.profile-card {
  margin: 16px 12px 0;
  padding: 20px 16px;
  background: linear-gradient(135deg, #1989fa 0%, #5fadff 100%);
  border-radius: 12px;
  display: flex;
  align-items: center;
  gap: 14px;
  color: #fff;
}

.avatar-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  color: #1989fa;
  font-size: 24px;
  font-weight: 600;
}

.profile-card__name {
  font-size: 18px;
  font-weight: 600;
}

.profile-card__account {
  font-size: 13px;
  opacity: 0.85;
  margin-top: 4px;
}

.logout-wrap {
  margin: 32px 16px;
}
</style>
