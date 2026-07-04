<template>
  <PageContainer variant="card" :breadcrumb="false">
    <div v-loading="loading" class="detail-page">
      <template v-if="detail">
        <div class="detail-layout">
          <div class="detail-main">
            <div v-if="showPrimaryActions" class="detail-actions">
              <div class="detail-actions__primary">
                <el-button v-if="canEditRequirement" type="primary" @click="handleEdit">
                  <el-icon style="margin-right:4px"><Edit /></el-icon>编辑
                </el-button>
                <el-button v-if="canSplitRequirement" type="success" @click="handleSplit">
                  <el-icon style="margin-right:4px"><List /></el-icon>拆分子需求
                </el-button>
                <el-popconfirm v-if="canDeleteRequirement" title="确定删除该需求吗？" @confirm="handleDelete">
                  <template #reference>
                    <el-button type="danger" plain>
                      <el-icon style="margin-right:4px"><Delete /></el-icon>删除
                    </el-button>
                  </template>
                </el-popconfirm>
              </div>
            </div>

        <!-- Tabs -->
        <el-tabs v-model="activeTab" class="detail-tabs">
        <!-- 基本信息 -->
        <el-tab-pane name="basic">
          <template #label>
            <span class="detail-tab-label">
              <el-icon><Document /></el-icon>
              <span>基本信息</span>
            </span>
          </template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="所属项目">{{ projectLabel(detail.projectId) }}</el-descriptions-item>
            <el-descriptions-item label="需求编号">{{ detail.requirementNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="需求类型">{{ typeLabel(detail.type) }}</el-descriptions-item>
            <el-descriptions-item label="优先级">
              <el-tag :type="priorityTagType(detail.priority, priorityMap, priorityColorMap)" :style="priorityTagStyle(priorityColorMap[detail.priority])">{{ priorityLabel(detail.priority, priorityMap) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusTagType(detail.status)">{{ detail.status }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="提出人">{{ detail.creatorName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="当前处理人">{{ detail.currentHandlerName || detail.assigneeName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDate(detail.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="所属迭代">{{ detail.iterationId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="期望上线日期">{{ formatDate(detail.dueDate, 'YYYY-MM-DD') }}</el-descriptions-item>
            <el-descriptions-item label="分析完成时间">{{ detail.analysisCompletedAt ? formatDate(detail.analysisCompletedAt, 'YYYY-MM-DD HH:mm:ss') : '-' }}</el-descriptions-item>
            <el-descriptions-item label="需求确认时间">{{ detail.confirmAt ? formatDate(detail.confirmAt, 'YYYY-MM-DD HH:mm:ss') : '-' }}</el-descriptions-item>
            <el-descriptions-item label="开发完成时间">{{ detail.developmentCompletedAt ? formatDate(detail.developmentCompletedAt, 'YYYY-MM-DD HH:mm:ss') : '-' }}</el-descriptions-item>
            <el-descriptions-item label="估算工时">{{ detail.estimatedHours ? detail.estimatedHours + ' 小时' : '-' }}</el-descriptions-item>
            <el-descriptions-item label="实际工时">{{ detail.actualHours ? detail.actualHours + ' 小时' : '-' }}</el-descriptions-item>
          </el-descriptions>

          <!-- 工单内容单独展示 -->
          <div class="description-content-section">
            <div class="description-content-section__header">
              <span class="description-content-section__label">工单内容</span>
            </div>
            <div class="description-content-section__body">
              <div
                v-if="detail.description"
                class="rich-content"
                v-html="richDescription"
                @click="handleRichContentClick"
              ></div>
              <el-empty v-else description="暂无内容" :image-size="48" />
            </div>
          </div>
        </el-tab-pane>

        <!-- 附件 -->
        <el-tab-pane name="attachments">
          <template #label>
            <span class="detail-tab-label">
              <el-icon><Picture /></el-icon>
              <span>附件 ({{ attachmentCount }})</span>
            </span>
          </template>
          <div class="attachments-tab">
            <div class="section-header">
              <h3>需求附件</h3>
              <span class="section-hint">支持下载与在线预览</span>
            </div>
            <div v-if="detail.attachments?.length" class="attachment-list">
              <div v-for="attachment in detail.attachments" :key="attachment.fileId || attachment.url" class="attachment-item">
                <el-button link type="primary" class="attachment-name" @click="handleAttachmentDownload(attachment)">{{ attachment.name }}</el-button>
                <span class="attachment-meta">{{ formatAttachmentMeta(attachment) }}</span>
                <el-button
                  v-if="canPreviewAttachment(attachment)"
                  link
                  class="attachment-preview"
                  title="预览"
                  aria-label="预览附件"
                  @click="handleAttachmentPreview(attachment)"
                >
                  <el-icon><View /></el-icon>
                </el-button>
              </div>
            </div>
            <div v-else class="attachment-empty">暂无附件</div>

            <div v-if="detail.transitionAttachments?.length" class="attachment-transition-list">
              <div class="section-header">
                <h3>流转附件</h3>
              </div>
              <div
                v-for="group in detail.transitionAttachments"
                :key="group.transitionId ?? group.nodeName ?? ''"
                class="attachment-transition-group"
              >
                <div class="attachment-transition-header">
                  <span class="attachment-transition-node">
                    <i class="ri-node-tree" />
                    <strong>{{ group.nodeName || '流转节点' }}</strong>
                  </span>
                  <span class="attachment-transition-meta">
                    <span v-if="group.operatorName">{{ group.operatorName }}</span>
                    <span v-if="group.operatedAt">{{ group.operatedAt ? formatDateTime(group.operatedAt) : '' }}</span>
                  </span>
                </div>
                <div class="attachment-list">
                  <div
                    v-for="attachment in group.attachments"
                    :key="attachment.fileId || attachment.url"
                    class="attachment-item"
                  >
                    <el-button link type="primary" class="attachment-name" @click="handleAttachmentDownload(attachment)">{{ attachment.name }}</el-button>
                    <span class="attachment-meta">{{ formatAttachmentMeta(attachment) }}</span>
                    <el-button
                      v-if="canPreviewAttachment(attachment)"
                      link
                      class="attachment-preview"
                      title="预览"
                      aria-label="预览附件"
                      @click="handleAttachmentPreview(attachment)"
                    >
                      <el-icon><View /></el-icon>
                    </el-button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- 子需求 -->
        <el-tab-pane v-if="children.length > 0" name="children">
          <template #label>
            <span class="detail-tab-label">
              <el-icon><List /></el-icon>
              <span>子需求 ({{ children.length }})</span>
            </span>
          </template>
          <div class="children-section">
            <div class="section-header">
              <h3>子需求（{{ children.length }} 个）</h3>
              <el-button v-if="canSplitRequirement" type="primary" size="small" @click="handleSplit">+ 拆分子需求</el-button>
            </div>
            <el-table :data="children" border size="small">
              <el-table-column label="ID" width="60" align="center">
                <template #default="{ row }">{{ row.id }}</template>
              </el-table-column>
              <el-table-column label="标题" min-width="200">
                <template #default="{ row }">
                  <el-link type="primary" @click="router.push({ name: 'RequirementDetail', params: { id: row.id } })">
                    {{ row.title }}
                  </el-link>
                </template>
              </el-table-column>
              <el-table-column label="需求编号" min-width="180" align="center">
                <template #default="{ row }">{{ row.requirementNo || '-' }}</template>
              </el-table-column>
              <el-table-column label="类型" width="80" align="center">
                <template #default="{ row }">{{ typeLabel(row.type) }}</template>
              </el-table-column>
              <el-table-column label="优先级" width="80" align="center">
                <template #default="{ row }">
                  <el-tag :type="priorityTagType(row.priority, priorityMap, priorityColorMap)" :style="priorityTagStyle(priorityColorMap[row.priority])" size="small">{{ priorityLabel(row.priority, priorityMap) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- 流转历史 -->
        <el-tab-pane name="history">
          <template #label>
            <span class="detail-tab-label">
              <el-icon><Histogram /></el-icon>
              <span>流转历史 ({{ history.length }})</span>
            </span>
          </template>
          <el-empty v-if="history.length === 0" description="暂无流转历史" :image-size="60" />
          <el-timeline v-else class="requirement-history-timeline">
            <el-timeline-item
              v-for="item in history"
              :key="item.id"
              :timestamp="formatDate(item.createdAt)"
              placement="top"
            >
              <div class="history-item">
                <div class="history-item__title">
                  <strong>{{ item.operatorName || '系统' }}</strong>
                  <el-tag size="small" effect="plain">{{ item.fieldName || '流转记录' }}</el-tag>
                </div>
                <div class="history-item__content">
                  <span>{{ item.oldValue || '-' }}</span>
                  <span class="history-item__arrow">-&gt;</span>
                  <span>{{ item.newValue || '-' }}</span>
                </div>
                <div v-if="item.comment" class="history-item__comment">
                  <el-icon class="comment-icon"><ChatDotRound /></el-icon>
                  <span class="comment-text">{{ item.comment }}</span>
                </div>
              </div>
            </el-timeline-item>
          </el-timeline>
        </el-tab-pane>

        <!-- 审核记录 -->
        <el-tab-pane name="approvals">
          <template #label>
            <span class="detail-tab-label">
              <el-icon><ChatLineRound /></el-icon>
              <span>审核记录 ({{ approvalEvaluations.length }})</span>
            </span>
          </template>
          <div class="approval-evaluations-section">
            <div class="section-header">
              <h3>审核记录</h3>
              <span class="section-hint">按时间倒序展示提交、通过、驳回与取消意见</span>
            </div>
            <el-empty v-if="sortedApprovalEvaluations.length === 0" description="暂无审核记录" :image-size="60" />
            <div v-else class="approval-evaluation-list">
              <div
                v-for="item in sortedApprovalEvaluations"
                :key="item.id"
                class="approval-evaluation-card"
                :class="`is-${approvalResultTagType(item.result)}`"
              >
                <div class="approval-evaluation-card__timeline">
                  <div class="approval-evaluation-card__dot" :class="`is-${approvalResultTagType(item.result)}`" />
                  <div class="approval-evaluation-card__line" />
                </div>
                <div class="approval-evaluation-card__body">
                  <div class="approval-evaluation-header">
                    <el-avatar :size="28" class="approval-evaluation-avatar">{{ item.evaluatorName?.charAt(0) || '审' }}</el-avatar>
                    <div class="approval-evaluation-meta">
                      <div class="approval-evaluation-title">
                        <strong>{{ item.evaluatorName || '处理人' }}</strong>
                        <el-tag v-if="item.id !== -1" size="small" effect="dark" round :type="approvalResultTagType(item.result)">
                          {{ item.resultLabel || item.actionLabel || '审核' }}
                        </el-tag>
                        <span class="approval-evaluation-node">{{ item.nodeName }}</span>
                      </div>
                      <div class="approval-evaluation-time">{{ formatDate(item.createdAt) }}</div>
                    </div>
                    <div v-if="item.rating || item.canSupplement" class="approval-evaluation-side">
                      <div v-if="item.rating" class="approval-evaluation-rating">
                        <el-rate :model-value="item.rating" disabled size="small" />
                      </div>
                      <el-button
                        v-if="item.canSupplement"
                        link
                        type="primary"
                        class="approval-evaluation-supplement-button"
                        title="补充意见"
                        aria-label="补充意见"
                        @click="openSupplementDialog(item)"
                      >
                        <el-icon><ChatDotRound /></el-icon>
                      </el-button>
                    </div>
                  </div>
                  <p v-if="item.content" class="approval-evaluation-content">{{ item.content }}</p>
                  <div v-if="item.attachments?.length" class="approval-evaluation-attachments approval-evaluation-attachments--main">
                    <span class="approval-evaluation-attachments__label">附件：</span>
                    <ul class="approval-evaluation-attachments__list">
                      <li v-for="(att, idx) in item.attachments" :key="idx">
                        <a class="approval-evaluation-attachments__link" @click="downloadAttachmentFile(att)">{{ att.name }}</a>
                      </li>
                    </ul>
                  </div>
                  <div v-if="item.supplements?.length" class="approval-supplement-list">
                    <div v-for="supplement in item.supplements" :key="supplement.id" class="approval-supplement-item">
                      <div class="approval-supplement-item__header">
                        <span class="approval-supplement-item__tag">补充</span>
                        <strong>{{ supplement.evaluatorName || '处理人' }}</strong>
                        <span class="approval-supplement-item__time">{{ formatDate(supplement.createdAt) }}</span>
                      </div>
                      <p class="approval-supplement-item__content">{{ supplement.content || '未填写补充意见' }}</p>
                      <div v-if="supplement.attachments?.length" class="approval-evaluation-attachments">
                        <span class="approval-evaluation-attachments__label">附件：</span>
                        <ul class="approval-evaluation-attachments__list">
                          <li v-for="(att, idx) in supplement.attachments" :key="idx">
                            <a class="approval-evaluation-attachments__link" @click="downloadAttachmentFile(att)">{{ att.name }}</a>
                          </li>
                        </ul>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- 评论 -->
        <el-tab-pane name="comments">
          <template #label>
            <span class="detail-tab-label">
              <el-icon><ChatDotRound /></el-icon>
              <span>评论 ({{ comments.length }})</span>
            </span>
          </template>
          <!-- 评论区 -->
          <div class="comment-section-block">
            <div class="section-header">
              <h3>评论</h3>
            </div>
            <template v-if="canEditComment">
              <div class="comment-editor-wrapper">
                <IsleEditorToolbar v-if="commentEditorInstance" :editor="commentEditorInstance" />
                <div class="comment-editor-toolbar">
                  <el-button
                    link
                    type="primary"
                    size="small"
                    :loading="commentImageUploading"
                    @click="triggerCommentFileInput"
                  >
                    <el-icon style="margin-right: 4px"><Picture /></el-icon>插入图片
                  </el-button>
                  <span class="comment-editor-hint">支持点击选图 / 拖拽 / Ctrl+V 粘贴</span>
                  <input
                    ref="commentFileInputRef"
                    type="file"
                    accept="image/*"
                    multiple
                    style="display: none"
                    @change="handleCommentFileInput"
                  />
                </div>
                <div
                  class="comment-editor-dropzone"
                  :class="{ 'is-dragover': commentIsDragOver }"
                  @dragenter.prevent.stop="handleCommentDragEnter"
                  @dragover.prevent.stop="handleCommentDragOver"
                  @dragleave.prevent.stop="handleCommentDragLeave"
                  @drop.prevent.stop="handleCommentDrop"
                >
                  <IsleEditor v-model="commentRichText" :extensions="commentEditorExtensions" locale="zh" @create="onCommentEditorCreate" />
                </div>
              </div>
              <div class="comment-editor-actions">
                <template v-if="commentDraftDirty">
                  <el-button :disabled="commentSubmitting" @click="cancelCommentDraft">
                    取消
                  </el-button>
                  <el-button type="primary" :loading="commentSubmitting" @click="submitCommentRich">
                    保存
                  </el-button>
                </template>
              </div>
            </template>
            <el-empty v-if="comments.length === 0" description="暂无评论" :image-size="40" />
            <div v-for="comment in comments" :key="comment.id" class="comment-item">
              <el-avatar :size="32">{{ comment.userName?.charAt(0) || 'U' }}</el-avatar>
              <div class="comment-content">
                <div class="comment-header">
                  <strong>{{ comment.userName || '用户' }}</strong>
                  <span class="comment-time">{{ formatDate(comment.createdAt) }}</span>
                </div>
                <div
                  class="rich-content comment-body"
                  v-html="hydrateRichTextImageHtml(comment.content || '')"
                  @click="handleRichContentClick"
                ></div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        </el-tabs>

          </div>

          <aside
            v-if="showWorkflowActionPanel"
            :class="['workflow-sidebar', { 'is-collapsed': workflowPanelCollapsed }]"
          >
            <div class="workflow-sidebar__rail">
              <el-button
                class="workflow-sidebar__toggle-button"
                text
                circle
                :aria-label="workflowPanelCollapsed ? '展开审批面板' : '折叠审批面板'"
                :aria-expanded="!workflowPanelCollapsed"
                @click="workflowPanelCollapsed = !workflowPanelCollapsed"
              >
                <el-icon>
                  <ArrowRightBold v-if="workflowPanelCollapsed" />
                  <ArrowLeftBold v-else />
                </el-icon>
              </el-button>
              <span v-if="workflowPanelCollapsed" class="workflow-sidebar__collapsed-title">审批功能</span>
            </div>

            <div v-if="!workflowPanelCollapsed" class="workflow-action-panel">
              <div class="workflow-action-panel__header">
                <div class="workflow-action-panel__header-icon">
                  <el-icon><CircleCheck /></el-icon>
                </div>
                <div>
                  <div class="workflow-action-panel__title">审批操作</div>
                  <div class="workflow-action-panel__subtitle">当前节点决策与流转</div>
                </div>
              </div>

              <div v-if="showCurrentNodeStatus" class="workflow-action-panel__status-bar">
                <div class="workflow-action-panel__status-node">
                  <span class="workflow-action-panel__status-dot" :class="`is-${statusTagType(currentNodeStatusName)}`" />
                  <span class="workflow-action-panel__status-label">当前节点</span>
                  <span class="workflow-action-panel__status-value">{{ currentNodeDisplayName }}</span>
                </div>
                <el-tag
                  v-if="showNodeStatusTag"
                  size="small"
                  effect="light"
                  round
                  :type="statusTagType(currentNodeStatusName)"
                >
                  {{ currentNodeStatusName }}
                </el-tag>
              </div>

              <el-alert
                v-if="usingUnifiedEngine && !isWorkflowActive"
                class="workflow-action-panel__alert"
                type="warning"
                :closable="false"
                show-icon
                title="当前工作流已停用"
                description="因工作流正调整中或已停用，暂不支持提交审核、驳回或取消等操作。请等待管理员重新启用工作流。"
              />

              <div class="workflow-action-panel__body">
                <div class="workflow-action-panel__section">
                  <div class="workflow-action-panel__section-title">流转配置</div>

                  <div class="workflow-action-panel__field">
                    <span class="workflow-action-panel__field-label">目标节点</span>
                    <el-select
                      v-model="selectedTransitionTargetId"
                      :disabled="transitionLoading || transitionOptions.length === 0"
                      :placeholder="transitionOptions.length > 0 ? '选择目标节点' : '当前无可执行操作'"
                      class="workflow-action-panel__control"
                    >
                      <el-option
                        v-for="transition in transitionOptions"
                        :key="transitionOptionKey(transition)"
                        :label="transitionOptionLabel(transition)"
                        :value="transitionOptionValue(transition)"
                      />
                    </el-select>
                  </div>

                  <div v-if="selectedTransitionScopeLabel" class="workflow-action-panel__field">
                    <span class="workflow-action-panel__field-label">{{ selectedTransitionScopeLabel }}</span>
                    <div class="workflow-action-panel__assignee-readonly">
                      {{ selectedTransitionScopeName || selectedTransitionAssigneeTypeName }}
                    </div>
                  </div>

                  <div class="workflow-action-panel__field">
                    <span class="workflow-action-panel__field-label">处理人</span>
                    <el-select
                      v-if="showTransitionAssigneeSelector"
                      v-model="selectedTransitionAssigneeId"
                      filterable
                      clearable
                      placeholder="请选择处理人"
                      class="workflow-action-panel__control"
                    >
                      <el-option
                        v-for="candidate in selectedTransitionAssigneeCandidates"
                        :key="candidate.id"
                        :label="candidate.name"
                        :value="candidate.id"
                      />
                    </el-select>
                    <div v-else class="workflow-action-panel__assignee-readonly">
                      {{ selectedTransitionAssigneeName }}
                    </div>
                  </div>

                  <div v-if="requiresProjectBinding" class="workflow-action-panel__field">
                    <span class="workflow-action-panel__field-label">绑定项目</span>
                    <el-select
                      v-model="bindingProjectId"
                      filterable
                      clearable
                      placeholder="流转前绑定项目"
                      class="workflow-action-panel__control"
                    >
                      <el-option
                        v-for="project in bindableProjects"
                        :key="project.id"
                        :label="projectOptionLabel(project)"
                        :value="project.id"
                      />
                    </el-select>
                  </div>

                  <div
                    v-if="workflowRuntime.parallelActive && parallelBranches.length > 0"
                    class="workflow-action-panel__field"
                  >
                    <span class="workflow-action-panel__field-label">并行分支</span>
                    <el-select
                      :model-value="workflowRuntime.activeParallelBranchId"
                      placeholder="切换并行分支"
                      class="workflow-action-panel__control"
                      @change="handleSwitchParallelBranch"
                    >
                      <el-option
                        v-for="branch in parallelBranches"
                        :key="branch.id"
                        :label="`${branch.branchName} (${parallelBranchStatusLabel(branch.status)})`"
                        :value="branch.id"
                        :disabled="branch.status === 'completed' || branch.status === 'skipped'"
                      />
                    </el-select>
                  </div>
                </div>

                <div class="workflow-action-panel__section">
                  <div class="workflow-action-panel__section-title">操作</div>

                  <div class="workflow-action-panel__actions">
                    <AppButton
                      v-if="usingUnifiedEngine && workflowRuntime.canCountersign"
                      permission="button:requirement:submit"
                      @click="openCountersignDialog(workflowRuntime.currentNodeId || '')"
                    >
                      会签审批
                    </AppButton>
                    <AppButton
                      v-if="usingUnifiedEngine && workflowRuntime.canRollback"
                      type="danger"
                      plain
                      :loading="transitionLoading"
                      permission="button:requirement:rollback"
                      @click="handleRollback"
                    >
                      驳回
                    </AppButton>
                    <AppButton
                      v-if="usingUnifiedEngine && workflowRuntime.canCancel"
                      :loading="transitionLoading"
                      permission="button:requirement:cancel"
                      @click="handleCancel"
                    >
                      取消
                    </AppButton>
                  </div>

                  <div class="workflow-action-panel__submit-actions">
                    <AppButton
                      type="primary"
                      permission="button:requirement:submit"
                      :loading="transitionLoading"
                      :disabled="transitionSubmitDisabled"
                      @click="handleStatusTransition"
                    >
                      提交
                    </AppButton>
                  </div>
                </div>
              </div>
            </div>
          </aside>
        </div>

        <el-dialog
          v-model="supplementDialogVisible"
          title="补充意见"
          width="480px"
          :close-on-click-modal="false"
          @closed="resetSupplementDialog"
        >
          <p class="approval-dialog-tip">补充内容会追加在原审核记录下方，不会覆盖原始意见。</p>
          <el-input
            v-model="supplementContent"
            type="textarea"
            :rows="4"
            placeholder="请输入补充意见"
            maxlength="1000"
            show-word-limit
          />
          <div v-if="canSubmitApproval" class="approval-attachment-section" style="margin-top: 12px;">
            <div class="approval-attachment-header">
              <span>附件材料</span>
            </div>
            <AttachmentUploader
              v-model="supplementAttachments"
              :show-preview="false"
            />
          </div>
          <template #footer>
            <el-button @click="supplementDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="supplementSubmitting" @click="submitSupplement">
              提交补充
            </el-button>
          </template>
        </el-dialog>

        <el-dialog
          v-model="approvalDialogVisible"
          title="审核操作"
          width="480px"
          :close-on-click-modal="false"
          @closed="resetApprovalDialog"
        >
          <p class="approval-dialog-tip">
            提交到下一节点前{{ workflowRuntime.evaluationRequired ? '请补充审核信息' : '可补充审核信息（选填）' }}。
          </p>
          <el-form-item
            v-if="workflowRuntime.evaluationRequired"
            label="评分"
            :required="true"
            class="approval-dialog-rate-item"
          >
            <el-rate
              v-model="approvalRating"
              :max="5"
              show-text
              :texts="['不满意', '一般', '满意', '比较满意', '非常满意']"
              class="approval-dialog-rate"
            />
          </el-form-item>
          <el-form-item
            label="审核意见"
            :required="isCommentRequired"
            :error="approvalCommentError"
            style="margin-bottom: 12px;"
          >
            <el-input
              v-model="approvalComment"
              type="textarea"
              :rows="4"
              :placeholder="isCommentRequired ? '请输入审核意见（必填）' : '请输入审核意见（选填）'"
              maxlength="1000"
              show-word-limit
            />
          </el-form-item>
          <div v-if="canSubmitApproval" class="approval-attachment-section">
            <div class="approval-attachment-header">
              <span>附件材料</span>
            </div>
            <AttachmentUploader
              v-model="approvalAttachments"
              :show-preview="false"
              :required="isAttachmentRequired"
            />
          </div>
          <template #footer>
            <el-button @click="approvalDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="transitionLoading" @click="confirmApprovalTransition">
              确认提交
            </el-button>
          </template>
        </el-dialog>

        <!-- 会签审批对话框 -->
        <el-dialog
          v-model="countersignDialogVisible"
          title="会签审批"
          width="500px"
          :close-on-click-modal="false"
        >
          <div v-if="currentCountersignRecords.length > 0" class="countersign-records">
            <div class="countersign-records-title">会签记录</div>
            <el-table :data="currentCountersignRecords" size="small" border>
              <el-table-column prop="approverName" label="会签人" />
              <el-table-column prop="status" label="状态" width="80">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'approved' ? 'success' : row.status === 'rejected' ? 'danger' : 'info'" size="small">
                    {{ row.status === 'approved' ? '已通过' : row.status === 'rejected' ? '已驳回' : '待审批' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="rating" label="评分" width="60">
                <template #default="{ row }">
                  {{ row.rating ? row.rating + '星' : '-' }}
                </template>
              </el-table-column>
              <el-table-column prop="comment" label="意见" min-width="120" show-overflow-tooltip />
            </el-table>
          </div>
          <div v-if="canCountersign" class="countersign-submit">
            <p class="countersign-tip">请对本次会签进行审批操作</p>
            <div class="countersign-rate">
              <span class="countersign-label">评分</span>
              <el-rate v-model="countersignRating" :max="5" allow-half />
            </div>
            <el-input
              v-model="countersignComment"
              type="textarea"
              :rows="3"
              placeholder="请输入审批意见（选填）"
              maxlength="500"
              show-word-limit
            />
          </div>
          <div v-else-if="!countersignDialogLoading" class="countersign-empty">
            <el-empty description="您不是当前节点的会签人，无需操作" />
          </div>
          <template #footer>
            <el-button @click="countersignDialogVisible = false">关闭</el-button>
            <el-button v-if="canCountersign" v-permission="'button:requirement:countersign-approve'" type="success" @click="handleCountersignApprove">通过</el-button>
            <el-button v-if="canCountersign" v-permission="'button:requirement:countersign-reject'" type="danger" @click="handleCountersignReject">驳回</el-button>
          </template>
        </el-dialog>
      </template>
    </div>

    <!-- 附件预览弹窗（与知识库管理共用 FilePreviewDialog） -->
    <FilePreviewDialog
      v-if="previewFile"
      v-model="previewVisible"
      :file-name="previewFile.name"
      :file-type="getFileExt(previewFile.name)"
      :file-id="previewFile.fileId || undefined"
      :download-url="previewFile.url"
    />

    <el-dialog
      v-model="richImagePreviewVisible"
      class="rich-image-preview-dialog"
      :title="richImagePreviewTitle"
      width="86%"
      top="5vh"
      :show-close="true"
      @closed="resetRichImagePreview"
    >
      <template #header>
        <div class="rich-image-preview-header">
          <span class="rich-image-preview-title">{{ richImagePreviewTitle }}</span>
          <div class="rich-image-preview-toolbar">
            <el-button size="small" :disabled="richImagePreviewZoom <= 50" title="缩小" @click="zoomOutRichImage">
              <el-icon><ZoomOut /></el-icon>
            </el-button>
            <span class="rich-image-preview-zoom">{{ richImagePreviewZoom }}%</span>
            <el-button size="small" :disabled="richImagePreviewZoom >= 300" title="放大" @click="zoomInRichImage">
              <el-icon><ZoomIn /></el-icon>
            </el-button>
            <el-button size="small" title="重置为100%" @click="resetRichImageZoom">
              <el-icon><RefreshLeft /></el-icon>
            </el-button>
          </div>
        </div>
      </template>
      <div class="rich-image-preview-body" @wheel.prevent="handleRichImageWheel">
        <img
          v-if="richImagePreviewSrc"
          :src="richImagePreviewSrc"
          :alt="richImagePreviewTitle"
          class="rich-image-preview-img"
          :style="{ transform: `scale(${richImagePreviewZoom / 100})` }"
        />
      </div>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, watch, type Ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeftBold, ArrowRightBold, Document, Picture, List, Histogram, ChatLineRound, ChatDotRound, View, Download, Edit, Delete, ZoomIn, ZoomOut, RefreshLeft, CircleCheck } from '@element-plus/icons-vue'
import { requirementApi, projectApi, relationApi } from '@/api'
import { downloadRequirementAttachment, uploadRequirementAttachment } from '@/api/modules/file'
import type { RelationItem } from '@/api/modules/relation'
import { requirementConfigApi } from '@/api/modules/requirementConfig'
import { workflowEngineApi, type AvailableTransition, type TransitionAssigneeCandidate, type WorkflowAvailableActions } from '@/api/modules/workflow-engine'
import { getTabBadgeCounts } from '@/api/modules/statistics'
import { getCountersignRecords, canCurrentUserCountersign, submitCountersignApproval, switchParallelBranch, type CountersignRecord, type ParallelBranch } from '@/api/modules/workflow'
import type {
  Requirement,
  RequirementApprovalEvaluation,
  RequirementAttachment,
  RequirementComment,
  RequirementHistory,
  RequirementUpdate, RequirementDetailVO,
} from '@/types/requirement'
import { normalizeText, formatDate, formatFileSize, getFileExt, stripPriorityPrefix } from '@/utils/format'
import AppButton from '@/components/common/AppButton.vue'
import { usePermission } from '@/composables/usePermission'
import { useRequirementTag } from '@/composables/useRequirementTag'
import { useUserStore } from '@/stores/modules/user'
import { hydrateRichTextImageHtml, buildRichTextImagePreviewUrl } from '@/utils/richTextFileImage'
import { IsleEditor, IsleEditorToolbar, RichTextKit } from '@isle-editor/vue3'
import { addLocale } from '@isle-editor/core'
import Image from '@tiptap/extension-image'
import '@isle-editor/vue3/dist/style.css'
import PageContainer from '@/components/common/PageContainer.vue'
import FilePreviewDialog from '@/components/document/FilePreviewDialog.vue'
import AttachmentUploader from '@/components/AttachmentUploader.vue'

addLocale('zh', {
  isleEditor: '岛屿编辑器',
  fontFamily: '字体',
  fontSize: '字号',
  textStyle: '文字样式',
  background: '背景颜色',
  color: '文字颜色',
  lineHeight: '行高',
  letterSpacing: '字间距',
  bold: '加粗',
  italic: '斜体',
  underline: '下划线',
  strike: '删除线',
  code: '行内代码',
  link: '链接',
  linkPlaceholder: '请输入链接',
  openInNewTab: '在新标签页中打开',
  unlink: '取消链接',
  subscript: '下标',
  superscript: '上标',
  heading: '标题',
  heading1: '一级标题',
  heading2: '二级标题',
  heading3: '三级标题',
  heading4: '四级标题',
  heading5: '五级标题',
  heading6: '六级标题',
  paragraph: '段落',
  blockquote: '引用',
  bulletList: '无序列表',
  orderedList: '有序列表',
  taskList: '任务列表',
  codeBlock: '代码块',
  divider: '分割线',
  indent: '增加缩进',
  outdent: '减少缩进',
  hardBreak: '换行',
  undo: '撤销',
  redo: '重做',
  textAlign: '文字对齐',
  alignLeft: '左对齐',
  alignCenter: '居中对齐',
  alignRight: '右对齐',
  alignJustify: '两端对齐',
  table: '表格',
  edit: '编辑',
  textClear: '清除',
  copy: '复制',
  paste: '粘贴',
  cancel: '取消',
  open: '打开',
  empty: '空',
  fonts: {
    Default: '默认字体',
    MicrosoftYaHei: '微软雅黑',
    SimSun: '宋体',
    SimHei: '黑体',
    KaiTi: '楷体',
    FangSong: '仿宋',
    PingFangSC: '苹方',
    HiraginoSansGB: '冬青黑体',
    SourceHanSansSC: '思源黑体',
    STXihei: '华文细黑',
    STZhongsong: '华文中宋',
    Arial: 'Arial',
    TimesNewRoman: 'Times New Roman',
    CourierNew: 'Courier New',
    Georgia: 'Georgia',
  },
  sizes: {
    tiny: '超小',
    small: '小',
    normal: '中',
    large: '大',
    huge: '超大',
  },
  colors: {
    defaultColor: '默认颜色',
    baseColor: '基础颜色',
    standardColor: '标准颜色',
    recentUse: '最近使用',
    palette: '调色板',
  },
  placeholder: '写点什么 ...',
})

const route = useRoute()
const router = useRouter()
const { hasAnyPermission, hasPermission } = usePermission()
const {
  statusTagType,
  priorityTagType,
  priorityLabel,
  priorityTagStyle,
  approvalResultTagType,
} = useRequirementTag()
const canSubmitApproval = computed(() => hasPermission('button:requirement:submit'))
const userStore = useUserStore()
const currentUserId = computed(() => {
  const info: any = userStore.userInfo
  return info?.id ?? null
})

const id = Number(route.params.id)
const loading = ref(false)
const detail = ref<Requirement | null>(null)
const history = ref<RequirementHistory[]>([])
const relatedRequirements = ref<any[]>([])
const comments = ref<RequirementComment[]>([])
const approvalEvaluations = ref<RequirementApprovalEvaluation[]>([])
const approvalDialogVisible = ref(false)
const approvalRating = ref(0)
const approvalComment = ref('')
const approvalAttachments = ref<RequirementAttachment[]>([])
const supplementDialogVisible = ref(false)
const supplementSubmitting = ref(false)
const supplementContent = ref('')
const supplementAttachments = ref<RequirementAttachment[]>([])
const supplementTarget = ref<RequirementApprovalEvaluation | null>(null)
const children = ref<any[]>([])
const activeTab = ref('basic')
const projectName = ref<string>('')
const projectOptions = ref<Array<{ id: number; name: string; status?: string | null; endDate?: string | null }>>([])
const typeMap = ref<Record<string, string>>({})
const priorityMap = ref<Record<string, string>>({})
const priorityColorMap = ref<Record<string, string>>({})
const workflowRuntime = ref<WorkflowAvailableActions>({
  canTransition: false,
  canRollback: false,
  canCancel: false,
  transitions: [],
  currentNodeRequireAttachment: false,
})
const usingUnifiedEngine = ref(false)
const selectedTransitionTargetId = ref<string | number | null>(null)
const selectedTransitionAssigneeId = ref<number | null>(null)
const bindingProjectId = ref<number | null>(null)
const transitionLoading = ref(false)
const workflowPanelCollapsed = ref(false)
// 会签相关
const countersignDialogVisible = ref(false)
const countersignDialogLoading = ref(false)
const countersignRating = ref(0)
const countersignComment = ref('')
const currentCountersignRecords = ref<CountersignRecord[]>([])
const canCountersign = ref(false)
const currentCountersignNodeId = ref<string>('')
const parallelBranches = ref<ParallelBranch[]>([])
const richDescription = computed(() => hydrateRichTextImageHtml(detail.value?.description || ''))
const currentNodeStatusName = computed(() => {
  return workflowRuntime.value.currentNodeStatusName || detail.value?.status || ''
})
const currentNodeDisplayName = computed(() => {
  return workflowRuntime.value.currentNodeName || '当前节点'
})
// 修复 P2：当前节点是否必填意见（来自后端 currentNodeRequireComment）
const isCommentRequired = computed(() => Boolean(workflowRuntime.value.currentNodeRequireComment))
const isAttachmentRequired = computed(() => Boolean(workflowRuntime.value.currentNodeRequireAttachment))
const approvalCommentError = computed(() => {
  if (!isCommentRequired.value) return ''
  return approvalComment.value.trim() ? '' : '当前节点要求必须填写意见'
})
const showCurrentNodeStatus = computed(() => {
  return usingUnifiedEngine.value
    && Boolean(detail.value?.workflowInstanceId)
    && workflowRuntime.value.currentNodeType !== 'start'
    && Boolean(currentNodeStatusName.value)
})
// 状态名与节点名相同时不再重复渲染右侧 tag，避免"待分析"等关键词出现两次
const showNodeStatusTag = computed(() => {
  const statusName = currentNodeStatusName.value?.trim()
  if (!statusName) return false
  return statusName !== currentNodeDisplayName.value?.trim()
})
const attachmentCount = computed(() => {
  const requirementAttachmentCount = detail.value?.attachments?.length || 0
  const transitionAttachmentCount = (detail.value?.transitionAttachments || [])
    .reduce((total, group) => total + (group.attachments?.length || 0), 0)
  return requirementAttachmentCount + transitionAttachmentCount
})

const sortedApprovalEvaluations = computed(() => {
  // ISO 8601 字符串可直接字符串比较，无需 new Date() 分配
  const sorted = [...approvalEvaluations.value].sort((a, b) => {
    const cmp = (b.createdAt || '').localeCompare(a.createdAt || '')
    return cmp !== 0 ? cmp : b.id - a.id
  })

  // 创建人起始节点：如果后端尚未记录"新建"动作，在时间线最前面补一条虚拟记录
  if (detail.value && detail.value.creatorName && detail.value.createdAt) {
    const hasCreateRecord = sorted.some(
      (item) => item.nodeName === '新建' || item.action === 'create',
    )
    if (!hasCreateRecord) {
      const createRecord: RequirementApprovalEvaluation = {
        id: -1,
        requirementId: detail.value.id,
        instanceId: 0,
        nodeId: '',
        nodeName: '新建',
        nodeStatusName: '新建',
        evaluatorId: detail.value.creatorId ?? 0,
        evaluatorName: detail.value.creatorName,
        action: 'create',
        actionLabel: '新建',
        result: 'SUBMIT',
        resultLabel: '新建',
        content: '创建需求',
        createdAt: detail.value.createdAt,
        supplements: [],
        canSupplement: false,
      }
      return [createRecord, ...sorted]
    }
  }
  return sorted
})
const commentRichText = ref('')
const commentSubmitting = ref(false)
const commentEditorInstance = ref<any>(null)
const commentEditorExtensions = [
  RichTextKit.configure({
    placeholder: { placeholder: '输入评论内容...' },
    table: {
      resizable: true,
      HTMLAttributes: {
        class: 'comment-editor-table',
      },
    },
  }),
  Image.configure({
    inline: false,
    allowBase64: true,
    resize: {
      enabled: true,
      directions: ['top-left', 'top-right', 'bottom-left', 'bottom-right'],
      minWidth: 100,
      minHeight: 100,
      alwaysPreserveAspectRatio: true,
    },
    HTMLAttributes: {
      class: 'comment-editor-image',
    },
  }),
]

function onCommentEditorCreate({ editor }: { editor: any }) {
  commentEditorInstance.value = editor
  const editorEl = editor.view.dom as HTMLElement
  editorEl.addEventListener('paste', handleCommentImagePaste as unknown as EventListener)
}

const commentImageUploading = ref(false)
const commentIsDragOver = ref(false)
const commentFileInputRef = ref<HTMLInputElement | null>(null)
let commentDragDepth = 0

async function insertCommentImageFile(file: File) {
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('仅支持图片文件: ' + file.name)
    return
  }
  let processedFile = file
  if (!file.name || file.name === 'image' || !file.name.includes('.')) {
    const ext = file.type.split('/')[1] || 'png'
    processedFile = new File([file], `clipboard_${Date.now()}.${ext}`, { type: file.type })
  }
  try {
    commentImageUploading.value = true
    ElMessage.info(`上传图片中: ${processedFile.name}`)
    const attachment = await uploadRequirementAttachment(processedFile)
    const src = attachment.fileId ? buildRichTextImagePreviewUrl(attachment.fileId) : attachment.url
    if (src && commentEditorInstance.value) {
      const safeAlt = processedFile.name.replace(/"/g, '&quot;')
      const editor = commentEditorInstance.value
      editor.chain().focus().setImage({ src, alt: safeAlt, width: 400 }).run()
      ElMessage.success(`图片 ${processedFile.name} 已插入`)
    }
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, `图片 ${processedFile.name} 插入失败`))
  } finally {
    commentImageUploading.value = false
  }
}

async function handleCommentImagePaste(event: ClipboardEvent) {
  const files = event.clipboardData?.files
  if (!files || files.length === 0) return
  for (const file of Array.from(files)) {
    if (!file.type.startsWith('image/')) continue
    event.preventDefault()
    await insertCommentImageFile(file)
  }
}

function triggerCommentFileInput() {
  commentFileInputRef.value?.click()
}

async function handleCommentFileInput(event: Event) {
  const input = event.target as HTMLInputElement
  if (!input.files || input.files.length === 0) return
  const files = Array.from(input.files)
  for (const file of files) {
    await insertCommentImageFile(file)
  }
  input.value = ''
}

function handleCommentDragEnter() {
  commentDragDepth += 1
  if (commentDragDepth === 1) commentIsDragOver.value = true
}
function handleCommentDragOver() {
  // 由 .prevent 阻止默认行为即可，必须 preventDefault 才能触发 drop
}
function handleCommentDragLeave() {
  commentDragDepth = Math.max(0, commentDragDepth - 1)
  if (commentDragDepth === 0) commentIsDragOver.value = false
}
async function handleCommentDrop(event: DragEvent) {
  commentDragDepth = 0
  commentIsDragOver.value = false
  const files = event.dataTransfer?.files
  if (!files || files.length === 0) return
  for (const file of Array.from(files)) {
    if (file.type.startsWith('image/')) {
      await insertCommentImageFile(file)
    }
  }
}

function resetWorkflowMeta() {
  workflowRuntime.value = {
    canTransition: false,
    canRollback: false,
    canCancel: false,
    transitions: [],
  }
  usingUnifiedEngine.value = false
  selectedTransitionTargetId.value = null
  selectedTransitionAssigneeId.value = null
  bindingProjectId.value = null
  workflowPanelCollapsed.value = false
}

function resolveErrorMessage(error: unknown, fallback: string) {
  const message = (error as any)?.response?.data?.message || (error as any)?.message
  return typeof message === 'string' && message.trim() ? message : fallback
}

// Fetch detail
async function fetchDetail() {
  loading.value = true
  try {
    const res = await requirementApi.getRequirementById(id)
    detail.value = res
    await Promise.all([loadProjectName(), loadWorkflowMeta()])
  } catch {
    ElMessage.error('获取需求详情失败')
  } finally {
    loading.value = false
  }
}

async function loadProjectName() {
  if (!detail.value?.projectId) {
    projectName.value = ''
    return
  }
  try {
    const res = await projectApi.getProjectById(detail.value.projectId) as any
    projectName.value = res?.name || ''
  } catch {
    projectName.value = ''
  }
}

async function loadConfig() {
  try {
    const [typesRes, prioritiesRes] = await Promise.all([
      requirementConfigApi.listTypes(),
      requirementConfigApi.listPriorities(),
    ])
    const typeList = Array.isArray(typesRes) ? typesRes : (typesRes as any)?.data || []
    const priorityList = Array.isArray(prioritiesRes) ? prioritiesRes : (prioritiesRes as any)?.data || []
    typeMap.value = Object.fromEntries(typeList.map((t: any) => [t.code, normalizeText(t.name)]))
    priorityMap.value = Object.fromEntries(priorityList.map((p: any) => [p.code, stripPriorityPrefix(normalizeText(p.name))]))
    priorityColorMap.value = Object.fromEntries(
      priorityList
        .filter((p: any) => p.color)
        .map((p: any) => [p.code, p.color])
    )
  } catch {
    typeMap.value = {}
    priorityMap.value = {}
    priorityColorMap.value = {}
  }
}

async function loadWorkflowMeta() {
  resetWorkflowMeta()
  bindingProjectId.value = detail.value?.projectId && detail.value.projectId > 0 ? detail.value.projectId : null
  parallelBranches.value = []

  if (detail.value?.workflowInstanceId) {
    try {
      const actions = await workflowEngineApi.getAvailableActions(id)
      workflowRuntime.value = actions
      usingUnifiedEngine.value = true
      parallelBranches.value = actions.parallelBranches || []
      selectedTransitionTargetId.value = getDefaultVisibleTransition(actions.transitions)?.toNodeId ?? null
      return
    } catch {
      usingUnifiedEngine.value = false
    }
  }

  if (detail.value?.isDraft) {
    return
  }

  resetWorkflowMeta()
}

// Fetch history
async function fetchHistory() {
  try {
    if (detail.value?.workflowInstanceId) {
      const transitions = await workflowEngineApi.getTransitionHistory(id)
      history.value = Array.isArray(transitions)
        ? transitions.map((item: any) => {
            // 修复 P1：action=proxy_approve 标记为"代审批"以便审计追踪
            const fieldName = item.action === 'rollback'
              ? '流程驳回'
              : item.action === 'cancel'
                ? '流程取消'
                : item.action === 'proxy_approve'
                  ? '代审批'
                  : '流程流转'
            return {
              id: item.id,
              requirementId: item.requirementId,
              operatorId: item.operatorId,
              operatorName: item.operatorName,
              action: item.action,
              fieldName,
              oldValue: item.fromNodeName || item.fromNodeId || '开始',
              newValue: item.toNodeName || item.toNodeId || (item.durationDisplay ? `已处理（${item.durationDisplay}）` : '完成'),
              comment: item.comment,
              createdAt: item.createdAt,
            }
          })
        : []
      return
    }

    const res = await requirementApi.getRequirementHistory(id)
    history.value = Array.isArray(res) ? res : []
  } catch {
    history.value = []
  }
}

// Fetch children
async function fetchChildren() {
  try {
    const res = await requirementApi.getRequirementChildren(id)
    children.value = res
  } catch {
    // children fetch failure is non-critical
  }
}

async function loadProjectOptions() {
  try {
    const res = await projectApi.getProjectList({ pageNum: 1, pageSize: 100 }) as any
    const list = Array.isArray(res?.list) ? res.list : []
    projectOptions.value = list.filter((project: any) => !isProjectExpired(project))
  } catch {
    projectOptions.value = []
  }
}

async function fetchRelations() {
  try {
    const res = await relationApi.getRelationList(id)
    relatedRequirements.value = Array.isArray(res)
      ? res.map((item: RelationItem) => ({
          id: item.targetId,
          title: item.targetTitle,
          type: item.targetType,
          status: item.targetStatus,
          priority: item.targetPriority,
          relationType: item.relationType,
        }))
      : []
  } catch {
    relatedRequirements.value = []
  }
}

async function fetchComments() {
  try {
    const res = await requirementApi.getRequirementComments(id)
    comments.value = Array.isArray(res) ? res : []
  } catch {
    comments.value = []
  }
}

async function fetchApprovalEvaluations() {
  try {
    const res = await requirementApi.getApprovalEvaluations(id)
    approvalEvaluations.value = Array.isArray(res) ? res : []
  } catch {
    approvalEvaluations.value = []
  }
}

function resetApprovalDialog() {
  approvalRating.value = 0
  approvalComment.value = ''
  approvalAttachments.value = []
}

function resetSupplementDialog() {
  supplementTarget.value = null
  supplementContent.value = ''
  supplementAttachments.value = []
}

function canRemoveApprovalAttachment(file: RequirementAttachment) {
  if (!file) return false
  if (userStore.isSuperAdmin) return true
  const uploaderId = file.uploaderId
  if (uploaderId == null) {
    // 没有 uploaderId 视为当前用户上传（兼容旧数据）
    return true
  }
  return uploaderId === currentUserId.value
}

function downloadAttachmentFile(file: RequirementAttachment) {
  downloadRequirementAttachment(file)
}

async function executeTransition(extra?: { rating?: number; ratingDimensions?: Record<string, number>; comment?: string; attachments?: RequirementAttachment[] }) {
  transitionLoading.value = true
  try {
    await workflowEngineApi.transition({
      requirementId: id,
      toNodeId: String(selectedTransitionTargetId.value),
      projectId: requiresProjectBinding.value ? bindingProjectId.value : undefined,
      action: 'submit',
      comment: extra?.comment,
      rating: extra?.rating,
      ratingDimensions: extra?.ratingDimensions,
      attachments: extra?.attachments,
      lockVersion: workflowRuntime.value.lockVersion ?? undefined,
      // 流转时选择的处理人（传给后端用于更新 assignee_id 和待办同步）
      selectedAssigneeId: selectedTransitionAssigneeId.value,
    })
    ElMessage.success('提交审核成功')
    selectedTransitionTargetId.value = null
    approvalDialogVisible.value = false
    resetApprovalDialog()

    // 智能跳转：检查待办数量决定跳转目标
    await navigateAfterSubmit()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '状态流转失败'))
  } finally {
    transitionLoading.value = false
  }
}

// 提交审核后智能跳转
async function navigateAfterSubmit() {
  try {
    // 使用轻量接口检查当前用户还有多少待办
    const res = await getTabBadgeCounts() as any
    const counts = res?.data ?? res
    const hasPending = (counts?.pending ?? 0) > 0

    if (hasPending) {
      // 还有待办，跳转到"我的待办"列表
      router.push({ path: '/requirements', query: { view: 'pending' } })
    } else {
      // 没有待办了，跳转到"全部需求"列表
      router.push({ path: '/requirements', query: { view: 'all' } })
    }
  } catch {
    // 如果查询失败，默认跳转到"我的待办"
    router.push({ path: '/requirements', query: { view: 'pending' } })
  }
}

// Tag 颜色映射已抽取到 useRequirementTag composable
// 见 src/composables/useRequirementTag.ts

function openSupplementDialog(item: RequirementApprovalEvaluation) {
  supplementTarget.value = item
  supplementContent.value = ''
  supplementDialogVisible.value = true
}

function typeLabel(code: string) {
  return typeMap.value[code] || code || '-'
}

function projectLabel(projectId: number) {
  if (!projectId) return '未绑定'
  return projectName.value || String(projectId)
}

function isProjectExpired(project: { status?: string | null; endDate?: string | null }) {
  if (project.status === 'expired') return true
  if (!project.endDate) return false
  return new Date(project.endDate).getTime() < Date.now() - 24 * 60 * 60 * 1000
}

function projectOptionLabel(project: { name: string }) {
  return project.name
}

function pad2(n: number) {
  return n < 10 ? `0${n}` : String(n)
}

function formatDateTime(value?: string | null) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} ${pad2(date.getHours())}:${pad2(date.getMinutes())}`
}

function formatAttachmentMeta(attachment: RequirementAttachment) {
  const parts: string[] = []
  if (attachment.size) {
    if (attachment.size < 1024) {
      parts.push(`${attachment.size} B`)
    } else if (attachment.size < 1024 * 1024) {
      parts.push(`${(attachment.size / 1024).toFixed(1)} KB`)
    } else {
      parts.push(`${(attachment.size / 1024 / 1024).toFixed(1)} MB`)
    }
  }
  if (attachment.uploadedAt) {
    parts.push(formatDateTime(attachment.uploadedAt))
  }
  if (attachment.uploaderName) {
    parts.push(attachment.uploaderName)
  }
  return parts.join(' / ')
}

async function handleAttachmentDownload(attachment: RequirementAttachment) {
  try {
    await downloadRequirementAttachment(attachment)
  } catch {
    ElMessage.error('附件下载失败')
  }
}

const previewVisible = ref(false)
const previewFile = ref<RequirementAttachment | null>(null)
const richImagePreviewVisible = ref(false)
const richImagePreviewSrc = ref('')
const richImagePreviewTitle = ref('图片预览')
const richImagePreviewZoom = ref(100)

/**
 * 是否允许预览：必须存在 fileId（用于走 /api/v1/files/{id}/preview-url 链路）
 * 或者有可访问的 url（兼容历史数据）。注意：url 模式不会经过 MinIO 预签名，
 * 仅作为兜底，主要场景是 fileId。
 */
function canPreviewAttachment(attachment: RequirementAttachment) {
  return !!(attachment.fileId || attachment.url)
}

function handleAttachmentPreview(attachment: RequirementAttachment) {
  if (!canPreviewAttachment(attachment)) {
    ElMessage.warning('该附件暂不支持预览')
    return
  }
  previewFile.value = attachment
  previewVisible.value = true
}

function handleRichContentClick(event: MouseEvent) {
  const target = event.target
  if (!(target instanceof HTMLImageElement)) return

  const src = target.currentSrc || target.src || target.getAttribute('src') || ''
  if (!src) return

  richImagePreviewSrc.value = src
  richImagePreviewTitle.value = target.alt?.trim() || '图片预览'
  richImagePreviewZoom.value = 100
  richImagePreviewVisible.value = true
}

function handleRichImageWheel(event: WheelEvent) {
  const delta = event.deltaY || event.detail
  if (delta < 0) {
    richImagePreviewZoom.value = Math.min(300, richImagePreviewZoom.value + 15)
  } else {
    richImagePreviewZoom.value = Math.max(50, richImagePreviewZoom.value - 15)
  }
}

function zoomInRichImage() {
  richImagePreviewZoom.value = Math.min(300, richImagePreviewZoom.value + 25)
}

function zoomOutRichImage() {
  richImagePreviewZoom.value = Math.max(50, richImagePreviewZoom.value - 25)
}

function resetRichImageZoom() {
  richImagePreviewZoom.value = 100
}

function resetRichImagePreview() {
  richImagePreviewSrc.value = ''
  richImagePreviewTitle.value = '图片预览'
  richImagePreviewZoom.value = 100
}

type TransitionOption = AvailableTransition

const transitionOptions = computed<TransitionOption[]>(() => {
  return workflowRuntime.value.transitions.filter(transition => !isCancelTransition(transition))
})

const selectedUnifiedTransition = computed<AvailableTransition | null>(() => {
  if (!usingUnifiedEngine.value) return null
  return workflowRuntime.value.transitions.find(
    (transition) => transition.toNodeId === String(selectedTransitionTargetId.value ?? ''),
  ) || null
})

const selectedTransitionAssigneeCandidates = computed<TransitionAssigneeCandidate[]>(() => {
  return selectedUnifiedTransition.value?.assigneeCandidates || []
})

const selectedTransitionAssigneeTypeName = computed(() => {
  return selectedUnifiedTransition.value?.assigneeTypeName || ''
})

/** 处理人作用域标签前缀：根据 assigneeType 返回中文前缀 */
const selectedTransitionScopeLabel = computed(() => {
  const type = selectedUnifiedTransition.value?.assigneeType
  switch (type) {
    case 'SPECIFIED_USER': return '处理类型'
    case 'SPECIFIED_ROLE': return '处理角色'
    case 'SPECIFIED_ROLE_GROUP': return '处理角色组'
    case 'SPECIFIED_ORG': return '处理类型'
    case 'CREATOR': return '处理类型'
    case 'PREV_APPROVER': return '处理类型'
    default: return ''
  }
})

/** 处理人作用域名称：角色名称 / 角色组名称 / 类型名称 */
const selectedTransitionScopeName = computed(() => {
  return selectedUnifiedTransition.value?.assigneeScopeName || ''
})

const showTransitionAssigneeSelector = computed(() => {
  return selectedTransitionAssigneeCandidates.value.length > 1
})

const requiresSingleTransitionAssignee = computed(() => {
  return showTransitionAssigneeSelector.value
})

const selectedTransitionAssigneeOption = computed<TransitionAssigneeCandidate | null>(() => {
  if (!selectedTransitionAssigneeCandidates.value.length) {
    return null
  }
  return selectedTransitionAssigneeCandidates.value.find(
    candidate => candidate.id === selectedTransitionAssigneeId.value,
  ) || selectedTransitionAssigneeCandidates.value[0] || null
})

/** 当前选中的处理人显示名（仅展示人名，不含作用域前缀） */
const selectedTransitionAssigneeName = computed(() => {
  if (!selectedUnifiedTransition.value) {
    return transitionOptions.value.length > 0 ? '请选择目标节点' : '当前无可执行操作'
  }
  return selectedTransitionAssigneeOption.value?.name || '未配置处理人'
})

const requiresProjectBinding = computed(() => (
  usingUnifiedEngine.value
  && !detail.value?.projectId
  && Boolean(selectedUnifiedTransition.value?.projectRequired)
))

const bindableProjects = computed(() => projectOptions.value)

const showPrimaryActions = computed(() => {
  // 草稿：创建人可见编辑/删除
  if (detail.value?.isDraft) {
    return hasPermission('button:requirement:update') || hasPermission('button:requirement:delete')
  }
  // 非草稿：根据工作流权限动态判断
  return Boolean(canEditRequirement.value || canSplitRequirement.value || canDeleteRequirement.value)
})

/** 当前用户是否可编辑需求（基于工作流节点权限） */
const canEditRequirement = computed(() => {
  // 草稿：创建人可编辑
  if (detail.value?.isDraft) {
    return hasPermission('button:requirement:update')
  }
  // 非草稿：需同时有静态权限和工作流节点权限
  if (!hasPermission('button:requirement:update')) {
    return false
  }
  if (usingUnifiedEngine.value) {
    return Boolean(workflowRuntime.value.canEdit)
  }
  return true
})

/** 当前用户是否可拆分子需求（基于工作流节点权限） */
const canSplitRequirement = computed(() => {
  if (!hasPermission('button:requirement:split')) {
    return false
  }
  if (detail.value?.isDraft) {
    return false // 草稿不允许拆分
  }
  if (usingUnifiedEngine.value) {
    return Boolean(workflowRuntime.value.canSplit)
  }
  return true
})

/** 当前用户是否可删除需求（草稿：创建人可删除；非草稿：需要权限） */
const canDeleteRequirement = computed(() => {
  // 草稿状态：创建人可以删除（无需特殊权限）
  if (detail.value?.isDraft) {
    return detail.value?.creatorId === userStore.userInfo?.id
  }

  // 非草稿状态：需要删除权限
  if (!hasPermission('button:requirement:delete')) {
    return false
  }

  if (usingUnifiedEngine.value) {
    return Boolean(workflowRuntime.value.canDelete)
  }
  return true
})

const commentDraftDirty = computed(() => hasMeaningfulCommentContent(commentRichText.value))
const canOperateCurrentNode = computed(() => {
  if (!usingUnifiedEngine.value) {
    return true
  }
  return Boolean(
    workflowRuntime.value.canTransition
    || workflowRuntime.value.canRollback
    || workflowRuntime.value.canCancel
    || workflowRuntime.value.canCountersign
  )
})
const canEditComment = computed(() => {
  return hasPermission('button:requirement:comment') && canOperateCurrentNode.value
})

const transitionSubmitDisabled = computed(() => {
  if (!isWorkflowActive.value) {
    return true
  }
  return transitionOptions.value.length === 0
    || (requiresProjectBinding.value && !bindingProjectId.value)
    || (requiresSingleTransitionAssignee.value && !selectedTransitionAssigneeId.value)
})

/** 工作流是否处于启用状态：默认 true（无 workflowInstanceId 时按启用处理，避免误判）；
 *  显式返回 false 才视为停用（与后端 workflowActive=false 对齐）。 */
const isWorkflowActive = computed(() => {
  if (!usingUnifiedEngine.value) {
    return true
  }
  const flag = workflowRuntime.value.workflowActive
  return flag === undefined || flag === null ? true : Boolean(flag)
})

const showWorkflowActionPanel = computed(() => {
  return showCurrentNodeStatus.value
    || transitionOptions.value.length > 0
    || Boolean(workflowRuntime.value.canCountersign)
    || Boolean(workflowRuntime.value.canRollback)
    || Boolean(workflowRuntime.value.canCancel)
    || (Boolean(workflowRuntime.value.parallelActive) && parallelBranches.value.length > 0)
})

watch(
  selectedUnifiedTransition,
  (transition) => {
    const fallbackId = transition?.defaultAssigneeId ?? transition?.assigneeCandidates?.[0]?.id ?? null
    selectedTransitionAssigneeId.value = fallbackId
  },
  { immediate: true },
)

watch(
  canEditComment,
  (allowed) => {
    if (!allowed) {
      resetCommentDraft()
    }
  },
  { immediate: true },
)

function isCancelTransition(transition: TransitionOption | null | undefined) {
  if (!transition) return false
  const statusCode = transition.bindStatusCode?.trim().toUpperCase()
  if (statusCode === 'CANCELLED') {
    return true
  }
  const text = `${transition.toNodeName || ''} ${transition.label || ''} ${transition.bindStatusName || ''}`.replace(/\s+/g, '')
  return text.includes('已取消')
}

function getDefaultVisibleTransition(transitions: TransitionOption[]) {
  return transitions.find(transition => !isCancelTransition(transition)) || null
}

function transitionOptionKey(transition: TransitionOption) {
  return transition.toNodeId
}

function transitionOptionValue(transition: TransitionOption) {
  return transition.toNodeId
}

function transitionOptionLabel(transition: TransitionOption) {
  const baseLabel = transition.label || transition.toNodeName
  const projectLabel = transition.projectRequired ? ' [需绑定项目]' : ''
  return `${baseLabel}${projectLabel}`
}

// Handlers
function handleEdit() {
  router.push({ name: 'RequirementCreate', query: { id } })
}

function handleSplit() {
  router.push({ name: 'RequirementCreate', query: { parentId: id } })
}

async function handleDelete() {
  try {
    await requirementApi.deleteRequirement(id)
    ElMessage.success('删除成功')
    router.push({ name: 'Requirements' })
  } catch {
    ElMessage.error('删除失败')
  }
}

async function handleStatusTransition() {
  if (!isWorkflowActive.value) {
    ElMessage.warning('当前工作流已停用，暂不支持提交审核')
    return
  }
  if (!selectedTransitionTargetId.value) {
    ElMessage.warning('请选择目标节点')
    return
  }
  if (requiresSingleTransitionAssignee.value && !selectedTransitionAssigneeId.value) {
    ElMessage.warning('请选择处理人')
    return
  }

  if (workflowRuntime.value.countersignPending) {
    ElMessage.warning('会签尚未完成，请先完成会签审批')
    if (workflowRuntime.value.currentNodeId) {
      await openCountersignDialog(workflowRuntime.value.currentNodeId)
    }
    return
  }

  resetApprovalDialog()
  approvalDialogVisible.value = true
}

// 会签审批方法
async function openCountersignDialog(nodeId: string) {
  currentCountersignNodeId.value = nodeId
  countersignDialogVisible.value = true
  countersignDialogLoading.value = true
  try {
    const [recordsRes, canRes] = await Promise.all([
      getCountersignRecords(id, nodeId),
      canCurrentUserCountersign(id, nodeId),
    ])
    currentCountersignRecords.value = recordsRes || []
    canCountersign.value = canRes || false
  } catch (error) {
    ElMessage.error('获取会签信息失败')
  } finally {
    countersignDialogLoading.value = false
  }
}

async function handleCountersignApprove() {
  await submitCountersign('approved')
}

async function handleCountersignReject() {
  await submitCountersign('rejected')
}

async function submitCountersign(status: 'approved' | 'rejected') {
  try {
    await submitCountersignApproval({
      requirementId: id,
      nodeId: currentCountersignNodeId.value,
      status,
      rating: countersignRating.value || undefined,
      comment: countersignComment.value.trim() || undefined,
    })
    ElMessage.success(status === 'approved' ? '会签通过' : '会签已驳回')
    countersignDialogVisible.value = false
    countersignRating.value = 0
    countersignComment.value = ''
    await Promise.all([fetchDetail(), fetchHistory(), fetchApprovalEvaluations()])
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '提交会签审批失败'))
  }
}

function parallelBranchStatusLabel(status: string): string {
  const map: Record<string, string> = {
    pending: '待处理',
    running: '进行中',
    completed: '已完成',
    skipped: '已跳过',
  }
  return map[status] || status
}

async function handleSwitchParallelBranch(branchId: number) {
  if (branchId === workflowRuntime.value.activeParallelBranchId) {
    return
  }
  try {
    await switchParallelBranch(id, branchId)
    ElMessage.success('已切换并行分支')
    await fetchDetail()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '切换并行分支失败'))
  }
}

async function confirmApprovalTransition() {
  // 工作流版本启用评价时，评分必填
  if (workflowRuntime.value.evaluationRequired && (!approvalRating.value || approvalRating.value < 1)) {
    ElMessage.warning('请选择 1-5 星评价')
    return
  }
  // 修复 P2：节点要求必填意见时，前端先校验
  if (isCommentRequired.value && !approvalComment.value.trim()) {
    ElMessage.warning('当前节点要求必须填写意见')
    return
  }
  if (isAttachmentRequired.value && approvalAttachments.value.length === 0) {
    ElMessage.warning('当前节点要求必须上传附件')
    return
  }
  await executeTransition({
    rating: workflowRuntime.value.evaluationRequired ? approvalRating.value : undefined,
    comment: approvalComment.value.trim() || undefined,
    attachments: approvalAttachments.value.length > 0 ? approvalAttachments.value : undefined,
  })
}

async function submitSupplement() {
  const content = supplementContent.value.trim()
  if (!supplementTarget.value?.id) {
    ElMessage.warning('未找到原审核记录')
    return
  }
  if (!content) {
    ElMessage.warning('请输入补充意见')
    return
  }

  supplementSubmitting.value = true
  try {
    await requirementApi.createApprovalEvaluationSupplement(id, supplementTarget.value.id, {
      content,
      attachments: supplementAttachments.value.length > 0 ? supplementAttachments.value : undefined,
    })
    ElMessage.success('补充意见已提交')
    supplementDialogVisible.value = false
    resetSupplementDialog()
    await fetchApprovalEvaluations()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '提交补充意见失败'))
  } finally {
    supplementSubmitting.value = false
  }
}

async function handleRollback() {
  if (!isWorkflowActive.value) {
    ElMessage.warning('当前工作流已停用，暂不支持驳回')
    return
  }
  await confirmAndExecute(
    '请输入驳回原因', '驳回需求', '确认驳回', '请输入驳回原因',
    (v) => workflowEngineApi.rollback(id, v || undefined),
    '驳回成功', '驳回失败',
    (input) => !!input?.trim() || '请输入驳回原因'
  )
}

async function handleCancel() {
  if (!isWorkflowActive.value) {
    ElMessage.warning('当前工作流已停用，暂不支持取消')
    return
  }
  await confirmAndExecute(
    '请输入取消原因', '取消需求', '确认取消', '取消原因必填',
    (v) => workflowEngineApi.cancel(id, v),
    '需求已取消', '取消失败',
    (input) => !!input?.trim() || '请输入取消原因'
  )
}

async function confirmAndExecute(
  message: string, title: string, confirmText: string, placeholder: string,
  action: (value: string) => Promise<any>,
  successMsg: string, errorMsg: string,
  validator?: (input: string) => string | boolean
) {
  try {
    const opts: any = { confirmButtonText: confirmText, cancelButtonText: '取消', inputPlaceholder: placeholder }
    if (validator) opts.inputValidator = validator
    const { value } = await ElMessageBox.prompt(message, title, opts)
    transitionLoading.value = true
    await action(value)
    ElMessage.success(successMsg)
    await Promise.all([fetchDetail(), fetchHistory(), fetchApprovalEvaluations()])
  } catch (error: any) {
    if (error === 'cancel' || error?.action === 'cancel') return
    ElMessage.error(resolveErrorMessage(error, errorMsg))
  } finally {
    transitionLoading.value = false
  }
}

async function submitCommentRich() {
  const html = commentEditorInstance.value?.getHTML?.() || ''
  if (!hasMeaningfulCommentContent(html)) {
    ElMessage.warning('请输入评论内容')
    return
  }
  const content = html.trim()

  commentSubmitting.value = true
  try {
    await requirementApi.createRequirementComment(id, { content })
    ElMessage.success('评论已提交')
    resetCommentDraft()
    await fetchComments()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '评论提交失败'))
  } finally {
    commentSubmitting.value = false
  }
}

function cancelCommentDraft() {
  resetCommentDraft()
}

function resetCommentDraft() {
  commentRichText.value = ''
  commentEditorInstance.value?.commands?.clearContent?.()
}

function hasMeaningfulCommentContent(html: string) {
  if (!html) return false
  if (/<img\b/i.test(html)) return true
  const text = html
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/gi, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  return text.length > 0
}

async function initializePage() {
  // Load config and project options in parallel with batch detail fetch
  // 注：getRequirementDetailBatch 在用户无权查看时会返回 code=403，
  //     request.ts 已不再对业务 403 清 token，但 promise 仍 reject；
  //     这里兜住异常并提供友好提示，避免控制台 Uncaught 噪声。
  let batchData: any = null
  try {
    [batchData] = await Promise.all([
      requirementApi.getRequirementDetailBatch(id),
      loadConfig(),
      loadProjectOptions(),
    ])
  } catch (error: any) {
    // 业务 403（无权查看等）由 request.ts 拦截器统一弹过 ElMessage.error，
    // 这里静默避免重复提示；其他错误（网络异常/500 等）仍弹消息
    if (error?.code !== 403) {
      const msg = error?.message || '加载需求详情失败'
      ElMessage.error(msg)
    }
    loading.value = false
    return
  }

  // Populate data from batch response
  if (batchData) {
    detail.value = batchData.requirement
    history.value = (batchData.history || []) as any
    children.value = (batchData.children || []) as any
    relatedRequirements.value = (batchData.relations || []) as any
    comments.value = (batchData.comments || []) as any
    approvalEvaluations.value = (batchData.approvalEvaluations || []) as any

    // Load project name and workflow meta after getting detail
    await Promise.all([loadProjectName(), loadWorkflowMeta()])
  }
}

onMounted(() => {
  void initializePage()
})
</script>

<style scoped>
.detail-page {
  min-height: 200px;
}

.detail-layout {
  display: flex;
  align-items: flex-start;
  gap: 24px;
}

.detail-main {
  flex: 1;
  min-width: 0;
}

/* ===== 页面操作栏 ===== */
.detail-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.detail-actions__primary {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

/* ===== Tabs ===== */
.detail-tabs {
  margin-top: 0;
}

.detail-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-bottom: none;
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
  padding: 0 8px;
}

.detail-tabs :deep(.el-tabs__nav-wrap) {
  padding: 4px 0;
}

.detail-tabs :deep(.el-tabs__content) {
  padding: 24px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-top: none;
  border-radius: 0 0 var(--radius-lg) var(--radius-lg);
  min-height: 300px;
}

/* 工单内容独立区块 */
.description-content-section {
  margin-top: 24px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.description-content-section__header {
  padding: 12px 16px;
  background: var(--color-surface-alt);
  border-bottom: 1px solid var(--color-border);
}

.description-content-section__label {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.description-content-section__body {
  padding: 16px;
  background: var(--color-surface);
}

.description-content-section__body .rich-content {
  min-height: 80px;
}

.detail-tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
}

.detail-tab-label .el-icon {
  font-size: 15px;
}

/* ===== 审批侧边栏 ===== */
.workflow-sidebar {
  position: sticky;
  top: 16px;
  flex: 0 0 340px;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
}

.workflow-sidebar.is-collapsed {
  flex-basis: 56px;
}

.workflow-sidebar__rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
  width: 40px;
  padding: 14px 4px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

.workflow-sidebar__toggle-button {
  color: var(--color-accent);
  padding: 6px;
  border-radius: var(--radius-sm);
  transition: background-color var(--duration-fast) var(--ease-standard);

  &:hover {
    background: var(--el-color-primary-light-9);
  }
}

.workflow-sidebar__collapsed-title {
  margin-top: 4px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  line-height: 1.4;
  writing-mode: vertical-rl;
  letter-spacing: 2px;
  user-select: none;
}

.workflow-action-panel {
  flex: 1;
  min-width: 0;
  padding: 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-md);
  overflow: hidden;
}

.workflow-action-panel__header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px 20px 14px;
  border-bottom: 1px solid var(--color-border);
}

.workflow-action-panel__header-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: linear-gradient(
    135deg,
    rgba(99, 102, 241, 0.12) 0%,
    rgba(37, 99, 235, 0.08) 100%
  );
  color: var(--color-accent);
  font-size: 18px;
  flex-shrink: 0;
  box-shadow: 0 1px 2px rgba(99, 102, 241, 0.08);
}

.workflow-action-panel__title {
  color: var(--color-text-primary);
  font-size: 15px;
  font-weight: 600;
  line-height: 22px;
}

.workflow-action-panel__subtitle {
  margin-top: 2px;
  color: var(--color-muted-text);
  font-size: 12px;
  line-height: 16px;
}

.workflow-action-panel__status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 16px;
  padding: 12px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-alt);
}

.workflow-action-panel__status-node {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.workflow-action-panel__status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;

  &.is-primary { background: var(--el-color-primary); }
  &.is-success { background: var(--el-color-success); }
  &.is-warning { background: var(--el-color-warning); }
  &.is-danger { background: var(--el-color-danger); }
  &.is-info { background: var(--el-color-info); }
}

.workflow-action-panel__status-label {
  color: var(--color-text-secondary);
  font-size: 12px;
  white-space: nowrap;
  flex-shrink: 0;
}

.workflow-action-panel__status-value {
  color: var(--color-text-primary);
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.workflow-action-panel__alert {
  margin: 0 16px 12px;
}

.workflow-action-panel__body {
  display: flex;
  flex-direction: column;
  padding: 16px 20px 20px;
  gap: 0;
}

.workflow-action-panel__section {
  padding: 14px 0;

  & + & {
    border-top: 1px solid var(--color-border);
    margin-top: 4px;
  }
}

.workflow-action-panel__section-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--color-muted-text);
  text-transform: uppercase;
  letter-spacing: 0.6px;
  margin-bottom: 14px;
}

.workflow-action-panel__field {
  display: flex;
  align-items: center;
  gap: 12px;

  & + & {
    margin-top: 12px;
  }
}

.workflow-action-panel__field--vertical {
  flex-direction: column;
  gap: 10px;
}

.workflow-action-panel__field-label-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.workflow-action-panel__field-tag {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--color-accent-tint-light, #eff6ff);
  color: var(--color-accent, #2563eb);
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.workflow-action-panel__field-label {
  flex: 0 0 88px;
  padding-top: 6px;
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 20px;
}

.workflow-action-panel__field--vertical .workflow-action-panel__field-label {
  flex: none;
  padding-top: 0;
  font-weight: 500;
  color: var(--color-text-primary);
}

.workflow-action-panel__control {
  flex: 1;
  min-width: 0;
}

.workflow-action-panel__assignee-readonly {
  flex: 1;
  min-width: 0;
  box-sizing: border-box;
  padding: 4px 11px;
  min-height: 32px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-alt);
  color: var(--color-text-primary);
  font-size: 13px;
  line-height: 24px;
  word-break: break-all;
  display: flex;
  align-items: center;
}

.workflow-action-panel__actions {
  display: flex;
  justify-content: flex-start;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding-top: 4px;
}

.workflow-action-panel__actions :deep(.el-button),
.workflow-action-panel__actions :deep(button) {
  min-width: 80px;
}

.workflow-action-panel__submit-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 8px;
  padding-top: 18px;
  border-top: 1px solid var(--color-border);
}

.workflow-action-panel__submit-actions :deep(.el-button),
.workflow-action-panel__submit-actions :deep(button) {
  min-width: 96px;
}

/* ===== 当前节点状态栏 ===== */
.current-node-status {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-alt);
  flex-wrap: nowrap;
  white-space: nowrap;
}

.current-node-status__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

.current-node-status__label {
  color: var(--color-text-secondary);
  font-size: 13px;
  white-space: nowrap;
}

.current-node-status__value {
  color: var(--color-text-primary);
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}

.current-node-status__sep {
  width: 1px;
  height: 14px;
  background: var(--color-border);
  flex-shrink: 0;
}

/* ===== 响应式 ===== */
@media (max-width: 1200px) {
  .detail-layout {
    flex-direction: column;
  }

  .detail-main,
  .workflow-sidebar,
  .workflow-action-panel,
  .detail-actions__primary {
    width: 100%;
  }

  .detail-actions {
    flex-wrap: wrap;
  }

  .workflow-sidebar {
    position: static;
    flex-basis: auto;
  }

  .workflow-sidebar.is-collapsed {
    flex-basis: auto;
    width: 100%;
  }

  .workflow-sidebar__rail {
    display: none;
  }

  .workflow-action-panel {
    min-width: 0;
    width: 100%;
  }

  .workflow-action-panel__field {
    flex-direction: column;
    gap: 6px;
  }

  .workflow-action-panel__field--vertical {
    gap: 6px;
  }

  .workflow-action-panel__field-label {
    flex: none;
    padding-top: 0;
  }
}

.rich-content {
  line-height: var(--line-height-relaxed);
  color: var(--el-text-color-regular);
  overflow-x: auto;

  :deep(p) {
    margin: 0 0 12px;
  }

  :deep(h1),
  :deep(h2),
  :deep(h3) {
    margin: 16px 0 8px;
    font-weight: var(--font-weight-semibold);
    color: var(--color-text-primary);
  }

  :deep(ul),
  :deep(ol) {
    padding-left: 24px;
    margin: 8px 0;
  }

  :deep(code) {
    background: var(--el-fill-color-light);
    padding: 2px 6px;
    border-radius: 4px;
    font-family: var(--font-family-mono);
    font-size: 0.9em;
  }

  :deep(blockquote) {
    border-left: 3px solid var(--el-color-primary);
    padding: 4px 12px;
    margin: 8px 0;
    color: var(--el-text-color-secondary);
    background: var(--el-fill-color-light);
  }

  :deep(img) {
    max-width: 100%;
    height: auto;
    cursor: zoom-in;
    border-radius: 4px;
    transition: opacity 0.15s ease, box-shadow 0.15s ease;
  }

  :deep(img:hover) {
    opacity: 0.92;
    box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.18);
  }

  :deep(table) {
    border-collapse: collapse;
    table-layout: auto;
    width: max-content;
    max-width: 100%;
    margin: 12px 0;
  }

  :deep(td),
  :deep(th) {
    min-width: 1em;
    border: 1px solid #dcdfe6 !important;
    padding: 8px 12px;
    vertical-align: top;
    box-sizing: border-box;
    word-break: normal;
    overflow-wrap: break-word;
  }

  :deep(th) {
    font-weight: 600;
    text-align: left;
    background-color: #f5f7fa;
  }

  :deep(.rich-text-table) {
    background: #fff;
  }
}

.rich-image-preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-right: 36px;
}

.rich-image-preview-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.rich-image-preview-toolbar {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.rich-image-preview-zoom {
  min-width: 48px;
  text-align: center;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.rich-image-preview-body {
  height: 72vh;
  overflow: auto;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 24px;
  background: var(--color-surface-alt);
}

.rich-image-preview-img {
  display: block;
  max-width: 100%;
  height: auto;
  transform-origin: top center;
  transition: transform 0.16s ease;
  box-shadow: var(--shadow-md);
  background: #fff;
}

:global(.rich-image-preview-dialog .el-dialog__body) {
  padding: 0;
}

.attachments-tab {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.attachment-empty {
  line-height: 22px;
  font-size: 13px;
  color: var(--color-muted-text);
}

.old-value {
  color: var(--color-muted-text);
  text-decoration: line-through;
}

.new-value {
  color: var(--color-accent);
  font-weight: 500;
}

.comment-section {
  margin-bottom: 0;
}

.comment-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.comment-item {
  display: flex;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid var(--color-border);
}

.comment-content {
  flex: 1;
}

.comment-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.comment-time {
  color: var(--color-muted-text);
  font-size: 12px;
}

.comment-section-block {
  margin-top: 0;
  padding-top: 0;
  border-top: none;
}

.comment-editor-wrapper {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  margin-top: 16px;
  min-height: 160px;
  background: var(--color-surface);
}

.comment-editor-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  background: var(--color-surface-alt);
  border-bottom: 1px solid var(--color-border);
}

.comment-editor-hint {
  color: var(--color-muted-text);
  font-size: 12px;
}

.comment-editor-dropzone {
  min-height: 140px;
  transition: background-color 0.15s ease, box-shadow 0.15s ease;
}

.comment-editor-dropzone.is-dragover {
  background-color: var(--color-info-light);
  box-shadow: inset 0 0 0 2px var(--color-accent);
}

.comment-editor-dropzone :deep(.comment-editor-image) {
  display: block;
  max-width: 100%;
  height: auto;
  border-radius: 6px;
  position: relative;
}

/* 评论编辑器表格样式 */
.comment-editor-dropzone :deep(.comment-editor-table) {
  border-collapse: collapse;
  table-layout: fixed;
  width: 100%;
  margin: 12px 0;
  overflow: hidden;
}

.comment-editor-dropzone :deep(.comment-editor-table td),
.comment-editor-dropzone :deep(.comment-editor-table th) {
  min-width: 1em;
  border: 1px solid #dcdfe6;
  padding: 8px 12px;
  vertical-align: top;
  box-sizing: border-box;
  position: relative;
}

.comment-editor-dropzone :deep(.comment-editor-table th) {
  font-weight: 600;
  text-align: left;
  background-color: #f5f7fa;
}

.comment-editor-dropzone :deep(.comment-editor-table .selectedCell) {
  background-color: #e8f4ff;
}

.comment-editor-dropzone :deep(.comment-editor-table .column-resize-handle) {
  position: absolute;
  right: -2px;
  top: 0;
  bottom: -2px;
  width: 4px;
  background-color: #409eff;
  pointer-events: none;
}

.comment-editor-dropzone :deep([data-resize-container]) {
  margin-top: 12px;
  margin-bottom: 12px;
  width: fit-content;
  max-width: 100%;
}

.comment-editor-dropzone :deep([data-resize-wrapper]) {
  display: inline-block !important;
  max-width: 100%;
}

.comment-editor-dropzone :deep([data-resize-container].ProseMirror-selectednode .comment-editor-image),
.comment-editor-dropzone :deep([data-resize-container][data-resize-state='true'] .comment-editor-image),
.comment-editor-dropzone :deep([data-resize-wrapper].ProseMirror-selectednode .comment-editor-image) {
  outline: 2px solid rgba(64, 158, 255, 0.45);
  outline-offset: 2px;
}

.comment-editor-dropzone :deep([data-resize-handle]) {
  position: absolute;
  width: 12px;
  height: 12px;
  border-radius: 999px;
  border: 2px solid #fff;
  background: var(--el-color-primary);
  box-shadow: 0 0 0 1px rgba(64, 158, 255, 0.28), 0 2px 8px rgba(64, 158, 255, 0.25);
  z-index: 3;
}

.comment-editor-dropzone :deep([data-resize-handle='top-left']) {
  top: -6px;
  left: -6px;
  cursor: nwse-resize;
}

.comment-editor-dropzone :deep([data-resize-handle='top-right']) {
  top: -6px;
  right: -6px;
  cursor: nesw-resize;
}

.comment-editor-dropzone :deep([data-resize-handle='bottom-left']) {
  bottom: -6px;
  left: -6px;
  cursor: nesw-resize;
}

.comment-editor-dropzone :deep([data-resize-handle='bottom-right']) {
  right: -6px;
  bottom: -6px;
  cursor: nwse-resize;
}

.comment-editor-wrapper :deep(.comment-editor-image) {
  display: block;
  max-width: 100%;
  border-radius: 6px;
}

.comment-editor-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.comment-body :deep(img) {
  max-width: 100%;
  height: auto;
}

.comment-body :deep(p) {
  margin: 0 0 4px 0;
}

.children-section {
  margin-top: 24px;
}

.attachment-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.attachment-item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 28px;
  flex-wrap: nowrap;
}

.attachment-item .attachment-name {
  flex: 0 1 auto;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-meta {
  color: var(--color-muted-text);
  font-size: 12px;
  white-space: nowrap;
  flex-shrink: 0;
}

.attachment-preview {
  flex-shrink: 0;
  margin-left: auto;
  padding: 4px;
  line-height: 1;
  opacity: 0.6;
  color: var(--color-accent);
  border-radius: 4px;
  transition: opacity 0.15s ease, background-color 0.15s ease;
}

.attachment-preview:hover {
  opacity: 1;
  background: var(--el-color-primary-light-9);
}

.attachment-transition-list {
  margin-top: 12px;
  border-top: 1px dashed var(--el-border-color-lighter, #ebeef5);
  padding-top: 8px;
}

.attachment-transition-group + .attachment-transition-group {
  margin-top: 8px;
}

.attachment-transition-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: var(--color-text-secondary, #909399);
  margin-bottom: 4px;
}

.attachment-transition-node {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--el-color-primary);
}

.attachment-transition-meta {
  display: inline-flex;
  gap: 8px;
}

.attachment-transition-meta span:empty {
  display: none;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.section-header h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.section-hint {
  color: var(--color-muted-text);
  font-size: 12px;
}

.approval-evaluations-section {
  margin-top: 0;
}

.approval-evaluation-list {
  display: flex;
  flex-direction: column;
  gap: 0;
  margin-top: 20px;
  padding-left: 8px;
}

.approval-evaluation-card {
  display: flex;
  gap: 16px;
  position: relative;
  padding-bottom: 24px;

  &:last-child {
    padding-bottom: 0;

    .approval-evaluation-card__line {
      display: none;
    }
  }
}

.approval-evaluation-card__timeline {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
  width: 12px;
}

.approval-evaluation-card__dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 2.5px solid;
  flex-shrink: 0;
  background: var(--color-surface);
  z-index: 1;
  box-shadow: 0 0 0 3px var(--color-surface);

  &.is-primary { border-color: var(--el-color-primary); }
  &.is-success { border-color: var(--el-color-success); }
  &.is-warning { border-color: var(--el-color-warning); }
  &.is-danger { border-color: var(--el-color-danger); }
  &.is-info { border-color: var(--el-color-info); }
}

.approval-evaluation-card__line {
  flex: 1;
  width: 1px;
  background: var(--color-border);
  margin-top: 4px;
}

.approval-evaluation-card__body {
  flex: 1;
  min-width: 0;
  padding: 14px 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;

  &:hover {
    border-color: var(--color-accent-light, #93c5fd);
    box-shadow: 0 2px 12px rgba(37, 99, 235, 0.06);
  }
}

.approval-evaluation-card.is-success .approval-evaluation-card__body {
  border-left: 3px solid var(--el-color-success);
}

.approval-evaluation-card.is-danger .approval-evaluation-card__body {
  border-left: 3px solid var(--el-color-danger);
}

.approval-evaluation-card.is-warning .approval-evaluation-card__body {
  border-left: 3px solid var(--el-color-warning);
}

.approval-evaluation-card.is-primary .approval-evaluation-card__body {
  border-left: 3px solid var(--el-color-primary);
}

.approval-evaluation-card.is-info .approval-evaluation-card__body {
  border-left: 3px solid var(--el-color-info);
}

.approval-evaluation-header {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.approval-evaluation-avatar {
  flex-shrink: 0;
  margin-top: 1px;
  background: var(--color-accent-tint-light, #eff6ff);
  color: var(--color-accent);
  font-size: 12px;
  font-weight: 600;
}

.approval-evaluation-meta {
  flex: 1;
  min-width: 0;
}

.approval-evaluation-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.approval-evaluation-node {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--color-surface-alt);
  color: var(--color-text-secondary);
  font-size: 11px;
  line-height: 16px;
  white-space: nowrap;
}

.approval-evaluation-time {
  margin-top: 2px;
  color: var(--color-muted-text);
  font-size: 12px;
  line-height: 16px;
}

.approval-evaluation-side {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex: 0 0 auto;
  margin-left: auto;
  white-space: nowrap;
}

.approval-evaluation-rating {
  display: inline-flex;
  align-items: center;
  line-height: 1;
}

.approval-evaluation-rating :deep(.el-rate) {
  height: 16px;
}

.approval-evaluation-rating :deep(.el-rate__item) {
  padding-right: 2px;
}

.approval-evaluation-rating :deep(.el-rate__icon) {
  margin-right: 0;
  font-size: 14px;
}

.approval-evaluation-content {
  margin: 10px 0 0;
  color: var(--color-text-primary);
  line-height: 1.7;
  white-space: pre-wrap;
  font-size: 13px;
}

.approval-evaluation-supplement-button {
  width: 24px;
  height: 24px;
  padding: 0;
  font-size: 15px;
  border-radius: 4px;
}

.approval-evaluation-supplement-button:hover {
  background: var(--el-color-primary-light-9);
}

@media (max-width: 768px) {
  .approval-evaluation-header {
    flex-wrap: wrap;
  }

  .approval-evaluation-side {
    width: calc(100% - 40px);
    margin-left: 40px;
    justify-content: flex-start;
    flex-wrap: wrap;
  }
}

.approval-supplement-list {
  margin: 12px 0 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.approval-supplement-item {
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--color-surface-alt);
  border: 1px solid var(--color-border);
}

.approval-supplement-item__header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 6px;
}

.approval-supplement-item__tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 7px;
  border-radius: 999px;
  background: var(--color-accent);
  color: #fff;
  font-size: 11px;
  font-weight: 500;
  line-height: 16px;
}

.approval-supplement-item__time {
  color: var(--color-muted-text);
  font-size: 12px;
}

.approval-supplement-item__content {
  margin: 0;
  color: var(--color-text-primary);
  line-height: 1.6;
  white-space: pre-wrap;
  font-size: 13px;
}

.approval-dialog-tip {
  margin: 0 0 16px;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.approval-dialog-rate-item {
  margin-bottom: 18px;
}

.approval-dialog-rate-item :deep(.el-form-item__label) {
  color: var(--color-text-secondary);
  font-weight: 500;
  letter-spacing: 0.2px;
}

.approval-dialog-rate {
  display: flex;
  align-items: center;
  gap: 10px;
}

.approval-dialog-rate :deep(.el-rate__icon) {
  font-size: 22px;
  transition: transform 200ms ease, color 200ms ease;
}

.approval-dialog-rate :deep(.el-rate__item:hover .el-rate__icon) {
  transform: scale(1.12);
}

.approval-dialog-rate :deep(.el-rate__text) {
  margin-left: 8px;
  color: var(--color-text-secondary);
  font-size: 13px;
}

/* 会签审批样式 */
.countersign-records {
  margin-bottom: 20px;
}

.countersign-records-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-primary);
  margin-bottom: 12px;
}

.countersign-submit {
  padding-top: 16px;
  border-top: 1px solid var(--color-border);
}

.countersign-tip {
  color: var(--color-text-secondary);
  font-size: 14px;
  margin-bottom: 16px;
}

.countersign-rate {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.countersign-label {
  color: var(--color-text-secondary);
  font-size: 14px;
}

.countersign-empty {
  padding: 20px 0;
}

/* ===== 审批附件上传 ===== */
.approval-attachment-section {
  margin-top: 16px;
}

.approval-attachment-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.approval-attachment-header span {
  font-size: 14px;
  color: var(--color-text-secondary);
}

/* 附件上传区和附件列表样式已移至 AttachmentUploader 组件 */

/* 审批评价中的附件展示 */
.approval-evaluation-attachments {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  margin-top: 6px;
  min-width: 0;
}

.approval-evaluation-attachments--main {
  margin-left: 46px;
}

.approval-evaluation-attachments__label {
  flex: 0 0 auto;
  line-height: 20px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.approval-evaluation-attachments__list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}

.approval-evaluation-attachments__list li {
  min-width: 0;
  line-height: 20px;
}

.approval-evaluation-attachments__link {
  font-size: 12px;
  color: var(--color-primary);
  cursor: pointer;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.approval-evaluation-attachments__link:hover {
  text-decoration: underline;
}

/* 流转历史评审意见样式 */
.history-item__comment {
  margin-top: 8px;
  padding: 8px 12px;
  background-color: var(--el-fill-color-light);
  border-radius: 4px;
  display: flex;
  align-items: flex-start;
  gap: 6px;
}

.history-item__comment .comment-icon {
  color: var(--el-color-primary);
  font-size: 14px;
  margin-top: 2px;
  flex-shrink: 0;
}

.history-item__comment .comment-text {
  flex: 1;
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.6;
  word-break: break-word;
}
</style>
