<template>
  <el-dialog :model-value="visible" title="分享视图" width="560px" @close="handleClose">
    <el-form label-width="110px">
      <el-form-item label="有效期至">
        <el-date-picker
          v-model="form.expireAt"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          placeholder="留空表示永不过期"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="允许下载">
        <el-switch v-model="form.allowDownload" />
      </el-form-item>
    </el-form>

    <template v-if="shareInfo">
      <el-divider />
      <div class="view-share-link">
        <span class="view-share-link__label">分享链接（只读）</span>
        <el-input :model-value="publicUrl" readonly>
          <template #append>
            <el-button @click="copyLink">
              <el-icon><CopyDocument /></el-icon>
            </el-button>
          </template>
        </el-input>
        <div class="view-share-link__meta">
          <el-tag size="small" :type="shareInfo.status === 'enabled' ? 'success' : 'info'">
            {{ shareInfo.status === 'enabled' ? '分享中' : '已停用' }}
          </el-tag>
          <el-button
            link
            size="small"
            :type="shareInfo.status === 'enabled' ? 'danger' : 'primary'"
            @click="toggleStatus"
          >
            {{ shareInfo.status === 'enabled' ? '停用分享' : '恢复分享' }}
          </el-button>
        </div>
      </div>
    </template>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
      <el-button type="primary" :loading="saving" @click="handleShare">{{ shareInfo ? '更新分享' : '创建分享' }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { CopyDocument } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { shareView, getViewShare, updateViewShareStatus, type ViewShareInfo } from '@/api/modules/bitable'

const props = defineProps<{
  visible: boolean
  viewId: number | null
}>()

const emit = defineEmits<{ close: [] }>()

const saving = ref(false)
const shareInfo = ref<ViewShareInfo | null>(null)
const form = reactive({
  expireAt: '',
  allowDownload: false,
})

watch(
  () => props.visible,
  async (v) => {
    if (!v || !props.viewId) return
    form.expireAt = ''
    form.allowDownload = false
    shareInfo.value = await getViewShare(props.viewId).catch(() => null)
    if (shareInfo.value) {
      form.expireAt = shareInfo.value.expireAt ?? ''
      form.allowDownload = !!shareInfo.value.allowDownload
    }
  },
  { immediate: true },
)

const publicUrl = computed(() => {
  if (!shareInfo.value) return ''
  return `${window.location.origin}/public/bitable/view/${shareInfo.value.token}`
})

async function handleShare() {
  if (!props.viewId) return
  saving.value = true
  try {
    shareInfo.value = await shareView(props.viewId, {
      expireAt: form.expireAt || null,
      allowDownload: form.allowDownload,
    })
    ElMessage.success('分享链接已生成')
  } catch (e: any) {
    ElMessage.error(e?.message || '分享失败')
  } finally {
    saving.value = false
  }
}

async function toggleStatus() {
  if (!props.viewId || !shareInfo.value) return
  const enabled = shareInfo.value.status !== 'enabled'
  await updateViewShareStatus(props.viewId, enabled)
  shareInfo.value.status = enabled ? 'enabled' : 'disabled'
  ElMessage.success(enabled ? '已恢复分享' : '已停用分享')
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
.view-share-link__label {
  display: block;
  font-size: 13px;
  color: var(--color-text-secondary, var(--color-muted-text));
  margin-bottom: 6px;
}
.view-share-link__meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--color-text-secondary, var(--color-muted-text));
}
</style>
