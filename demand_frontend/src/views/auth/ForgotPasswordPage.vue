<template>
  <div class="forgot-password-container">
    <div class="forgot-password-card">
      <div class="forgot-password-header">
        <h2>找回密码</h2>
        <p>输入用户名或邮箱，我们将发送重置链接</p>
      </div>

      <el-form
        ref="forgotFormRef"
        :model="forgotForm"
        :rules="forgotRules"
        class="forgot-form"
      >
        <el-form-item prop="identifier">
          <el-input
            v-model="forgotForm.identifier"
            placeholder="请输入用户名或邮箱"
            clearable
            size="large"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            style="width: 100%"
            size="large"
            @click="handleSendReset"
          >
            发送重置链接
          </el-button>
        </el-form-item>

        <div class="forgot-footer">
          <router-link to="/login" class="link">返回登录</router-link>
          <el-divider direction="vertical" />
          <router-link to="/register" class="link">注册账号</router-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { requestPasswordReset } from '@/api/modules/auth'

const router = useRouter()
const forgotFormRef = ref<FormInstance>()
const loading = ref(false)

const forgotForm = reactive({
  identifier: ''
})

const forgotRules = reactive<FormRules>({
  identifier: [
    { required: true, message: '请输入用户名或邮箱', trigger: 'blur' }
  ]
})

const handleSendReset = async () => {
  if (!forgotFormRef.value) return

  await forgotFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const identifier = forgotForm.identifier.replace(/\s/g, '')
        const isEmail = identifier.includes('@')

        const payload = isEmail
          ? { email: identifier }
          : { username: identifier }

        const response = await requestPasswordReset(payload)

        if (response.data.code === 200) {
          ElMessage.success('重置链接已发送到您的邮箱（如果该账号存在）')
          setTimeout(() => {
            router.push('/login')
          }, 2000)
        } else {
          ElMessage.error(response.data.message || '发送失败')
        }
      } catch (error: any) {
        const msg = error.response?.data?.message || '发送失败，请稍后重试'
        ElMessage.error(msg)
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped>
.forgot-password-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.forgot-password-card {
  width: 420px;
  padding: 40px;
  background: white;
  border-radius: 10px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.1);
}

.forgot-password-header {
  text-align: center;
  margin-bottom: 30px;
}

.forgot-password-header h2 {
  margin: 0 0 10px 0;
  font-size: 28px;
  color: #333;
}

.forgot-password-header p {
  margin: 0;
  color: #666;
  font-size: 14px;
}

.forgot-form {
  margin-top: 20px;
}

.forgot-footer {
  text-align: center;
  margin-top: 20px;
  display: flex;
  justify-content: center;
  align-items: center;
}

.link {
  color: #667eea;
  text-decoration: none;
  font-size: 14px;
}

.link:hover {
  text-decoration: underline;
}
</style>
