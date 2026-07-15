<template>
  <el-dialog
    :model-value="visible"
    title="从模板创建"
    width="900px"
    @update:model-value="$emit('close')"
  >
    <div v-if="loading" class="template-gallery__loading">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>加载模板...</span>
    </div>

    <div v-else-if="templates.length" class="template-gallery__grid">
      <el-card
        v-for="template in templates"
        :key="template.code"
        class="template-card"
        shadow="hover"
      >
        <div class="template-card__header">
          <el-icon class="template-card__icon" :size="32">
            <component :is="getIcon(template.icon)" />
          </el-icon>
          <span class="template-card__name">{{ template.name }}</span>
        </div>
        <p class="template-card__desc">{{ template.description }}</p>
        <div class="template-card__footer">
          <span class="template-card__count">{{ template.fieldCount }} 个字段</span>
          <el-button type="primary" size="small" :loading="creating === template.code" @click="handleUse(template)">
            使用模板
          </el-button>
        </div>
      </el-card>
    </div>

    <el-empty v-else description="暂无可用模板" />

    <template #footer>
      <el-button @click="$emit('close')">取消</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { resolveErrorMessage } from '@/utils/error'
import { Loading, FolderOpened, UserFilled, OfficeBuilding } from '@element-plus/icons-vue'
import { listTemplates, createFromTemplate, type BitableTemplateVO } from '@/api/modules/bitableTemplate'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{
  (e: 'create', baseId: number): void
  (e: 'close'): void
}>()

const templates = ref<BitableTemplateVO[]>([])
const loading = ref(false)
const creating = ref<string | null>(null)

async function loadTemplates() {
  loading.value = true
  try {
    const res = await listTemplates()
    templates.value = Array.isArray(res) ? res : []
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '加载模板失败'))
  } finally {
    loading.value = false
  }
}

async function handleUse(template: BitableTemplateVO) {
  creating.value = template.code
  try {
    const baseId = await createFromTemplate(template.code)
    ElMessage.success(`已从模板「${template.name}」创建成功`)
    emit('create', baseId)
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '创建失败'))
  } finally {
    creating.value = null
  }
}

function getIcon(iconName: string) {
  switch (iconName) {
    case 'FolderOpened': return FolderOpened
    case 'UserFilled': return UserFilled
    case 'OfficeBuilding': return OfficeBuilding
    default: return FolderOpened
  }
}

onMounted(() => {
  loadTemplates()
})
</script>

<style scoped lang="scss">
.template-gallery__loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-lg) 0;
  color: var(--color-text-secondary);
}

.template-gallery__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--spacing-md);
}

.template-card {
  transition: transform 0.2s ease, box-shadow 0.2s ease;

  &:hover {
    transform: translateY(-2px);
  }

  .template-card__header {
    display: flex;
    align-items: center;
    gap: var(--spacing-sm);
    margin-bottom: var(--spacing-sm);
  }

  .template-card__icon {
    color: var(--el-color-primary);
  }

  .template-card__name {
    font-size: var(--font-size-md);
    font-weight: var(--font-weight-semibold);
    color: var(--color-text-primary);
  }

  .template-card__desc {
    font-size: var(--font-size-sm);
    color: var(--color-text-secondary);
    margin: var(--spacing-xs) 0;
    min-height: 2.4em;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .template-card__footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: var(--spacing-sm);
    border-top: 1px solid var(--color-border);
    padding-top: var(--spacing-sm);
  }

  .template-card__count {
    font-size: var(--font-size-xs);
    color: var(--color-muted-text);
  }
}
</style>
