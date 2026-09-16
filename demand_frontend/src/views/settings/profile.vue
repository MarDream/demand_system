<template>
  <div class="profile-page">
    <div class="page-header">
      <h2 class="page-title">个人设置</h2>
      <p class="page-desc">仅支持修改邮箱和手机号，其余信息由管理员维护，如需变更请联系管理员。</p>
    </div>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never" class="profile-card">
          <template #header><span class="card-title">基本信息（只读）</span></template>
          <el-descriptions :column="1" border v-loading="loading">
            <el-descriptions-item label="姓名">{{ userStore.userInfo?.realName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="账号名">{{ userStore.userInfo?.username || '-' }}</el-descriptions-item>
            <el-descriptions-item label="所属组织">{{ userStore.userInfo?.orgName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="角色">{{ roleText }}</el-descriptions-item>
            <el-descriptions-item label="工号">{{ userStore.userInfo?.jobNumber || '-' }}</el-descriptions-item>
          </el-descriptions>
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
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/modules/user'
import { updateProfile, changePassword } from '@/api/modules/auth'
import { resolveErrorMessage } from '@/utils/error'

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
  color: #6b7280;
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
</style>
