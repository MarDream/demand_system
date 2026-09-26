<template>
  <div class="profile-page">
    <div class="page-header">
      <h2 class="page-title">个人设置</h2>
      <p class="page-desc">仅支持修改邮箱和手机号，其余信息由管理员维护，如需变更请联系管理员。</p>
    </div>

    <!-- ============ 外观设置 ============ -->
    <el-card shadow="never" class="profile-card appearance-card">
      <template #header>
        <div class="card-title-row">
          <span class="card-title">外观</span>
          <span class="card-title-sub">对系统全局生效，自动保存</span>
        </div>
      </template>

      <!-- 实时预览：项目首页总体概要缩略 -->
      <div class="appearance-preview" :class="[`preview--${appearanceMode === 'auto' ? resolvedAutoMode : appearanceMode}`, `preview--radius-${appearanceRadius}`]">
        <div class="preview-chip">永久有效</div>
        <div class="preview-window">
          <!-- 欢迎区 -->
          <div class="pv-header">
            <div class="pv-welcome">
              <div class="pv-welcome__title">
                {{ previewGreeting }}，<span class="pv-welcome__name">{{ userStore.userInfo?.realName || '用户' }}</span>
              </div>
              <div class="pv-welcome__desc">今日{{ previewDate }}，{{ previewWeekDay }}，祝工作顺利 🚀</div>
            </div>
            <div class="pv-actions">
              <span class="pv-btn pv-btn--primary">新建需求</span>
              <span class="pv-btn">我的待办</span>
            </div>
          </div>
          <!-- 统计卡片 -->
          <div class="pv-stats">
            <div v-for="s in previewStatCards" :key="s.label" class="pv-stat">
              <span class="pv-stat__icon" :style="{ background: `linear-gradient(135deg, ${s.color}, ${s.colorLight})` }">
                <el-icon :size="13"><component :is="s.icon" /></el-icon>
              </span>
              <div class="pv-stat__info">
                <span class="pv-stat__value">{{ s.value }}</span>
                <span class="pv-stat__label">{{ s.label }}</span>
              </div>
            </div>
          </div>
          <!-- 图表双栏 -->
          <div class="pv-panels">
            <div class="pv-panel">
              <div class="pv-panel__title"><span class="pv-panel__dot"></span>需求状态分布</div>
              <div class="pv-panel__body">
                <span class="pv-donut">
                  <span class="pv-donut__hole">
                    <b>{{ previewTotal }}</b>
                    <i>需求总数</i>
                  </span>
                </span>
                <div class="pv-legend">
                  <div v-for="l in previewLegend" :key="l.name" class="pv-legend__item">
                    <span class="pv-legend__color" :style="{ background: l.color }"></span>
                    <span class="pv-legend__name">{{ l.name }}</span>
                    <span class="pv-legend__value">{{ l.value }}</span>
                  </div>
                </div>
              </div>
            </div>
            <div class="pv-panel">
              <div class="pv-panel__title"><span class="pv-panel__dot pv-panel__dot--accent"></span>流程处理概览</div>
              <div class="pv-panel__body">
                <span class="pv-ring"><span class="pv-ring__rate">86%</span></span>
                <div class="pv-rows">
                  <div v-for="r in previewFlowRows" :key="r.label" class="pv-row">
                    <span class="pv-row__dot" :style="{ background: r.color }"></span>
                    <span class="pv-row__label">{{ r.label }}</span>
                    <span class="pv-row__value" :style="{ color: r.color }">{{ r.value }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 全部主题 -->
      <div class="appearance-section-title">全部主题</div>
      <div class="theme-grid">
        <div
          v-for="t in themeCards"
          :key="t.key"
          class="theme-item"
          :class="{ 'is-active': appearanceMode === t.key }"
          @click="setMode(t.key)"
        >
          <div class="theme-thumb" :class="`theme-thumb--${t.key}`">
            <template v-if="t.key !== 'auto'">
              <span class="theme-thumb__bar"></span>
              <span class="theme-thumb__side" :class="{ light: t.key === 'light' }"></span>
              <span class="theme-thumb__block"></span>
            </template>
            <template v-else>
              <span class="theme-thumb__half theme-thumb__half--light"></span>
              <span class="theme-thumb__half theme-thumb__half--dark"></span>
              <span class="theme-thumb__auto-icon">A</span>
            </template>
          </div>
          <div class="theme-name">{{ t.name }}</div>
        </div>
      </div>

      <!-- 个性化：主题色 + 圆角 -->
      <div class="appearance-section-title">个性化</div>
      <el-row :gutter="24">
        <el-col :span="12">
          <div class="appearance-sub-title">主题色</div>
          <div class="swatch-row">
            <div
              v-for="c in PRIMARY_SWATCHES"
              :key="c.value"
              class="swatch"
              :class="{ 'is-active': appearancePrimary === c.value }"
              :style="{ background: c.value }"
              :title="c.name"
              @click="appearancePrimary = c.value"
            >
              <el-icon v-if="appearancePrimary === c.value" :size="14" color="#fff"><Check /></el-icon>
            </div>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="appearance-sub-title">圆角风格</div>
          <div class="radius-row">
            <div
              v-for="r in RADIUS_LEVELS"
              :key="r.value"
              class="radius-option"
              :class="{ 'is-active': appearanceRadius === r.value }"
              @click="appearanceRadius = r.value"
            >
              <span class="radius-demo" :class="`radius-demo--${r.value}`"></span>
              <span class="radius-name">{{ r.name }}</span>
              <span class="radius-desc">{{ r.desc }}</span>
            </div>
          </div>
        </el-col>
      </el-row>

      <!-- 侧边栏风格：六种高级质感方案 + 经典默认 -->
      <div class="appearance-sub-title sidebar-style-title">侧边栏风格</div>
      <div class="sidebar-style-row">
        <div
          v-for="s in SIDEBAR_STYLES"
          :key="s.value"
          class="sidebar-style-option"
          :class="{ 'is-active': appearanceSidebar === s.value }"
          @click="appearanceSidebar = s.value"
        >
          <span class="sidebar-style-preview" :class="`sidebar-style-preview--${s.value}`">
            <i class="ssp-page"></i>
            <i class="ssp-bar">
              <i class="ssp-logo"></i>
              <i class="ssp-line ssp-line--1"></i>
              <i class="ssp-line ssp-line--2 is-active"></i>
              <i class="ssp-line ssp-line--3"></i>
            </i>
            <i class="ssp-panel">
              <i class="ssp-panel-title"></i>
              <i class="ssp-panel-line"></i>
              <i class="ssp-panel-line short"></i>
            </i>
          </span>
          <span class="sidebar-style-name">{{ s.name }}</span>
          <span class="sidebar-style-desc">{{ s.desc }}</span>
        </div>
      </div>
    </el-card>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never" class="profile-card">
          <template #header><span class="card-title">基本信息（只读）</span></template>
          <div v-loading="loading" class="profile-base">
            <div class="profile-avatar-block">
              <el-avatar :size="72" :src="avatarUrl || undefined" class="profile-avatar">
                {{ (userStore.userInfo?.realName || '?').slice(0, 1) }}
              </el-avatar>
              <el-button link type="primary" @click="avatarDialogVisible = true">修改头像</el-button>
            </div>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="姓名">{{ userStore.userInfo?.realName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="账号名">{{ userStore.userInfo?.username || '-' }}</el-descriptions-item>
              <el-descriptions-item label="所属组织">{{ userStore.userInfo?.orgName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="角色">{{ roleText }}</el-descriptions-item>
              <el-descriptions-item label="工号">{{ userStore.userInfo?.jobNumber || '-' }}</el-descriptions-item>
            </el-descriptions>
          </div>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="never" class="profile-card">
          <template #header><span class="card-title">联系方式（可修改）</span></template>
          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-width="90px"
            class="profile-form"
            v-loading="loading"
          >
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" placeholder="请输入邮箱" clearable />
            </el-form-item>
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="form.phone" placeholder="请输入手机号" clearable />
            </el-form-item>
            <el-form-item>
              <el-button @click="resetForm">重置</el-button>
              <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="profile-card password-card">
      <template #header><span class="card-title">安全设置（修改密码）</span></template>
      <el-form
        ref="pwdFormRef"
        :model="pwdForm"
        :rules="pwdRules"
        label-width="110px"
        class="profile-form"
      >
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入当前密码" clearable />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="6-20位" clearable />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="changingPwd" @click="handleChangePassword">修改密码</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 头像编辑弹窗：预设选择 / 上传裁剪 -->
    <AvatarEditorDialog
      :visible="avatarDialogVisible"
      :saving="avatarSaving"
      :current-avatar="userStore.userInfo?.avatar || null"
      @close="avatarDialogVisible = false"
      @confirm="handleAvatarConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { Check, Document, Loading, CircleCheck, Warning } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/modules/user'
import { updateProfile, changePassword } from '@/api/modules/auth'
import { uploadFile } from '@/api/modules/file'
import { resolveAvatarUrl } from '@/utils/presetAvatars'
import { resolveErrorMessage } from '@/utils/error'
import AvatarEditorDialog from './components/AvatarEditorDialog.vue'
import {
  useAppearance,
  applyAppearance,
  PRIMARY_SWATCHES,
  RADIUS_LEVELS,
  SIDEBAR_STYLES,
  type ThemeMode,
} from '@/composables/useAppearance'

// ===== 外观设置 =====
const appearance = useAppearance()
// 模板顶层解包（模板内非顶层 ref 不会自动 unwrap，直接比较会出类型问题）
const appearanceMode = appearance.mode
const appearancePrimary = appearance.primary
const appearanceRadius = appearance.radius
const appearanceSidebar = appearance.sidebar
const themeCards: Array<{ key: ThemeMode; name: string }> = [
  { key: 'light', name: '浅色' },
  { key: 'dark', name: '深色' },
  { key: 'auto', name: '跟随系统' },
]

const resolvedAutoMode = computed<'light' | 'dark'>(() =>
  window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
)

function setMode(mode: ThemeMode) {
  appearanceMode.value = mode
  // auto 档位下立即按系统主题应用一次，保证预览与全局即时联动
  applyAppearance()
}

// ===== 首页概要预览（静态示意数据，与首页仪表盘同构） =====
const previewGreeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const previewDate = computed(() => {
  const now = new Date()
  return `${now.getMonth() + 1}月${now.getDate()}日`
})

const previewWeekDay = computed(() => {
  const days = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
  return days[new Date().getDay()]
})

const previewStatCards = [
  { label: '总需求数', value: 128, icon: Document, color: '#0369A1', colorLight: '#0284C7' },
  { label: '进行中需求', value: 32, icon: Loading, color: '#D97706', colorLight: '#F59E0B' },
  { label: '已完成', value: 44, icon: CircleCheck, color: '#059669', colorLight: '#10B981' },
  { label: '已逾期', value: 4, icon: Warning, color: '#DC2626', colorLight: '#EF4444' },
]

const previewTotal = 128

const previewLegend = [
  { name: '待处理', value: 52, color: 'var(--color-primary)' },
  { name: '进行中', value: 32, color: '#F59E0B' },
  { name: '已完成', value: 44, color: '#10B981' },
]

const previewFlowRows = [
  { label: '待办流程', value: 6, color: 'var(--color-primary)' },
  { label: '已办流程', value: 42, color: '#059669' },
  { label: '我发起的', value: 18, color: '#D97706' },
  { label: '抄送我的', value: 9, color: 'var(--color-accent)' },
]

const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  email: '',
  phone: '',
})

const rules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  phone: [
    { pattern: /^[0-9+\-\s]{3,20}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
}

const roleText = computed(() => {
  const names = userStore.userInfo?.allRoleNames?.length
    ? userStore.userInfo.allRoleNames
    : (userStore.userInfo?.roleNames || [])
  return names.length ? names.join('、') : '-'
})

// ==================== 头像设置 ====================
const avatarDialogVisible = ref(false)
const avatarSaving = ref(false)

/** 当前头像可渲染地址（preset: 前缀转 data URL；空回退姓名首字） */
const avatarUrl = computed(() => resolveAvatarUrl(userStore.userInfo?.avatar))

/**
 * 头像确认：
 * - preset：直接把 preset:xxx 写入 profile
 * - crop：先上传裁剪 Blob 拿 URL，再写入 profile
 */
async function handleAvatarConfirm(payload: { kind: 'preset'; value: string } | { kind: 'crop'; blob: Blob }) {
  avatarSaving.value = true
  try {
    let avatarValue: string
    if (payload.kind === 'preset') {
      avatarValue = payload.value
    } else {
      const file = new File([payload.blob], 'avatar.png', { type: 'image/png' })
      const res = await uploadFile(file) as any
      const url: string = res?.url || (typeof res === 'string' ? res : '')
      if (!url) {
        ElMessage.error('头像上传失败')
        return
      }
      avatarValue = url.startsWith('http') || url.startsWith('/') ? url : `/${url}`
    }
    await updateProfile({
      email: form.email.trim(),
      phone: form.phone?.trim() || undefined,
      avatar: avatarValue,
    })
    ElMessage.success('头像已更新')
    avatarDialogVisible.value = false
    await userStore.getUserInfo()
    fillForm()
  } catch (error) {
    // 错误提示由 request 拦截器统一弹出
  } finally {
    avatarSaving.value = false
  }
}

function fillForm() {
  form.email = userStore.userInfo?.email || ''
  form.phone = userStore.userInfo?.phone || ''
}

function resetForm() {
  fillForm()
  formRef.value?.clearValidate()
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await updateProfile({ email: form.email.trim(), phone: form.phone?.trim() || undefined })
    ElMessage.success('个人信息已更新')
    await userStore.getUserInfo()
    fillForm()
  } catch (error) {
    // 错误提示由 request 拦截器统一弹出，这里不再重复 toast
  } finally {
    saving.value = false
  }
}

// 修改密码
const pwdFormRef = ref<FormInstance>()
const changingPwd = ref(false)
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度必须在6-20个字符之间', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value && value === pwdForm.oldPassword) callback(new Error('新密码不能与旧密码相同'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value && value !== pwdForm.newPassword) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

function resetPwdForm() {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  pwdFormRef.value?.clearValidate()
}

async function handleChangePassword() {
  const valid = await pwdFormRef.value?.validate().catch(() => false)
  if (!valid) return
  changingPwd.value = true
  try {
    await changePassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    ElMessage.success('密码已修改，下次登录请使用新密码')
    resetPwdForm()
  } catch (error) {
    // 错误提示由 request 拦截器统一弹出，这里不再重复 toast
  } finally {
    changingPwd.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    if (!userStore.userInfo) {
      await userStore.getUserInfo()
    }
    fillForm()
  } catch (error) {
    // 错误提示由 request 拦截器统一弹出
  } finally {
    loading.value = false
  }
})
</script>

<style scoped lang="scss">
.profile-page {
  padding: 16px 20px;
}

.page-header {
  margin-bottom: 16px;
}

.page-title {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 600;
}

.page-desc {
  margin: 0;
  font-size: 13px;
  color: var(--color-muted-text);
}

.profile-card {
  border-radius: 12px;
}

.card-title {
  font-size: 14px;
  font-weight: 600;
}

.profile-form {
  max-width: 420px;
  padding-top: 8px;
}

.password-card {
  margin-top: 16px;
}

// ============ 头像区块 ============
.profile-base {
  display: flex;
  gap: 18px;
  align-items: flex-start;
}

.profile-avatar-block {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.profile-avatar {
  background: var(--color-primary, var(--color-primary));
  color: #fff;
  font-size: 26px;
  font-weight: 600;
}

.profile-base :deep(.el-descriptions) {
  flex: 1;
}

// ============ 外观设置 ============
.appearance-card {
  margin-bottom: 16px;
}

.card-title-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.card-title-sub {
  font-size: 12px;
  font-weight: 400;
  color: var(--color-muted-text);
}

// ----- 预览窗口（首页总体概要缩略） -----
.appearance-preview {
  position: relative;
  border-radius: var(--app-radius-card);
  padding: 28px 20px 24px;
  overflow: hidden;
  border: 1px solid var(--color-border);
  background: var(--color-background);
  transition: background 0.25s, border-color 0.25s;

  // 预览内部配色变量（随主题档位切换，不依赖全局 class）
  --pv-page: #EEF2F7;
  --pv-surface: #fff;
  --pv-border: var(--color-border);
  --pv-border-strong: #CBD5E1;
  --pv-text: #0F172A;
  --pv-text-secondary: #475569;
  --pv-muted: #94A3B8;

  .preview-chip {
    position: absolute;
    top: 12px;
    right: 12px;
    font-size: 11px;
    padding: 2px 10px;
    border-radius: 999px;
    background: var(--color-surface-alt);
    color: var(--color-muted-text);
  }

  .preview-window {
    max-width: 560px;
    margin: 0 auto;
    background: var(--pv-page);
    border: 1px solid var(--pv-border);
    padding: 14px;
    transition: border-radius 0.25s, background 0.25s, border-color 0.25s;
  }

  // 欢迎区
  .pv-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 10px;
  }

  .pv-welcome {
    min-width: 0;
  }

  .pv-welcome__title {
    font-size: 14px;
    font-weight: 600;
    color: var(--pv-text);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .pv-welcome__name {
    background: var(--gradient-primary, linear-gradient(135deg, var(--color-primary), var(--color-accent)));
    -webkit-background-clip: text;
    background-clip: text;
    -webkit-text-fill-color: transparent;
  }

  .pv-welcome__desc {
    margin-top: 3px;
    font-size: 11px;
    color: var(--pv-muted);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .pv-actions {
    display: flex;
    gap: 6px;
    flex-shrink: 0;
  }

  .pv-btn {
    font-size: 11px;
    line-height: 1;
    padding: 6px 10px;
    white-space: nowrap;
    border: 1px solid var(--pv-border-strong);
    color: var(--pv-text-secondary);
    background: transparent;
    transition: border-radius 0.25s;

    &.pv-btn--primary {
      background: var(--color-primary, var(--color-primary));
      border-color: transparent;
      color: #fff;
    }
  }

  // 统计卡片
  .pv-stats {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 8px;
    margin-bottom: 8px;
  }

  .pv-stat {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
    padding: 8px 10px;
    background: var(--pv-surface);
    border: 1px solid var(--pv-border);
    transition: border-radius 0.25s;
  }

  .pv-stat__icon {
    flex-shrink: 0;
    width: 26px;
    height: 26px;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
  }

  .pv-stat__info {
    display: flex;
    flex-direction: column;
    min-width: 0;
  }

  .pv-stat__value {
    font-size: 15px;
    font-weight: 700;
    line-height: 1.2;
    color: var(--pv-text);
    font-variant-numeric: tabular-nums;
  }

  .pv-stat__label {
    font-size: 10px;
    color: var(--pv-muted);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  // 图表双栏
  .pv-panels {
    display: grid;
    grid-template-columns: 1.25fr 1fr;
    gap: 8px;
  }

  .pv-panel {
    min-width: 0;
    padding: 10px;
    background: var(--pv-surface);
    border: 1px solid var(--pv-border);
    transition: border-radius 0.25s;
  }

  .pv-panel__title {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 11px;
    font-weight: 600;
    color: var(--pv-text-secondary);
    margin-bottom: 10px;
  }

  .pv-panel__dot {
    flex-shrink: 0;
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--color-primary, var(--color-primary));
    box-shadow: 0 0 0 2px var(--color-primary-bg, rgba(37, 99, 235, 0.15));

    &.pv-panel__dot--accent {
      background: var(--color-accent);
      box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.18);
    }
  }

  .pv-panel__body {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .pv-donut {
    position: relative;
    flex-shrink: 0;
    width: 76px;
    height: 76px;
    border-radius: 50%;
    // 与首页状态饼图同源的三段配色，首段跟随主题色
    background: conic-gradient(var(--color-primary, var(--color-primary)) 0 40%, #F59E0B 40% 65%, #10B981 65% 100%);
  }

  .pv-donut__hole {
    position: absolute;
    inset: 10px;
    border-radius: 50%;
    background: var(--pv-surface);
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;

    b {
      font-size: 14px;
      line-height: 1.2;
      color: var(--pv-text);
      font-variant-numeric: tabular-nums;
    }

    i {
      font-style: normal;
      font-size: 9px;
      color: var(--pv-muted);
    }
  }

  .pv-legend {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 7px;
  }

  .pv-legend__item {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 11px;
  }

  .pv-legend__color {
    flex-shrink: 0;
    width: 8px;
    height: 8px;
    border-radius: 2px;
  }

  .pv-legend__name {
    flex: 1;
    min-width: 0;
    color: var(--pv-text-secondary);
  }

  .pv-legend__value {
    color: var(--pv-text);
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }

  .pv-ring {
    position: relative;
    flex-shrink: 0;
    width: 58px;
    height: 58px;
    border-radius: 50%;
    background: conic-gradient(var(--color-primary, var(--color-primary)) 0 86%, var(--pv-border) 86% 100%);
    display: flex;
    align-items: center;
    justify-content: center;

    &::before {
      content: '';
      position: absolute;
      inset: 6px;
      border-radius: 50%;
      background: var(--pv-surface);
    }
  }

  .pv-ring__rate {
    position: relative;
    font-size: 12px;
    font-weight: 700;
    color: var(--pv-text);
    font-variant-numeric: tabular-nums;
  }

  .pv-rows {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 5px;
  }

  .pv-row {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 11px;
  }

  .pv-row__dot {
    flex-shrink: 0;
    width: 6px;
    height: 6px;
    border-radius: 50%;
  }

  .pv-row__label {
    flex: 1;
    min-width: 0;
    color: var(--pv-text-secondary);
    white-space: nowrap;
  }

  .pv-row__value {
    font-weight: 700;
    font-variant-numeric: tabular-nums;
  }

  // 圆角档位在预览中的映射
  &.preview--radius-round {
    .preview-window { border-radius: 18px; }
    .pv-stat, .pv-panel, .pv-btn { border-radius: 12px; }
    .pv-stat__icon { border-radius: 9px; }
  }

  &.preview--radius-soft {
    .preview-window { border-radius: 14px; }
    .pv-stat, .pv-panel, .pv-btn { border-radius: 8px; }
    .pv-stat__icon { border-radius: 7px; }
  }

  &.preview--radius-none {
    .preview-window { border-radius: 2px; }
    .pv-stat, .pv-panel, .pv-btn { border-radius: 2px; }
    .pv-stat__icon { border-radius: 2px; }
  }

  // 预览独立深浅色（不依赖全局 class，切换主题卡时即时可见）
  &.preview--dark {
    background: #0B1220;
    border-color: #243050;

    --pv-page: #0B1220;
    --pv-surface: #111A2C;
    --pv-border: #243050;
    --pv-border-strong: #324268;
    --pv-text: #F1F5F9;
    --pv-text-secondary: #94A3B8;
    --pv-muted: #64748B;
  }

  &.preview--light {
    background: var(--color-background);
    border-color: var(--color-border);
  }
}

// ----- 主题网格 -----
.appearance-section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 18px 0 12px;
}

.appearance-sub-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-secondary);
  margin-bottom: 10px;
}

.theme-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 14px;
}

.theme-item {
  cursor: pointer;
  text-align: center;
  user-select: none;

  .theme-thumb {
    position: relative;
    height: 64px;
    border-radius: 10px;
    border: 2px solid var(--color-border);
    overflow: hidden;
    transition: border-color 0.2s, transform 0.2s, box-shadow 0.2s;
    background: var(--color-surface-alt);
  }

  &:hover .theme-thumb {
    transform: translateY(-2px);
    border-color: var(--color-border-hover);
  }

  &.is-active .theme-thumb {
    border-color: var(--color-primary);
    box-shadow: 0 0 0 3px var(--color-primary-bg);
  }

  .theme-name {
    margin-top: 6px;
    font-size: 12px;
    color: var(--color-text-secondary);

    .is-active & {
      color: var(--color-primary);
      font-weight: 600;
    }
  }
}

.theme-thumb--light {
  background: var(--color-surface-alt);

  .theme-thumb__bar {
    position: absolute;
    top: 6px;
    left: 6px;
    right: 6px;
    height: 6px;
    border-radius: 3px;
    background: var(--color-surface);
  }

  .theme-thumb__side {
    position: absolute;
    left: 6px;
    top: 16px;
    bottom: 6px;
    width: 14px;
    border-radius: 3px;
    background: #0F172A;

    &.light { background: var(--color-surface); }
  }

  .theme-thumb__block {
    position: absolute;
    left: 24px;
    top: 16px;
    width: 40%;
    height: 14px;
    border-radius: 3px;
    background: var(--color-primary);
  }
}

.theme-thumb--dark {
  background: #0B1220;

  .theme-thumb__bar { background: #1A2439; }
  .theme-thumb__side { background: #111A2C; }
  .theme-thumb__block { background: var(--color-primary); }
}

.theme-thumb--auto {
  .theme-thumb__half {
    position: absolute;
    top: 0;
    bottom: 0;
    width: 50%;

    &--light { left: 0; background: var(--color-surface-alt); }
    &--dark { right: 0; background: #0B1220; }
  }

  .theme-thumb__auto-icon {
    position: absolute;
    inset: 0;
    margin: auto;
    width: 24px;
    height: 24px;
    line-height: 24px;
    text-align: center;
    border-radius: 50%;
    background: var(--color-primary);
    color: #fff;
    font-size: 12px;
    font-weight: 700;
  }
}

// ----- 主题色色板 -----
.swatch-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.swatch {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid transparent;
  box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.08);
  transition: transform 0.15s, box-shadow 0.15s;

  &:hover {
    transform: scale(1.1);
  }

  &.is-active {
    box-shadow: 0 0 0 2px var(--color-surface), 0 0 0 4px var(--color-primary);
  }
}

// ----- 圆角档位 -----
.radius-row {
  display: flex;
  gap: 10px;
}

.radius-option {
  flex: 1;
  cursor: pointer;
  border: 1px solid var(--color-border);
  border-radius: var(--app-radius-input);
  padding: 10px 8px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  transition: border-color 0.2s, box-shadow 0.2s;

  &:hover {
    border-color: var(--color-border-hover);
  }

  &.is-active {
    border-color: var(--color-primary);
    box-shadow: 0 0 0 3px var(--color-primary-bg);

    .radius-name {
      color: var(--color-primary);
      font-weight: 600;
    }
  }

  .radius-demo {
    display: block;
    width: 28px;
    height: 28px;
    border: 2px solid var(--color-text-secondary);
    background: var(--color-surface-alt);

    &--none { border-radius: 2px; }
    &--soft { border-radius: 8px; }
    &--round { border-radius: 14px; }
  }

  .radius-name {
    font-size: 12px;
    color: var(--color-text-primary);
  }

  .radius-desc {
    font-size: 11px;
    color: var(--color-muted-text);
  }
}

// ----- 侧边栏风格选择器 -----
.sidebar-style-title {
  margin-top: 18px;
}

.sidebar-style-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.sidebar-style-option {
  cursor: pointer;
  border: 1px solid var(--color-border);
  border-radius: var(--app-radius-input);
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 3px;
  transition: border-color 0.2s, box-shadow 0.2s;

  &:hover {
    border-color: var(--color-border-hover);
  }

  &.is-active {
    border-color: var(--color-primary);
    box-shadow: 0 0 0 3px var(--color-primary-bg);

    .sidebar-style-name {
      color: var(--color-primary);
      font-weight: 600;
    }
  }

  .sidebar-style-name {
    font-size: 13px;
    color: var(--color-text-primary);
    line-height: 1.4;
  }

  .sidebar-style-desc {
    font-size: 11px;
    color: var(--color-muted-text);
    line-height: 1.4;
    min-height: 15px;
  }
}

// 迷你预览：一个页面底 + 侧边栏条，按风格变体绘制
.sidebar-style-preview {
  position: relative;
  display: block;
  height: 56px;
  border-radius: 8px;
  overflow: hidden;
  background: var(--color-surface-alt);
  border: 1px solid var(--color-border);
  margin-bottom: 4px;

  .ssp-page {
    position: absolute;
    inset: 0;
    background:
      radial-gradient(60% 80% at 20% 20%, var(--color-accent-tint), transparent 70%),
      var(--color-background);
  }

  // 侧边栏条（默认形态）
  .ssp-bar {
    position: absolute;
    top: 0;
    left: 0;
    bottom: 0;
    width: 22px;
    background: var(--color-sidebar-bg);
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
    padding-top: 5px;
  }

  .ssp-logo {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--color-primary);
    margin-bottom: 3px;
    flex-shrink: 0;
  }

  .ssp-line {
    width: 12px;
    height: 3px;
    border-radius: 2px;
    background: rgba(148, 163, 184, 0.45);
    flex-shrink: 0;

    &.is-active {
      background: var(--color-primary);
    }
  }

  // 二级面板（双层图标轨用）
  .ssp-panel {
    display: none;
    position: absolute;
    top: 6px;
    left: 26px;
    width: 30px;
    height: 44px;
    border-radius: 5px;
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    box-shadow: 0 3px 8px rgba(15, 23, 42, 0.12);
    flex-direction: column;
    gap: 3px;
    padding: 5px 4px;
  }

  .ssp-panel-title {
    width: 60%;
    height: 3px;
    border-radius: 2px;
    background: var(--color-text-secondary);
    opacity: 0.6;
  }

  .ssp-panel-line {
    width: 100%;
    height: 3px;
    border-radius: 2px;
    background: var(--color-border);

    &.short {
      width: 70%;
    }
  }

  .ssp-user {
    display: none;
  }

  /* ① 悬浮岛：条体脱边 + 圆角 + 阴影 */
  &--floating .ssp-bar {
    top: 6px;
    left: 6px;
    bottom: 6px;
    width: 18px;
    border-radius: 6px;
    box-shadow: 0 3px 8px rgba(15, 23, 42, 0.18);
  }

  /* ② 深色重底：整页浅、唯一深条，激活线高亮 */
  &--dark .ssp-page {
    background: var(--color-background);
  }

  &--dark .ssp-bar {
    background: #0B111E;
  }

  &--dark .ssp-line {
    background: rgba(255, 255, 255, 0.22);

    &.is-active {
      background: var(--color-primary);
      width: 14px;
      border-radius: 2px;
    }
  }

  /* ③ 磨砂玻璃：底纹透出 + 半透明条 */
  &--glass .ssp-page {
    background:
      radial-gradient(70% 90% at 15% 20%, var(--color-accent-tint), transparent 75%),
      radial-gradient(80% 90% at 85% 85%, var(--color-accent-glow), transparent 75%),
      var(--color-background);
  }

  &--glass .ssp-bar {
    background: color-mix(in srgb, var(--color-surface) 55%, transparent);
    backdrop-filter: blur(3px);
    border-right: 1px solid rgba(255, 255, 255, 0.65);
  }

  /* ④ 双层图标轨：细轨 + 右侧二级面板 */
  &--dual-rail .ssp-bar {
    width: 12px;
  }

  &--dual-rail .ssp-logo {
    width: 6px;
    height: 6px;
  }

  &--dual-rail .ssp-line {
    width: 6px;
  }

  &--dual-rail .ssp-panel {
    display: flex;
  }

  /* ⑤ 折叠展开：窄条 + 展开虚线示意 */
  &--collapsible .ssp-bar {
    width: 12px;
  }

  &--collapsible .ssp-logo {
    width: 6px;
    height: 6px;
  }

  &--collapsible .ssp-line {
    width: 6px;
  }

  &--collapsible .ssp-panel {
    display: flex;
    left: 14px;
    border-style: dashed;
    box-shadow: none;
    opacity: 0.75;
  }

  /* ⑥ 分组留白：组名点 + 底部用户圆点 */
  &--grouped .ssp-bar {
    width: 26px;
    align-items: flex-start;
    padding-left: 5px;
  }

  &--grouped .ssp-line {
    margin: 2px 0;
  }

  &--grouped .ssp-user {
    display: block;
    position: absolute;
    left: 5px;
    bottom: 5px;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--color-primary);
    opacity: 0.75;
  }
}
</style>
