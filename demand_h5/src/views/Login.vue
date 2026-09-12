<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { login, getOrgTree, bindOrg, type OrgNode } from '@/api/auth'
import { setToken, setRefreshToken } from '@/utils/auth'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const form = reactive({ username: '', password: '' })
const loading = ref(false)

// 无组织用户首次登录强制选择组织
const showOrgPicker = ref(false)
const orgColumns = ref<OrgNode[]>([])
const selectedOrgIndex = ref(0)
const orgLoading = ref(false)

async function handleLogin() {
  if (!form.username.trim() || !form.password) {
    showToast('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const result = await login(form.username.trim(), form.password)
    setToken(result.accessToken)
    setRefreshToken(result.refreshToken)
    await userStore.fetchUserInfo()
    if (userStore.userInfo?.needOrgBind) {
      await openOrgPicker()
    } else {
      goNext()
    }
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function openOrgPicker() {
  orgLoading.value = true
  try {
    const tree = await getOrgTree()
    orgColumns.value = flatten(tree)
    showOrgPicker.value = true
  } finally {
    orgLoading.value = false
  }
}

function flatten(nodes: OrgNode[], depth = 0): OrgNode[] {
  const list: OrgNode[] = []
  for (const node of nodes) {
    list.push({ ...node, name: depth > 0 ? `${'　'.repeat(depth)}${node.name}` : node.name })
    if (node.children?.length) {
      list.push(...flatten(node.children, depth + 1))
    }
  }
  return list
}

async function handleBindOrg() {
  const org = orgColumns.value[selectedOrgIndex.value]
  if (!org) {
    showToast('请选择组织')
    return
  }
  await bindOrg(org.id)
  await userStore.fetchUserInfo()
  showOrgPicker.value = false
  showToast('组织绑定成功')
  goNext()
}

function goNext() {
  const redirect = (route.query.redirect as string) || '/tasks'
  router.replace(redirect)
}
</script>

<template>
  <div class="login-page">
    <div class="login-header">
      <div class="login-logo">📋</div>
      <h1 class="login-title">需求管理系统</h1>
      <p class="login-subtitle">移动端 · 随时随地处理需求</p>
    </div>

    <van-form @submit="handleLogin">
      <van-cell-group inset>
        <van-field
          v-model="form.username"
          name="username"
          label="用户名"
          placeholder="请输入用户名"
          left-icon="manager-o"
          :rules="[{ required: true, message: '请输入用户名' }]"
        />
        <van-field
          v-model="form.password"
          type="password"
          name="password"
          label="密码"
          placeholder="请输入密码"
          left-icon="lock"
          :rules="[{ required: true, message: '请输入密码' }]"
        />
      </van-cell-group>
      <div class="login-submit">
        <van-button round block type="primary" native-type="submit" :loading="loading">
          登 录
        </van-button>
      </div>
    </van-form>

    <van-popup v-model:show="showOrgPicker" round position="bottom" :close-on-click-overlay="false">
      <van-picker
        title="选择所属组织"
        :columns="orgColumns"
        :loading="orgLoading"
        :columns-field-names="{ text: 'name', value: 'id' }"
        @confirm="(e: { selectedIndexes: number[] }) => { selectedOrgIndex = e.selectedIndexes[0]; handleBindOrg() }"
        @cancel="showOrgPicker = false"
      />
    </van-popup>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  justify-content: center;
  background: linear-gradient(180deg, #e8f3ff 0%, #f7f8fa 40%);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.login-logo {
  font-size: 56px;
}

.login-title {
  margin: 8px 0 4px;
  font-size: 22px;
  color: #323233;
}

.login-subtitle {
  margin: 0;
  font-size: 13px;
  color: #969799;
}

.login-submit {
  margin: 24px 16px;
}
</style>
