<template>
  <PageContainer title="多维表格" class="bitable-list">
    <div class="bitable-list__header">
      <el-button type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon> 新建多维表格
      </el-button>
      <el-button @click="showTemplateGallery = true">
        <el-icon><Collection /></el-icon> 从模板创建
      </el-button>
    </div>

    <!-- Base 卡片列表 -->
    <div v-if="bases.length" class="bitable-list__grid">
      <el-card
        v-for="base in bases"
        :key="base.id"
        class="base-card"
        shadow="hover"
        @click="goToEditor(base.id)"
      >
        <div class="base-card__header">
          <span class="base-card__name">{{ base.name }}</span>
          <el-dropdown trigger="click" @command="handleAction($event, base)">
            <el-button link @click.stop><el-icon><MoreFilled /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit">重命名</el-dropdown-item>
                <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <p class="base-card__desc">{{ base.description || '暂无描述' }}</p>
        <div class="base-card__footer">
          <span>{{ base.tableCount || 0 }} 个数据表</span>
          <span>{{ base.creatorName }}</span>
          <span>{{ formatDate(base.createdAt) }}</span>
        </div>
      </el-card>
    </div>
    <el-empty v-else description="暂无多维表格，点击上方按钮创建" />

    <!-- 模板库弹窗 -->
    <TemplateGallery
      :visible="showTemplateGallery"
      @create="handleTemplateCreate"
      @close="showTemplateGallery = false"
    />

    <!-- 创建/编辑弹窗 -->
    <el-dialog v-model="showDialog" :title="editingBase ? '编辑多维表格' : '新建多维表格'" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="输入多维表格名称" maxlength="200" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" placeholder="输入描述" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, MoreFilled, Collection } from '@element-plus/icons-vue'
import { resolveErrorMessage } from '@/utils/error'
import PageContainer from '@/components/common/PageContainer.vue'
import { listBases, createBase, updateBase, deleteBase } from '@/api/modules/bitable'
import type { BitableBase, BitableBaseCreateDTO } from '@/types/bitable'
import TemplateGallery from './components/TemplateGallery.vue'

const router = useRouter()

const bases = ref<BitableBase[]>([])
const showDialog = ref(false)
const showTemplateGallery = ref(false)
const editingBase = ref<BitableBase | null>(null)
const saving = ref(false)
const form = ref<BitableBaseCreateDTO>({
  name: '',
  description: '',
})

async function loadBases() {
  try {
    const res = await listBases()
    bases.value = Array.isArray(res) ? res : (res as any).data || []
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '加载失败'))
  }
}

function goToEditor(id: number) {
  router.push({ name: 'BitableEditor', params: { baseId: id } })
}

function handleTemplateCreate(baseId: number) {
  showTemplateGallery.value = false
  loadBases()
  goToEditor(baseId)
}

function handleCreate() {
  editingBase.value = null
  form.value = { name: '', description: '' }
  showDialog.value = true
}

function handleAction(command: string, base: BitableBase) {
  if (command === 'edit') {
    editingBase.value = base
    form.value = { name: base.name, description: base.description || '' }
    showDialog.value = true
  } else if (command === 'delete') {
    ElMessageBox.confirm(`确定删除多维表格「${base.name}」吗？此操作不可恢复。`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    }).then(async () => {
      try {
        await deleteBase(base.id)
        ElMessage.success('删除成功')
        loadBases()
      } catch (e: any) {
        ElMessage.error(resolveErrorMessage(e, '删除失败'))
      }
    }).catch(() => {})
  }
}

async function handleSave() {
  if (!form.value.name.trim()) {
    ElMessage.warning('请输入名称')
    return
  }
  saving.value = true
  try {
    if (editingBase.value) {
      await updateBase(editingBase.value.id, form.value)
      ElMessage.success('更新成功')
    } else {
      await createBase(form.value)
      ElMessage.success('创建成功')
    }
    showDialog.value = false
    loadBases()
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '保存失败'))
  } finally {
    saving.value = false
  }
}

function formatDate(dateStr: string): string {
  if (!dateStr) return ''
  try {
    return new Date(dateStr).toLocaleDateString('zh-CN')
  } catch {
    return dateStr
  }
}

onMounted(() => {
  loadBases()
})
</script>

<style scoped lang="scss">
.bitable-list__header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--spacing-sm);
}

.bitable-list__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--spacing-md);
  margin-top: var(--spacing-md);
}

.base-card {
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: var(--shadow-lg);
  }

  .base-card__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--spacing-sm);
    margin-bottom: var(--spacing-sm);
  }

  .base-card__name {
    font-size: var(--font-size-md);
    font-weight: var(--font-weight-semibold);
    color: var(--color-text-primary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .base-card__desc {
    font-size: var(--font-size-sm);
    color: var(--color-text-secondary);
    margin: var(--spacing-xs) 0;
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    min-height: 2.4em;
  }

  .base-card__footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--spacing-xs);
    font-size: var(--font-size-xs);
    color: var(--color-muted-text);
    margin-top: var(--spacing-sm);
    border-top: 1px solid var(--color-border);
    padding-top: var(--spacing-sm);

    span {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}
</style>