<template>
  <el-dialog
    v-model="visible"
    title="添加/申请记录"
    width="1040px"
    class="application-records-dialog"
    :close-on-click-modal="false"
    @open="handleOpen"
  >
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <!-- ─────────── 邀请记录 ─────────── -->
      <el-tab-pane name="invitation">
        <template #label>
          <span>邀请记录<em v-if="pendingInviteCount" class="tab-badge">{{ pendingInviteCount }}</em></span>
        </template>

        <div class="records-toolbar">
          <el-input
            v-model="invitationQuery.keyword"
            placeholder="搜索姓名、手机号或邀请码"
            clearable
            class="records-search"
            @keyup.enter="loadInvitations(1)"
            @clear="loadInvitations(1)"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="invitationQuery.status" placeholder="全部状态" clearable class="records-filter" @change="loadInvitations(1)">
            <el-option label="待接受" value="pending" />
            <el-option label="已接受" value="accepted" />
            <el-option label="已过期" value="expired" />
            <el-option label="已撤回" value="revoked" />
          </el-select>
          <el-select v-model="invitationQuery.inviteType" placeholder="全部方式" clearable class="records-filter" @change="loadInvitations(1)">
            <el-option label="链接邀请" value="link" />
            <el-option label="批量邀请" value="batch" />
          </el-select>
        </div>

        <el-table :data="invitations" v-loading="invitationLoading" border height="360">
          <el-table-column label="被邀请人" min-width="150">
            <template #default="{ row }">
              <div class="record-person">
                <span class="record-person__name">{{ row.targetName || '（未指定）' }}</span>
                <span v-if="row.target" class="record-person__sub">{{ row.target }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="方式" width="96" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.inviteType === 'batch' ? 'info' : 'primary'" effect="plain">
                {{ row.inviteTypeText }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="所属组织" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.orgName || '-' }}</template>
          </el-table-column>
          <el-table-column label="角色" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.roleNames.length ? row.roleNames.join('、') : '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="INVITATION_STATUS_TAG[row.status] || 'info'">{{ row.statusText }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="有效期" width="130" align="center">
            <template #default="{ row }">
              {{ row.expiresAt ? formatDateTime(row.expiresAt) : '永久有效' }}
            </template>
          </el-table-column>
          <el-table-column label="邀请人" width="104" align="center">
            <template #default="{ row }">{{ row.inviterName || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160" align="center" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'pending'"
                link
                type="primary"
                size="small"
                @click="copyText(invitationApi.buildInviteUrl(row.inviteCode))"
              >
                复制链接
              </el-button>
              <el-button
                v-if="row.status === 'pending'"
                link
                type="primary"
                size="small"
                @click="handleRevoke(row)"
              >
                撤回
              </el-button>
              <el-button
                v-if="row.status === 'expired' || row.status === 'revoked'"
                link
                type="primary"
                size="small"
                @click="handleResend(row)"
              >
                重发
              </el-button>
              <span v-if="row.status === 'accepted'" class="record-muted">已完成</span>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无邀请记录" :image-size="80" />
          </template>
        </el-table>

        <div class="records-pagination">
          <AppPagination
            v-model:page-num="invitationQuery.pageNum"
            v-model:page-size="invitationQuery.pageSize"
            :total="invitationTotal"
            :page-sizes="[10, 20, 50]"
            @change="loadInvitations()"
          />
        </div>
      </el-tab-pane>

      <!-- ─────────── 申请记录 ─────────── -->
      <el-tab-pane name="join">
        <template #label>
          <span>申请记录<em v-if="pendingJoinCount" class="tab-badge">{{ pendingJoinCount }}</em></span>
        </template>

        <div class="records-toolbar">
          <el-input
            v-model="joinQuery.keyword"
            placeholder="搜索姓名、手机号或邮箱"
            clearable
            class="records-search"
            @keyup.enter="loadJoinRequests(1)"
            @clear="loadJoinRequests(1)"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="joinQuery.status" placeholder="全部状态" clearable class="records-filter" @change="loadJoinRequests(1)">
            <el-option label="待处理" value="pending" />
            <el-option label="已通过" value="approved" />
            <el-option label="已拒绝" value="rejected" />
          </el-select>
          <el-select v-model="joinQuery.source" placeholder="全部来源" clearable class="records-filter" @change="loadJoinRequests(1)">
            <el-option label="邀请链接" value="link" />
            <el-option label="批量邀请" value="batch" />
            <el-option label="管理员添加" value="admin" />
            <el-option label="自助申请" value="self" />
          </el-select>
        </div>

        <el-table :data="joinRequests" v-loading="joinLoading" border height="360">
          <el-table-column label="申请人" min-width="140">
            <template #default="{ row }">
              <div class="record-person">
                <span class="record-person__name">{{ row.applicantName }}</span>
                <span class="record-person__sub">{{ row.applicantPhone || row.applicantEmail || '-' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="来源" width="92" align="center">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ row.sourceText }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="申请加入" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.orgName || '待指定' }}</template>
          </el-table-column>
          <el-table-column label="角色" min-width="100" show-overflow-tooltip>
            <template #default="{ row }">{{ row.roleNames.length ? row.roleNames.join('、') : '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="84" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="JOIN_STATUS_TAG[row.status] || 'info'">{{ row.statusText }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="申请时间" width="140" align="center">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="审批信息" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <template v-if="row.reviewedAt">
                {{ row.reviewerName || '-' }} · {{ formatDateTime(row.reviewedAt) }}
                <span v-if="row.reviewRemark">（{{ row.reviewRemark }}）</span>
              </template>
              <span v-else class="record-muted">-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" align="center" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === 'pending'">
                <el-button link type="primary" size="small" @click="openApprove(row)">通过</el-button>
                <el-button link type="danger" size="small" @click="handleReject(row)">拒绝</el-button>
              </template>
              <span v-else class="record-muted">{{ row.username || '已处理' }}</span>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无申请记录" :image-size="80" />
          </template>
        </el-table>

        <div class="records-pagination">
          <AppPagination
            v-model:page-num="joinQuery.pageNum"
            v-model:page-size="joinQuery.pageSize"
            :total="joinTotal"
            :page-sizes="[10, 20, 50]"
            @change="loadJoinRequests()"
          />
        </div>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>

  <!-- 审批通过：确认组织与角色 -->
  <el-dialog
    v-model="approveVisible"
    title="通过申请"
    width="520px"
    append-to-body
    class="approve-request-dialog"
  >
    <p class="approve-tip">
      通过后系统会自动为 <strong>{{ approveTarget?.applicantName }}</strong> 创建账号，
      初始密码为「账号名 + 手机号后 3 位」并发送到其邮箱。
    </p>
    <el-form label-width="96px">
      <el-form-item label="所属组织" required>
        <el-tree-select
          v-model="approveForm.orgId"
          :data="orgTree"
          :props="{ label: 'name', value: 'id', children: 'children' }"
          placeholder="请选择组织"
          check-strictly
          clearable
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="角色">
        <RoleSelect
          v-model="approveForm.roleIds"
          multiple
          :exclude-codes="['SUPER_ADMIN']"
          placeholder="请选择角色（可多选）"
        />
      </el-form-item>
      <el-form-item label="审批意见">
        <el-input v-model="approveForm.reviewRemark" maxlength="255" placeholder="选填" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="approveVisible = false">取消</el-button>
      <el-button type="primary" :loading="approving" @click="handleApprove">确定通过</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import AppPagination from '@/components/common/AppPagination.vue'
import RoleSelect from '@/components/common/RoleSelect.vue'
import * as invitationApi from '@/api/modules/invitation'
import type { Invitation, JoinRequest, InvitationQuery, JoinRequestQuery } from '@/types/invitation'
import type { OrgNode } from '@/types/user'
import { formatDate as formatDateTime } from '@/utils/format'
import { resolveErrorMessage } from '@/utils/error'

const props = withDefaults(defineProps<{
  modelValue: boolean
  orgTree?: OrgNode[]
}>(), {
  orgTree: () => [],
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  /** 审批通过会新增成员，父级需要刷新列表 */
  changed: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})

const INVITATION_STATUS_TAG: Record<string, 'primary' | 'success' | 'info' | 'warning' | 'danger'> = {
  pending: 'warning',
  accepted: 'success',
  expired: 'info',
  revoked: 'danger',
}

const JOIN_STATUS_TAG: Record<string, 'primary' | 'success' | 'info' | 'warning' | 'danger'> = {
  pending: 'warning',
  approved: 'success',
  rejected: 'danger',
}

const activeTab = ref<'invitation' | 'join'>('invitation')

const invitations = ref<Invitation[]>([])
const invitationTotal = ref(0)
const invitationLoading = ref(false)
const pendingInviteCount = ref(0)
const invitationQuery = reactive<InvitationQuery>({
  inviteType: '',
  status: '',
  keyword: '',
  pageNum: 1,
  pageSize: 10,
})

const joinRequests = ref<JoinRequest[]>([])
const joinTotal = ref(0)
const joinLoading = ref(false)
const pendingJoinCount = ref(0)
const joinQuery = reactive<JoinRequestQuery>({
  status: '',
  source: '',
  keyword: '',
  pageNum: 1,
  pageSize: 10,
})

const approveVisible = ref(false)
const approving = ref(false)
const approveTarget = ref<JoinRequest | null>(null)
const approveForm = reactive({
  orgId: null as number | null,
  roleIds: [] as number[],
  reviewRemark: '',
})

function handleOpen() {
  activeTab.value = 'invitation'
  loadInvitations(1)
  loadJoinRequests(1)
  loadPendingCounts()
}

function handleTabChange() {
  // 切 tab 时同步一下另一侧角标，保证数字是最新的
  loadPendingCounts()
}

async function loadInvitations(page?: number) {
  if (page) invitationQuery.pageNum = page
  invitationLoading.value = true
  try {
    const result = await invitationApi.getInvitationList(invitationQuery)
    invitations.value = result.list || []
    invitationTotal.value = result.total || 0
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '操作失败，请稍后重试'))
  } finally {
    invitationLoading.value = false
  }
}

async function loadJoinRequests(page?: number) {
  if (page) joinQuery.pageNum = page
  joinLoading.value = true
  try {
    const result = await invitationApi.getJoinRequestList(joinQuery)
    joinRequests.value = result.list || []
    joinTotal.value = result.total || 0
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '操作失败，请稍后重试'))
  } finally {
    joinLoading.value = false
  }
}

/** 角标只关心「还有多少条要处理」，单独用 pageSize=1 拿 total，避免拉全量 */
async function loadPendingCounts() {
  try {
    const [pendingInvites, pendingJoins] = await Promise.all([
      invitationApi.getInvitationList({ inviteType: '', status: 'pending', pageNum: 1, pageSize: 1 }),
      invitationApi.getJoinRequestList({ status: 'pending', source: '', pageNum: 1, pageSize: 1 }),
    ])
    pendingInviteCount.value = pendingInvites.total || 0
    pendingJoinCount.value = pendingJoins.total || 0
  } catch {
    // 角标是锦上添花，拿不到就静默跳过
  }
}

async function copyText(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('链接已复制')
  } catch {
    ElMessage.warning('复制失败，请手动复制')
  }
}

async function handleRevoke(row: Invitation) {
  try {
    await ElMessageBox.confirm('撤回后该链接立即失效，确定撤回吗？', '撤回邀请', {
      confirmButtonText: '确定撤回',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await invitationApi.revokeInvitation(row.id)
    ElMessage.success('已撤回')
    loadInvitations()
    loadPendingCounts()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '操作失败，请稍后重试'))
  }
}

async function handleResend(row: Invitation) {
  try {
    await ElMessageBox.confirm('重发会换一张新的邀请码并重置有效期，原链接立即失效。确定重发吗？', '重发邀请', {
      confirmButtonText: '确定重发',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    const updated = await invitationApi.resendInvitation(row.id)
    await copyText(invitationApi.buildInviteUrl(updated.inviteCode))
    loadInvitations()
    loadPendingCounts()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '操作失败，请稍后重试'))
  }
}

function openApprove(row: JoinRequest) {
  approveTarget.value = row
  approveForm.orgId = row.orgId ?? null
  approveForm.roleIds = [...(row.roleIds || [])]
  approveForm.reviewRemark = ''
  approveVisible.value = true
}

async function handleApprove() {
  if (!approveTarget.value) return
  if (!approveForm.orgId) {
    ElMessage.warning('请选择所属组织')
    return
  }
  approving.value = true
  try {
    await invitationApi.approveJoinRequest(approveTarget.value.id, {
      orgId: approveForm.orgId,
      roleIds: approveForm.roleIds,
      reviewRemark: approveForm.reviewRemark.trim() || undefined,
    })
    ElMessage.success('已通过，账号创建成功')
    approveVisible.value = false
    loadJoinRequests()
    loadInvitations()
    loadPendingCounts()
    emit('changed')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '操作失败，请稍后重试'))
  } finally {
    approving.value = false
  }
}

async function handleReject(row: JoinRequest) {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt('可填写拒绝原因，对方在申请记录里能看到。', '拒绝申请', {
      confirmButtonText: '确定拒绝',
      cancelButtonText: '取消',
      inputPlaceholder: '选填',
      inputValidator: () => true,
    })
    reason = (result.value || '').trim()
  } catch {
    return
  }
  try {
    await invitationApi.rejectJoinRequest(row.id, { reviewRemark: reason || undefined })
    ElMessage.success('已拒绝')
    loadJoinRequests()
    loadPendingCounts()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '操作失败，请稍后重试'))
  }
}
</script>

<style scoped lang="scss">
.records-toolbar {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-sm);
}

.records-search {
  width: 280px;
}

.records-filter {
  width: 130px;
}

.records-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--spacing-sm);
}

.tab-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  margin-left: 6px;
  padding: 0 5px;
  border-radius: var(--radius-full);
  background: var(--color-danger);
  color: #fff;
  font-size: var(--font-size-2xs);
  font-style: normal;
  line-height: 1;
}

.record-person {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.record-person__name {
  color: var(--color-text-primary);
}

.record-person__sub {
  font-size: var(--font-size-xs);
  color: var(--color-text-placeholder);
}

.record-muted {
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
}

.approve-tip {
  margin: 0 0 var(--spacing-md);
  font-size: var(--font-size-sm);
  line-height: 1.6;
  color: var(--color-text-secondary);

  strong {
    color: var(--color-text-primary);
  }
}
</style>
