<template>
  <div class="public-form-page">
    <div class="public-form-page__card">
      <template v-if="error">
        <el-result icon="warning" :title="error" />
      </template>
      <template v-else-if="schema">
        <h1 class="public-form-page__title">{{ schema.title }}</h1>
        <p v-if="schema.description" class="public-form-page__desc">{{ schema.description }}</p>

        <!-- 密码门 -->
        <div v-if="schema.hasPassword && !unlocked" class="public-form-page__gate">
          <el-input v-model="password" type="password" placeholder="请输入访问密码" @keyup.enter="unlock" />
          <el-button type="primary" @click="unlock">解锁表单</el-button>
        </div>

        <template v-else>
          <el-form label-position="top">
            <el-form-item v-for="field in schema.fields" :key="field.id" :required="field.required">
              <template #label>{{ field.name }}</template>
              <!-- 单选 -->
              <el-select
                v-if="field.fieldType === 'single_select' || field.fieldType === 'process'"
                v-model="model[field.id]"
                clearable
                :placeholder="field.placeholder || '请选择'"
                style="width: 100%"
              >
                <el-option v-for="opt in field.options || []" :key="opt.label" :label="opt.label" :value="opt.label" />
              </el-select>
              <!-- 多选 -->
              <el-select
                v-else-if="field.fieldType === 'multi_select'"
                v-model="multiModel[field.id]"
                multiple
                :placeholder="field.placeholder || '请选择（可多选）'"
                style="width: 100%"
              >
                <el-option v-for="opt in field.options || []" :key="opt.label" :label="opt.label" :value="opt.label" />
              </el-select>
              <!-- 数字 -->
              <el-input-number
                v-else-if="field.fieldType === 'number' || field.fieldType === 'currency' || field.fieldType === 'progress' || field.fieldType === 'rating'"
                v-model="numberModel[field.id]"
                style="width: 100%"
              />
              <!-- 日期 -->
              <el-date-picker
                v-else-if="field.fieldType === 'date'"
                v-model="dateModel[field.id]"
                type="date"
                value-format="YYYY-MM-DD"
                :placeholder="field.placeholder || '选择日期'"
                style="width: 100%"
              />
              <!-- 复选框 -->
              <el-checkbox v-else-if="field.fieldType === 'check' || field.fieldType === 'checkbox'" v-model="checkModel[field.id]">
                {{ field.placeholder || '勾选' }}
              </el-checkbox>
              <!-- 文本 -->
              <el-input v-else v-model="model[field.id]" :placeholder="field.placeholder || `请输入${field.name}`" />
            </el-form-item>
          </el-form>

          <el-button type="primary" size="large" style="width: 100%" :loading="submitting" @click="submit">
            提交
          </el-button>
        </template>

        <!-- 提交成功 -->
        <el-result v-if="submitted" icon="success" :title="successMessage">
          <template #extra>
            <el-button v-if="redirectUrl" type="primary" @click="goRedirect">前往</el-button>
          </template>
        </el-result>
      </template>
      <div v-else class="public-form-page__loading">
        <el-icon class="is-loading" :size="28"><Loading /></el-icon>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { getPublicFormSchema, submitPublicForm, type PublicFormSchema } from '@/api/modules/bitable'

const route = useRoute()
const token = String(route.params.token || '')

const schema = ref<PublicFormSchema | null>(null)
const error = ref('')
const unlocked = ref(false)
const password = ref('')
const submitting = ref(false)
const submitted = ref(false)
const successMessage = ref('')
const redirectUrl = ref('')

const model = reactive<Record<number, string>>({})
const multiModel = reactive<Record<number, string[]>>({})
const numberModel = reactive<Record<number, number | null>>({})
const dateModel = reactive<Record<number, string | null>>({})
const checkModel = reactive<Record<number, boolean>>({})

const successText = computed(() => successMessage.value || '提交成功，感谢填写！')

onMounted(async () => {
  try {
    schema.value = await getPublicFormSchema(token)
  } catch (e: any) {
    error.value = e?.message || '表单不存在或已停止收集'
  }
})

function unlock() {
  if (!password.value) {
    ElMessage.warning('请输入访问密码')
    return
  }
  unlocked.value = true
}

function validateRequired(): boolean {
  if (!schema.value) return false
  for (const field of schema.value.fields) {
    if (!field.required) continue
    const type = field.fieldType
    let empty = false
    if (type === 'check' || type === 'checkbox') {
      empty = !checkModel[field.id]
    } else if (type === 'multi_select') {
      empty = !multiModel[field.id]?.length
    } else if (type === 'number' || type === 'currency' || type === 'progress' || type === 'rating') {
      empty = numberModel[field.id] == null
    } else if (type === 'date') {
      empty = !dateModel[field.id]
    } else {
      empty = !model[field.id]?.trim()
    }
    if (empty) {
      ElMessage.warning(`请填写必填项：${field.name}`)
      return false
    }
  }
  return true
}

async function submit() {
  if (!validateRequired()) return
  submitting.value = true
  try {
    const values: Record<number, Record<string, unknown>> = {}
    for (const field of schema.value?.fields || []) {
      const id = field.id
      const type = field.fieldType
      if (type === 'multi_select') {
        if (multiModel[id]?.length) values[id] = { valueJson: multiModel[id] }
      } else if (type === 'number' || type === 'currency' || type === 'progress' || type === 'rating') {
        if (numberModel[id] != null) values[id] = { valueNumber: numberModel[id] }
      } else if (type === 'date') {
        if (dateModel[id]) values[id] = { valueDate: dateModel[id] }
      } else if (type === 'check' || type === 'checkbox') {
        values[id] = { valueText: checkModel[id] ? 'true' : 'false' }
      } else {
        if (model[id]?.trim()) values[id] = { valueText: model[id].trim() }
      }
    }
    const result = await submitPublicForm(token, values, schema.value?.hasPassword ? password.value : undefined)
    successMessage.value = (result?.successMessage as string) || '提交成功，感谢填写！'
    redirectUrl.value = (result?.redirectUrl as string) || ''
    submitted.value = true
  } catch (e: any) {
    ElMessage.error(e?.message || '提交失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

function goRedirect() {
  if (redirectUrl.value) {
    window.location.href = redirectUrl.value
  }
}
</script>

<style scoped lang="scss">
.public-form-page {
  min-height: 100vh;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 48px 16px;
  background: var(--color-background, var(--color-background));
}

.public-form-page__card {
  width: 100%;
  max-width: 560px;
  background: var(--color-surface, var(--color-surface));
  border-radius: 12px;
  padding: 28px 32px;
  box-shadow: 0 8px 30px rgba(15, 23, 42, 0.08);
}

.public-form-page__title {
  font-size: 22px;
  margin: 0 0 6px;
}

.public-form-page__desc {
  font-size: 13px;
  color: var(--color-text-secondary, var(--color-muted-text));
  margin: 0 0 20px;
}

.public-form-page__gate {
  display: flex;
  gap: 10px;
}

.public-form-page__loading {
  display: flex;
  justify-content: center;
  padding: 40px 0;
}
</style>
