<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getOrgTree } from '@/api/modules/user'
import { useUserStore } from '@/stores/modules/user'
import type { OrgNode } from '@/types/user'

const userStore = useUserStore()
const orgTree = ref<OrgNode[]>([])
const selectedOrgId = ref<number | null>(null)
const submitting = ref(false)
const loading = ref(false)

const visible = computed({
  get: () => userStore.needOrgBind,
  set: (val: boolean) => {
    // 强制不让关闭（只能通过绑定成功后 needOrgBind=false 关闭）
    if (!val) userStore.needOrgBind = false
  },
})

async function loadTree() {
  loading.value = true
  try {
    orgTree.value = (await getOrgTree()) as OrgNode[]
  } catch (e) {
    // ignore, request interceptor already toasts
  } finally {
    loading.value = false
  }
}

async function handleConfirm() {
  if (!selectedOrgId.value) {
    ElMessage.warning('请选择组织')
    return
  }
  submitting.value = true
  try {
    await userStore.bindOrg(selectedOrgId.value)
    ElMessage.success('组织绑定成功')
  } catch (e) {
    // 拦截器已弹错
  } finally {
    submitting.value = false
  }
}

onMounted(loadTree)
</script>

<template>
  <el-dialog
    v-model="visible"
    title="首次登录 - 请选择您的组织"
    width="520px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="false"
    :modal="true"
    align-center
    data-testid="org-bind-dialog"
  >
    <el-alert
      type="warning"
      :closable="false"
      title="您当前没有归属任何组织，请选择您所在的组织后才能继续使用系统。"
      style="margin-bottom: 16px"
    />
    <el-form label-width="0">
      <el-form-item>
        <el-tree-select
          v-model="selectedOrgId"
          :data="orgTree"
          :props="{ label: 'name', value: 'id', children: 'children' }"
          placeholder="请选择组织"
          check-strictly
          clearable
          filterable
          style="width: 100%"
          :loading="loading"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="submitting" @click="handleConfirm">
        确认绑定
      </el-button>
    </template>
  </el-dialog>
</template>
