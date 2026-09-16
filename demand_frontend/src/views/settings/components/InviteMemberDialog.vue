<template>
  <el-dialog
    v-model="visible"
    title="邀请成员"
    width="760px"
    class="invite-member-dialog"
    :close-on-click-modal="false"
    @close="resetAll"
  >
    <el-tabs v-model="activeTab" class="invite-tabs">
      <!-- ─────────── 通过链接邀请 ─────────── -->
      <el-tab-pane label="通过链接邀请" name="link">
        <template v-if="!generatedLink">
          <p class="invite-hint">
            生成一条邀请链接发给对方，对方打开后填写姓名、手机号与邮箱提交申请，你审批通过即完成入组。
          </p>
          <el-form label-width="96px" class="invite-form">
            <el-form-item label="所属组织">
              <el-tree-select
                v-model="shared.orgId"
                :data="orgTree"
                :props="{ label: 'name', value: 'id', children: 'children' }"
                placeholder="请选择组织（可留空，审批时再指定）"
                check-strictly
                clearable
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="角色">
              <RoleSelect
                v-model="shared.roleIds"
                multiple
                :exclude-codes="['SUPER_ADMIN']"
                placeholder="请选择角色（可多选，可留空）"
              />
            </el-form-item>
            <el-form-item label="有效期">
              <el-select v-model="expireOption" style="width: 100%">
                <el-option
                  v-for="option in EXPIRE_OPTIONS"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="可用次数">
              <el-input-number v-model="linkForm.maxUses" :min="0" :max="9999" />
              <span class="field-tip">0 表示不限次数</span>
            </el-form-item>
            <el-form-item label="备注">
              <el-input
                v-model="linkForm.remark"
                maxlength="255"
                show-word-limit
                placeholder="选填，仅内部可见"
              />
            </el-form-item>
          </el-form>
        </template>

        <template v-else>
          <div class="invite-result">
            <div class="invite-result__icon">
              <el-icon><Link /></el-icon>
            </div>
            <div class="invite-result__title">邀请链接已生成</div>
            <div class="invite-result__desc">
              {{ generatedLink.inviteTypeText }}
              <template v-if="generatedLink.orgName"> · 加入「{{ generatedLink.orgName }}」</template>
              <template v-if="generatedLink.expiresAt"> · {{ formatDateTime(generatedLink.expiresAt) }} 过期</template>
              <template v-else> · 永久有效</template>
            </div>

            <div class="invite-link">
              <span class="invite-link__text">{{ inviteUrl }}</span>
              <el-button type="primary" :icon="CopyDocument" @click="copyText(inviteUrl)">复制链接</el-button>
            </div>

            <div class="invite-result__actions">
              <el-button :icon="Refresh" @click="regenerate">重新生成</el-button>
              <el-button @click="resetAll">再邀请一位</el-button>
            </div>
          </div>
        </template>
      </el-tab-pane>

      <!-- ─────────── 批量邀请 ─────────── -->
      <el-tab-pane label="批量邀请" name="batch">
        <template v-if="!batchResult">
          <p class="invite-hint">
            逐行填写被邀请人，或点「批量粘贴」一次导入多行。每人会拿到一条专属链接，用过即失效。
          </p>

          <el-form label-width="96px" class="invite-form">
            <el-form-item label="所属组织">
              <el-tree-select
                v-model="shared.orgId"
                :data="orgTree"
                :props="{ label: 'name', value: 'id', children: 'children' }"
                placeholder="请选择组织（可留空，审批时再指定）"
                check-strictly
                clearable
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="角色">
              <RoleSelect
                v-model="shared.roleIds"
                multiple
                :exclude-codes="['SUPER_ADMIN']"
                placeholder="请选择角色（可多选，可留空）"
              />
            </el-form-item>
            <el-form-item label="有效期">
              <el-select v-model="expireOption" style="width: 100%">
                <el-option
                  v-for="option in EXPIRE_OPTIONS"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>
          </el-form>

          <div class="batch-toolbar">
            <span class="batch-toolbar__count">
              已填写 <strong>{{ filledRowCount }}</strong> 位
            </span>
            <div class="batch-toolbar__actions">
              <el-button :icon="Plus" @click="addRow">添加一行</el-button>
              <el-button :icon="DocumentCopy" @click="pasteVisible = true">批量粘贴</el-button>
              <el-button
                :icon="Delete"
                :disabled="batchRows.length <= 1"
                @click="clearRows"
              >
                清空
              </el-button>
            </div>
          </div>

          <div class="batch-table">
            <div class="batch-table__head">
              <span>姓名</span>
              <span>手机号</span>
              <span>邮箱</span>
              <span />
            </div>
            <div class="batch-table__body">
              <div v-for="(row, index) in batchRows" :key="index" class="batch-table__row">
                <el-input v-model="row.name" placeholder="必填" maxlength="64" />
                <el-input v-model="row.phone" placeholder="手机号" maxlength="20" />
                <el-input v-model="row.email" placeholder="邮箱" maxlength="128" />
                <el-button
                  link
                  type="danger"
                  :icon="Delete"
                  :disabled="batchRows.length <= 1"
                  aria-label="删除该行"
                  @click="removeRow(index)"
                />
              </div>
            </div>
          </div>
        </template>

        <template v-else>
          <div class="batch-result">
            <el-alert
              v-if="batchResult.successCount > 0"
              type="success"
              :closable="false"
              show-icon
              :title="`已生成 ${batchResult.successCount} 条邀请`"
              description="把各自的专属链接发给对方即可，对方提交后可在「添加/申请记录」里审批。"
            />
            <el-alert
              v-else
              type="warning"
              :closable="false"
              show-icon
              title="没有任何一条邀请被创建"
              description="请根据下方原因修正后重试。"
            />

            <div v-if="batchResult.skipped.length" class="batch-skipped">
              <div class="batch-skipped__title">已跳过 {{ batchResult.skipped.length }} 行</div>
              <ul class="batch-skipped__list">
                <li v-for="(item, index) in batchResult.skipped" :key="index">
                  <span class="batch-skipped__who">{{ item.name || item.target || '未命名' }}</span>
                  <span class="batch-skipped__reason">{{ item.reason }}</span>
                </li>
              </ul>
            </div>
          </div>
        </template>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <el-button @click="visible = false">{{ batchResult ? '关闭' : '取消' }}</el-button>
      <template v-if="activeTab === 'link' && !generatedLink">
        <el-button type="primary" :loading="submitting" @click="generateLink">生成邀请链接</el-button>
      </template>
      <template v-else-if="activeTab === 'batch' && !batchResult">
        <el-button type="primary" :loading="submitting" @click="submitBatch">提交邀请</el-button>
      </template>
      <template v-else-if="batchResult">
        <el-button type="primary" @click="backToEdit">继续邀请</el-button>
      </template>
    </template>
  </el-dialog>

  <el-dialog
    v-model="pasteVisible"
    title="批量粘贴"
    width="520px"
    append-to-body
    class="invite-paste-dialog"
  >
    <p class="invite-hint">
      每行一位成员，用逗号或空格分隔，格式：<code>姓名,手机号,邮箱</code>。手机号与邮箱至少填一个。
    </p>
    <el-input
      v-model="pasteText"
      type="textarea"
      :rows="10"
      placeholder="张三,13800000000,zhangsan@example.com&#10;李四,13900000000"
    />
    <template #footer>
      <el-button @click="pasteVisible = false">取消</el-button>
      <el-button type="primary" @click="applyPaste">导入</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument, Delete, DocumentCopy, Link, Plus, Refresh } from '@element-plus/icons-vue'
import RoleSelect from '@/components/common/RoleSelect.vue'
import * as invitationApi from '@/api/modules/invitation'
import type { BatchInviteMember, BatchInviteResult, Invitation } from '@/types/invitation'
import type { OrgNode } from '@/types/user'
import { formatDate as formatDateTime } from '@/utils/format'
import { resolveErrorMessage } from '@/utils/error'

const props = withDefaults(defineProps<{
  modelValue: boolean
  orgTree?: OrgNode[]
  defaultOrgId?: number | null
  /** 打开时默认落在哪个 tab，由外层下拉的选项决定 */
  initialTab?: 'link' | 'batch'
}>(), {
  orgTree: () => [],
  defaultOrgId: null,
  initialTab: 'link',
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  /** 成功创建邀请后触发，便于父级刷新数据 */
  invited: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})

/** 有效期下拉：-1 表示永久 */
const EXPIRE_OPTIONS = [
  { label: '7 天', value: 7 },
  { label: '15 天', value: 15 },
  { label: '30 天', value: 30 },
  { label: '永久有效', value: -1 },
] as const

const activeTab = ref<'link' | 'batch'>('link')
const submitting = ref(false)
const expireOption = ref<number>(7)

/** 两个 tab 共用的组织/角色设置 */
const shared = reactive({
  orgId: null as number | null,
  roleIds: [] as number[],
})

const linkForm = reactive({
  maxUses: 0,
  remark: '',
})

const generatedLink = ref<Invitation | null>(null)

const batchRows = ref<BatchInviteMember[]>([createEmptyRow()])
const batchResult = ref<BatchInviteResult | null>(null)

const pasteVisible = ref(false)
const pasteText = ref('')

const inviteUrl = computed(() =>
  generatedLink.value ? invitationApi.buildInviteUrl(generatedLink.value.inviteCode) : '',
)

const filledRowCount = computed(() =>
  batchRows.value.filter(row => (row.name || '').trim() || (row.phone || '').trim() || (row.email || '').trim()).length,
)

function createEmptyRow(): BatchInviteMember {
  return { name: '', phone: '', email: '' }
}

// 打开弹窗时把当前选中组织带进来，省得每次都手动选
watch(() => props.modelValue, (open) => {
  if (!open) return
  activeTab.value = props.initialTab
  shared.orgId = props.defaultOrgId ?? null
})

function addRow() {
  batchRows.value.push(createEmptyRow())
}

function removeRow(index: number) {
  batchRows.value.splice(index, 1)
}

function clearRows() {
  batchRows.value = [createEmptyRow()]
}

/** 把粘贴文本解析成成员行；按逗号/制表符/空白切分，自动识别手机号与邮箱 */
function parsePastedRows(text: string): BatchInviteMember[] {
  return text
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean)
    .map((line) => {
      const parts = line.split(/[,，\t;；]+|\s+/).map(part => part.trim()).filter(Boolean)
      const member = createEmptyRow()
      const leftovers: string[] = []
      for (const part of parts) {
        if (!member.email && part.includes('@')) {
          member.email = part
        } else if (!member.phone && /^1[3-9]\d{9}$/.test(part)) {
          member.phone = part
        } else {
          leftovers.push(part)
        }
      }
      if (leftovers.length) {
        member.name = leftovers.shift() as string
      }
      return member
    })
    .filter(member => member.name || member.phone || member.email)
}

function applyPaste() {
  const rows = parsePastedRows(pasteText.value)
  if (!rows.length) {
    ElMessage.warning('没有解析到有效内容')
    return
  }
  // 首行是空行时直接替换，避免留下一条空白行
  const current = batchRows.value
  const isBlank = current.length === 1
    && !current[0].name && !current[0].phone && !current[0].email
  batchRows.value = isBlank ? rows : [...current, ...rows]
  pasteText.value = ''
  pasteVisible.value = false
  ElMessage.success(`已导入 ${rows.length} 行`)
}

async function copyText(text: string) {
  if (!text) return
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      // 非安全上下文（例如用局域网 IP 访问）没有 clipboard API，退回到临时 textarea
      const helper = document.createElement('textarea')
      helper.value = text
      helper.style.position = 'fixed'
      helper.style.opacity = '0'
      document.body.appendChild(helper)
      helper.select()
      document.execCommand('copy')
      document.body.removeChild(helper)
    }
    ElMessage.success('链接已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选中链接复制')
  }
}

function buildPayload() {
  const neverExpire = expireOption.value === -1
  return {
    orgId: shared.orgId,
    roleIds: shared.roleIds,
    neverExpire,
    expireDays: neverExpire ? undefined : expireOption.value,
  }
}

async function generateLink() {
  submitting.value = true
  try {
    generatedLink.value = await invitationApi.createLinkInvite({
      ...buildPayload(),
      maxUses: linkForm.maxUses || 0,
      remark: linkForm.remark.trim() || undefined,
    })
    emit('invited')
    ElMessage.success('邀请链接已生成')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '邀请操作失败，请稍后重试'))
  } finally {
    submitting.value = false
  }
}

function regenerate() {
  generatedLink.value = null
  void generateLink()
}

async function submitBatch() {
  const members = batchRows.value
    .map(row => ({
      name: (row.name || '').trim(),
      phone: (row.phone || '').trim(),
      email: (row.email || '').trim(),
    }))
    .filter(row => row.name || row.phone || row.email)

  if (!members.length) {
    ElMessage.warning('请至少填写一位被邀请人')
    return
  }

  submitting.value = true
  try {
    const result = await invitationApi.batchInvite({
      ...buildPayload(),
      members,
    })
    batchResult.value = result
    emit('invited')
    if (result.skipped.length === 0) {
      ElMessage.success(`已生成 ${result.successCount} 条邀请`)
    }
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '邀请操作失败，请稍后重试'))
  } finally {
    submitting.value = false
  }
}

function backToEdit() {
  batchResult.value = null
  clearRows()
}

function resetAll() {
  activeTab.value = 'link'
  generatedLink.value = null
  batchResult.value = null
  expireOption.value = 7
  linkForm.maxUses = 0
  linkForm.remark = ''
  shared.orgId = null
  shared.roleIds = []
  clearRows()
  pasteText.value = ''
  pasteVisible.value = false
}
</script>

<style scoped lang="scss">
.invite-hint {
  margin: 0 0 var(--spacing-md);
  font-size: var(--font-size-sm);
  line-height: 1.6;
  color: var(--color-text-secondary);

  code {
    padding: 1px 5px;
    border-radius: var(--radius-xs);
    background: var(--color-surface-alt);
    font-size: var(--font-size-xs);
  }
}

.invite-form {
  :deep(.el-form-item) {
    margin-bottom: var(--spacing-md);
  }
}

.field-tip {
  margin-left: var(--spacing-sm);
  font-size: var(--font-size-xs);
  color: var(--color-text-placeholder);
}

/* ── 链接生成结果 ── */
.invite-result {
  padding: var(--spacing-xl) var(--spacing-lg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface-alt);
  text-align: center;
}

.invite-result__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  margin-bottom: var(--spacing-sm);
  border-radius: var(--radius-full);
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-size: 22px;
}

.invite-result__title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.invite-result__desc {
  margin-top: 4px;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.invite-link {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-lg);
  padding: var(--spacing-sm) var(--spacing-sm) var(--spacing-sm) var(--spacing-md);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  text-align: left;
}

.invite-link__text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
}

.invite-result__actions {
  display: flex;
  justify-content: center;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-lg);
}

/* ── 批量邀请 ── */
.batch-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: var(--spacing-md) 0 var(--spacing-sm);
}

.batch-toolbar__count {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);

  strong {
    color: var(--color-primary);
    font-weight: var(--font-weight-semibold);
  }
}

.batch-toolbar__actions {
  display: flex;
  gap: var(--spacing-xs);
}

.batch-table {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.batch-table__head,
.batch-table__row {
  display: grid;
  grid-template-columns: 1.1fr 1.1fr 1.4fr 40px;
  gap: var(--spacing-sm);
  align-items: center;
  padding: var(--spacing-xs) var(--spacing-sm);
}

.batch-table__head {
  background: var(--color-surface-alt);
  border-bottom: 1px solid var(--color-border);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-secondary);
}

.batch-table__body {
  max-height: 260px;
  overflow-y: auto;
}

.batch-table__row + .batch-table__row {
  border-top: 1px solid var(--color-border);
}

/* ── 批量结果 ── */
.batch-result {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.batch-skipped__title {
  margin-bottom: var(--spacing-xs);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}

.batch-skipped__list {
  margin: 0;
  padding: var(--spacing-sm) var(--spacing-md);
  max-height: 200px;
  overflow-y: auto;
  list-style: none;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-alt);
  font-size: var(--font-size-sm);

  li {
    display: flex;
    align-items: baseline;
    gap: var(--spacing-sm);
    padding: 3px 0;
  }
}

.batch-skipped__who {
  flex: 0 0 auto;
  min-width: 90px;
  color: var(--color-text-primary);
}

.batch-skipped__reason {
  color: var(--color-danger);
}
</style>
