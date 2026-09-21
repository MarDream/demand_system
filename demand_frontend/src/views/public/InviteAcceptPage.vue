<template>
  <div class="invite-page">
    <div class="invite-card" v-loading="loading">
      <!-- 加载失败 / 链接不可用 -->
      <template v-if="!loading && info && !info.valid">
        <div class="invite-card__icon is-invalid">
          <el-icon><CircleClose /></el-icon>
        </div>
        <h1 class="invite-card__title">邀请链接不可用</h1>
        <p class="invite-card__desc">{{ info.invalidReason || '该邀请链接已失效' }}</p>
        <p class="invite-card__foot">如需加入，请联系邀请你的管理员重新发送。</p>
      </template>

      <!-- 加载失败（网络/不存在） -->
      <template v-else-if="!loading && !info">
        <div class="invite-card__icon is-invalid">
          <el-icon><WarningFilled /></el-icon>
        </div>
        <h1 class="invite-card__title">邀请链接无效</h1>
        <p class="invite-card__desc">{{ errorText || '未找到对应的邀请，请确认链接是否完整。' }}</p>
      </template>

      <!-- 提交成功 -->
      <template v-else-if="submitted">
        <div class="invite-card__icon is-success">
          <el-icon><CircleCheckFilled /></el-icon>
        </div>
        <h1 class="invite-card__title">申请已提交</h1>
        <p class="invite-card__desc">
          你的资料已发送给管理员，审批通过后系统会把初始密码发送到
          <strong>{{ form.email }}</strong>，请留意查收。
        </p>
      </template>

      <!-- 填写表单 -->
      <template v-else>
        <div class="invite-card__icon">
          <el-icon><Promotion /></el-icon>
        </div>
        <h1 class="invite-card__title">
          {{ info?.inviterName || '管理员' }} 邀请你加入
          <span class="invite-card__org">{{ info?.orgName || '本组织' }}</span>
        </h1>
        <p class="invite-card__desc">
          填写以下信息提交申请，管理员审批通过后即可登录使用。
          <template v-if="info?.expiresAt">
            <br />该邀请将于 {{ formatDate(info.expiresAt) }} 过期。
          </template>
        </p>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="invite-form">
          <el-form-item label="姓名" prop="name">
            <el-input v-model="form.name" placeholder="请输入真实姓名" maxlength="64" />
          </el-form-item>
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="form.phone" placeholder="用于生成初始密码，请填写正确" maxlength="11" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="form.email" placeholder="初始密码将发送到该邮箱" maxlength="128" />
          </el-form-item>
          <el-form-item label="留言">
            <el-input
              v-model="form.remark"
              type="textarea"
              :rows="3"
              maxlength="255"
              show-word-limit
              placeholder="选填，可简单介绍自己"
            />
          </el-form-item>
        </el-form>

        <el-button type="primary" size="large" class="invite-submit" :loading="submitting" @click="handleSubmit">
          提交申请
        </el-button>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { CircleCheckFilled, CircleClose, Promotion, WarningFilled } from '@element-plus/icons-vue'
import * as invitationApi from '@/api/modules/invitation'
import type { InviteInfo } from '@/types/invitation'
import { formatDate } from '@/utils/format'
import { resolveErrorMessage } from '@/utils/error'

const route = useRoute()

const loading = ref(true)
const submitting = ref(false)
const submitted = ref(false)
const info = ref<InviteInfo | null>(null)
const errorText = ref('')
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  phone: '',
  email: '',
  remark: '',
})

const rules: FormRules = {
  name: [{ required: true, message: '请填写姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请填写手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请填写邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
}

const code = String(route.params.code || '')

onMounted(async () => {
  try {
    const result = await invitationApi.getInviteInfo(code)
    info.value = result
    // 批量邀请里管理员已经填过姓名，这里预填减少一次输入
    if (result.targetName) {
      form.name = result.targetName
    }
  } catch (error) {
    errorText.value = resolveErrorMessage(error, '未找到对应的邀请，请确认链接是否完整。')
  } finally {
    loading.value = false
  }
})

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await invitationApi.submitInviteApplication(code, {
      name: form.name.trim(),
      phone: form.phone.trim(),
      email: form.email.trim(),
      remark: form.remark.trim() || undefined,
    })
    submitted.value = true
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '提交失败，请稍后重试'))
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
.invite-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: var(--spacing-xl);
  background: linear-gradient(160deg, #eef2ff 0%, var(--color-background) 45%, #eef7ff 100%);
}

.invite-card {
  width: 100%;
  max-width: 460px;
  padding: var(--spacing-2xl) var(--spacing-xl);
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  box-shadow: 0 12px 40px rgba(15, 23, 42, 0.1);
  text-align: center;
}

.invite-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  margin-bottom: var(--spacing-md);
  border-radius: var(--radius-full);
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-size: 26px;

  &.is-success {
    background: var(--color-success-light);
    color: var(--color-success);
  }

  &.is-invalid {
    background: var(--color-danger-light);
    color: var(--color-danger);
  }
}

.invite-card__title {
  margin: 0 0 var(--spacing-sm);
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-semibold);
  line-height: 1.4;
  color: var(--color-text-primary);
}

.invite-card__org {
  color: var(--color-primary);
}

.invite-card__desc {
  margin: 0 0 var(--spacing-lg);
  font-size: var(--font-size-sm);
  line-height: 1.7;
  color: var(--color-text-secondary);

  strong {
    color: var(--color-text-primary);
  }
}

.invite-card__foot {
  margin: 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-placeholder);
}

.invite-form {
  text-align: left;

  :deep(.el-form-item) {
    margin-bottom: var(--spacing-md);
  }
}

.invite-submit {
  width: 100%;
  margin-top: var(--spacing-xs);
}
</style>
