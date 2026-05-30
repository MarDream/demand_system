<template>
  <div class="requirement-templates-page">
    <div class="page-header">
      <h2>需求模板管理</h2>
      <el-button type="primary" @click="handleCreate">新建模板</el-button>
    </div>

    <el-table :data="templates" border v-loading="loading">
      <el-table-column prop="requirementTypeCode" label="需求类型" width="150" />
      <el-table-column prop="templateName" label="模板名称" />
      <el-table-column prop="isActive" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.isActive === 1 ? 'success' : 'info'">
            {{ row.isActive === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="250">
        <template #default="{ row }">
          <el-button size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button
            size="small"
            :type="row.isActive === 1 ? 'warning' : 'success'"
            @click="handleToggleStatus(row)"
          >
            {{ row.isActive === 1 ? '禁用' : '启用' }}
          </el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="800px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-width="120px" v-loading="saving">
        <el-form-item label="需求类型">
          <el-input v-model="form.requirementTypeCode" placeholder="如: FEATURE, BUG" />
        </el-form-item>
        <el-form-item label="模板名称">
          <el-input v-model="form.templateName" placeholder="如: 功能需求模板" />
        </el-form-item>
        <el-form-item label="模板字段">
          <div class="template-sections">
            <div
              v-for="(section, index) in form.templateContent.sections"
              :key="index"
              class="section-item"
            >
              <el-card>
                <div class="section-header">
                  <span>字段 {{ index + 1 }}</span>
                  <el-button
                    size="small"
                    type="danger"
                    text
                    @click="removeSection(index)"
                  >
                    删除
                  </el-button>
                </div>
                <el-form-item label="字段ID">
                  <el-input v-model="section.sectionId" placeholder="如: background" />
                </el-form-item>
                <el-form-item label="字段名称">
                  <el-input v-model="section.sectionName" placeholder="如: 历史背景" />
                </el-form-item>
                <el-form-item label="字段类型">
                  <el-select v-model="section.fieldType">
                    <el-option label="单行文本" value="text" />
                    <el-option label="多行文本" value="textarea" />
                    <el-option label="富文本" value="richtext" />
                  </el-select>
                </el-form-item>
                <el-form-item label="是否必填">
                  <el-switch v-model="section.required" />
                </el-form-item>
                <el-form-item label="占位提示">
                  <el-input v-model="section.placeholder" />
                </el-form-item>
                <el-form-item v-if="section.fieldType === 'text'" label="最大长度">
                  <el-input-number v-model="section.maxLength" :min="1" />
                </el-form-item>
                <el-form-item v-if="section.fieldType === 'richtext'" label="默认内容">
                  <el-input
                    v-model="section.defaultContent"
                    type="textarea"
                    :rows="3"
                    placeholder="HTML格式"
                  />
                </el-form-item>
              </el-card>
            </div>
            <el-button @click="addSection">添加字段</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAllRequirementTemplates,
  saveRequirementTemplate,
  deleteRequirementTemplate,
  toggleRequirementTemplateStatus
} from '@/api/modules/requirement'
import type { RequirementTemplate, TemplateSection } from '@/types/requirement'

const templates = ref<RequirementTemplate[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('新建模板')
const form = ref<RequirementTemplate>({
  requirementTypeCode: '',
  templateName: '',
  templateContent: {
    sections: []
  }
})

onMounted(() => {
  loadTemplates()
})

async function loadTemplates() {
  loading.value = true
  try {
    templates.value = await getAllRequirementTemplates()
  } catch (error) {
    ElMessage.error('加载模板列表失败')
  } finally {
    loading.value = false
  }
}

function handleCreate() {
  dialogTitle.value = '新建模板'
  form.value = {
    requirementTypeCode: '',
    templateName: '',
    templateContent: {
      sections: []
    }
  }
  dialogVisible.value = true
}

function handleEdit(row: RequirementTemplate) {
  dialogTitle.value = '编辑模板'
  form.value = JSON.parse(JSON.stringify(row))
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.value.requirementTypeCode) {
    ElMessage.warning('请输入需求类型')
    return
  }
  if (!form.value.templateName) {
    ElMessage.warning('请输入模板名称')
    return
  }
  if (form.value.templateContent.sections.length === 0) {
    ElMessage.warning('请至少添加一个字段')
    return
  }

  saving.value = true
  try {
    await saveRequirementTemplate(form.value)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadTemplates()
  } catch (error) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function handleToggleStatus(row: RequirementTemplate) {
  const newStatus = row.isActive === 1 ? 0 : 1
  try {
    await toggleRequirementTemplateStatus(row.id!, newStatus)
    ElMessage.success(newStatus === 1 ? '已启用' : '已禁用')
    loadTemplates()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

async function handleDelete(row: RequirementTemplate) {
  try {
    await ElMessageBox.confirm('确定删除该模板吗？', '提示', {
      type: 'warning'
    })
    await deleteRequirementTemplate(row.id!)
    ElMessage.success('删除成功')
    loadTemplates()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

function addSection() {
  form.value.templateContent.sections.push({
    sectionId: '',
    sectionName: '',
    fieldType: 'text',
    required: false,
    placeholder: ''
  })
}

function removeSection(index: number) {
  form.value.templateContent.sections.splice(index, 1)
}
</script>

<style scoped lang="scss">
.requirement-templates-page {
  padding: 20px;

  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;

    h2 {
      margin: 0;
    }
  }

  .template-sections {
    width: 100%;

    .section-item {
      margin-bottom: 16px;

      .section-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 12px;
        font-weight: bold;
      }
    }
  }
}
</style>
