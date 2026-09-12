<template>
  <el-dialog :model-value="visible" title="发布表单" width="620px" @close="handleClose">
    <el-form label-width="110px">
      <el-form-item label="访问密码">
        <el-input
          v-model="form.password"
          type="password"
          show-password
          placeholder="留空表示无需密码（已设置时不填写则保持不变）"
        />
      </el-form-item>
      <el-form-item label="有效期至">
        <el-date-picker
          v-model="form.expireAt"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          placeholder="留空表示永不过期"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="提交上限">
        <el-input-number v-model="form.submitLimit" :min="1" placeholder="留空表示不限制" style="width: 100%" />
      </el-form-item>
      <el-form-item label="成功提示语">
        <el-input v-model="form.successMessage" placeholder="提交成功" maxlength="200" />
      </el-form-item>
      <el-form-item label="跳转链接">
        <el-input v-model="form.redirectUrl" placeholder="提交成功后跳转的 URL（可空）" />
      </el-form-item>
    </el-form>

    <template v-if="publishInfo">
      <el-divider />
      <div class="form-publish-link">
        <span class="form-publish-link__label">表单链接</span>
        <el-input :model-value="publicUrl" readonly>
          <template #append>
            <el-button @click="copyLink">
              <el-icon><CopyDocument /></el-icon>
            </el-button>
          </template>
        </el-input>
        <div class="form-publish-link__meta">
          <el-tag size="small" :type="publishInfo.status === 'enabled' ? 'success' : 'info'">
            {{ publishInfo.status === 'enabled' ? '收集中' : '已停用' }}
          </el-tag>
          <span v-if="publishInfo.submitLimit">已提交 {{ publishInfo.submitCount }} / {{ publishInfo.submitLimit }}</span>
          <span v-else>已提交 {{ publishInfo.submitCount }}</span>
          <el-button
            link
            size="small"
            :type="publishInfo.status === 'enabled' ? 'danger' : 'primary'"
            @click="toggleStatus"
          >
            {{ publishInfo.status === 'enabled' ? '停止收集' : '恢复收集' }}
          </el-button>
        </div>
      </div>
    </template>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
      <el-button type="primary" :loading="saving" @click="handlePublish">{{ publishInfo ? '更新发布' : '发布' }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { CopyDocument } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { publishForm, getFormPublish, updateFormPublishStatus, type FormPublishInfo } from '@/api/modules/bitable'

const props = defineProps<{
  visible: boolean
  tableId: number | null
  viewId: number | null
}>()

const emit = defineEmits<{ close: [] }>()

const saving = ref(false)
const publishInfo = ref<FormPublishInfo | null>(null)
const form = reactive({
  password: '',
  expireAt: '',
  submitLimit: undefined as number | undefined,
  successMessage: '',
  redirectUrl: '',
})

watch(
  () => props.visible,
  async (v) => {
    if (!v || !props.viewId) return
    form.password = ''
    form.expireAt = ''
    form.submitLimit = undefined
    form.successMessage = ''
    form.redirectUrl = ''
    publishInfo.value = await getFormPublish(props.viewId).catch(() => null)
    if (publishInfo.value) {
      form.expireAt = publishInfo.value.expireAt ?? ''
      form.submitLimit = publishInfo.value.submitLimit ?? undefined
      form.successMessage = publishInfo.value.successMessage ?? ''
      form.redirectUrl = publishInfo.value.redirectUrl ?? ''
    }
  },
  { immediate: true },
)

const publicUrl = computed(() => {
  if (!publishInfo.value) return ''
  return `${window.location.origin}/public/bitable/form/${publishInfo.value.token}`
})

async function handlePublish() {
  if (!props.tableId || !props.viewId) return
  saving.value = true
  try {
    publishInfo.value = await publishForm(props.tableId, props.viewId, {
      expireAt: form.expireAt || null,
      submitLimit: form.submitLimit ?? null,
      password: form.password || undefined,
      successMessage: form.successMessage || undefined,
      redirectUrl: form.redirectUrl || undefined,
    })
    ElMessage.success('发布成功')
  } catch (e: any) {
    ElMessage.error(e?.message || '发布失败')
  } finally {
    saving.value = false
  }
}

async function toggleStatus() {
  if (!props.viewId || !publishInfo.value) return
  const enabled = publishInfo.value.status !== 'enabled'
  await updateFormPublishStatus(props.viewId, enabled)
  publishInfo.value.status = enabled ? 'enabled' : 'disabled'
  ElMessage.success(enabled ? '已恢复收集' : '已停止收集')
}

async function copyLink() {
  if (!publicUrl.value) return
  await navigator.clipboard.writeText(publicUrl.value)
  ElMessage.success('链接已复制')
}

function handleClose() {
  emit('close')
}
</script>

<style scoped>
.form-publish-link__label {
  display: block;
  font-size: 13px;
  color: var(--color-text-secondary, #64748b);
  margin-bottom: 6px;
}
.form-publish-link__meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--color-text-secondary, #64748b);
}
</style>
