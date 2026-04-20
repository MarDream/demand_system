<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-header">
        <el-icon class="login-logo"><Monitor /></el-icon>
        <h1 class="login-title">需求管理系统</h1>
      </div>
      <el-form
        ref="formRef"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入用户名"
            size="large"
            :prefix-icon="User"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            show-password
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-form-item>
          <div class="login-options">
            <el-checkbox v-model="loginForm.remember">记住我</el-checkbox>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="login-btn"
            @click="handleLogin"
          >
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { Monitor, User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/modules/user'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const loginForm = reactive({
  username: '',
  password: '',
  remember: false,
})

const loginRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 3, message: '密码长度不能少于3个字符', trigger: 'blur' },
  ],
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.login(loginForm.username, loginForm.password)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (err: any) {
    ElMessage.error(err?.message || '登录失败，请检查用户名和密码')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-container {
  width: 100%;
  height: 100vh;
  background: linear-gradient(135deg, #409EFF 0%, #304156 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-card {
  width: 420px;
  background: #fff;
  border-radius: 8px;
  padding: 40px 36px 30px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.login-logo {
  font-size: 48px;
  color: #409EFF;
  margin-bottom: 12px;
}

.login-title {
  font-size: 24px;
  color: #303133;
  font-weight: 600;
  margin: 0;
}

.login-form {
  .el-form-item {
    margin-bottom: 22px;
  }
}

.login-options {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.login-btn {
  width: 100%;
}
</style>
