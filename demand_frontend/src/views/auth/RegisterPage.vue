<template>
  <div class="register-container">
    <div class="register-card">
      <div class="register-header">
        <h2>用户注册</h2>
        <p>创建您的需求管理账号</p>
      </div>

      <el-form
        ref="registerFormRef"
        :model="registerForm"
        :rules="registerRules"
        class="register-form"
        label-width="80px"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="registerForm.username"
            placeholder="请输入用户名"
            clearable
          />
        </el-form-item>

        <el-form-item label="真实姓名" prop="realName">
          <el-input
            v-model="registerForm.realName"
            placeholder="请输入真实姓名"
            clearable
          />
        </el-form-item>

        <el-form-item label="邮箱" prop="email">
          <el-input
            v-model="registerForm.email"
            placeholder="请输入邮箱地址"
            clearable
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="registerForm.password"
            type="password"
            placeholder="请输入密码"
            show-password
            clearable
          />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="registerForm.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            show-password
            clearable
          />
        </el-form-item>

        <el-form-item label="所属区域" prop="regionId">
          <el-select
            v-model="registerForm.regionId"
            placeholder="请选择所属区域（可选）"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="region in regions"
              :key="region.id"
              :label="region.name"
              :value="region.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="所属部门" prop="departmentId">
          <el-select
            v-model="registerForm.departmentId"
            placeholder="请选择所属部门（可选）"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="dept in departments"
              :key="dept.id"
              :label="dept.name"
              :value="dept.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="岗位" prop="positionId">
          <el-select
            v-model="registerForm.positionId"
            placeholder="请选择岗位（可选）"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="position in positions"
              :key="position.id"
              :label="position.name"
              :value="position.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            style="width: 100%"
            @click="handleRegister"
          >
            注册
          </el-button>
        </el-form-item>

        <div class="register-footer">
          <router-link to="/login" class="link">已有账号？立即登录</router-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { register } from '@/api/modules/auth'
import { getOrgTree } from '@/api/modules/organization'
import { getPositionList } from '@/api/modules/user'
import type { OrgNode } from '@/types/user'

const router = useRouter()
const registerFormRef = ref<FormInstance>()
const loading = ref(false)

const registerForm = reactive({
  username: '',
  realName: '',
  email: '',
  password: '',
  confirmPassword: '',
  regionId: undefined as number | undefined,
  departmentId: undefined as number | undefined,
  positionId: undefined as number | undefined
})

const regions = ref<OrgNode[]>([])
const departments = ref<OrgNode[]>([])
const positions = ref<Array<{ id: number; name: string }>>([])

const validatePassword = (rule: any, value: any, callback: any) => {
  if (value === '') {
    callback(new Error('请输入密码'))
  } else if (value.length < 6) {
    callback(new Error('密码长度不能少于6位'))
  } else {
    callback()
  }
}

const validateConfirmPassword = (rule: any, value: any, callback: any) => {
  if (value === '') {
    callback(new Error('请再次输入密码'))
  } else if (value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const registerRules = reactive<FormRules>({
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在3到20个字符', trigger: 'blur' }
  ],
  realName: [
    { required: true, message: '请输入真实姓名', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ],
  password: [
    { required: true, validator: validatePassword, trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, validator: validateConfirmPassword, trigger: 'blur' }
  ]
})

const flattenOrgTree = (list: OrgNode[]): OrgNode[] => {
  const result: OrgNode[] = []
  for (const item of list) {
    result.push({ ...item, children: undefined })
    if (item.children) {
      result.push(...flattenOrgTree(item.children))
    }
  }
  return result
}

const loadRegionsAndDepartments = async () => {
  try {
    const response = await getOrgTree()
    if (response.data.code === 200) {
      const flat = flattenOrgTree(response.data.data || [])
      regions.value = flat.filter(n => n.orgType === 'region' || n.orgType === 'company' || n.orgType === 'bureau')
      departments.value = flat.filter(n => n.orgType === 'department')
    }
  } catch (error) {
    console.error('加载组织架构失败:', error)
  }
}

const loadPositions = async () => {
  try {
    const response = await getPositionList()
    if (response.data.code === 200) {
      positions.value = response.data.data
    }
  } catch (error) {
    console.error('加载岗位列表失败:', error)
  }
}

const handleRegister = async () => {
  if (!registerFormRef.value) return

  await registerFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const response = await register({
          username: registerForm.username,
          realName: registerForm.realName,
          email: registerForm.email,
          password: registerForm.password,
          regionId: registerForm.regionId,
          departmentId: registerForm.departmentId,
          positionId: registerForm.positionId
        })

        if (response.data.code === 200) {
          ElMessage.success('注册成功，请登录')
          router.push('/login')
        } else {
          ElMessage.error(response.data.message || '注册失败')
        }
      } catch (error: any) {
        ElMessage.error(error.response?.data?.message || '注册失败，请稍后重试')
      } finally {
        loading.value = false
      }
    }
  })
}

onMounted(() => {
  loadRegionsAndDepartments()
  loadPositions()
})
</script>

<style scoped>
.register-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.register-card {
  width: 500px;
  padding: 40px;
  background: white;
  border-radius: 10px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.1);
}

.register-header {
  text-align: center;
  margin-bottom: 30px;
}

.register-header h2 {
  margin: 0 0 10px 0;
  font-size: 28px;
  color: #333;
}

.register-header p {
  margin: 0;
  color: #666;
  font-size: 14px;
}

.register-form {
  margin-top: 20px;
}

.register-footer {
  text-align: center;
  margin-top: 20px;
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
