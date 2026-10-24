<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { storeToRefs } from 'pinia'
import { useUserStore } from '@/stores/user'
import { useNotificationStore } from '@/stores/notification'
import { logout } from '@/api/auth'
import { setActiveRole } from '@/utils/auth'
import { resolveAvatarUrl } from '@/utils/avatar'

const router = useRouter()
const userStore = useUserStore()
const notificationStore = useNotificationStore()
const { unread } = storeToRefs(notificationStore)

const user = computed(() => userStore.userInfo)
/** 头像可渲染地址：preset: 等非图片串返回 null，模板回退姓名首字 */
const avatarSrc = computed(() => resolveAvatarUrl(user.value?.avatar))

/** 全部角色（切换候选） */
const allRoles = computed(() => user.value?.allRoles || user.value?.roles || [])
const allRoleNames = computed(() => user.value?.allRoleNames || user.value?.roleNames || [])
/** 多角色用户才展示切换入口 */
const canSwitchRole = computed(() => allRoles.value.length > 1)
const activeRoleLabel = computed(() => {
  const active = user.value?.activeRole
  if (!active) return '全部角色'
  const idx = allRoles.value.indexOf(active)
  return allRoleNames.value[idx] || active
})

interface RoleAction {
  name: string
  value: string
}

const roleSheetShow = ref(false)
const roleActions = computed<RoleAction[]>(() => [
  { name: '全部角色', value: '' },
  ...allRoles.value.map((code, i) => ({ name: allRoleNames.value[i] || code, value: code })),
])

function openRolePicker() {
  roleSheetShow.value = true
}

/** select 回调参数按 Vant 的 ActionSheetAction 结构收（带自定义 value） */
async function onRoleSelect(action: { name?: string; value?: string }) {
  roleSheetShow.value = false
  const value = action.value ?? ''
  if (value === (user.value?.activeRole || '')) return
  setActiveRole(value)
  // 重新拉取用户信息：待办/已办按当前生效角色收窄
  await userStore.fetchUserInfo()
  showToast(`已切换为${action.name || value || '全部角色'}`)
}

const unreadBadge = computed(() => (unread.value > 0 ? (unread.value > 99 ? '99+' : String(unread.value)) : ''))

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
        :src="avatarSrc || undefined"
      >
        <template v-if="!avatarSrc" #loading>
          <div class="avatar-fallback">{{ (user?.realName || user?.username || '?').slice(0, 1) }}</div>
        </template>
      </van-image>
      <div class="profile-card__info">
        <div class="profile-card__name">{{ user?.realName || user?.username || '-' }}</div>
        <div class="profile-card__account">@{{ user?.username }}</div>
      </div>
    </div>

    <van-cell-group inset style="margin-top: 12px">
      <van-cell
        title="消息通知"
        is-link
        :badge="unreadBadge || undefined"
        @click="router.push('/notifications')"
      />
      <van-cell
        v-if="canSwitchRole"
        title="生效角色"
        is-link
        :value="activeRoleLabel"
        @click="openRolePicker"
      />
      <van-cell title="角色" :value="user?.roleNames?.join('、') || user?.roles?.join('、') || '-'" />
      <van-cell title="邮箱" :value="user?.email || '-'" />
      <van-cell title="手机号" :value="user?.phone || '-'" />
    </van-cell-group>

    <van-cell-group inset style="margin-top: 12px">
      <van-cell title="系统提示" label="移动端支持需求查看、评论、审批流转与通知；多维表格等完整功能请使用 PC 端。" />
    </van-cell-group>

    <van-action-sheet
      v-model:show="roleSheetShow"
      :actions="roleActions"
      cancel-text="取消"
      close-on-click-action
      @select="onRoleSelect"
    />

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
