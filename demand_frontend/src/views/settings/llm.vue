<template>
  <div class="config-container">
    <div class="config-header">
      <h2>模型配置</h2>
      <p class="config-desc">管理大模型接入组和模型实例，支持 OpenAI 和 Anthropic 协议</p>
    </div>

    <el-tabs v-model="activeTab" class="llm-tabs">
      <el-tab-pane label="接入组与模型" name="providers">
    <!-- 知识库配置状态卡片 -->
    <div v-if="ragConfig" class="rag-status-bar">
      <div class="rag-status-item">
        <span class="rag-status-label">Embedding</span>
        <template v-if="ragConfig.embedding?.configured">
          <span class="rag-status-value">{{ ragConfig.embedding.name }}</span>
          <el-tag v-if="ragConfig.embedding.dimension" size="small" type="info">{{ ragConfig.embedding.dimension }}d</el-tag>
          <span v-if="ragConfig.embedding.dimensionMatch === false" class="rag-status-warn">维度不匹配</span>
          <span v-else class="rag-status-ok">正常</span>
        </template>
        <template v-else>
          <span class="rag-status-missing">未配置</span>
        </template>
      </div>
      <div class="rag-status-item">
        <span class="rag-status-label">Reranker</span>
        <template v-if="ragConfig.reranker?.configured">
          <span class="rag-status-value">{{ ragConfig.reranker.name }}</span>
          <span class="rag-status-ok">正常</span>
        </template>
        <template v-else>
          <span class="rag-status-missing">未配置</span>
        </template>
      </div>
      <div class="rag-status-item">
        <span class="rag-status-label">Milvus</span>
        <span class="rag-status-value">{{ ragConfig.milvusDimension }}d</span>
      </div>
      <div class="rag-status-item">
        <span class="rag-status-label">分块</span>
        <span class="rag-status-value">{{ ragConfig.chunkSize }} / {{ ragConfig.chunkOverlap }}</span>
      </div>
    </div>

    <div class="config-layout" :style="providerPanel.styleVars">
      <!-- 左侧：接入组列表 -->
      <div class="provider-panel" :class="{ 'is-collapsed': providerPanel.collapsed }">
        <div class="provider-panel__inner">
          <div class="panel-header">
            <span class="panel-title">接入组</span>
            <div class="panel-header-actions">
              <el-tooltip content="折叠侧边栏" placement="top">
                <el-icon class="panel-collapse-btn" @click="providerPanel.toggle"><Fold /></el-icon>
              </el-tooltip>
              <AppButton :icon="Plus" size="small" type="primary" permission="button:llm-provider:create" @click="openCreateProvider">新增</AppButton>
            </div>
          </div>
          <div class="provider-list" v-loading="loading">
            <div
              v-for="p in providers"
              :key="p.id"
              class="provider-item"
              :class="{ 'is-selected': selectedProviderId === p.id }"
              @click="selectedProviderId = p.id!"
            >
              <div class="provider-item-main">
                <div class="provider-item-left">
                  <div class="provider-name">
                    <span>{{ p.name }}</span>
                    <el-tag :type="p.protocol === 'openai' ? 'primary' : 'warning'" size="small">
                      {{ p.protocol === 'openai' ? 'OpenAI' : 'Anthropic' }}
                    </el-tag>
                  </div>
                  <div class="provider-meta">{{ p.baseUrl }}</div>
                </div>
                <div class="provider-item-right">
                  <span v-permission="'button:llm-provider:update'">
                    <el-switch
                      :model-value="p.enabled"
                      size="small"
                      @change="handleToggleProvider(p)"
                      @click.stop
                    />
                  </span>
                  <div class="provider-count">{{ p.models?.length ?? 0 }} 个模型{{ providerValidCount(p) > 0 ? `，有效 ${providerValidCount(p)} 个` : '' }}</div>
                </div>
              </div>
              <div class="provider-item-actions" @click.stop>
                <el-tooltip content="嗅探模型" placement="top">
                  <span v-permission="'button:llm-provider:test'">
                    <el-icon class="action-icon" style="color: var(--color-warning);" @click="handleSniff(p)"><Search /></el-icon>
                  </span>
                </el-tooltip>
                <el-tooltip content="查看密钥" placement="top">
                  <span v-permission="'button:llm-provider:update'">
                    <el-icon class="action-icon" @click="handleViewApiKey(p)"><View /></el-icon>
                  </span>
                </el-tooltip>
                <el-tooltip content="编辑" placement="top">
                  <span v-permission="'button:llm-provider:update'">
                    <el-icon class="action-icon primary" @click="openEditProvider(p)"><EditPen /></el-icon>
                  </span>
                </el-tooltip>
                <el-tooltip content="删除" placement="top">
                  <span v-permission="'button:llm-provider:delete'">
                    <el-icon class="action-icon danger" @click="handleDeleteProvider(p)"><Delete /></el-icon>
                  </span>
                </el-tooltip>
              </div>
            </div>
            <el-empty v-if="!loading && providers.length === 0" description="暂无接入组" />
          </div>
        </div>
      </div>

      <!-- 可拖拽分隔条 -->
      <div class="provider-panel__resizer" @mousedown="providerPanel.startResize" @dblclick="providerPanel.toggle" />

      <!-- 折叠时的展开按钮 -->
      <button
        v-if="providerPanel.collapsed"
        class="provider-panel__expand-btn"
        type="button"
        title="展开侧边栏"
        @click="providerPanel.toggle"
      >
        <el-icon><ArrowRight /></el-icon>
      </button>

      <!-- 右侧：模型管理 -->
      <div class="model-panel">
        <div v-if="!selectedProviderId" class="model-empty">
          <el-empty description="请选择左侧接入组" />
        </div>
        <template v-else-if="selectedProvider">
          <div class="panel-header">
            <div class="panel-header-left">
              <span class="panel-title">
                {{ selectedProvider.name }} 的模型
                <el-tag :type="selectedProvider.protocol === 'openai' ? 'primary' : 'warning'" size="small" style="margin-left: 8px;">
                  {{ selectedProvider.protocol === 'openai' ? 'OpenAI' : 'Anthropic' }}
                </el-tag>
              </span>
              <AppButton :icon="Plus" size="small" type="primary" permission="button:llm-provider:create" @click="openCreateModel(selectedProvider)">新增模型</AppButton>
            </div>
            <div class="panel-header-right">
              <el-tooltip content="列表字段设置" placement="top">
                <el-button
                  :icon="Setting"
                  circle
                  size="small"
                  @click="openColumnConfig"
                />
              </el-tooltip>
              <el-tooltip content="批量测试所有模型连通性" placement="top">
                <AppButton
                  size="small"
                  :icon="Connection"
                  :loading="batchTesting"
                  permission="button:llm-provider:test"
                  @click="handleBatchTest"
                >
                  批量测试
                </AppButton>
              </el-tooltip>
              <el-tooltip content="嗅探可用模型" placement="top">
                <AppButton size="small" :icon="Search" permission="button:llm-provider:test" @click="handleSniff(selectedProvider)">嗅探</AppButton>
              </el-tooltip>
            </div>
          </div>

          <!-- 模型类型快捷筛选 -->
          <div class="model-type-filter">
            <span
              v-for="ft in modelTypeFilters"
              :key="ft.value"
              class="model-type-filter-item"
              :class="{ 'is-active': modelTypeFilter === ft.value }"
              @click="modelTypeFilter = ft.value"
            >{{ ft.label }} <sup v-if="ft.count > 0">{{ ft.count }}</sup></span>
          </div>

          <el-table :data="pagedModels" border style="width: 100%" size="small" row-key="id" :row-class-name="modelRowClassName" @selection-change="handleModelSelectionChange">
            <el-table-column type="selection" width="40" align="center" />
            <template v-for="col in visibleColumns" :key="col.key">
              <el-table-column
                :prop="col.prop"
                :label="col.label"
                :width="col.width"
                :min-width="col.minWidth"
                :align="col.align || 'left'"
                :fixed="col.fixed"
                :show-overflow-tooltip="col.showOverflowTooltip"
                :sortable="col.sortable || false"
              >
                <template #default="{ row }">
                  <template v-if="col.key === 'modelId'">
                    <span :class="{ 'model-name-default': row.isDefault }">{{ row.modelId }}</span>
                  </template>
                  <template v-else-if="col.key === 'modelType'">
                    <el-tag :type="typeTagType(row.modelType)" size="small">{{ row.modelType || 'general' }}</el-tag>
                  </template>
                  <template v-else-if="col.key === 'contextWindow'">
                    <span :class="row.contextWindow ? '' : 'conn-pending'">{{ formatContextWindow(row.contextWindow ?? null) }}</span>
                  </template>
                  <template v-else-if="col.key === 'ownedBy'">
                    <template v-if="row.ownedBy">{{ row.ownedBy }}</template>
                    <span v-else class="conn-pending">-</span>
                  </template>
                  <template v-else-if="col.key === 'temperature'">
                    {{ row.temperature }}
                  </template>
                  <template v-else-if="col.key === 'maxTokens'">
                    {{ row.maxTokens }}
                  </template>
                  <template v-else-if="col.key === 'isDefault'">
                    <template v-if="hasUpdatePermission">
                      <el-switch :model-value="row.isDefault" size="small" @change="handleToggleDefault(selectedProvider!, row)" />
                    </template>
                    <template v-else>
                      <el-tag v-if="row.isDefault" type="success" size="small">默认</el-tag>
                      <span v-else class="conn-pending">-</span>
                    </template>
                  </template>
                  <template v-else-if="col.key === 'enabled'">
                    <span v-permission="'button:llm-provider:update'">
                      <el-switch :model-value="row.enabled" size="small" @change="handleToggleModel(selectedProvider, row)" />
                    </span>
                  </template>
                  <template v-else-if="col.key === 'connectivity'">
                    <div class="conn-status">
                      <template v-if="testingModels[row.id!]">
                        <span class="testing-text"><el-icon class="is-loading"><Loading /></el-icon> 测试中</span>
                      </template>
                      <template v-else-if="row.testSuccess != null">
                        <el-tooltip :content="connTooltip(row)" placement="top">
                          <span class="conn-light" :class="connLightClass(row)"></span>
                        </el-tooltip>
                      </template>
                      <span v-else class="conn-pending">-</span>
                    </div>
                  </template>
                  <template v-else-if="col.key === 'testDuration'">
                    <template v-if="row.testDuration != null">
                      <span :style="{ color: durationColor(row.testDuration) }">
                        {{ formatDuration(row.testDuration) }}
                      </span>
                    </template>
                    <span v-else class="conn-pending">-</span>
                  </template>
                  <template v-else-if="col.key === 'testAt'">
                    <template v-if="row.testAt">
                      {{ formatTestAt(row.testAt) }}
                    </template>
                    <span v-else class="conn-pending">-</span>
                  </template>
                  <template v-else-if="col.key === 'operations'">
                    <el-tooltip v-if="row.testAt" content="测试详情" placement="top">
                      <el-icon class="action-icon info" @click="openTestDetail(row)"><Document /></el-icon>
                    </el-tooltip>
                    <el-tooltip content="测试连通性" placement="top">
                      <span v-permission="'button:llm-provider:test'">
                        <el-icon class="action-icon" @click="handleTestModel(row)"><Connection /></el-icon>
                      </span>
                    </el-tooltip>
                    <el-tooltip content="编辑" placement="top">
                      <span v-permission="'button:llm-provider:update'">
                        <el-icon class="action-icon primary" @click="openEditModel(row)"><EditPen /></el-icon>
                      </span>
                    </el-tooltip>
                    <el-tooltip content="删除" placement="top">
                      <span v-permission="'button:llm-provider:delete'">
                        <el-icon class="action-icon danger" @click="handleDeleteModel(row)"><Delete /></el-icon>
                      </span>
                    </el-tooltip>
                  </template>
                </template>
              </el-table-column>
            </template>
          </el-table>

          <div v-if="selectedProviderModels.length > 0" class="model-table-footer">
            <div class="model-table-footer-left">
              <AppButton
                v-if="selectedModelRows.length > 0"
                size="small"
                type="danger"
                :icon="Delete"
                permission="button:llm-provider:delete"
                @click="handleBatchDeleteModels"
              >
                删除选中 ({{ selectedModelRows.length }})
              </AppButton>
              <span v-if="selectedModelRows.length > 0" class="model-selection-info">
                已选 {{ selectedModelRows.length }} / {{ selectedProviderModels.length }} 项
              </span>
            </div>
            <el-pagination
              v-if="selectedProviderModels.length > modelPageSize"
              v-model:current-page="modelCurrentPage"
              :page-size="modelPageSize"
              :total="selectedProviderModels.length"
              layout="total, prev, pager, next"
              size="small"
              class="model-pagination"
            />
          </div>

          <el-empty v-if="selectedProviderModels.length === 0" description="暂无模型，请新增或嗅探" />
        </template>
      </div>
    </div>

    <!-- 列配置弹窗 -->
    <ColumnConfigDialog
      v-model="showColumnConfig"
      :column-groups="columnGroups"
      :draft-selected-columns="draftSelectedColumns"
      :draft-column-keys="draftColumnKeys"
      @update:draft-column-keys="draftColumnKeys = $event"
      @remove="removeDraftColumn"
      @save="saveColumns"
    />

    <!-- Provider 对话框 -->
    <el-dialog
      v-model="providerDialogVisible"
      :title="editingProviderId ? '编辑接入组' : '新增接入组'"
      width="620px"
      class="provider-dialog"
    >
      <el-form
        ref="providerFormRef"
        :model="providerForm"
        :rules="providerRules"
        label-width="126px"
        class="provider-form provider-dialog-form"
      >
        <section class="form-section-card">
          <div class="form-section-title">接入信息</div>
          <el-form-item label="名称" prop="name">
            <el-input v-model="providerForm.name" placeholder="如 OpenAI 官方、智谱 GLM" />
          </el-form-item>
          <div class="form-row form-row--provider">
            <el-form-item label="协议类型" prop="protocol" class="form-row-item">
              <el-select v-model="providerForm.protocol" style="width: 100%">
                <el-option label="OpenAI" value="openai" />
                <el-option label="Anthropic" value="anthropic" />
              </el-select>
            </el-form-item>
            <el-form-item label="启用" class="form-row-item form-switch-item" label-width="60px">
              <el-switch v-model="providerForm.enabled" />
            </el-form-item>
          </div>
        </section>

        <section class="form-section-card">
          <div class="form-section-title">接入配置</div>
          <el-form-item label="API Base URL" prop="baseUrl">
            <el-input v-model="providerForm.baseUrl" placeholder="https://api.openai.com" />
          </el-form-item>
          <el-form-item label="API Key" prop="apiKey">
            <el-input
              v-model="providerForm.apiKey"
              :type="apiKeyVisible ? 'text' : 'password'"
              :placeholder="editingProviderId ? '不修改请留空' : '请输入 API Key'"
            >
              <template #suffix>
                <el-icon class="apiKey-eye" @click="toggleApiKeyVisible" style="cursor: pointer">
                  <View v-if="!apiKeyVisible" />
                  <Hide v-else />
                </el-icon>
              </template>
            </el-input>
          </el-form-item>
        </section>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="providerDialogVisible = false">取消</el-button>
          <AppButton type="primary" :loading="submitting" :permission="providerSavePermission" @click="handleProviderSubmit">保存</AppButton>
        </div>
      </template>
    </el-dialog>

    <!-- Model 对话框 -->
    <el-dialog v-model="modelDialogVisible" :title="editingModelId ? '编辑模型' : '新增模型'" width="560px">
      <el-form :model="modelForm" :rules="modelRules" ref="modelFormRef" label-width="110px" class="provider-form">
        <div class="form-section-title">基础信息</div>
        <div class="form-row">
          <el-form-item label="名称" prop="name" class="form-row-item">
            <el-input v-model="modelForm.name" placeholder="如 GPT-4o" />
          </el-form-item>
          <el-form-item label="模型ID" prop="modelId" class="form-row-item">
            <el-input v-model="modelForm.modelId" placeholder="如 gpt-4o" />
          </el-form-item>
        </div>
        <el-form-item label="模型类型" prop="modelType">
          <el-select v-model="modelForm.modelType" allow-create filterable default-first-option style="width: 100%">
            <el-option v-for="r in presetTypes" :key="r" :label="modelTypeOptionLabel(r)" :value="r" />
          </el-select>
          <div v-if="modelForm.modelType === 'vision'" class="form-hint">用于图片 OCR、截图、图表和页面内容理解，并在“模型应用”中按功能点绑定</div>
        </el-form-item>
        <el-form-item v-if="modelForm.modelType === 'embedding'" label="向量维度" class="form-row-item">
          <el-input-number v-model="modelForm.dimension" :min="1" :max="8192" :step="256" placeholder="如 1024、2048" style="width: 100%" />
          <div class="form-hint">Embedding 模型输出维度，需与 Milvus 集合维度一致</div>
        </el-form-item>
        <template v-if="modelForm.modelType === 'embedding'">
          <div class="form-section-title">Embedding 参数</div>
          <div class="form-row">
            <el-form-item label="分块大小" class="form-row-item">
              <el-input-number v-model="modelForm.chunkSize" :min="1" :max="10000" :step="64" placeholder="如 512" style="width: 100%" />
              <div class="form-hint">每个文本块的字符数</div>
            </el-form-item>
            <el-form-item label="分块重叠" class="form-row-item">
              <el-input-number v-model="modelForm.chunkOverlap" :min="0" :max="5000" :step="32" placeholder="如 128" style="width: 100%" />
              <div class="form-hint">相邻块之间的重叠字符数</div>
            </el-form-item>
          </div>
          <el-form-item label="检索 TopK">
            <el-input-number v-model="modelForm.searchTopK" :min="1" :max="1000" :step="5" placeholder="如 20" style="width: 100%" />
            <div class="form-hint">检索时返回的最相关结果数量</div>
          </el-form-item>
        </template>
        <div class="form-row">
          <el-form-item label="上下文长度" class="form-row-item">
            <span class="form-static-value">{{ formatContextWindow(modelForm.contextWindow ?? null) }}</span>
          </el-form-item>
          <el-form-item label="厂商" class="form-row-item">
            <span class="form-static-value">{{ modelForm.ownedBy || '-' }}</span>
          </el-form-item>
        </div>
        <div class="form-section-title">模型参数</div>
        <div class="form-row">
          <el-form-item label="温度" class="form-row-item">
            <el-slider v-model="modelForm.temperature" :min="0" :max="1" :step="0.01" show-input size="small" />
          </el-form-item>
          <el-form-item label="最大 Tokens" class="form-row-item">
            <el-input-number v-model="modelForm.maxTokens" :min="1" :max="128000" :step="512" style="width: 100%" />
          </el-form-item>
        </div>
        <div class="form-section-title">其他设置</div>
        <div class="form-row">
          <el-form-item label="角色默认" class="form-row-item">
            <el-switch v-model="modelForm.isDefault" />
          </el-form-item>
          <el-form-item label="启用" class="form-row-item" label-width="60px">
            <el-switch v-model="modelForm.enabled" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="modelDialogVisible = false">取消</el-button>
        <AppButton type="primary" :loading="submitting" :permission="modelSavePermission" @click="handleModelSubmit">保存</AppButton>
      </template>
    </el-dialog>

    <!-- 嗅探模型对话框 -->
    <el-dialog v-model="sniffDialogVisible" title="嗅探模型" width="620px">
      <div v-loading="sniffing" style="min-height: 100px;">
        <el-alert
          v-if="!sniffing && sniffedModels.length > 0"
          type="info"
          :closable="false"
          style="margin-bottom: 12px;"
        >
          发现 {{ sniffedModels.length }} 个可用模型，已自动选择未导入的模型。已导入的模型将显示为灰色。
        </el-alert>
        <el-empty v-if="!sniffing && sniffedModels.length === 0" description="未发现可用模型" />
        <template v-if="sniffedModels.length > 0">
          <el-input
            v-model="sniffSearchKeyword"
            placeholder="搜索模型名称..."
            clearable
            size="small"
            style="margin-bottom: 10px;"
            :prefix-icon="Search"
          />
          <div class="sniff-select-bar">
            <el-checkbox
              :model-value="sniffIsAllFilteredSelected"
              :indeterminate="sniffIsFilteredPartialSelected"
              @change="handleSniffSelectAllFiltered"
            >
              全选当前结果（已选 {{ sniffSelectedModelIds.length }} 个）
            </el-checkbox>
          </div>
          <div class="sniff-model-list">
            <el-checkbox-group v-model="sniffSelectedModelIds">
              <div v-for="model in filteredSniffedModels" :key="model.modelId" class="sniff-model-item">
                <el-checkbox :value="model.modelId" :disabled="model.alreadyExists">
                  <div class="sniff-model-info">
                    <div class="sniff-model-row">
                      <span class="sniff-model-id" :style="{ color: model.alreadyExists ? '#c0c4cc' : '' }">
                        {{ model.modelId }}
                      </span>
                      <el-tag v-if="model.alreadyExists" size="small" type="info">已导入</el-tag>
                      <el-tag v-if="model.ownedBy" size="small">{{ model.ownedBy }}</el-tag>
                      <el-tag
                        v-if="model.inferredType && model.inferredType !== 'general'"
                        :type="sniffTypeTagType(model.inferredType)"
                        size="small"
                      >{{ model.inferredType }}</el-tag>
                    </div>
                    <div class="sniff-model-meta">
                      <span class="sniff-meta-item">
                        <span class="sniff-meta-label">上下文</span>
                        <span class="sniff-meta-value">{{ formatContextWindow(model.contextWindow) }}</span>
                      </span>
                      <span class="sniff-meta-item">
                        <span class="sniff-meta-label">发布日期</span>
                        <span class="sniff-meta-value">{{ formatCreatedDate(model.created) }}</span>
                      </span>
                    </div>
                  </div>
                </el-checkbox>
              </div>
            </el-checkbox-group>
            <el-empty v-if="filteredSniffedModels.length === 0" description="无匹配模型" :image-size="60" />
          </div>
        </template>
      </div>
      <template #footer>
        <el-button @click="sniffDialogVisible = false">取消</el-button>
        <AppButton type="primary" :loading="submitting" :disabled="sniffSelectedModelIds.length === 0" permission="button:llm-provider:create" @click="handleSniffImport">
          导入选中 ({{ sniffSelectedModelIds.length }})
        </AppButton>
      </template>
    </el-dialog>

    <!-- 测试详情 Drawer -->
    <el-drawer
      v-model="testDetailVisible"
      :title="testDetailModel ? `${testDetailModel.name} - 测试详情` : '测试详情'"
      direction="rtl"
      size="480px"
      :destroy-on-close="true"
    >
      <template v-if="testDetailModel">
        <!-- 基础状态 -->
        <div class="test-detail-section">
          <div class="test-detail-section-title">测试状态</div>
          <div class="test-detail-grid">
            <div class="test-detail-item">
              <span class="test-detail-label">连通性</span>
              <span class="test-detail-value">
                <span v-if="testDetailModel.testSuccess === true" class="conn-badge conn-badge-success">成功</span>
                <span v-else-if="testDetailModel.testSuccess === false" class="conn-badge conn-badge-fail">失败</span>
                <span v-else class="conn-badge conn-badge-pending">未测试</span>
              </span>
            </div>
            <div class="test-detail-item">
              <span class="test-detail-label">响应耗时</span>
              <span class="test-detail-value" :style="{ color: testDetailModel.testDuration ? durationColor(testDetailModel.testDuration) : '' }">
                {{ testDetailModel.testDuration != null ? formatDuration(testDetailModel.testDuration) : '-' }}
              </span>
            </div>
            <div class="test-detail-item">
              <span class="test-detail-label">测试时间</span>
              <span class="test-detail-value">{{ testDetailModel.testAt ? formatTestAt(testDetailModel.testAt) : '-' }}</span>
            </div>
          </div>
        </div>

        <!-- 错误信息 -->
        <div v-if="testDetailModel.testError" class="test-detail-section">
          <div class="test-detail-section-title">错误信息</div>
          <div class="test-detail-error-box">{{ testDetailModel.testError }}</div>
        </div>

        <!-- 完整测试结果 -->
        <template v-if="testDetailModel.testContent || testDetailModel.testResponseModel || testDetailModel.testPromptTokens != null">
          <div class="test-detail-section">
            <div class="test-detail-section-title">响应详情</div>
            <div class="test-detail-grid">
              <div v-if="testDetailModel.testResponseModel" class="test-detail-item">
                <span class="test-detail-label">响应模型</span>
                <span class="test-detail-value">{{ testDetailModel.testResponseModel }}</span>
              </div>
              <div v-if="testDetailModel.testPromptTokens != null" class="test-detail-item">
                <span class="test-detail-label">Prompt Tokens</span>
                <span class="test-detail-value">{{ testDetailModel.testPromptTokens }}</span>
              </div>
              <div v-if="testDetailModel.testCompletionTokens != null" class="test-detail-item">
                <span class="test-detail-label">Completion Tokens</span>
                <span class="test-detail-value">{{ testDetailModel.testCompletionTokens }}</span>
              </div>
              <div v-if="testDetailModel.testTotalTokens != null" class="test-detail-item">
                <span class="test-detail-label">Total Tokens</span>
                <span class="test-detail-value">{{ testDetailModel.testTotalTokens }}</span>
              </div>
            </div>
          </div>

          <!-- 响应内容 -->
          <div v-if="testDetailModel.testContent" class="test-detail-section">
            <div class="test-detail-section-title">响应内容</div>
            <div class="test-detail-content-box">{{ testDetailModel.testContent }}</div>
          </div>
        </template>

        <!-- 无完整结果提示 -->
        <div v-else-if="testDetailModel.testAt" class="test-detail-section">
          <el-alert type="info" :closable="false" description="该测试结果未包含完整响应内容，请重新测试以查看详情" />
        </div>
      </template>
    </el-drawer>
      </el-tab-pane>

      <el-tab-pane label="模型应用" name="applications">
        <div class="application-panel" v-loading="applicationsLoading">
          <div class="application-intro">
            <div>
              <h3>功能点模型应用</h3>
              <p>为不同业务功能单独指定默认模型；未指定时普通功能会自动回退，图片理解功能未配置时会跳过图片处理。</p>
            </div>
            <el-tag type="info">共 {{ applications.length }} 个功能点</el-tag>
          </div>

          <el-alert
            title="模型选择只影响对应功能点，不会修改接入组或模型本身的全局默认标记。"
            type="info"
            :closable="false"
            show-icon
            class="application-alert"
          />

          <div class="application-grid">
            <div v-for="application in applications" :key="application.code" class="application-card">
              <div class="application-card-header">
                <div>
                  <div class="application-name">
                    <span>{{ application.name }}</span>
                    <el-tag v-if="application.modelType === 'vision'" type="warning" size="small">多模态</el-tag>
                  </div>
                  <div class="application-code">{{ application.code }}</div>
                </div>
                <el-switch
                  v-model="application.enabled"
                  :loading="savingApplicationCode === application.code"
                  @change="saveApplication(application)"
                />
              </div>
              <p class="application-description">{{ application.description }}</p>
              <el-form label-position="top" size="default">
                <el-form-item :label="applicationModelLabel(application)">
                  <el-select
                    v-model="application.modelId"
                    clearable
                    filterable
                    style="width: 100%"
                    :disabled="!application.enabled || savingApplicationCode === application.code"
                    :placeholder="applicationModelOptions(application).length ? '请选择模型' : '暂无可用模型'"
                    @change="saveApplication(application)"
                  >
                    <el-option
                      v-for="option in applicationModelOptions(application)"
                      :key="option.modelId"
                      :label="`${option.providerName} / ${option.name} (${option.modelId})`"
                      :value="option.id"
                    />
                  </el-select>
                  <div
                    v-if="application.modelType === 'vision' && applicationModelOptions(application).length === 0"
                    class="form-hint"
                  >
                    请先在“接入组与模型”中新增并启用 vision 类型模型；未配置时将自动跳过图片理解
                  </div>
                </el-form-item>
              </el-form>
              <div class="application-current">
                <template v-if="!application.enabled">
                  <el-tag type="warning" size="small">已停用</el-tag>
                  <span>该功能点不会调用 LLM</span>
                </template>
                <template v-else-if="application.modelName && application.modelAvailable">
                  <el-tag type="success" size="small">已绑定</el-tag>
                  <span>{{ application.providerName }} / {{ application.modelName }}</span>
                </template>
                <template v-else-if="application.modelId">
                  <el-tag type="danger" size="small">不可用</el-tag>
                  <span>模型不存在、未启用或接入组未启用</span>
                </template>
                <template v-else-if="isImageUnderstandingApplication(application)">
                  <el-tag type="warning" size="small">未配置</el-tag>
                  <span>未选择 vision 模型，向量化时将跳过正文图片 OCR/理解</span>
                </template>
                <template v-else>
                  <el-tag type="info" size="small">自动回退</el-tag>
                  <span>使用对应类型的全局默认模型</span>
                </template>
              </div>
            </div>
          </div>
          <el-empty v-if="!applicationsLoading && applications.length === 0" description="暂无模型应用配置" />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Loading, View, Hide, Setting, EditPen, Delete, Connection, Search, Document, ArrowLeft, ArrowRight, Fold } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import {
  llmProviderApi,
  type LlmProvider,
  type LlmProviderForm,
  type LlmModel,
  type LlmModelForm,
  type SniffedModel,
  type LlmApplication,
} from '@/api/modules/llmProvider'
import { getRagConfig, type RagConfig } from '@/api/modules/knowledge'
import { resolveErrorMessage } from '@/utils/error'
import AppButton from '@/components/common/AppButton.vue'
import ColumnConfigDialog from '@/components/common/ColumnConfigDialog.vue'
import { useColumnConfig, type ColumnDef } from '@/composables/useColumnConfig'
import { usePermission } from '@/composables/usePermission'
import { useCollapsibleSidebar } from '@/composables/useCollapsibleSidebar'

// ==================== 列字段设置 ====================

const llmModelColumns: ColumnDef[] = [
  { key: 'modelId', label: '模型名称', group: '基础字段', prop: 'modelId', minWidth: 140, showOverflowTooltip: true },
  { key: 'modelType', label: '类型', group: '基础字段', prop: 'modelType', width: 100, align: 'center' },
  { key: 'contextWindow', label: '上下文', group: '基础字段', prop: 'contextWindow', width: 80, align: 'center' },
  { key: 'ownedBy', label: '厂商', group: '基础字段', prop: 'ownedBy', width: 100, align: 'center' },
  { key: 'temperature', label: '温度', group: '参数配置', prop: 'temperature', width: 60, align: 'center' },
  { key: 'maxTokens', label: 'Max Tokens', group: '参数配置', prop: 'maxTokens', width: 90, align: 'center' },
  { key: 'isDefault', label: '默认', group: '状态信息', width: 55, align: 'center' },
  { key: 'enabled', label: '状态', group: '状态信息', width: 60, align: 'center' },
  { key: 'connectivity', label: '连通性', group: '测试信息', width: 90, align: 'center' },
  { key: 'testDuration', label: '最近耗时', group: '测试信息', width: 100, align: 'center', sortable: true },
  { key: 'testAt', label: '测试时间', group: '测试信息', width: 170, align: 'center' },
  { key: 'operations', label: '操作', width: 140, align: 'center', fixed: false },
]

const llmModelDefaultKeys = ['modelId', 'modelType', 'contextWindow', 'ownedBy', 'temperature', 'maxTokens', 'isDefault', 'enabled', 'connectivity', 'testDuration', 'testAt', 'operations']

const {
  showColumnConfig,
  openColumnConfig,
  saveColumns,
  loadColumnConfig,
  columnGroups,
  draftSelectedColumns,
  draftColumnKeys,
  visibleColumns,
  removeDraftColumn,
} = useColumnConfig({
  pageKey: 'llm_model_list',
  columns: llmModelColumns,
  defaultKeys: llmModelDefaultKeys,
})

const activeTab = ref<'providers' | 'applications'>('providers')
const loading = ref(false)
const applicationsLoading = ref(false)
const savingApplicationCode = ref<string | null>(null)
const applications = ref<LlmApplication[]>([])
const submitting = ref(false)
const providers = ref<LlmProvider[]>([])
const selectedProviderId = ref<number | null>(null)

// Computed
const selectedProvider = computed(() => {
  return providers.value.find(p => p.id === selectedProviderId.value) ?? null
})
const selectedProviderModels = computed(() => {
  const models = selectedProvider.value?.models ?? []
  // 默认模型排在最前面，其余按 id 升序
  return [...models].sort((a, b) => {
    const aDef = a.isDefault ? 0 : 1
    const bDef = b.isDefault ? 0 : 1
    if (aDef !== bDef) return aDef - bDef
    return (a.id ?? 0) - (b.id ?? 0)
  })
})

// RAG 配置状态
const ragConfig = ref<RagConfig | null>(null)

// 使用与用户管理一致的侧边栏收缩方案
const providerPanel = useCollapsibleSidebar({
  defaultWidth: 340,
  minWidth: 280,
  maxWidth: 500,
  widthVar: '--provider-panel-width',
  resizerWidth: 4,
  resizerWidthVar: '--provider-panel-resizer-width',
})

// 模型类型快捷筛选
const modelTypeFilter = ref('all')
const modelTypeFilters = computed(() => {
  const all = selectedProviderModels.value
  const countByType = (type: string) => type === 'all'
    ? all.length
    : type === 'chat'
      ? all.filter(m => m.modelType !== 'embedding' && m.modelType !== 'rerank' && m.modelType !== 'vision').length
      : all.filter(m => m.modelType === type).length
  return [
    { value: 'all', label: '全部', count: countByType('all') },
    { value: 'chat', label: '对话', count: countByType('chat') },
    { value: 'embedding', label: 'Embedding', count: countByType('embedding') },
    { value: 'rerank', label: 'Reranker', count: countByType('rerank') },
    { value: 'vision', label: '多模态', count: countByType('vision') },
  ]
})

// Model pagination
const modelCurrentPage = ref(1)
const modelPageSize = 20
const pagedModels = computed(() => {
  let all = selectedProviderModels.value
  if (modelTypeFilter.value === 'embedding') {
    all = all.filter(m => m.modelType === 'embedding')
  } else if (modelTypeFilter.value === 'rerank') {
    all = all.filter(m => m.modelType === 'rerank')
  } else if (modelTypeFilter.value === 'vision') {
    all = all.filter(m => m.modelType === 'vision')
  } else if (modelTypeFilter.value === 'chat') {
    all = all.filter(m => m.modelType !== 'embedding' && m.modelType !== 'rerank' && m.modelType !== 'vision')
  }
  if (all.length <= modelPageSize) return all
  const start = (modelCurrentPage.value - 1) * modelPageSize
  return all.slice(start, start + modelPageSize)
})

// Model batch selection
const selectedModelRows = ref<LlmModel[]>([])
function modelRowClassName({ row }: { row: LlmModel }) {
  return row.isDefault ? 'is-default-model' : ''
}

function handleModelSelectionChange(rows: LlmModel[]) {
  selectedModelRows.value = rows
}

// Reset page when provider or filter changes
watch([() => selectedProviderId.value, () => modelTypeFilter.value], () => {
  modelCurrentPage.value = 1
  selectedModelRows.value = []
})

const providerSavePermission = computed(() =>
  editingProviderId.value ? 'button:llm-provider:update' : 'button:llm-provider:create'
)
const modelSavePermission = computed(() =>
  editingModelId.value ? 'button:llm-provider:update' : 'button:llm-provider:create'
)

// Permission check for isDefault column
const { hasPermission } = usePermission()
const hasUpdatePermission = computed(() => hasPermission('button:llm-provider:update'))

// Provider dialog
const providerDialogVisible = ref(false)
const editingProviderId = ref<number | null>(null)
const providerFormRef = ref<FormInstance>()
const apiKeyVisible = ref(false)
const providerForm = reactive<LlmProviderForm>({
  name: '',
  protocol: 'openai',
  baseUrl: '',
  apiKey: '',
  enabled: true,
})
const providerRules = reactive<FormRules>({
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  protocol: [{ required: true, message: '请选择协议类型', trigger: 'change' }],
  baseUrl: [{ required: true, message: '请输入 API Base URL', trigger: 'blur' }],
})

// Model dialog
const modelDialogVisible = ref(false)
const editingModelId = ref<number | null>(null)
const currentProviderId = ref<number | null>(null)
const modelFormRef = ref<FormInstance>()
const presetTypes = ref<string[]>(['primary', 'haiku', 'sonnet', 'opus', 'embedding', 'rerank', 'vision', 'general'])
const modelForm = reactive<LlmModelForm>({
  name: '',
  modelId: '',
  modelType: 'general',
  dimension: null,
  contextWindow: null,
  ownedBy: null,
  modelCreated: null,
  temperature: 0.3,
  maxTokens: 2048,
  isDefault: false,
  enabled: true,
})
const modelRules = reactive<FormRules>({
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  modelId: [{ required: true, message: '请输入模型ID', trigger: 'blur' }],
})

// Testing state
const testingModels = reactive<Record<number, boolean>>({})
const batchTesting = ref(false)

// Test detail drawer
const testDetailVisible = ref(false)
const testDetailModel = ref<LlmModel | null>(null)

function openTestDetail(model: LlmModel) {
  testDetailModel.value = model
  testDetailVisible.value = true
}

// Sniff state
const sniffDialogVisible = ref(false)
const sniffing = ref(false)
const sniffedModels = ref<SniffedModel[]>([])
const sniffSelectedModelIds = ref<string[]>([])
const sniffProviderId = ref<number | null>(null)
const sniffSearchKeyword = ref('')

const filteredSniffedModels = computed(() => {
  const kw = sniffSearchKeyword.value.trim().toLowerCase()
  if (!kw) return sniffedModels.value
  return sniffedModels.value.filter(m =>
    m.modelId.toLowerCase().includes(kw) || (m.ownedBy?.toLowerCase().includes(kw))
  )
})

const sniffIsAllFilteredSelected = computed(() => {
  const selectable = filteredSniffedModels.value.filter(m => !m.alreadyExists)
  return selectable.length > 0 && selectable.every(m => sniffSelectedModelIds.value.includes(m.modelId))
})

const sniffIsFilteredPartialSelected = computed(() => {
  const selectable = filteredSniffedModels.value.filter(m => !m.alreadyExists)
  const selectedCount = selectable.filter(m => sniffSelectedModelIds.value.includes(m.modelId)).length
  return selectedCount > 0 && selectedCount < selectable.length
})

function handleSniffSelectAllFiltered(checked: boolean) {
  const selectable = filteredSniffedModels.value.filter(m => !m.alreadyExists)
  const selectableIds = selectable.map(m => m.modelId)
  if (checked) {
    const newIds = selectableIds.filter(id => !sniffSelectedModelIds.value.includes(id))
    sniffSelectedModelIds.value = [...sniffSelectedModelIds.value, ...newIds]
  } else {
    sniffSelectedModelIds.value = sniffSelectedModelIds.value.filter(id => !selectableIds.includes(id))
  }
}

// ==================== Lifecycle ====================

onMounted(() => {
  loadProviders()
  loadColumnConfig()
  loadRoles()
  loadRagConfig()
  loadApplications()
})

// ==================== Data ====================

async function loadProviders() {
  loading.value = true
  try {
    const res = await llmProviderApi.list() as any
    providers.value = res?.data ?? res ?? []
    // Auto-select first provider
    if (providers.value.length > 0 && selectedProviderId.value === null) {
      selectedProviderId.value = providers.value[0].id!
    }
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '加载模型接入组失败'))
    providers.value = []
  } finally {
    loading.value = false
  }
  // Refresh RAG config to keep status bar in sync
  loadRagConfig()
}

async function loadRagConfig() {
  try {
    const res = await getRagConfig() as any
    ragConfig.value = res?.data ?? res ?? null
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '加载 RAG 配置失败'))
    ragConfig.value = null
  }
}

async function loadApplications() {
  applicationsLoading.value = true
  try {
    const res = await llmProviderApi.listApplications() as any
    applications.value = res?.data ?? res ?? []
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '加载模型应用失败'))
    applications.value = []
  } finally {
    applicationsLoading.value = false
  }
}

function isImageUnderstandingApplication(application: LlmApplication) {
  return application.code === 'knowledge.image-understanding'
}

function applicationModelLabel(application: LlmApplication) {
  return application.modelType === 'vision' ? '多模态模型' : '默认模型'
}

function modelTypeOptionLabel(modelType: string) {
  return modelType === 'vision' ? '多模态（vision）' : modelType
}

function applicationModelOptions(application: LlmApplication) {
  const result: Array<{ id: number; providerName: string; name: string; modelId: string; modelType: string }> = []
  for (const provider of providers.value) {
    if (!provider.enabled) continue
    for (const model of provider.models ?? []) {
      if (!model.enabled || model.id == null) continue
      const isChat = application.modelType === 'chat'
      const compatible = isChat
        ? model.modelType !== 'embedding' && model.modelType !== 'rerank'
        : model.modelType === application.modelType
      if (!compatible) continue
      result.push({
        id: model.id,
        providerName: provider.name,
        name: model.name,
        modelId: model.modelId,
        modelType: model.modelType,
      })
    }
  }
  return result
}

async function saveApplication(application: LlmApplication) {
  savingApplicationCode.value = application.code
  try {
    const res = await llmProviderApi.updateApplication(application.code, {
      modelId: application.modelId ?? null,
      enabled: application.enabled,
    }) as any
    const updated = res?.data ?? res
    const index = applications.value.findIndex(item => item.code === application.code)
    if (index >= 0 && updated) applications.value[index] = updated
    ElMessage.success('模型应用配置已保存')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '保存模型应用配置失败'))
    await loadApplications()
  } finally {
    savingApplicationCode.value = null
  }
}

async function loadRoles() {
  try {
    const res = await llmProviderApi.getRoles() as any
    const data = res?.data ?? res
    if (Array.isArray(data)) presetTypes.value = data
  } catch { /* use defaults */ }
}

// ==================== Provider CRUD ====================

function resetProviderForm() {
  editingProviderId.value = null
  apiKeyVisible.value = false
  providerForm.name = ''
  providerForm.protocol = 'openai'
  providerForm.baseUrl = ''
  providerForm.apiKey = ''
  providerForm.enabled = true
  providerFormRef.value?.resetFields()
}

function openCreateProvider() {
  resetProviderForm()
  providerDialogVisible.value = true
}

function openEditProvider(row: LlmProvider) {
  editingProviderId.value = row.id!
  apiKeyVisible.value = false
  providerForm.name = row.name
  providerForm.protocol = row.protocol
  providerForm.baseUrl = row.baseUrl
  providerForm.apiKey = row.maskedApiKey
  providerForm.enabled = row.enabled
  providerDialogVisible.value = true
}

async function handleProviderSubmit() {
  const valid = await providerFormRef.value?.validate().catch(() => false)
  if (!valid) return

  if (!editingProviderId.value && !providerForm.apiKey) {
    ElMessage.warning('请输入 API Key')
    return
  }

  submitting.value = true
  try {
    if (editingProviderId.value) {
      await llmProviderApi.update(editingProviderId.value, providerForm)
      ElMessage.success('更新成功')
    } else {
      await llmProviderApi.create(providerForm)
      ElMessage.success('创建成功')
    }
    providerDialogVisible.value = false
    await loadProviders()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, editingProviderId.value ? '更新失败' : '创建失败'))
  } finally {
    submitting.value = false
  }
}

async function handleToggleProvider(row: LlmProvider) {
  try {
    await llmProviderApi.toggle(row.id!)
    ElMessage.success(row.enabled ? '已停用' : '已启用')
    await loadProviders()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '操作失败'))
  }
}

async function handleDeleteProvider(row: LlmProvider) {
  await ElMessageBox.confirm(`确认删除接入组"${row.name}"及其所有模型吗？`, '提示', { type: 'warning' })
  try {
    await llmProviderApi.delete(row.id!)
    ElMessage.success('删除成功')
    if (selectedProviderId.value === row.id) {
      selectedProviderId.value = providers.value.find(p => p.id !== row.id)?.id ?? null
    }
    await loadProviders()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '删除失败'))
  }
}

async function toggleApiKeyVisible() {
  if (!apiKeyVisible.value && editingProviderId.value) {
    try {
      const res = await llmProviderApi.getApiKey(editingProviderId.value) as any
      const data = res?.data ?? res
      providerForm.apiKey = data.apiKey ?? ''
    } catch (error) {
      ElMessage.error(resolveErrorMessage(error, '获取 API Key 失败'))
      return
    }
  }
  apiKeyVisible.value = !apiKeyVisible.value
}

async function handleViewApiKey(row: LlmProvider) {
  try {
    const res = await llmProviderApi.getApiKey(row.id!) as any
    const data = res?.data ?? res
    ElMessageBox.alert(data.apiKey ?? '', 'API Key', { confirmButtonText: '关闭' })
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '获取失败'))
  }
}

// ==================== Model CRUD ====================

function resetModelForm() {
  editingModelId.value = null
  modelForm.name = ''
  modelForm.modelId = ''
  modelForm.modelType = 'general'
  modelForm.dimension = null
  modelForm.contextWindow = null
  modelForm.ownedBy = null
  modelForm.modelCreated = null
  modelForm.temperature = 0.3
  modelForm.maxTokens = 2048
  modelForm.isDefault = false
  modelForm.enabled = true
  modelForm.chunkSize = null
  modelForm.chunkOverlap = null
  modelForm.searchTopK = null
  modelFormRef.value?.resetFields()
}

function openCreateModel(provider: LlmProvider) {
  currentProviderId.value = provider.id!
  resetModelForm()
  modelDialogVisible.value = true
}

function openEditModel(model: LlmModel) {
  currentProviderId.value = model.providerId
  editingModelId.value = model.id!
  modelForm.name = model.name
  modelForm.modelId = model.modelId
  modelForm.modelType = model.modelType
  modelForm.dimension = model.dimension ?? null
  modelForm.contextWindow = model.contextWindow ?? null
  modelForm.ownedBy = model.ownedBy ?? null
  modelForm.modelCreated = model.modelCreated ?? null
  modelForm.temperature = model.temperature
  modelForm.maxTokens = model.maxTokens
  modelForm.isDefault = model.isDefault
  modelForm.enabled = model.enabled
  modelForm.chunkSize = model.chunkSize ?? null
  modelForm.chunkOverlap = model.chunkOverlap ?? null
  modelForm.searchTopK = model.searchTopK ?? null
  modelDialogVisible.value = true
}

async function handleModelSubmit() {
  const valid = await modelFormRef.value?.validate().catch(() => false)
  if (!valid || !currentProviderId.value) return

  submitting.value = true
  try {
    if (editingModelId.value) {
      await llmProviderApi.updateModel(currentProviderId.value, editingModelId.value, modelForm)
      ElMessage.success('更新成功')
    } else {
      await llmProviderApi.addModel(currentProviderId.value, modelForm)
      ElMessage.success('创建成功')
    }
    modelDialogVisible.value = false
    await loadProviders()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, editingModelId.value ? '更新失败' : '创建失败'))
  } finally {
    submitting.value = false
  }
}

async function handleToggleModel(_provider: LlmProvider, model: LlmModel) {
  try {
    await llmProviderApi.toggleModel(model.providerId, model.id!)
    ElMessage.success(model.enabled ? '已停用' : '已启用')
    await loadProviders()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '操作失败'))
  }
}

async function handleToggleDefault(_provider: LlmProvider, model: LlmModel) {
  try {
    await llmProviderApi.toggleDefault(model.providerId, model.id!)
    ElMessage.success(model.isDefault ? '已取消默认' : '已设为默认')
    await loadProviders()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '操作失败'))
  }
}

async function handleDeleteModel(model: LlmModel) {
  await ElMessageBox.confirm(`确认删除模型"${model.name}"吗？`, '提示', { type: 'warning' })
  try {
    await llmProviderApi.deleteModel(model.providerId, model.id!)
    ElMessage.success('删除成功')
    selectedModelRows.value = selectedModelRows.value.filter(m => m.id !== model.id)
    await loadProviders()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '删除失败'))
  }
}

async function handleBatchDeleteModels() {
  if (selectedModelRows.value.length === 0) return
  const names = selectedModelRows.value.map(m => m.name).join('、')
  await ElMessageBox.confirm(`确认批量删除以下 ${selectedModelRows.value.length} 个模型吗？\n${names}`, '批量删除', { type: 'warning' })
  let deleted = 0
  let failed = 0
  for (const model of selectedModelRows.value) {
    try {
      await llmProviderApi.deleteModel(model.providerId, model.id!)
      deleted++
    } catch {
      failed++
    }
  }
  if (failed === 0) {
    ElMessage.success(`已删除 ${deleted} 个模型`)
  } else {
    ElMessage.warning(`删除完成：${deleted} 个成功，${failed} 个失败`)
  }
  selectedModelRows.value = []
  await loadProviders()
}

// ==================== Test ====================

function connLightClass(model: LlmModel): string {
  if (!model.testSuccess) return 'conn-red'
  if (model.testDuration != null && model.testDuration > 5000) return 'conn-yellow'
  return 'conn-green'
}

function connTooltip(model: LlmModel): string {
  if (!model.testSuccess) return model.testError || '连接失败'
  if (model.testDuration != null && model.testDuration > 5000) return `响应较慢 ${model.testDuration}ms`
  return `连通正常 ${model.testDuration ?? 0}ms`
}

async function handleTestModel(model: LlmModel) {
  testingModels[model.id!] = true

  try {
    const timeoutPromise = new Promise<never>((_, reject) => {
      setTimeout(() => reject(new Error('timeout')), 120000)
    })

    const testPromise = llmProviderApi.testModel(model.providerId, model.id!, { userMessage: '你好' })

    const res = await Promise.race([testPromise, timeoutPromise]) as any
    const result = res?.data ?? res

    // 缓存完整测试结果
    model.testResult = result

    if (result.success) {
      if (result.durationMs > 5000) {
        ElMessage.warning(`${model.name} 响应较慢 (${result.durationMs}ms)`)
      } else {
        ElMessage.success(`${model.name} 连通正常 (${result.durationMs}ms)`)
      }
    } else {
      ElMessage.error(`${model.name} 连接失败: ${result.errorMessage}`)
    }
    await loadProviders()
  } catch (error: any) {
    if (error?.message === 'timeout') {
      ElMessage.error(`${model.name} 测试超时 (>120s)`)
    } else {
      ElMessage.error(resolveErrorMessage(error, `${model.name} 请求失败`))
    }
  } finally {
    delete testingModels[model.id!]
  }
}

async function handleBatchTest() {
  if (!selectedProvider.value || selectedProviderModels.value.length === 0) {
    ElMessage.warning('当前接入组没有可测试的模型')
    return
  }

  batchTesting.value = true
  const models = selectedProviderModels.value.filter(m => m.enabled)

  if (models.length === 0) {
    ElMessage.warning('没有启用的模型可测试')
    batchTesting.value = false
    return
  }

  ElMessage.info(`开始并行测试 ${models.length} 个模型...`)

  let successCount = 0
  let failCount = 0
  let timeoutCount = 0

  // 并行启动所有测试，每个测试完成后仅局部更新该模型行的状态
  const testPromises = models.map(async (model) => {
    const result = await testModelWithTimeout(model, 120000)

    // 局部更新模型状态（不刷新整个 providers 列表）
    if (result.success) {
      model.testSuccess = true
      model.testDuration = result.duration ?? null
      model.testError = null
      model.testAt = new Date().toISOString()
      successCount++
    } else if (result.timeout) {
      model.testSuccess = false
      model.testDuration = null
      model.testError = '超时 (>120s)'
      model.testAt = new Date().toISOString()
      timeoutCount++
      failCount++
    } else {
      model.testSuccess = false
      model.testDuration = null
      model.testError = result.errorMessage ?? '连接失败'
      model.testAt = new Date().toISOString()
      failCount++
    }

    return result
  })

  // 等待所有测试完成
  await Promise.all(testPromises)

  batchTesting.value = false

  // 所有模型测试完成后，统一刷新一次接入组列表（更新左侧卡片的总模型数/有效数）
  await loadProviders()

  // 显示最终统计结果
  if (failCount === 0 && timeoutCount === 0) {
    ElMessage.success(`批量测试完成：全部 ${successCount} 个模型连通正常`)
  } else {
    const parts = [`${successCount} 个成功`]
    if (failCount - timeoutCount > 0) parts.push(`${failCount - timeoutCount} 个失败`)
    if (timeoutCount > 0) parts.push(`${timeoutCount} 个超时`)
    ElMessage.warning(`批量测试完成：${parts.join('，')}`)
  }
}

async function testModelWithTimeout(model: LlmModel, timeout: number = 120000): Promise<{ success: boolean; timeout: boolean; duration?: number; errorMessage?: string; result?: any }> {
  testingModels[model.id!] = true

  try {
    const timeoutPromise = new Promise<never>((_, reject) => {
      setTimeout(() => reject(new Error('timeout')), timeout)
    })

    const testPromise = llmProviderApi.testModel(model.providerId, model.id!, { userMessage: '你好' })

    const res = await Promise.race([testPromise, timeoutPromise]) as any
    const result = res?.data ?? res

    delete testingModels[model.id!]

    // 缓存完整测试结果
    model.testResult = result

    if (result.success) {
      return { success: true, timeout: false, duration: result.durationMs, result }
    } else {
      return { success: false, timeout: false, errorMessage: result.errorMessage, result }
    }
  } catch (error: any) {
    delete testingModels[model.id!]

    if (error?.message === 'timeout') {
      return { success: false, timeout: true, errorMessage: `超时 (>${timeout / 1000}s)` }
    }
    return { success: false, timeout: false, errorMessage: resolveErrorMessage(error, '连接失败') }
  }
}

function formatDuration(ms: number): string {
  if (ms < 1000) {
    return `${ms}ms`
  }
  return `${(ms / 1000).toFixed(2)}s`
}

function durationColor(ms: number): string {
  if (ms > 5000) return '#f59e0b' // 黄色
  if (ms > 3000) return '#fb923c' // 橙色
  return '#22c55e' // 绿色
}

function formatTestAt(val: string): string {
  if (!val) return '-'
  const d = new Date(val)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function providerValidCount(p: LlmProvider): number {
  if (!p.models || p.models.length === 0) return 0
  return p.models.filter(m => m.testSuccess === true).length
}

// ==================== Role ====================

function typeTagType(modelType: string): string {
  const map: Record<string, string> = {
    primary: 'primary',
    haiku: 'info',
    sonnet: 'success',
    opus: 'warning',
    embedding: 'info',
    rerank: 'danger',
    vision: 'warning',
    general: 'info',
  }
  return map[modelType] ?? 'info'
}

function sniffTypeTagType(modelType: string): string {
  const map: Record<string, string> = {
    embedding: 'info',
    rerank: 'danger',
  }
  return map[modelType] ?? 'info'
}

// ==================== Sniff ====================

async function handleSniff(row: LlmProvider) {
  sniffProviderId.value = row.id!
  sniffing.value = true
  sniffDialogVisible.value = true
  sniffedModels.value = []
  sniffSelectedModelIds.value = []
  sniffSearchKeyword.value = ''
  try {
    const res = await llmProviderApi.sniffModels(row.id!) as any
    const data = res?.data ?? res ?? []
    sniffedModels.value = data.map((m: SniffedModel) => ({
      ...m,
      inferredType: m.inferredType || inferModelType(m.modelId),
    }))
    sniffSelectedModelIds.value = sniffedModels.value.filter((m: SniffedModel) => !m.alreadyExists).map((m: SniffedModel) => m.modelId)
  } catch (error) {
    ElMessage.error(formatSniffError(error))
    sniffDialogVisible.value = false
  } finally {
    sniffing.value = false
  }
}

/**
 * 根据模型 ID 推断 modelType：
 * - embedding: 包含 embed/embedding/text-embedding/bge/m3e/gte 等关键词
 * - rerank: 包含 rerank/reranker 等关键词
 * - 其他: general
 */
function inferModelType(modelId: string): string {
  const id = modelId.toLowerCase()
  // 先判断 rerank，避免 BAAI/bge-reranker-v2-m3 被 bge 规则误判为 embedding
  if (id.includes('rerank') || id.includes('reranker')) return 'rerank'
  if (
    id.includes('embedding') ||
    id.includes('embed') ||
    id.includes('text-embedding') ||
    id.includes('bge') ||
    id.includes('m3e') ||
    id.includes('gte') ||
    id.includes('e5')
  ) {
    return 'embedding'
  }
  return 'general'
}

function formatContextWindow(val: number | null): string {
  if (val == null) return '-'
  if (val >= 1_000_000) return `${(val / 1_000_000).toFixed(val % 1_000_000 === 0 ? 0 : 1)}M`
  if (val >= 1_000) return `${(val / 1_000).toFixed(val % 1_000 === 0 ? 0 : 1)}K`
  return String(val)
}

function formatCreatedDate(val: number | null): string {
  if (val == null || val === 0) return '-'
  const d = new Date(val * 1000)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function formatSniffError(error: unknown): string {
  const message = resolveErrorMessage(error, '')
  return message ? `嗅探模型失败：${message}` : '嗅探模型失败，请检查接入配置'
}

async function handleSniffImport() {
  if (!sniffProviderId.value || sniffSelectedModelIds.value.length === 0) {
    ElMessage.warning('请选择要导入的模型')
    return
  }
  submitting.value = true
  let imported = 0
  try {
    for (const modelId of sniffSelectedModelIds.value) {
      const model = sniffedModels.value.find(m => m.modelId === modelId)
      if (model && !model.alreadyExists) {
        const inferredType = model.inferredType || inferModelType(model.modelId)
        await llmProviderApi.addModel(sniffProviderId.value!, {
          name: modelId,
          modelId: model.modelId,
          modelType: inferredType,
          dimension: null,
          contextWindow: model.contextWindow ?? null,
          ownedBy: model.ownedBy ?? null,
          modelCreated: model.created ?? null,
          temperature: 0.3,
          maxTokens: 2048,
          isDefault: false,
          enabled: true,
        })
        imported++
      }
    }
    ElMessage.success(`成功导入 ${imported} 个模型`)
    sniffDialogVisible.value = false
    await loadProviders()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '导入模型失败'))
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
.config-container {
  padding: 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.config-header {
  margin-bottom: 20px;
  flex-shrink: 0;
  h2 { margin: 0 0 8px; font-size: 22px; color: var(--color-text-primary); }
  .config-desc { margin: 0; color: var(--color-muted-text); font-size: 14px; }
}

// ==================== 两栏布局 ====================
.config-layout {
  flex: 1;
  display: grid;
  grid-template-columns: var(--provider-panel-width, 340px) var(--provider-panel-resizer-width, 4px) minmax(0, 1fr);
  min-height: 0;
  overflow: hidden;
  position: relative;
}

// ==================== 左侧接入组面板 ====================
.provider-panel {
  display: flex;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  overflow: hidden;

  &.is-collapsed {
    display: none;
  }

  .provider-panel__inner {
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    min-width: 0;
  }
}

// 可拖拽分隔条
.provider-panel__resizer {
  width: 4px;
  cursor: col-resize;
  background: transparent;
  transition: background 0.15s;
  align-self: stretch;
  z-index: 1;

  &:hover,
  &:active {
    background: var(--color-accent);
  }
}

// 折叠时的展开按钮
.provider-panel__expand-btn {
  position: absolute;
  top: 50%;
  left: 0;
  transform: translateY(-50%);
  width: 20px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--color-border);
  border-left: 0;
  border-radius: 0 6px 6px 0;
  background: #fff;
  cursor: pointer;
  z-index: 2;
  box-shadow: 2px 0 6px rgba(0, 0, 0, 0.06);

  &:hover {
    background: #f5f7fa;
  }
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid var(--color-surface-alt);
  flex-shrink: 0;
}

.panel-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.panel-header-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.panel-collapse-btn {
  font-size: 15px;
  cursor: pointer;
  color: #9ca3af;
  padding: 4px;
  border-radius: 4px;
  transition: color 0.15s, background 0.15s;

  &:hover {
    color: #6b7280;
    background: #f3f4f6;
  }
}

.panel-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}

.provider-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.provider-item {
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 6px;
  cursor: pointer;
  border: 1.5px solid transparent;
  transition: all 0.18s ease;
  background: #fafafa;

  &:hover {
    background: #f0f7ff;
    border-color: #d0e3ff;
  }

  &.is-selected {
    background: #eff6ff;
    border-color: #3b82f6;
    box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
  }
}

.provider-item-main {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}

.provider-item-left {
  flex: 1;
  min-width: 0;
}

.provider-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 500;
  color: #1f2937;
  margin-bottom: 4px;
}

.provider-meta {
  font-size: 12px;
  color: #9ca3af;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.provider-item-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  flex-shrink: 0;
}

.provider-count {
  font-size: 11px;
  color: #9ca3af;
}

.provider-item-actions {
  display: flex;
  justify-content: flex-end;
  gap: 2px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #e5e7eb;
}

// ==================== 右侧模型面板 ====================
.model-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  min-width: 0;
  /* 显式指定 grid-column：当 sidebar 折叠（display: none）时，
     防止 main 被错位放到第二个 track 而被压缩到 0 宽 */
  grid-column: 3;
}

.model-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.model-panel .el-table {
  flex: 1;
  border-radius: 0;
}

// ==================== 操作图标 ====================
.action-icon {
  font-size: 15px;
  cursor: pointer;
  color: #6b7280;
  padding: 2px;
  border-radius: 4px;
  transition: color 0.15s, background 0.15s;

  &:hover {
    color: var(--el-color-primary);
    background: rgba(59, 130, 246, 0.08);
  }
  &.primary { color: var(--el-color-primary); }
  &.primary:hover { color: var(--el-color-primary); }
  &.info { color: #6366f1; }
  &.info:hover { color: #4f46e5; background: rgba(99, 102, 241, 0.08); }
  &.danger { color: #ef4444; }
  &.danger:hover { color: #dc2626; background: rgba(239, 68, 68, 0.08); }
}

// ==================== 连通性状态 ====================
.conn-status {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 20px;
}

.testing-text {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 12px;
  color: #6b7280;
}

.conn-pending { color: #d1d5db; font-size: 13px; }

.conn-light {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  box-shadow: 0 0 4px rgba(0, 0, 0, 0.12);
}

.conn-green { background-color: #22c55e; }
.conn-yellow { background-color: #f59e0b; }
.conn-red { background-color: #ef4444; }

// ==================== 对话框 ====================
.form-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 8px 0 16px;
  padding-left: 10px;
  border-left: 3px solid var(--el-color-primary);
  line-height: 1;
}

.form-hint {
  font-size: 12px;
  color: #9ca3af;
  margin-top: 4px;
  line-height: 1.4;
}

.form-static-value {
  font-size: 14px;
  color: #374151;
  line-height: 32px;
}

.provider-form {
  .el-form-item { margin-bottom: 18px; }
}

.form-row {
  display: flex;
  gap: 16px;
  .form-row-item { flex: 1; min-width: 0; }
}

.form-section-card {
  margin-bottom: 18px;
  padding: 18px 18px 6px;
  border: 1px solid #e7edf5;
  border-radius: 16px;
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
}

.form-row--provider {
  align-items: flex-start;
}

.form-switch-item {
  flex: 0 0 132px !important;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.provider-dialog-form {
  :deep(.el-form-item__label) {
    white-space: nowrap;
    color: #4b5563;
    font-weight: 500;
  }

  :deep(.el-input__wrapper),
  :deep(.el-select__wrapper) {
    min-height: 44px;
    border-radius: 12px;
    box-shadow: 0 0 0 1px #d8e2f0 inset;
  }

  :deep(.el-input__wrapper.is-focus),
  :deep(.el-select__wrapper.is-focused) {
    box-shadow: 0 0 0 1px var(--el-color-primary) inset;
  }
}

.provider-dialog {
  :deep(.el-dialog) {
    border-radius: 20px;
    overflow: hidden;
  }

  :deep(.el-dialog__header) {
    margin-right: 0;
    padding: 24px 24px 18px;
    border-bottom: 1px solid #eef2f7;
  }

  :deep(.el-dialog__title) {
    font-size: 18px;
    font-weight: 600;
    color: #1f2937;
    letter-spacing: 0.01em;
  }

  :deep(.el-dialog__body) {
    padding: 20px 24px 8px;
  }

  :deep(.el-dialog__footer) {
    padding: 8px 24px 24px;
  }
}

// 列设置弹窗样式已迁至 src/styles/column-config.scss（全局）

// ==================== 测试详情 Drawer ====================
.test-detail-section {
  margin-bottom: 20px;
}

.test-detail-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 10px;
  padding-left: 10px;
  border-left: 3px solid var(--el-color-primary);
  line-height: 1;
}

.test-detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.test-detail-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.test-detail-label {
  font-size: 12px;
  color: #9ca3af;
  font-weight: 500;
}

.test-detail-value {
  font-size: 14px;
  color: var(--color-text-primary);
  font-weight: 500;
  word-break: break-all;
}

.test-detail-error-box {
  padding: 12px 14px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 8px;
  font-size: 13px;
  color: #dc2626;
  line-height: 1.6;
  word-break: break-all;
  white-space: pre-wrap;
}

.test-detail-content-box {
  padding: 12px 14px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: 8px;
  font-size: 13px;
  color: #166534;
  line-height: 1.6;
  word-break: break-all;
  white-space: pre-wrap;
  max-height: 300px;
  overflow-y: auto;
}

.conn-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.6;
}

.conn-badge-success {
  background: #dcfce7;
  color: #16a34a;
}

.conn-badge-fail {
  background: #fef2f2;
  color: #dc2626;
}

// ==================== 嗅探搜索 ====================
.sniff-model-list {
  max-height: 340px;
  overflow-y: auto;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 8px 12px;
  background: #fafbfc;
}

.sniff-model-item {
  padding: 6px 4px;
  border-radius: 6px;
  transition: background 0.15s;

  &:hover {
    background: #f0f7ff;
  }

  :deep(.el-checkbox__label) {
    font-size: 13px;
    line-height: 1.6;
  }
}

.sniff-model-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sniff-model-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.sniff-model-id {
  font-weight: 500;
}

.sniff-model-meta {
  display: flex;
  gap: 16px;
  padding-left: 2px;
}

.sniff-meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
}

.sniff-meta-label {
  color: #9ca3af;
}

.sniff-meta-value {
  color: #6b7280;
  font-variant-numeric: tabular-nums;
}

.sniff-select-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
  margin-bottom: 4px;
  font-size: 13px;
  color: #4b5563;
}

// ==================== 模型表格底栏 ====================
.model-table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-top: 1px solid var(--color-surface-alt);
  background: #fafbfc;
  flex-shrink: 0;
}

.model-table-footer-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.model-selection-info {
  font-size: 12px;
  color: #6b7280;
}

.model-pagination {
  :deep(.el-pagination) {
    margin: 0;
  }
}

.conn-badge-pending {
  background: #f3f4f6;
  color: #9ca3af;
}

// ==================== RAG 状态条 ====================
.rag-status-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 16px;
  margin-bottom: 16px;
  background: #f8fbff;
  border: 1px solid #d8e2f0;
  border-radius: 12px;
  flex-shrink: 0;
}

.rag-status-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.rag-status-label {
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
}

.rag-status-value {
  font-size: 13px;
  font-weight: 500;
  color: #1f2937;
}

.rag-status-ok {
  font-size: 12px;
  color: #16a34a;
  font-weight: 500;
}

.rag-status-warn {
  font-size: 12px;
  color: #f59e0b;
  font-weight: 500;
}

.rag-status-missing {
  font-size: 12px;
  color: #ef4444;
  font-weight: 500;
}

// ==================== 模型类型筛选 ====================
.model-type-filter {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px;
  border-bottom: 1px solid var(--color-surface-alt);
  flex-shrink: 0;
}

.model-type-filter-item {
  font-size: 13px;
  padding: 4px 10px;
  border-radius: 6px;
  cursor: pointer;
  color: #6b7280;
  transition: all 0.15s;
  user-select: none;

  sup {
    font-size: 10px;
    color: #9ca3af;
    margin-left: 1px;
  }

  &:hover {
    background: #f0f7ff;
    color: #3b82f6;
  }

  &.is-active {
    background: #eff6ff;
    color: #2563eb;
    font-weight: 600;

    sup { color: #2563eb; }
  }
}

// 默认模型名称样式
.model-name-default {
  font-weight: 600;
  color: var(--el-color-primary, #2563eb);
}

// 默认模型行高亮
:deep(.el-table__row) {
  &.is-default-model {
    background-color: #f0f7ff;
  }
}


.llm-tabs {
  margin-top: 16px;
}

.application-panel {
  min-height: 360px;
}

.application-intro {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin: 8px 0 16px;
}

.application-intro h3 {
  margin: 0 0 6px;
  color: var(--el-text-color-primary);
  font-size: 18px;
}

.application-intro p,
.application-description {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.application-alert {
  margin-bottom: 16px;
}

.application-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(330px, 1fr));
  gap: 16px;
}

.application-card {
  padding: 18px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.application-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.application-name {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.application-code {
  margin-top: 3px;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.application-card .el-form {
  margin-top: 16px;
}

.application-card .el-form-item {
  margin-bottom: 10px;
}

.application-current {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 24px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

</style>
