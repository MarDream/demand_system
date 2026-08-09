<template>
  <div class="system-assistant">
    <button
      ref="fabRef"
      class="assistant-fab"
      type="button"
      title="AI助手（Ctrl/⌘ + K）"
      :style="fabStyle"
      @click="handleFabClick"
      @mousedown.prevent="onFabDragStart"
      @touchstart.prevent="onFabDragStart"
    >
      <AssistantAvatar :variant="avatarVariant" />
    </button>

    <el-dialog
      v-model="visible"
      width="920px"
      class="assistant-dialog"
      :fullscreen="assistantFullscreen"
      :draggable="!assistantFullscreen"
      :show-close="false"
      :close-on-click-modal="false"
      align-center
      destroy-on-close
    >
      <template #header>
        <div class="assistant-panel__header">
          <div class="assistant-panel__title-block">
            <div class="assistant-panel__title">AI 操作助手</div>
            <div class="assistant-panel__subtitle" :title="assistantSubtitle">结合当前页面、系统菜单和模型能力，为你提供入口导航与操作建议</div>
          </div>

          <div class="assistant-panel__header-right">
            <!-- 信息标签组：纯文本状态展示，无操作语义 -->
            <div class="assistant-panel__info-group">
              <el-tag v-if="currentPageContext.pageTitle" type="info" effect="plain" size="small" class="assistant-meta-tag">当前页面：{{ currentPageContext.pageTitle }}</el-tag>
              <el-tag type="success" effect="plain" size="small" class="assistant-meta-tag">Ctrl/⌘ + K</el-tag>
              <el-dropdown trigger="click" @command="handleAvatarCommand">
                <el-tag class="assistant-avatar-tag" type="warning" effect="plain" size="small">
                  头像：{{ avatarVariantLabel }}
                  <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-tag>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="girl" :disabled="avatarVariant === 'girl'">圆脸少女 · 双马尾</el-dropdown-item>
                    <el-dropdown-item command="fox" :disabled="avatarVariant === 'fox'">小狐狸 · 戴眼镜</el-dropdown-item>
                    <el-dropdown-item command="cat" :disabled="avatarVariant === 'cat'">小猫咪 · 竖瞳</el-dropdown-item>
                    <el-dropdown-item command="elf" :disabled="avatarVariant === 'elf'">魔法少女 · 尖耳</el-dropdown-item>
                    <el-dropdown-item command="default" :disabled="avatarVariant === 'default'">经典数字人</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>

            <!-- 操作按钮组：统一 32x32 圆角按钮，关闭按钮位于组的最右端 -->
            <div class="assistant-panel__action-group" role="toolbar" aria-label="弹窗操作">
              <el-tooltip content="新会话" placement="bottom">
                <el-button
                  class="assistant-tool-button"
                  text
                  aria-label="新会话"
                  @click="handleCreateSession"
                >
                  <el-icon><Plus /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip :content="assistantFullscreen ? '退出全屏' : '全屏'" placement="bottom">
                <el-button
                  class="assistant-tool-button"
                  text
                  :aria-label="assistantFullscreen ? '退出全屏' : '全屏'"
                  @click="toggleAssistantFullscreen"
                >
                  <el-icon>
                    <ScaleToOriginal v-if="assistantFullscreen" />
                    <FullScreen v-else />
                  </el-icon>
                </el-button>
              </el-tooltip>
              <span class="assistant-panel__action-divider" aria-hidden="true" />
              <el-tooltip content="关闭（Esc）" placement="bottom">
                <el-button
                  class="assistant-tool-button assistant-tool-button--close"
                  text
                  aria-label="关闭"
                  @mousedown.stop
                  @click="assistantStore.close()"
                >
                  <el-icon><Close /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
          </div>
        </div>
      </template>

      <div class="assistant-panel" :class="{ 'assistant-panel--fullscreen': assistantFullscreen }">
        <div class="assistant-panel__body">
          <aside class="assistant-session-list">
            <div class="assistant-session-list__header">
              <el-icon class="assistant-session-list__header-icon"><Folder /></el-icon>
              <span>会话</span>
              <el-tooltip content="新会话" placement="right">
                <button type="button" class="assistant-session-list__new-btn" @click="handleCreateSession">
                  <el-icon><Plus /></el-icon>
                </button>
              </el-tooltip>
            </div>

            <!-- 搜索框 -->
            <div class="assistant-session-list__search">
              <el-input
                v-model="sessionSearchKeyword"
                size="small"
                placeholder="搜索会话…"
                :prefix-icon="Search"
                clearable
                class="session-search-input"
              />
            </div>

            <!-- 会议列表滚动区 -->
            <div class="assistant-session-list__scrollable">
              <div v-if="filteredSessionGroups.length === 0 && sessions.length === 0" class="assistant-session-list__empty">暂无会话</div>
              <div v-else-if="filteredSessionGroups.length === 0" class="assistant-session-list__empty">无匹配会话</div>
              <div
                v-for="group in filteredSessionGroups"
                :key="group.key"
                class="assistant-session-group"
              >
                <div class="assistant-session-group__label">{{ group.label }}</div>
                <div
                  v-for="session in group.items"
                  :key="session.id"
                  class="assistant-session-item"
                  :class="{ 'is-active': session.id === activeSessionId }"
                  @click="handleSelectSession(session.id)"
                >
                  <span class="assistant-session-item__title">{{ session.title || '新会话' }}</span>
                  <span class="assistant-session-item__time">{{ formatSessionTime(session.lastMessageAt || session.updatedAt) }}</span>
                  <el-icon
                    class="assistant-session-item__delete"
                    @click.stop="handleDeleteSession(session.id)"
                  >
                    <Delete />
                  </el-icon>
                </div>
              </div>
            </div>

            <!-- 底部信息 -->
            <div class="assistant-session-list__footer">
              {{ sessions.length }} 个会话
            </div>
          </aside>

          <section class="assistant-chat">
            <div ref="messageListRef" class="assistant-message-list">
              <div v-if="messages.length === 0" class="assistant-empty">
                <el-icon class="assistant-empty__icon"><ChatDotRound /></el-icon>
                <div class="assistant-empty__title">告诉我你想完成什么操作</div>
                <div class="assistant-empty__desc">例如：如何新建需求、去哪里配置工作流、在哪里维护知识库</div>

                <div v-if="recommendedQuestions.length" class="assistant-empty__quick-asks">
                  <div class="assistant-section-title">你可以这样问我</div>
                  <div class="assistant-chip-list">
                    <button
                      v-for="question in recommendedQuestions"
                      :key="question"
                      type="button"
                      class="assistant-chip"
                      :disabled="sending"
                      @click="handleQuickAsk(question)"
                    >
                      {{ question }}
                    </button>
                  </div>
                </div>
              </div>

              <div
                v-for="message in messages"
                :key="String(message.id)"
                class="assistant-message"
                :class="[`assistant-message--${message.role}`, { 'is-streaming': message.status === 'streaming' }]"
              >
                <div v-if="message.role === 'assistant'" class="assistant-message__avatar">
                  <AssistantAvatar :variant="avatarVariant" />
                </div>

                <div class="assistant-message__main">
                  <div v-if="message.role === 'user'" class="assistant-message__bubble assistant-message__bubble--user">
                    <div class="assistant-message__content assistant-message__content--user">{{ message.content }}</div>
                  </div>

                  <div v-else class="assistant-message__bubble assistant-message__bubble--assistant">
                    <!-- 流式生成中：动态提示 + 闪烁光标 -->
                    <div v-if="message.status === 'streaming' && !message.content" class="assistant-streaming-hint">
                      <span class="assistant-streaming-hint__text">{{ streamingHintText }}</span>
                      <span class="assistant-streaming-hint__dots">
                        <span class="dot" /><span class="dot" /><span class="dot" />
                      </span>
                    </div>
                    <MarkdownContent
                      v-else
                      class="assistant-message__content assistant-message__content--assistant"
                      :content="message.content || ''"
                    />
                    <!-- 流式输出中，内容末尾闪烁光标 -->
                    <span v-if="message.status === 'streaming' && message.content" class="assistant-streaming-cursor">|</span>
                  </div>

                  <div
                    v-if="message.role === 'assistant' && message.reasoning"
                    class="assistant-reasoning"
                  >
                    <button
                      type="button"
                      class="assistant-reasoning__header"
                      :class="{ 'is-open': reasoningFoldState[String(message.id)] || message.status === 'streaming' }"
                      @click="toggleReasoningFold(message)"
                    >
                      <el-icon class="assistant-reasoning__icon"><MagicStick /></el-icon>
                      <span class="assistant-reasoning__label">{{ message.status === 'streaming' ? '深度思考中…' : '已深度思考' }}</span>
                      <!-- 折叠态下显示推理内容预览（前 60 字） -->
                      <span
                        v-if="!isReasoningOpen(message) && message.status !== 'streaming'"
                        class="assistant-reasoning__preview"
                      >{{ reasoningPreview(message.reasoning) }}</span>
                      <el-icon class="assistant-reasoning__arrow" :class="{ 'is-open': isReasoningOpen(message) || message.status === 'streaming' }">
                        <ArrowDown />
                      </el-icon>
                    </button>
                    <div v-show="isReasoningOpen(message) || message.status === 'streaming'" class="assistant-reasoning__body">
                      <div class="assistant-reasoning__content">{{ message.reasoning }}</div>
                    </div>
                  </div>

                  <!-- 任务列表（检索过程，对标 WorkBuddy） -->
                  <AssistantTaskPanel
                    v-if="message.role === 'assistant' && message.tasks?.length"
                    :tasks="message.tasks"
                    :is-streaming="message.status === 'streaming'"
                    class="assistant-message__task-panel"
                  />

                  <div
                    v-if="message.role === 'assistant' && message.thinkingSteps?.length"
                    class="assistant-thinking-fold"
                  >
                    <button
                      type="button"
                      class="assistant-thinking-fold__header"
                      @click="toggleThinkingFold(message)"
                    >
                      <span class="assistant-thinking-fold__dot" />
                      <span class="assistant-thinking-fold__label">思考过程（{{ message.thinkingSteps.length }} 步）</span>
                      <el-icon class="assistant-thinking-fold__arrow" :class="{ 'is-open': thinkingFoldState[String(message.id)] }">
                        <ArrowDown />
                      </el-icon>
                    </button>
                    <div v-show="thinkingFoldState[String(message.id)]" class="assistant-thinking-fold__body">
                      <div
                        v-for="(step, index) in message.thinkingSteps"
                        :key="index"
                        class="assistant-thinking-step"
                        :class="`assistant-thinking-step--${step.stepType}`"
                      >
                        <div class="assistant-thinking-step__header">
                          <span class="assistant-thinking-step__title">{{ step.title }}</span>
                          <span v-if="step.score != null" class="assistant-thinking-step__score">{{ Math.round(step.score * 100) }}%</span>
                        </div>
                        <div v-if="step.detail" class="assistant-thinking-step__detail">{{ step.detail }}</div>
                      </div>
                    </div>
                  </div>

                  <div
                    v-if="message.role === 'assistant' && message.actions?.length"
                    class="assistant-message__actions"
                  >
                    <div class="assistant-section-title">建议入口</div>
                    <div class="assistant-action-list">
                      <button
                        v-for="action in message.actions"
                        :key="`${action.type}-${action.targetPath}-${action.label}`"
                        type="button"
                        class="assistant-action-card"
                        :class="[`is-${resolvePathMatch(action.targetPath)}`, { 'is-disabled': !action.targetPath }]"
                        :disabled="!action.targetPath"
                        @click="handleNavigate(action.targetPath)"
                      >
                        <div class="assistant-action-card__header">
                          <span class="assistant-action-card__label">{{ action.label }}</span>
                          <span class="assistant-action-card__badge">{{ resolvePathMatchLabel(action.targetPath) }}</span>
                        </div>
                        <div class="assistant-action-card__desc">{{ action.description || action.targetPath }}</div>
                        <div v-if="action.targetPath" class="assistant-action-card__path">{{ action.targetPath }}</div>
                      </button>
                    </div>
                  </div>

                  <div v-if="message.role === 'assistant' && message.warnings?.length" class="assistant-message__warnings">
                    <el-alert
                      v-for="warning in message.warnings"
                      :key="warning"
                      :title="warning"
                      type="warning"
                      :closable="false"
                      show-icon
                    />
                  </div>

                  <div
                    v-if="message.role === 'assistant' && message.sources?.length"
                    class="assistant-message__sources assistant-message__sources--compact"
                  >
                    <span class="assistant-sources-label">依据</span>
                    <div class="assistant-source-list assistant-source-list--compact">
                      <div
                        v-for="source in message.sources"
                        :key="`${source.code}-${source.path}-${source.title}`"
                        class="assistant-source-chip"
                        :class="[`is-${resolveSourceMatch(source)}`, { 'is-clickable': isSourceClickable(source), 'is-previewable': source.code === 'knowledge_document' && source.documentId }]"
                        :title="buildSourceTooltip(source)"
                        :role="isSourceClickable(source) ? 'link' : undefined"
                        :tabindex="isSourceClickable(source) ? 0 : undefined"
                        @click="handleSourceNavigate(source)"
                        @keydown.enter.prevent="handleSourceNavigate(source)"
                        @keydown.space.prevent="handleSourceNavigate(source)"
                      >
                        <span class="assistant-source-chip__title">{{ formatSourceTitle(source) }}</span>
                        <span class="assistant-source-chip__badge">{{ resolveSourceMatchLabel(source) }}</span>
                      </div>
                    </div>
                  </div>

                  <div v-if="message.role === 'assistant'" class="assistant-message__toolbar">
                    <el-tooltip content="复制回答" placement="top">
                      <button
                        type="button"
                        class="assistant-message__tool-btn"
                        :disabled="!message.content"
                        @click="handleCopyMessage(message)"
                      >
                        <el-icon><CopyDocument /></el-icon>
                      </button>
                    </el-tooltip>
                    <el-tooltip content="重新生成" placement="top">
                      <button
                        type="button"
                        class="assistant-message__tool-btn"
                        :disabled="sending || message.status === 'streaming'"
                        @click="handleRegenerate(message)"
                      >
                        <el-icon><RefreshRight /></el-icon>
                      </button>
                    </el-tooltip>
                    <span v-if="message.totalTokens != null && message.status !== 'streaming'" class="assistant-message__tokens" :title="`输入 ${message.inputTokens ?? 0} · 输出 ${message.outputTokens ?? 0} · 总计 ${message.totalTokens}`">
                      ↑{{ message.inputTokens ?? 0 }} · ↓{{ message.outputTokens ?? 0 }} · {{ message.totalTokens }} tokens
                    </span>
                    <span v-else-if="message.status === 'streaming'" class="assistant-message__tokens is-streaming">
                      <span class="assistant-message__tokens-pulse" />
                      {{ streamingStatusLabel }}
                    </span>
                    <span v-if="message.createdAt" class="assistant-message__time">{{ formatTime(message.createdAt) }}</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- 流式状态条：模型调用过程动态显示（对标 WorkBuddy 等待模型响应） -->
            <Transition name="stream-hud">
              <div v-if="sending" class="assistant-stream-hud" :class="streamHudVariantClass">
                <span class="assistant-stream-hud__pulse" aria-hidden="true">
                  <span class="assistant-stream-hud__pulse-dot" />
                  <span class="assistant-stream-hud__pulse-ring" />
                </span>
                <span class="assistant-stream-hud__label">
                  <template v-if="streamHudPhaseLabel">
                    {{ streamHudPhaseLabel }}
                  </template>
                  <template v-else>
                    等待模型响应
                  </template>
                </span>
                <span v-if="streamHudModelLabel" class="assistant-stream-hud__model">
                  · {{ streamHudModelLabel }}
                </span>
                <button
                  type="button"
                  class="assistant-stream-hud__stop"
                  title="停止生成"
                  @click="handleStop"
                >
                  <el-icon><VideoPause /></el-icon>
                  <span>停止</span>
                </button>
              </div>
            </Transition>

            <div
              class="assistant-composer"
              @dragover.prevent
              @drop.prevent="handleFileDrop"
              @paste="handlePaste"
            >
              <div v-if="attachedFiles.length" class="assistant-composer__files">
                <div
                  v-for="attached in attachedFiles"
                  :key="attached.uid"
                  class="assistant-file-card"
                  :class="{ 'is-uploading': attached.uploading }"
                >
                  <span class="assistant-file-card__icon">{{ getFileIconName(attached.contentType) }}</span>
                  <span class="assistant-file-card__name">{{ attached.name }}</span>
                  <span class="assistant-file-card__size">{{ formatBytes(attached.size) }}</span>
                  <button type="button" class="assistant-file-card__remove" @click="removeAttachedFile(attached.uid)">&times;</button>
                </div>
              </div>
              <div v-if="composerQuestions.length" class="assistant-composer__quick-asks">
                <div class="assistant-section-title">快捷提问</div>
                <div class="assistant-chip-list assistant-chip-list--compact">
                  <button
                    v-for="question in composerQuestions"
                    :key="question"
                    type="button"
                    class="assistant-chip assistant-chip--compact"
                    :disabled="sending"
                    @click="handleQuickAsk(question)"
                  >
                    {{ question }}
                  </button>
                </div>
              </div>

              <el-input
                v-model="draft"
                type="textarea"
                :rows="4"
                resize="none"
                maxlength="4000"
                show-word-limit
                :placeholder="composerPlaceholder"
                @keydown.enter.exact.prevent="handleSend"
              />
              <!-- 教育性提示：根据当前场景给出轻量化指引（对标 WorkBuddy 友好提示） -->
              <div v-if="currentModeHint" class="assistant-composer__hint">
                <el-icon class="assistant-composer__hint-icon"><InfoFilled /></el-icon>
                <span>{{ currentModeHint }}</span>
              </div>
              <div class="assistant-composer__footer">
                <div class="assistant-composer__scope">
                  <el-tooltip content="添加文件（支持拖拽、粘贴或点击上传）" placement="top">
                    <button
                      type="button"
                      class="assistant-composer__file-btn"
                      :disabled="sending"
                      @click="triggerFileSelect"
                    >
                      <el-icon><FolderAdd /></el-icon>
                    </button>
                  </el-tooltip>
                  <input
                    ref="fileInputRef"
                    type="file"
                    multiple
                    hidden
                    @change="handleFileInputChange"
                  />

                  <el-tooltip content="选择知识库问答范围：通用助手可开启联网搜索并整合本地知识库；全部/指定知识库会基于知识库内容回答" placement="top">
                    <el-select
                      v-model="selectedScopeKey"
                      size="small"
                      class="assistant-composer__scope-select"
                      :disabled="sending"
                    >
                      <template #prefix>
                        <el-icon class="assistant-composer__scope-icon"><Document /></el-icon>
                      </template>
                      <el-option label="通用助手" :value="SCOPE_GENERAL" />
                      <el-option label="全部知识库" :value="-1" />
                      <el-option
                        v-for="kb in knowledgeBases"
                        :key="kb.id"
                        :label="kb.name"
                        :value="kb.id"
                      />
                    </el-select>
                  </el-tooltip>

                  <el-popover
                    v-if="selectedKbScope != null || webSearchEnabled"
                    placement="top"
                    :width="230"
                    trigger="click"
                  >
                    <div class="assistant-search-scope-popover">
                      <strong>本地检索范围</strong>
                      <el-checkbox-group v-model="selectedAssistantSearchScopes">
                        <el-checkbox label="REQUIREMENT_BODY">工单正文</el-checkbox>
                        <el-checkbox label="KNOWLEDGE_BASE">知识库附件</el-checkbox>
                      </el-checkbox-group>
                      <span v-if="webSearchEnabled">联网内容由“联网”开关控制。</span>
                    </div>
                    <template #reference>
                      <el-button size="small" :disabled="sending">检索范围</el-button>
                    </template>
                  </el-popover>

                  <el-tooltip
                    v-if="selectedKbScope == null"
                    content="开启后：轻量检索全部本地知识库 + 大模型联网搜索，综合给出结果"
                    placement="top"
                  >
                    <div class="assistant-composer__websearch">
                      <el-icon class="assistant-composer__websearch-icon"><Link /></el-icon>
                      <el-switch
                        v-model="webSearchEnabled"
                        size="small"
                        :disabled="sending"
                        inline-prompt
                        active-text="联网"
                        inactive-text="联网"
                      />
                    </div>
                  </el-tooltip>

                  <el-tooltip v-if="selectedKbScope != null" content="检索模式" placement="top">
                    <el-select
                      v-model="searchMode"
                      size="small"
                      style="width: 120px"
                      :disabled="sending"
                    >
                      <el-option label="混合检索" value="hybrid" />
                      <el-option label="语义检索" value="semantic" />
                      <el-option label="关键词" value="keyword" />
                    </el-select>
                  </el-tooltip>

                  <el-tooltip v-if="selectedKbScope != null" content="召回片段数量" placement="top">
                    <el-input-number
                      v-model="topK"
                      size="small"
                      :min="1"
                      :max="50"
                      :step="5"
                      :disabled="sending"
                      style="width: 100px"
                    />
                  </el-tooltip>

                  <el-popover
                    v-model:visible="modelPopoverVisible"
                    trigger="click"
                    placement="top-start"
                    width="480"
                    popper-class="assistant-model-popover"
                  >
                    <template #reference>
                      <button
                        type="button"
                        class="assistant-composer__model-btn"
                        :disabled="!availableChatModels.length || sending"
                      >
                        <el-icon><Cpu /></el-icon>
                        <span>{{ selectedModelDisplay }}</span>
                        <el-icon class="assistant-composer__model-arrow"><ArrowDown /></el-icon>
                      </button>
                    </template>
                    <div class="assistant-model-menu">
                      <div class="assistant-model-menu__title">选择模型</div>
                      <div class="assistant-model-menu__panels">
                        <div class="assistant-model-menu__providers">
                          <div class="assistant-model-menu__label">接入组</div>
                          <button
                            v-for="group in groupedChatModels"
                            :key="group.providerName"
                            type="button"
                            class="assistant-model-menu__provider"
                            :class="{ 'is-active': selectedProvider === group.providerName }"
                            @click="selectedProvider = group.providerName"
                          >
                            <span class="assistant-model-menu__provider-name">{{ group.providerName }}</span>
                            <span class="assistant-model-menu__provider-count">{{ group.items.length }}</span>
                          </button>
                        </div>
                        <div class="assistant-model-menu__models">
                          <div class="assistant-model-menu__label">模型</div>
                          <button
                            v-for="model in currentProviderModels"
                            :key="model.id"
                            type="button"
                            class="assistant-model-menu__model"
                            :class="{ 'is-active': selectedLlmModelId === model.id }"
                            @click="handleModelSelect(model.id)"
                          >
                            <div class="assistant-model-menu__model-main">
                              <div class="assistant-model-menu__model-name">{{ model.name }}</div>
                              <div class="assistant-model-menu__model-id">{{ model.modelId }}</div>
                            </div>
                            <span class="assistant-model-menu__check">{{ selectedLlmModelId === model.id ? '✓' : '' }}</span>
                          </button>
                          <el-empty v-if="!currentProviderModels.length" description="该接入组暂无可用模型" :image-size="56" />
                        </div>
                      </div>
                    </div>
                  </el-popover>
                </div>
                <el-button v-if="sending" type="danger" @click="handleStop">
                  停止
                  <el-icon class="el-icon--right"><VideoPause /></el-icon>
                </el-button>
                <el-button v-else type="primary" :disabled="!draft.trim()" @click="handleSend">
                  发送
                  <el-icon class="el-icon--right"><Promotion /></el-icon>
                </el-button>
              </div>
            </div>
          </section>
        </div>
      </div>
    </el-dialog>

    <!-- 文档预览弹窗 -->
    <FilePreviewDialog
      v-if="previewFile"
      v-model="previewVisible"
      :file-name="previewFile.fileName"
      :file-type="previewFile.fileType"
      :knowledge-base-id="previewFile.knowledgeBaseId"
      :document-id="previewFile.documentId"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import type { CSSProperties } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, CopyDocument, Cpu, Delete, Document, FolderAdd, InfoFilled, MagicStick, Promotion, ArrowDown, FullScreen, RefreshRight, ScaleToOriginal, Plus, Close, VideoPause, Link, Search, Folder } from '@element-plus/icons-vue'
import MarkdownContent from '@/components/common/MarkdownContent.vue'
import AssistantAvatar from '@/components/assistant/avatars/AssistantAvatar.vue'
import AssistantTaskPanel from '@/components/assistant/AssistantTaskPanel.vue'
import { getToken } from '@/utils/auth'
import { useAssistantStore } from '@/stores/assistant'
import { useAssistantContext } from '@/composables/useAssistantContext'
import { useQuickQuestions } from '@/composables/useQuickQuestions'
import { getAllKnowledgeBases, type KnowledgeBase } from '@/api/modules/knowledge'
import { llmProviderApi, type ChatModelOption } from '@/api/modules/llmProvider'
import type { AssistantFileAttachment, AssistantMessage, AssistantPageContext, AssistantSearchScope, AssistantSource } from '@/types/assistant'
import FilePreviewDialog from '@/components/document/FilePreviewDialog.vue'

type PathMatchType = 'current-page' | 'current-menu' | 'related' | 'none'

const router = useRouter()
const assistantStore = useAssistantStore()
const { currentPageContext } = useAssistantContext()
const { visible, sessions, activeSessionId, messages, sending, initialized } = storeToRefs(assistantStore)

const draft = ref('')
const messageListRef = ref<HTMLDivElement | null>(null)
const assistantFullscreen = ref(false)
const assistantSubtitle = '结合当前页面、系统菜单和模型能力，为你提供入口导航与操作建议'

// ===== 快捷提问（动态轮询） =====
const quickQuestions = useQuickQuestions({
  pageRoute: () => currentPageContext.value?.routeName ?? '',
  dialogVisible: () => visible.value,
  hasActiveConversation: () => (activeSessionId.value !== null && messages.value.length > 0),
})

// ===== 会话搜索与分组 =====
const sessionSearchKeyword = ref('')

interface SessionGroup {
  key: string
  label: string
  items: typeof sessions.value
}

const filteredSessionGroups = computed<SessionGroup[]>(() => {
  const keyword = sessionSearchKeyword.value.trim().toLowerCase()
  let filtered = sessions.value
  if (keyword) {
    filtered = sessions.value.filter(s =>
      (s.title || '').toLowerCase().includes(keyword) ||
      (s.lastMessagePreview || '').toLowerCase().includes(keyword),
    )
  }

  // 按日期分组：今天、昨天、本周、更早
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const yesterday = new Date(today.getTime() - 86400000)
  const weekAgo = new Date(today.getTime() - 7 * 86400000)

  const groups: Record<string, SessionGroup> = {
    today: { key: 'today', label: '今天', items: [] },
    yesterday: { key: 'yesterday', label: '昨天', items: [] },
    thisWeek: { key: 'thisWeek', label: '本周', items: [] },
    earlier: { key: 'earlier', label: '更早', items: [] },
  }

  for (const s of filtered) {
    const t = new Date(s.lastMessageAt || s.updatedAt || s.createdAt || '')
    if (isNaN(t.getTime())) {
      groups.earlier.items.push(s)
    } else if (t >= today) {
      groups.today.items.push(s)
    } else if (t >= yesterday) {
      groups.yesterday.items.push(s)
    } else if (t >= weekAgo) {
      groups.thisWeek.items.push(s)
    } else {
      groups.earlier.items.push(s)
    }
  }

  return Object.values(groups).filter(g => g.items.length > 0)
})

function formatSessionTime(value?: string | null) {
  if (!value) return ''
  const d = new Date(value)
  if (isNaN(d.getTime())) return ''
  const now = new Date()
  const diffMs = now.getTime() - d.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  if (diffMins < 1) return '刚刚'
  if (diffMins < 60) return `${diffMins}分钟前`
  const diffHours = Math.floor(diffMins / 60)
  if (diffHours < 24) return `${diffHours}小时前`
  const md = `${String(d.getMonth() + 1).padStart(2, '0')}/${String(d.getDate()).padStart(2, '0')}`
  if (d.getFullYear() !== now.getFullYear()) return `${d.getFullYear()}/${md}`
  return md
}

// ===== 知识库问答范围 =====
// null = 通用助手（不检索知识库）；-1 = 全部知识库；具体值 = 指定知识库
// 注意：el-option.value 不允许 null（prop validator 拒绝），故 UI 绑定到 selectedScopeKey
//（'general' | -1 | kb.id），再由 watcher 同步到 selectedKbScope（number | null）。
const SCOPE_GENERAL = '__assistant_general__'
type ScopeKey = typeof SCOPE_GENERAL | -1 | number
const knowledgeBases = ref<KnowledgeBase[]>([])
const selectedKbScope = ref<number | null>(null)
const selectedScopeKey = ref<ScopeKey>(SCOPE_GENERAL)

// UI 变化 → 内部状态
watch(selectedScopeKey, (key) => {
  selectedKbScope.value = key === SCOPE_GENERAL ? null : (key as number)
})

// 内部状态变化 → UI（兜底，避免外部代码直接改 selectedKbScope 时 UI 不同步）
watch(selectedKbScope, (val) => {
  const expected: ScopeKey = val === null ? SCOPE_GENERAL : val
  if (selectedScopeKey.value !== expected) {
    selectedScopeKey.value = expected
  }
})

// ===== RAG 检索参数 =====
const searchMode = ref<'hybrid' | 'semantic' | 'keyword'>('hybrid')
const topK = ref<number>(10)
const selectedAssistantSearchScopes = ref<Array<Exclude<AssistantSearchScope, 'WEB'>>>([
  'REQUIREMENT_BODY',
  'KNOWLEDGE_BASE'
])

function buildAssistantSearchScopes(): AssistantSearchScope[] | undefined {
  if (selectedKbScope.value == null && !webSearchEnabled.value) return undefined
  const scopes: AssistantSearchScope[] = [...selectedAssistantSearchScopes.value]
  if (selectedKbScope.value == null && webSearchEnabled.value) scopes.push('WEB')
  return scopes
}

// ===== 联网搜索（仅通用助手模式生效）=====
const webSearchEnabled = ref<boolean>(false)

// ===== 文档预览 =====
const previewVisible = ref(false)
const previewFile = ref<{
  fileName: string
  fileType: string
  knowledgeBaseId: number
  documentId: number
} | null>(null)

function openSourcePreview(source: AssistantSource) {
  if (!source.documentId || !source.knowledgeBaseId) return
  const fileName = source.title || 'document'
  // 从文件名推断文件类型供预览组件使用
  const fileType = fileName.includes('.') ? fileName.split('.').pop()?.toLowerCase() || 'pdf' : 'pdf'
  previewFile.value = {
    fileName,
    fileType,
    knowledgeBaseId: source.knowledgeBaseId,
    documentId: source.documentId,
  }
  previewVisible.value = true
}

// ===== 文件上传（拖拽 / 粘贴 / 点击选择）=====
interface AttachedFile {
  uid: string
  file: File
  fileId?: number
  name: string
  size: number
  contentType: string
  extractedText?: string
  uploading: boolean
}
const attachedFiles = reactive<AttachedFile[]>([])
const fileInputRef = ref<HTMLInputElement | null>(null)
const MAX_FILE_SIZE = 10 * 1024 * 1024 // 10MB
const READABLE_EXTENSIONS = /\.(txt|md|json|xml|html?|css|jsx?|tsx?|java|py|go|rs|cpp|c|h|hpp|yml|yaml|toml|csv|log|sql|vue|svelte|env|cfg|ini|sh|bat|ps1)$/i
const IMAGE_EXTENSIONS = /\.(png|jpe?g|gif|webp|bmp|svg|ico)$/i

function triggerFileSelect() {
  fileInputRef.value?.click()
}

function handleFileInputChange(e: Event) {
  const target = e.target as HTMLInputElement
  if (target.files) {
    processFiles(Array.from(target.files))
    target.value = '' // reset for re-upload same file
  }
}

function handleFileDrop(e: DragEvent) {
  e.preventDefault()
  const items = e.dataTransfer?.files
  if (items) {
    processFiles(Array.from(items))
  }
}

function handlePaste(e: ClipboardEvent) {
  const items = e.clipboardData?.items
  if (!items) return
  for (let i = 0; i < items.length; i++) {
    const item = items[i]
    if (item.kind === 'file') {
      const file = item.getAsFile()
      if (file) processFiles([file])
    }
  }
}

function processFiles(newFiles: File[]) {
  for (const file of newFiles) {
    if (file.size > MAX_FILE_SIZE) {
      ElMessage.warning(`文件 ${file.name} 超过 10MB 限制，已跳过`)
      continue
    }
    const uid = `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`
    const attached: AttachedFile = {
      uid,
      file,
      name: file.name,
      size: file.size,
      contentType: file.type || 'application/octet-stream',
      uploading: true,
    }
    attachedFiles.push(attached)
    uploadAndExtract(attached)
  }
}

async function uploadAndExtract(attached: AttachedFile) {
  const file = attached.file
  try {
    // 文本文件：客户端读取内容
    if (READABLE_EXTENSIONS.test(file.name) || file.type.startsWith('text/')) {
      try {
        const text = await readFileAsText(file)
        attached.extractedText = text
      } catch {
        // 读取失败则跳过内容提取
      }
    }
    // 上传到文件服务
    const baseURL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
    const token = getToken() || ''
    const formData = new FormData()
    formData.append('file', file)
    const uploadRes = await fetch(`${baseURL}/v1/files/upload`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: formData,
    })
    if (uploadRes.ok) {
      const result = await uploadRes.json()
      attached.fileId = result?.data?.fileId || result?.fileId
    }
  } catch {
    // 上传失败也不阻断，fileId 为空时后端只在线展示文件名
  } finally {
    attached.uploading = false
  }
}

function readFileAsText(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      let text = reader.result as string
      // 限制 50KB 字符
      if (text.length > 50000) text = text.slice(0, 50000) + '\n…[已截断]'
      resolve(text)
    }
    reader.onerror = () => reject(new Error('读取失败'))
    reader.readAsText(file)
  })
}

function removeAttachedFile(uid: string) {
  const idx = attachedFiles.findIndex(f => f.uid === uid)
  if (idx >= 0) attachedFiles.splice(idx, 1)
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

function getFileIconName(ct: string) {
  if (ct.startsWith('image/')) return '🖼'
  if (ct.startsWith('text/')) return '📄'
  if (ct.includes('pdf')) return '📕'
  if (ct.includes('word') || ct.includes('document')) return '📝'
  if (ct.includes('sheet') || ct.includes('excel') || ct.includes('csv')) return '📊'
  return '📎'
}

function buildAttachmentsForRequest(): AssistantFileAttachment[] {
  return attachedFiles.map(f => ({
    fileId: f.fileId || 0,
    name: f.name,
    size: f.size,
    contentType: f.contentType,
    extractedText: f.extractedText || null,
  }))
}

async function loadKnowledgeBases() {
  try {
    const res = await getAllKnowledgeBases() as unknown as KnowledgeBase[] | { data?: KnowledgeBase[] }
    const list = Array.isArray(res) ? res : (res as { data?: KnowledgeBase[] })?.data || []
    knowledgeBases.value = list
  } catch {
    // 加载失败不阻断助手基础功能
  }
}

// ===== 聊天模型选择 =====
interface AssistantModelOption {
  id: number
  providerId: number
  providerName: string
  name: string
  label: string
  modelId: string
  isDefault: boolean
}

const availableChatModels = ref<AssistantModelOption[]>([])
const selectedLlmModelId = ref<number | null>(null)
const modelPopoverVisible = ref(false)
const selectedProvider = ref<string>('')

const selectedChatModel = computed<AssistantModelOption | null>(() => {
  if (selectedLlmModelId.value == null) return null
  return availableChatModels.value.find(item => item.id === selectedLlmModelId.value) || null
})

const groupedChatModels = computed(() => {
  const groups = new Map<string, AssistantModelOption[]>()
  availableChatModels.value.forEach((model) => {
    const providerName = model.providerName || '未命名提供商'
    if (!groups.has(providerName)) groups.set(providerName, [])
    groups.get(providerName)!.push(model)
  })
  return Array.from(groups.entries()).map(([providerName, items]) => ({ providerName, items }))
})

const currentProviderModels = computed<AssistantModelOption[]>(() => {
  if (!selectedProvider.value) {
    if (groupedChatModels.value.length > 0) {
      selectedProvider.value = groupedChatModels.value[0].providerName
    }
    return []
  }
  const group = groupedChatModels.value.find(g => g.providerName === selectedProvider.value)
  return group ? group.items : []
})

const selectedModelDisplay = computed(() => {
  if (!selectedChatModel.value) {
    return availableChatModels.value.length ? '选择模型' : '未配置模型'
  }
  return compactModelName(selectedChatModel.value)
})

function compactModelName(model: AssistantModelOption) {
  const source = (model.name || model.modelId || model.label).trim()
  const compact = source.replace(/^(gpt|claude|glm|qwen|deepseek|gemini)[-_:\s]*/i, '').trim()
  return compact || source
}

async function loadAvailableLlmModels() {
  try {
    const res = await llmProviderApi.listChatModels() as any
    const models = (res?.data ?? res ?? []) as ChatModelOption[]
    availableChatModels.value = models
      .map((model) => ({
        id: model.id,
        providerId: model.providerId,
        providerName: model.providerName,
        name: model.name,
        label: `${model.providerName} / ${model.name}`,
        modelId: model.modelId,
        isDefault: model.isDefault
      }))
      .sort((a, b) => Number(b.isDefault) - Number(a.isDefault) || a.label.localeCompare(b.label, 'zh-CN'))

    if (!availableChatModels.value.length) {
      selectedLlmModelId.value = null
      return
    }
    const stillExists = availableChatModels.value.some(item => item.id === selectedLlmModelId.value)
    if (!stillExists) {
      selectedLlmModelId.value = availableChatModels.value.find(item => item.isDefault)?.id || availableChatModels.value[0].id
    }
  } catch {
    availableChatModels.value = []
    selectedLlmModelId.value = null
  }
}

function handleModelSelect(modelId: number) {
  selectedLlmModelId.value = modelId
  modelPopoverVisible.value = false
}

// ===== 浮标头像切换 =====
type AvatarVariant = 'default' | 'girl' | 'fox' | 'cat' | 'elf'
const AVATAR_STORAGE_KEY = 'assistantFabAvatar'

function loadAvatarVariant(): AvatarVariant {
  try {
    const raw = localStorage.getItem(AVATAR_STORAGE_KEY)
    if (raw && ['default', 'girl', 'fox', 'cat', 'elf'].includes(raw)) {
      return raw as AvatarVariant
    }
  } catch {
    // localStorage 不可用时忽略
  }
  return 'girl'
}

const avatarVariant = ref<AvatarVariant>(loadAvatarVariant())

const avatarVariantLabel = computed(() => {
  switch (avatarVariant.value) {
    case 'girl': return '圆脸少女'
    case 'fox': return '小狐狸'
    case 'cat': return '小猫咪'
    case 'elf': return '魔法少女'
    default: return '经典数字人'
  }
})

function handleAvatarCommand(command: string) {
  setAvatarVariant(command as AvatarVariant)
  ElMessage.success(`已切换为 ${avatarVariantLabel.value}`)
}

function setAvatarVariant(variant: AvatarVariant) {
  avatarVariant.value = variant
  try {
    localStorage.setItem(AVATAR_STORAGE_KEY, variant)
  } catch {
    // 忽略存储失败
  }
}

// ===== 拖拽浮标 =====
const fabRef = ref<HTMLButtonElement | null>(null)

const FAB_SIZE = 48
const FAB_MARGIN = 16
const FAB_DEFAULT_OFFSET = 28

function getDefaultFabPosition() {
  if (typeof window === 'undefined') {
    return { x: FAB_MARGIN, y: FAB_MARGIN }
  }
  return {
    x: Math.max(FAB_MARGIN, window.innerWidth - FAB_SIZE - FAB_DEFAULT_OFFSET),
    y: Math.max(FAB_MARGIN, window.innerHeight - FAB_SIZE - FAB_DEFAULT_OFFSET),
  }
}

const fabPos = ref(getDefaultFabPosition())
const fabInitialized = ref(typeof window !== 'undefined')
const isDragging = ref(false)
const dragStartPos = ref({ x: 0, y: 0 })
const dragOffset = ref({ x: 0, y: 0 })
const hasMoved = ref(false)

function clampPosition(x: number, y: number) {
  if (typeof window === 'undefined') {
    return { x, y }
  }
  const maxX = window.innerWidth - FAB_SIZE - FAB_MARGIN
  const maxY = window.innerHeight - FAB_SIZE - FAB_MARGIN
  return {
    x: Math.max(FAB_MARGIN, Math.min(maxX, x)),
    y: Math.max(FAB_MARGIN, Math.min(maxY, y)),
  }
}

function initFabPosition() {
  if (!fabInitialized.value) {
    fabPos.value = getDefaultFabPosition()
    fabInitialized.value = true
    return
  }
  fabPos.value = clampPosition(fabPos.value.x, fabPos.value.y)
}

const fabStyle = computed<CSSProperties>(() => ({
  left: `${fabPos.value.x}px`,
  top: `${fabPos.value.y}px`,
  right: 'auto',
  bottom: 'auto',
  visibility: fabInitialized.value ? 'visible' : 'hidden',
  transition: !fabInitialized.value || isDragging.value ? 'none' : 'left 0.3s ease, top 0.3s ease',
}))

function onFabDragStart(e: MouseEvent | TouchEvent) {
  const clientX = 'touches' in e ? e.touches[0].clientX : e.clientX
  const clientY = 'touches' in e ? e.touches[0].clientY : e.clientY

  isDragging.value = true
  hasMoved.value = false
  dragStartPos.value = { x: clientX, y: clientY }
  dragOffset.value = {
    x: clientX - fabPos.value.x,
    y: clientY - fabPos.value.y,
  }

  document.addEventListener('mousemove', onFabDragMove)
  document.addEventListener('mouseup', onFabDragEnd)
  document.addEventListener('touchmove', onFabDragMove, { passive: false })
  document.addEventListener('touchend', onFabDragEnd)
}

function onFabDragMove(e: MouseEvent | TouchEvent) {
  if (!isDragging.value) return
  e.preventDefault()
  const clientX = 'touches' in e ? e.touches[0].clientX : e.clientX
  const clientY = 'touches' in e ? e.touches[0].clientY : e.clientY

  const dx = clientX - dragStartPos.value.x
  const dy = clientY - dragStartPos.value.y
  if (Math.abs(dx) > 3 || Math.abs(dy) > 3) {
    hasMoved.value = true
  }

  fabPos.value = clampPosition(
    clientX - dragOffset.value.x,
    clientY - dragOffset.value.y,
  )
}

function onFabDragEnd() {
  isDragging.value = false
  document.removeEventListener('mousemove', onFabDragMove)
  document.removeEventListener('mouseup', onFabDragEnd)
  document.removeEventListener('touchmove', onFabDragMove)
  document.removeEventListener('touchend', onFabDragEnd)

  // 吸附到最近的边缘
  if (hasMoved.value) {
    const centerX = fabPos.value.x + FAB_SIZE / 2
    const snapX = centerX < window.innerWidth / 2 ? FAB_MARGIN : window.innerWidth - FAB_SIZE - FAB_MARGIN
    fabPos.value = { ...fabPos.value, x: snapX }
  }
}

function handleFabClick() {
  // 拖拽过程中不触发点击
  if (hasMoved.value) {
    hasMoved.value = false
    return
  }
  handleOpen()
}

function handleFabResize() {
  fabPos.value = clampPosition(fabPos.value.x, fabPos.value.y)
}

onMounted(() => {
  window.addEventListener('keydown', handleGlobalKeydown)
  initFabPosition()
  window.addEventListener('resize', handleFabResize)
})

const composerPlaceholder = computed(() => {
  if (selectedKbScope.value == null && webSearchEnabled.value) {
    return '联网搜索已开启，可问任何问题，也可结合本地知识库综合回答'
  }
  const title = currentPageContext.value.pageTitle
  if (title) {
    return `例如：我在"${title}"页面想完成什么操作？`
  }
  return '请输入你的问题，例如：如何新建需求？去哪里配置工作流？'
})

/** 教育性提示：根据当前场景给出轻量化指引（仅在有内容时显示） */
const currentModeHint = computed(() => {
  if (selectedKbScope.value != null) {
    const scope = selectedKbScope.value === -1 ? '全部知识库' : (knowledgeBases.value.find(k => k.id === selectedKbScope.value)?.name || '指定知识库')
    return `回答仅基于「${scope}」内容生成，未命中片段将明确标注`
  }
  if (webSearchEnabled.value) {
    return '联网结果仅供参考，关键事实请结合本地资料二次核对'
  }
  if (draft.value.trim().length > 0) {
    return '能力不是开越多越好，无关的关掉，省 Token 又少干扰'
  }
  return ''
})

const recommendedQuestions = computed(() => {
  // 优先从后端获取动态问题（人工 + AI 提炼）
  const backend = quickQuestions.questions.value
  if (backend.length > 0) {
    return backend.map(q => q.questionText)
  }
  // 冷启动兜底：后端无数据时回退到硬编码默认问题
  return buildRecommendedQuestions(currentPageContext.value)
})
const composerQuestions = computed(() => recommendedQuestions.value.slice(0, 3))

function buildRecommendedQuestions(pageContext: AssistantPageContext) {
  const route = pageContext.route || pageContext.activeMenu || ''
  const routeName = pageContext.routeName || ''
  const entityType = pageContext.entityType || ''

  switch (routeName) {
    case 'RequirementCreate':
      return [
        '创建需求时哪些字段必须填写？',
        '如何保存草稿或提交需求？',
        '如何选择项目、优先级和需求类型？',
      ]
    case 'RequirementDetail':
      return [
        '如何查看这个需求的流转记录？',
        '如何在需求详情里查看评论、附件和评审信息？',
        '这个需求下一步应该如何处理？',
      ]
    case 'WorkflowConfigEditor':
      return [
        '如何新增工作流节点？',
        '如何配置节点之间的流转条件？',
        '工作流编辑后如何保存并发布？',
      ]
    case 'WorkflowMigration':
      return [
        '如何执行工作流迁移？',
        '迁移前需要确认哪些影响范围？',
        '工作流迁移失败后如何回滚？',
      ]
    case 'KnowledgeDetail':
      return [
        '如何上传和管理当前知识库文档？',
        '如何查看知识库文档解析状态？',
        '如何分享或维护这个知识库？',
      ]
    case 'KnowledgeSearch':
      return [
        '如何进行知识库语义检索？',
        '如何限定只检索某个知识库？',
        '检索结果不准确时应该怎么调整？',
      ]
    case 'RatingStatistics':
      return [
        '如何查看需求评分分析？',
        '评分统计里的指标分别代表什么？',
        '如何按项目或时间范围分析评分趋势？',
      ]
    case 'BitableList':
      return [
        '如何新建一张多维表格？',
        '如何找到已有的多维表格？',
        '多维表格适合管理哪些业务数据？',
      ]
    case 'BitableEditor':
      return [
        '如何在多维表格中新增字段和记录？',
        '如何切换或配置多维表格视图？',
        '如何把多维表格数据和业务需求关联起来？',
      ]
    case 'UserManage':
      return [
        '如何新增或停用用户？',
        '如何给用户分配角色？',
        '用户无法登录时应该检查哪些配置？',
      ]
    case 'RoleManage':
      return [
        '如何创建角色并分配权限？',
        '如何调整角色可访问的菜单？',
        '角色权限变更后多久生效？',
      ]
    case 'MenuManagement':
      return [
        '如何新增或调整系统菜单？',
        '如何配置菜单权限标识？',
        '菜单调整后前端看不到应该检查什么？',
      ]
    case 'RequirementConfig':
      return [
        '如何维护需求类型和字段配置？',
        '需求配置会影响哪些创建或流转环节？',
        '如何让配置变更对新需求生效？',
      ]
    case 'RequirementTemplates':
      return [
        '如何新增需求模板？',
        '如何维护模板字段和默认内容？',
        '创建需求时如何使用模板？',
      ]
    case 'Dashboard':
      return [
        '首页指标分别代表什么？',
        '如何从首页快速进入待办需求？',
        '如何查看我负责事项的整体进展？',
      ]
    case 'Notifications':
      return [
        '如何查看我的系统通知？',
        '如何处理未读通知？',
        '哪些操作会产生通知提醒？',
      ]
    default:
      break
  }

  if (route.startsWith('/requirements') || entityType === 'requirement') {
    return [
      '如何新建一个需求？',
      '如何筛选并快速找到我负责的需求？',
      '如何查看需求详情并继续跟进处理？',
    ]
  }

  if (route.startsWith('/iterations') || entityType === 'iteration') {
    return [
      '如何创建一个新的迭代？',
      '如何把需求安排进迭代？',
      '如何查看迭代排期和容量情况？',
    ]
  }

  if (route.startsWith('/settings/knowledge') || entityType === 'knowledge') {
    return [
      '如何新建或维护知识库？',
      '如何查看某个知识库的文档详情？',
      '如何缩小检索范围到指定知识库？',
    ]
  }

  if (route.startsWith('/system/workflow') || entityType === 'workflow') {
    return [
      '如何配置一个新的工作流？',
      '如何编辑现有工作流的节点和流转规则？',
      '工作流迁移入口在哪里？',
    ]
  }

  if (route.startsWith('/settings/llm') || entityType === 'llm') {
    return [
      '如何配置系统使用的大模型？',
      '如何启用默认的聊天模型？',
      'Embedding 和 Rerank 模型在哪里设置？',
    ]
  }

  if (route.startsWith('/settings/projects') || entityType === 'project') {
    return [
      '如何新增一个项目？',
      '如何维护项目归属和关联关系？',
      '项目配置完成后会影响哪些功能？',
    ]
  }

  if (route.startsWith('/settings/users') || entityType === 'user') {
    return [
      '如何新增用户并分配角色？',
      '如何调整用户状态或基础信息？',
      '用户权限异常时应该从哪里排查？',
    ]
  }

  if (route.startsWith('/settings/roles') || entityType === 'role') {
    return [
      '如何创建角色并绑定权限？',
      '如何查看角色覆盖了哪些菜单？',
      '如何安全调整管理员角色权限？',
    ]
  }

  if (route.startsWith('/settings/menus') || entityType === 'menu') {
    return [
      '如何维护系统菜单？',
      '如何配置菜单权限编码？',
      '菜单新增后为什么页面没有显示？',
    ]
  }

  if (route.startsWith('/settings/requirement-templates') || entityType === 'requirement-template') {
    return [
      '如何新增需求模板？',
      '如何设置模板默认内容？',
      '模板会影响哪些需求创建入口？',
    ]
  }

  if (route.startsWith('/settings/requirements') || entityType === 'requirement-config') {
    return [
      '如何维护需求配置？',
      '需求字段或类型调整后会影响哪些页面？',
      '如何确认配置已经生效？',
    ]
  }

  if (route.startsWith('/bitable') || entityType === 'bitable') {
    return [
      '如何创建和维护多维表格？',
      '如何在表格中配置字段和视图？',
      '多维表格可以用来管理哪些业务数据？',
    ]
  }

  return [
    '如何新建需求？',
    '去哪里配置工作流？',
    '在哪里维护知识库和上传文档？',
    '系统里的模型配置入口在哪里？',
  ]
}

function normalizePath(path?: string) {
  if (!path) return ''
  const [purePath] = path.split(/[?#]/)
  if (!purePath) return ''
  if (purePath.length > 1) {
    return purePath.replace(/\/+$/, '')
  }
  return purePath
}

function resolvePathMatch(targetPath?: string): PathMatchType {
  const normalizedTarget = normalizePath(targetPath)
  if (!normalizedTarget) return 'none'

  const currentRoute = normalizePath(currentPageContext.value.route)
  if (normalizedTarget === currentRoute) {
    return 'current-page'
  }

  const activeMenu = normalizePath(currentPageContext.value.activeMenu)
  if (normalizedTarget === activeMenu) {
    return 'current-menu'
  }

  return 'related'
}

function resolveSourceMatch(source: AssistantSource): PathMatchType {
  if (source.code === 'current.page') {
    return 'current-page'
  }
  return resolvePathMatch(source.path)
}

function resolvePathMatchLabel(targetPath?: string) {
  const match = resolvePathMatch(targetPath)
  if (match === 'current-page') return '当前页'
  if (match === 'current-menu') return '当前菜单'
  if (match === 'related') return '推荐入口'
  return '待确认'
}

function resolveSourceMatchLabel(source: AssistantSource) {
  if (source.code === 'web_search') return '联网来源'
  if (source.contentType === 'image_ocr') return '图片 OCR'
  if (source.contentType === 'image_caption') return '图片理解'
  if (source.contentType === 'body_image') return '正文 + 图片'
  if (isRequirementSource(source)) return '工单正文'
  if (source.code === 'knowledge_document') return '知识库依据'
  const match = resolveSourceMatch(source)
  if (match === 'current-page') return '当前页依据'
  if (match === 'current-menu') return '当前菜单依据'
  if (match === 'related') return '相关功能依据'
  return '系统依据'
}

function isRequirementSource(source: AssistantSource) {
  return (source.sourceType || source.code || '').startsWith('requirement_body')
}

function formatSourceTitle(source: AssistantSource) {
  if (isRequirementSource(source)) {
    const no = source.requirementNo || ''
    const title = source.requirementTitle || source.title || '未命名工单'
    return no ? `${no} ${title}` : title
  }
  return source.title || source.path || source.code || '系统匹配结果'
}

function buildSourceTooltip(source: AssistantSource) {
  const segments = [
    formatSourceTitle(source),
    source.path,
    source.contentType === 'image_ocr' ? '命中工单正文图片 OCR 内容' : source.contentType === 'image_caption' ? '命中工单正文图片理解内容' : source.reason || '系统根据页面上下文与功能目录生成了这条建议。',
  ].filter(Boolean)
  return segments.join('\n')
}

function isSourceClickable(source: AssistantSource) {
  if (isRequirementSource(source) && source.requirementId) return true
  if (source.code === 'knowledge_document' && source.documentId && source.knowledgeBaseId) return true
  return !!source.path
}

function toggleAssistantFullscreen() {
  assistantFullscreen.value = !assistantFullscreen.value
}

async function ensureReady() {
  if (!initialized.value) {
    await assistantStore.loadSessions()
  }
  if (activeSessionId.value) {
    await assistantStore.loadMessages(activeSessionId.value)
  }
}

async function handleOpen() {
  assistantStore.open()
  await ensureReady()
  if (knowledgeBases.value.length === 0) {
    await loadKnowledgeBases()
  }
  if (availableChatModels.value.length === 0) {
    await loadAvailableLlmModels()
  }
}

async function handleCreateSession() {
  draft.value = ''
  assistantStore.startNewSession()
}

async function handleSelectSession(sessionId: number) {
  await assistantStore.selectSession(sessionId)
}

async function handleDeleteSession(sessionId: number) {
  try {
    await ElMessageBox.confirm('删除后将清空该会话中的问答记录，是否继续？', '删除会话', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await assistantStore.removeSession(sessionId)
    ElMessage.success('会话已删除')
  } catch {
    // 用户取消删除
  }
}

async function submitMessage(message: string, clearDraft = false) {
  const content = message.trim()
  if (!content) {
    ElMessage.warning('请输入问题后再发送')
    return
  }
  if (sending.value) {
    return
  }
  if (selectedKbScope.value != null && selectedAssistantSearchScopes.value.length === 0) {
    ElMessage.warning('请至少选择一个本地检索范围')
    return
  }

  if (clearDraft) {
    draft.value = ''
  }

  // 发送前捕获文件列表，发送后清空
  const files = attachedFiles.length ? buildAttachmentsForRequest() : undefined

  await assistantStore.sendMessage({
    message: content,
    pageContext: currentPageContext.value,
    knowledgeBaseId: selectedKbScope.value,
    llmModelId: selectedLlmModelId.value,
    mode: selectedKbScope.value != null ? searchMode.value : undefined,
    topK: selectedKbScope.value != null ? topK.value : undefined,
    webSearch: selectedKbScope.value == null ? webSearchEnabled.value : undefined,
    searchScopes: buildAssistantSearchScopes(),
    files,
  })

  // 发送后清除文件
  attachedFiles.splice(0, attachedFiles.length)
}

async function handleSend() {
  await submitMessage(draft.value, true)
}

async function handleQuickAsk(question: string) {
  // 记录点击到后端（用于统计热度和提炼）
  const matched = quickQuestions.questions.value.find(q => q.questionText === question)
  if (matched) {
    quickQuestions.handleQuickClick(matched).catch(() => {})
  }
  draft.value = question
  await submitMessage(question, true)
}

// ===== 思维链折叠 =====
const thinkingFoldState = reactive<Record<string, boolean>>({})

function toggleThinkingFold(message: AssistantMessage) {
  const key = String(message.id)
  thinkingFoldState[key] = !thinkingFoldState[key]
}

// ===== 深度思考折叠 =====
const reasoningFoldState = reactive<Record<string, boolean>>({})

function toggleReasoningFold(message: AssistantMessage) {
  const key = String(message.id)
  reasoningFoldState[key] = !reasoningFoldState[key]
}

function isReasoningOpen(message: AssistantMessage) {
  return !!reasoningFoldState[String(message.id)]
}

/** 折叠态预览：取推理内容的前 60 字，去掉多余空白 */
function reasoningPreview(content?: string | null) {
  if (!content) return ''
  const compact = String(content).replace(/\s+/g, ' ').trim()
  if (compact.length <= 60) return compact
  return `${compact.slice(0, 60)}…`
}

// ===== 任务日志折叠已迁移到 AssistantTaskPanel 子组件 =====

// ===== 复制回答 =====
async function handleCopyMessage(message: AssistantMessage) {
  const text = message.content || ''
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('回答已复制到剪贴板')
  } catch {
    // 剪贴板不可用时降级为 textarea 复制
    try {
      const textarea = document.createElement('textarea')
      textarea.value = text
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
      ElMessage.success('回答已复制到剪贴板')
    } catch {
      ElMessage.error('复制失败，请手动选择文本复制')
    }
  }
}

// ===== 重新生成 =====
function findUserQuestionFor(assistantMessage: AssistantMessage): string {
  const index = messages.value.findIndex(item => String(item.id) === String(assistantMessage.id))
  for (let i = index - 1; i >= 0; i--) {
    if (messages.value[i].role === 'user') {
      return messages.value[i].content
    }
  }
  return ''
}

async function handleRegenerate(message: AssistantMessage) {
  if (sending.value || message.status === 'streaming') return
  if (selectedKbScope.value != null && selectedAssistantSearchScopes.value.length === 0) {
    ElMessage.warning('请至少选择一个本地检索范围')
    return
  }
  const question = findUserQuestionFor(message)
  if (!question) {
    ElMessage.warning('未找到对应的用户问题，无法重新生成')
    return
  }
  await assistantStore.regenerateMessage(message.id, {
    message: question,
    pageContext: currentPageContext.value,
    knowledgeBaseId: selectedKbScope.value,
    llmModelId: selectedLlmModelId.value,
    mode: selectedKbScope.value != null ? searchMode.value : undefined,
    topK: selectedKbScope.value != null ? topK.value : undefined,
    webSearch: selectedKbScope.value == null ? webSearchEnabled.value : undefined,
    searchScopes: buildAssistantSearchScopes(),
    files: attachedFiles.length ? buildAttachmentsForRequest() : undefined,
  })
}

function handleStop() {
  assistantStore.stopGenerating()
}

async function handleNavigate(targetPath?: string) {
  if (!targetPath) return
  const match = resolvePathMatch(targetPath)
  if (match === 'current-page' || match === 'current-menu') {
    ElMessage.info('你已经位于这个页面或菜单入口')
    return
  }
  await router.push(targetPath)
  assistantStore.close()
}

async function handleSourceNavigate(source: AssistantSource) {
  if (isRequirementSource(source) && source.requirementId) {
    await router.push({
      name: 'RequirementDetail',
      params: { id: source.requirementId },
      query: source.imageFileId
        ? {
            focus: source.focus || 'image',
            fileId: String(source.imageFileId),
            ...(source.imagePosition ? { position: String(source.imagePosition) } : {})
          }
        : undefined,
    })
    assistantStore.close()
    return
  }
  // 知识库附件来源：拉起文档预览
  if (source.code === 'knowledge_document' && source.documentId && source.knowledgeBaseId) {
    openSourcePreview(source)
    return
  }
  if (!source.path) return
  await handleNavigate(source.path)
}

function handleGlobalKeydown(event: KeyboardEvent) {
  const isOpenShortcut = (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k'
  if (isOpenShortcut) {
    event.preventDefault()
    void handleOpen()
    return
  }

  if (event.key === 'Escape' && visible.value) {
    event.preventDefault()
    assistantStore.close()
  }
}

function formatTime(value?: string | null) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${month}-${day} ${hour}:${minute}`
}

const messageLength = computed(() => messages.value.length)
const lastMessageSignature = computed(() => {
  const last = messages.value[messages.value.length - 1]
  return last ? `${last.id}-${last.content?.length || 0}-${last.status || ''}` : 'empty'
})

watch([visible, messageLength, lastMessageSignature], async ([opened]) => {
  if (!opened) return
  await nextTick()
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  }
})

watch(visible, async (opened) => {
  if (opened) {
    await ensureReady()
  }
})

// ===== 动态思考提示（对标 WorkBuddy 等待模型响应）=====
const thinkingHintTicker = ref(0)
let thinkingHintTimer: ReturnType<typeof setInterval> | null = null

/** 通用助手场景下的阶段轮播（无 task 数据时使用） */
const GENERAL_HINTS = [
  '正在理解问题…',
  '正在分析上下文…',
  '正在组织回答思路…',
  '正在精心整理回复…',
  '内容较多，正在优化格式…',
]
/** 联网搜索场景下的阶段轮播 */
const WEBSEARCH_HINTS = [
  '正在检索本地知识库…',
  '正在发起联网搜索…',
  '正在综合整理回复…',
]
/** 知识库 RAG 场景下的阶段轮播 */
const KNOWLEDGE_HINTS = [
  '正在解析问题…',
  '正在向量化查询…',
  '正在检索相关片段…',
  '正在重排序…',
  '正在生成回答…',
]

/** 获取当前最后一条 assistant 消息中正在运行的任务 */
function getRunningTask() {
  const lastMsg = messages.value[messages.value.length - 1]
  if (lastMsg?.role !== 'assistant' || !lastMsg.tasks?.length) return null
  return lastMsg.tasks.find(t => t.status === 'running') || null
}

/** 当前流式场景：根据助手模式/最后一条消息状态推断 */
function getCurrentScene(): 'general' | 'webSearch' | 'knowledge' {
  if (selectedKbScope.value != null) return 'knowledge'
  if (webSearchEnabled.value) return 'webSearch'
  return 'general'
}

/** 根据场景获取对应的阶段文案数组 */
function getHintsForScene(scene: 'general' | 'webSearch' | 'knowledge') {
  if (scene === 'webSearch') return WEBSEARCH_HINTS
  if (scene === 'knowledge') return KNOWLEDGE_HINTS
  return GENERAL_HINTS
}

/** 把运行中任务标题里的「中…」后缀去掉，避免 HUD 出现"…中…中…"重复 */
function stripRunningSuffix(title: string) {
  return String(title || '').replace(/中…?$/, '').trim()
}

const streamingHintText = computed(() => {
  if (!sending.value) return ''
  const runningTask = getRunningTask()
  if (runningTask) return `${runningTask.title}中…`
  const hints = getHintsForScene(getCurrentScene())
  return hints[thinkingHintTicker.value % hints.length]
})

/** 流式状态标签：消息气泡旁的 token 位置 */
const streamingStatusLabel = computed(() => {
  if (!sending.value) return '生成中…'
  const runningTask = getRunningTask()
  if (runningTask) return `${runningTask.title}中…`
  const hints = getHintsForScene(getCurrentScene())
  return hints[thinkingHintTicker.value % hints.length]
})

/** 流式状态条 HUD 主标签（顶部状态条显示） */
const streamHudPhaseLabel = computed(() => {
  if (!sending.value) return ''
  const runningTask = getRunningTask()
  if (runningTask) {
    const cleanTitle = stripRunningSuffix(runningTask.title)
    return `${cleanTitle}中…`
  }
  const hints = getHintsForScene(getCurrentScene())
  return hints[thinkingHintTicker.value % hints.length]
})

/** 流式状态条 HUD 模型名 */
const streamHudModelLabel = computed(() => {
  if (!sending.value) return ''
  if (selectedChatModel.value) {
    return compactModelName(selectedChatModel.value)
  }
  return ''
})

/** HUD 视觉变体（按场景区分） */
const streamHudVariantClass = computed(() => `is-${getCurrentScene()}`)

// 轮播提示定时器
watch(sending, (val) => {
  if (val) {
    thinkingHintTicker.value = 0
    // 每 2.5 秒切换一个提示文案
    thinkingHintTimer = setInterval(() => {
      thinkingHintTicker.value++
    }, 2500)
  } else {
    if (thinkingHintTimer) {
      clearInterval(thinkingHintTimer)
      thinkingHintTimer = null
    }
  }
})

onBeforeUnmount(() => {
  if (thinkingHintTimer) {
    clearInterval(thinkingHintTimer)
    thinkingHintTimer = null
  }
  window.removeEventListener('keydown', handleGlobalKeydown)
  window.removeEventListener('resize', handleFabResize)
  document.removeEventListener('mousemove', onFabDragMove)
  document.removeEventListener('mouseup', onFabDragEnd)
  document.removeEventListener('touchmove', onFabDragMove)
  document.removeEventListener('touchend', onFabDragEnd)
})
</script>

<style scoped lang="scss">
:deep(.assistant-dialog) {
  max-width: calc(100vw - 32px);
  border-radius: 16px;
  overflow: hidden;
}

:deep(.assistant-dialog .el-dialog__header) {
  margin: 0;
  padding: 0;
}

:deep(.assistant-dialog .el-dialog__body) {
  padding: 0;
  overflow: hidden;
}

:deep(.assistant-dialog.is-fullscreen) {
  max-width: 100vw;
  border-radius: 0;
}

.assistant-fab {
  position: fixed;
  z-index: 1200;
  border: none;
  border-radius: 50%;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: linear-gradient(135deg, #409eff, #7c4dff);
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.3);
  cursor: grab;
  user-select: none;
  -webkit-user-select: none;
  touch-action: none;

  &:active {
    cursor: grabbing;
    box-shadow: 0 8px 24px rgba(64, 158, 255, 0.4);
  }

  &:hover {
    box-shadow: 0 8px 24px rgba(64, 158, 255, 0.4);
    transform: scale(1.08);
  }
}

.assistant-fab__icon {
  width: 40px;
  height: 40px;
  color: #fff;
  overflow: visible;
}

.assistant-avatar-tag {
  cursor: pointer;
  user-select: none;
}

.assistant-panel {
  height: min(76vh, 720px);
  display: flex;
  flex-direction: column;
  background: #f7f8fa;
}

.assistant-panel--fullscreen {
  height: calc(100vh - 74px);
}

.assistant-panel__header {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 18px;
  border-bottom: 1px solid #e8ebf0;
  background: #fff;
  cursor: move;
  min-height: 56px;
}

.assistant-panel__header-right {
  flex: 0 1 auto;
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

/* 信息标签组：纯展示，灰色基调 */
.assistant-panel__info-group {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  overflow: hidden;
}

/* 操作按钮组：统一规格，关闭按钮在组的最右端 */
.assistant-panel__action-group {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 4px;
  background: #f5f7fa;
  border-radius: 10px;
  border: 1px solid #e8ebf0;
  flex-shrink: 0;
}

/* 操作组内的分割线：在普通操作按钮和关闭按钮之间建立视觉分组 */
.assistant-panel__action-divider {
  width: 1px;
  height: 16px;
  margin: 0 4px;
  background: #dcdfe6;
  flex-shrink: 0;
}

.assistant-panel__title-block {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.assistant-panel__tools {
  cursor: default;
  white-space: nowrap;
}

.assistant-meta-tag {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
  padding: 0 8px;
  height: 22px;
  line-height: 20px;
}

.assistant-avatar-tag {
  cursor: pointer;
  user-select: none;
  font-size: 11px;
  padding: 0 8px;
  height: 22px;
  line-height: 20px;
}

/* 操作按钮统一规格：32x32 方形圆角 */
.assistant-tool-button {
  width: 32px;
  height: 32px;
  padding: 0;
  font-size: 15px;
  color: #4e5969;
  border-radius: 8px;
  transition: all 0.2s ease;
  background: transparent;

  &:hover {
    color: #2563eb;
    background: #ffffff;
    box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
  }

  &:active {
    transform: scale(0.94);
  }
}

/* 关闭按钮作为操作组最后一位：稍重的默认态 + hover 强调红色 */
.assistant-tool-button--close {
  color: #64748b;

  &:hover {
    color: #f56c6c;
    background: #fef0f0;
    box-shadow: 0 1px 3px rgba(245, 108, 108, 0.18);
  }

  &:active {
    transform: scale(0.92);
  }
}

.assistant-panel__title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2329;
  letter-spacing: 0.2px;
}

.assistant-panel__subtitle {
  color: #86909c;
  font-size: 12px;
  line-height: 1.5;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}

.assistant-panel__body {
  flex: 1;
  min-height: 0;
  display: flex;
}

.assistant-session-list {
  width: 200px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #e8ebf0;
  background: #fafbfc;
  overflow: hidden;
}

.assistant-session-list__header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  border-bottom: 1px solid #e8ebf0;
  background: #fff;
  font-size: 12px;
  font-weight: 600;
  color: #1e293b;
}

.assistant-session-list__header-icon {
  font-size: 14px;
  color: #64748b;
}

.assistant-session-list__new-btn {
  margin-left: auto;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 6px;
  background: #f1f5f9;
  color: #64748b;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.15s;
}

.assistant-session-list__new-btn:hover {
  background: #e2e8f0;
  color: #1e293b;
}

.assistant-session-list__search {
  padding: 8px 10px;
  border-bottom: 1px solid #e8ebf0;
  background: #fff;
}

:deep(.session-search-input .el-input__wrapper) {
  border-radius: 6px;
  background: #f1f5f9;
  box-shadow: none !important;
}

:deep(.session-search-input .el-input__wrapper:hover),
:deep(.session-search-input .el-input__wrapper.is-focus) {
  background: #e8ecf2;
}

:deep(.session-search-input .el-input__inner) {
  font-size: 12px;
}

.assistant-session-list__empty {
  padding: 16px 12px;
  font-size: 12px;
  color: #94a3b8;
  text-align: center;
}

.assistant-session-group {
  padding: 4px 0;
}

.assistant-session-group__label {
  padding: 6px 12px 2px;
  font-size: 10px;
  font-weight: 600;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.assistant-session-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  margin: 0 6px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.12s ease;
}

.assistant-session-item:hover {
  background: #e8ecf2;
}

.assistant-session-item.is-active {
  background: #eff6ff;
}

.assistant-session-item.is-active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 6px;
  bottom: 6px;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: #2563eb;
}

.assistant-session-item__title {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  color: #334155;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.4;
}

.assistant-session-item.is-active .assistant-session-item__title {
  color: #1d4ed8;
  font-weight: 500;
}

.assistant-session-item__time {
  flex-shrink: 0;
  font-size: 10px;
  color: #94a3b8;
  white-space: nowrap;
}

.assistant-session-item__delete {
  flex-shrink: 0;
  font-size: 12px;
  color: #cbd5e1;
  opacity: 0;
  transition: all 0.12s;
  border-radius: 4px;
  padding: 2px;
}

.assistant-session-item:hover .assistant-session-item__delete {
  opacity: 1;
}

.assistant-session-item__delete:hover {
  color: #ef4444;
  background: #fee2e2;
}

.assistant-session-list__footer {
  margin-top: auto;
  padding: 8px 12px;
  border-top: 1px solid #e8ecf2;
  font-size: 10px;
  color: #94a3b8;
}

/* 滚动容器 */
.assistant-session-list {
  overflow-y: auto;
  overflow-x: hidden;
}

.assistant-session-list__scrollable {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.assistant-chat {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.assistant-message-list {
  flex: 1;
  overflow-y: auto;
  padding: 18px;
}

.assistant-empty {
  height: 100%;
  min-height: 320px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #86909c;
  text-align: center;
}

.assistant-empty__icon {
  font-size: 28px;
  margin-bottom: 12px;
  color: #409eff;
}

.assistant-empty__title {
  font-size: 16px;
  color: #1f2329;
  font-weight: 600;
}

.assistant-empty__desc {
  margin-top: 8px;
  font-size: 13px;
  max-width: 320px;
}

.assistant-empty__quick-asks {
  margin-top: 20px;
  width: 100%;
  max-width: 360px;
}

.assistant-section-title {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #86909c;
}

.assistant-chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
}

.assistant-chip-list--compact {
  justify-content: flex-start;
}

.assistant-chip {
  border: 1px solid #d9ecff;
  border-radius: 999px;
  padding: 8px 12px;
  background: #f5f9ff;
  color: #409eff;
  cursor: pointer;
  transition: all 0.2s ease;
}

.assistant-chip:hover:not(:disabled) {
  border-color: #409eff;
  background: #ecf5ff;
}

.assistant-chip:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.assistant-chip--compact {
  padding: 6px 10px;
  font-size: 12px;
}

.assistant-message {
  display: flex;
  gap: 10px;
  margin-bottom: 18px;
}

.assistant-message--user {
  justify-content: flex-end;
}

.assistant-message--assistant {
  justify-content: flex-start;
}

.assistant-message__avatar {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  margin-top: 2px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409eff, #7c4dff);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 6px rgba(64, 158, 255, 0.25);
  overflow: hidden;
}

.assistant-message__avatar :deep(.assistant-fab__icon) {
  width: 26px;
  height: 26px;
}

.assistant-message__main {
  max-width: calc(100% - 44px);
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.assistant-message--user .assistant-message__main {
  align-items: flex-end;
}

.assistant-message__bubble {
  padding: 10px 14px;
  border-radius: 14px;
  word-break: break-word;
}

.assistant-message__bubble--user {
  max-width: min(100%, 560px);
  background: #2563eb;
  color: #fff;
  border-bottom-right-radius: 4px;
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.22);
}

.assistant-message__bubble--assistant {
  max-width: 100%;
  background: #fff;
  border: 1px solid #e8ebf0;
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 4px rgba(31, 35, 41, 0.04);
}

.assistant-message__content {
  word-break: break-word;
  line-height: 1.7;
  font-size: 14px;
}

.assistant-message__content--user {
  white-space: pre-wrap;
}

.assistant-message__content--assistant {
  margin-top: 0;
  color: #1f2329;
}

/* ===== 动态思考提示 ===== */
.assistant-streaming-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}

.assistant-streaming-hint__text {
  font-size: 14px;
  color: #64748b;
  font-style: italic;
}

.assistant-streaming-hint__dots {
  display: flex;
  gap: 3px;
  align-items: center;
}

.assistant-streaming-hint__dots .dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #94a3b8;
  animation: streaming-dot-bounce 1.2s ease-in-out infinite;
}

.assistant-streaming-hint__dots .dot:nth-child(2) {
  animation-delay: 0.2s;
}

.assistant-streaming-hint__dots .dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes streaming-dot-bounce {
  0%, 60%, 100% {
    transform: translateY(0);
    opacity: 0.35;
  }
  30% {
    transform: translateY(-4px);
    opacity: 1;
  }
}

/* 闪烁光标 */
.assistant-streaming-cursor {
  display: inline;
  font-weight: 100;
  color: #2563eb;
  animation: streaming-cursor-blink 0.8s steps(1) infinite;
  font-size: 14px;
  vertical-align: baseline;
}

@keyframes streaming-cursor-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* 消息操作栏：复制 / 重新生成 / token / 时间 */
.assistant-message__toolbar {
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 4px;
  min-height: 24px;
}

.assistant-message__tool-btn {
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #a9b1bc;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.18s ease;
}

.assistant-message__tool-btn:hover:not(:disabled) {
  color: #2563eb;
  background: #eef4ff;
}

.assistant-message__tool-btn:disabled {
  cursor: not-allowed;
  opacity: 0.4;
}

.assistant-message__tokens {
  margin-left: 6px;
  font-size: 11px;
  color: #a9b1bc;
  white-space: nowrap;
}

.assistant-message__tokens.is-streaming {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #2563eb;
  font-weight: 500;
}

.assistant-message__tokens-pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #2563eb;
  animation: streaming-pulse-glow 1.4s ease-in-out infinite;
}

@keyframes streaming-pulse-glow {
  0%, 100% {
    transform: scale(0.8);
    opacity: 0.5;
  }
  50% {
    transform: scale(1.2);
    opacity: 1;
  }
}

/* ===== 流式状态条 HUD（对标 WorkBuddy 等待模型响应）===== */
.assistant-stream-hud {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 18px 8px;
  padding: 8px 12px;
  border: 1px solid #d1d5db;
  border-radius: 10px;
  background: linear-gradient(180deg, #f9fafb 0%, #f3f4f6 100%);
  font-size: 12.5px;
  color: #475569;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);

  &.is-webSearch {
    border-color: rgba(14, 165, 233, 0.35);
    background: linear-gradient(180deg, #f0f9ff 0%, #e0f2fe 100%);
    color: #075985;
  }

  &.is-knowledge {
    border-color: rgba(99, 102, 241, 0.35);
    background: linear-gradient(180deg, #eef2ff 0%, #e0e7ff 100%);
    color: #3730a3;
  }
}

.assistant-stream-hud__pulse {
  position: relative;
  width: 14px;
  height: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.assistant-stream-hud__pulse-dot {
  position: absolute;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #10b981;
  z-index: 1;
}

.assistant-stream-hud__pulse-ring {
  position: absolute;
  width: 14px;
  height: 14px;
  border: 1.5px solid rgba(16, 185, 129, 0.5);
  border-radius: 50%;
  animation: hud-pulse-ring 1.4s ease-out infinite;
}

.is-webSearch .assistant-stream-hud__pulse-dot {
  background: #0ea5e9;
}
.is-webSearch .assistant-stream-hud__pulse-ring {
  border-color: rgba(14, 165, 233, 0.5);
}

.is-knowledge .assistant-stream-hud__pulse-dot {
  background: #6366f1;
}
.is-knowledge .assistant-stream-hud__pulse-ring {
  border-color: rgba(99, 102, 241, 0.5);
}

@keyframes hud-pulse-ring {
  0% {
    transform: scale(0.6);
    opacity: 1;
  }
  80%, 100% {
    transform: scale(1.8);
    opacity: 0;
  }
}

.assistant-stream-hud__label {
  flex: 1;
  min-width: 0;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.assistant-stream-hud__model {
  flex-shrink: 0;
  color: #64748b;
  font-size: 11.5px;
  padding: 1px 8px;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 999px;
  font-weight: 500;
  white-space: nowrap;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.is-webSearch .assistant-stream-hud__model {
  color: #075985;
  background: rgba(255, 255, 255, 0.7);
}

.is-knowledge .assistant-stream-hud__model {
  color: #3730a3;
  background: rgba(255, 255, 255, 0.7);
}

.assistant-stream-hud__stop {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
  color: #475569;
  font-size: 11.5px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
  flex-shrink: 0;

  &:hover {
    border-color: #f43f5e;
    background: #fff1f2;
    color: #be123c;
  }
}

/* HUD 出现/消失过渡 */
.stream-hud-enter-active,
.stream-hud-leave-active {
  transition: opacity 0.18s ease, transform 0.2s ease;
}

.stream-hud-enter-from,
.stream-hud-leave-to {
  opacity: 0;
  transform: translateY(4px);
}

/* 消息列表底部内边距，给 HUD 留出呼吸空间 */
.assistant-message-list {
  padding-bottom: 8px;
}

.assistant-message__time {
  margin-left: auto;
  font-size: 11px;
  color: #c0c4cc;
  white-space: nowrap;
}

/* 深度思考折叠面板（WorkBuddy 同款） */
.assistant-reasoning {
  margin-top: 10px;
  width: 100%;
  border: 1px solid #e3e8ef;
  border-radius: 10px;
  background: linear-gradient(180deg, #f7f9fd, #f3f6fc);
  overflow: hidden;
}

.assistant-reasoning__header {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: background 0.18s ease;
}

.assistant-reasoning__header:hover {
  background: rgba(37, 99, 235, 0.06);
}

.assistant-reasoning__icon {
  flex-shrink: 0;
  font-size: 14px;
  color: #2563eb;
}

.assistant-reasoning__label {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  color: #2563eb;
}

.assistant-reasoning__preview {
  flex: 1;
  min-width: 0;
  margin-left: 10px;
  font-size: 11.5px;
  line-height: 1.5;
  color: #86909c;
  font-weight: 400;
  font-style: italic;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  /* 渐变淡出，提示"还有更多" */
  mask-image: linear-gradient(90deg, #000 0%, #000 70%, transparent 100%);
  -webkit-mask-image: linear-gradient(90deg, #000 0%, #000 70%, transparent 100%);
}

.assistant-reasoning__arrow {
  font-size: 12px;
  color: #86909c;
  transition: transform 0.2s ease;
}

.assistant-reasoning__arrow.is-open {
  transform: rotate(180deg);
}

.assistant-reasoning__body {
  padding: 0 12px 12px;
}

.assistant-reasoning__content {
  max-height: 260px;
  overflow-y: auto;
  padding: 10px 12px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #e8ebf0;
  font-size: 12px;
  line-height: 1.7;
  color: #4e5969;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 思维链折叠面板 */
.assistant-thinking-fold {
  margin-top: 10px;
  width: 100%;
  border: 1px solid #e8ebf0;
  border-radius: 10px;
  background: #fafbfc;
  overflow: hidden;
}

.assistant-thinking-fold__header {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: background 0.18s ease;
}

.assistant-thinking-fold__header:hover {
  background: #f2f4f7;
}

.assistant-thinking-fold__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #2563eb;
  flex-shrink: 0;
}

.assistant-thinking-fold__label {
  flex: 1;
  font-size: 12px;
  font-weight: 600;
  color: #4e5969;
}

.assistant-thinking-fold__arrow {
  font-size: 12px;
  color: #86909c;
  transition: transform 0.2s ease;
}

.assistant-thinking-fold__arrow.is-open {
  transform: rotate(180deg);
}

.assistant-thinking-fold__body {
  padding: 4px 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.assistant-message__actions,
.assistant-message__sources {
  margin-top: 14px;
}

.assistant-action-list,
.assistant-source-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* 任务面板样式已抽离到 AssistantTaskPanel.vue 子组件 */

.assistant-action-card {
  width: 100%;
  border: 1px solid #dfe5ef;
  border-radius: 12px;
  padding: 10px 12px;
  background: #f8fbff;
  text-align: left;
  cursor: pointer;
  transition: all 0.2s ease;
}

.assistant-action-card:hover:not(:disabled) {
  border-color: #409eff;
  transform: translateY(-1px);
}

.assistant-action-card.is-current-page,
.assistant-action-card.is-current-menu {
  border-color: #67c23a;
  background: #f0f9eb;
}

.assistant-action-card.is-disabled {
  cursor: not-allowed;
  opacity: 0.75;
}

.assistant-action-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.assistant-action-card__label {
  font-size: 14px;
  font-weight: 600;
  color: #1f2329;
}

.assistant-action-card__badge {
  flex-shrink: 0;
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 11px;
  line-height: 18px;
  color: #409eff;
  background: #ecf5ff;
}

.assistant-action-card.is-current-page .assistant-action-card__badge,
.assistant-action-card.is-current-menu .assistant-action-card__badge {
  color: #67c23a;
  background: #e8f5e9;
}

.assistant-action-card__desc {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: #5f6b7a;
}

.assistant-action-card__path {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
  word-break: break-all;
}

.assistant-message__warnings {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 10px;
}

.assistant-message__sources--compact {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.assistant-sources-label {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  color: #86909c;
}

.assistant-source-list--compact {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: 6px;
}

.assistant-source-chip {
  max-width: 100%;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid #e7ebf3;
  border-radius: 999px;
  padding: 4px 8px;
  background: #fafbfc;
  color: #5f6b7a;
  font-size: 12px;
  line-height: 18px;
  transition: all 0.2s ease;
}

.assistant-source-chip.is-clickable {
  cursor: pointer;
}

.assistant-source-chip.is-clickable:hover,
.assistant-source-chip.is-clickable:focus-visible {
  border-color: #409eff;
  background: #f8fbff;
  color: #409eff;
  outline: none;
}

/* 知识库文档来源 → 超链接样式 */
.assistant-source-chip.is-previewable {
  cursor: pointer;
  color: #2563eb;
  border-color: #bfdbfe;
  background: #eff6ff;
}

.assistant-source-chip.is-previewable:hover,
.assistant-source-chip.is-previewable:focus-visible {
  border-color: #2563eb;
  background: #dbeafe;
  text-decoration: underline;
  outline: none;
}

.assistant-source-chip.is-previewable .assistant-source-chip__title {
  text-decoration: underline;
  text-decoration-color: transparent;
  transition: text-decoration-color 0.2s ease;
}

.assistant-source-chip.is-previewable:hover .assistant-source-chip__title,
.assistant-source-chip.is-previewable:focus-visible .assistant-source-chip__title {
  text-decoration-color: #2563eb;
}

.assistant-source-chip.is-current-page,
.assistant-source-chip.is-current-menu {
  border-color: #b3e19d;
  background: #f0f9eb;
  color: #67c23a;
}

.assistant-source-chip__title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.assistant-source-chip__badge {
  flex-shrink: 0;
  color: #909399;
}

.assistant-composer {
  padding: 16px 18px 18px;
  border-top: 1px solid #e8ebf0;
  background: #fff;
}

.assistant-composer__quick-asks {
  margin-bottom: 12px;
}

/* 教育性提示（对标 WorkBuddy「能力不是开越多越好…」） */
.assistant-composer__hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin: 8px 0 0;
  padding: 4px 10px;
  background: #f1f5f9;
  border-radius: 6px;
  font-size: 11px;
  line-height: 1.6;
  color: #64748b;
  max-width: 100%;
  animation: hint-fade-in 0.25s ease-out;
}

.assistant-composer__hint-icon {
  flex-shrink: 0;
  font-size: 12px;
  color: #94a3b8;
}

@keyframes hint-fade-in {
  from {
    opacity: 0;
    transform: translateY(2px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.assistant-composer__footer {
  margin-top: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.assistant-composer__scope {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex: 1;
}

.assistant-composer__scope-select {
  width: 172px;
  flex-shrink: 0;
}

.assistant-composer__scope-icon {
  color: #409eff;
}

.assistant-search-scope-popover {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.assistant-search-scope-popover strong {
  color: var(--el-text-color-primary);
  font-size: 13px;
}

.assistant-search-scope-popover :deep(.el-checkbox-group) {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.assistant-search-scope-popover :deep(.el-checkbox) {
  margin-right: 0;
}

.assistant-search-scope-popover span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.assistant-composer__websearch {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 8px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #fff;
  flex-shrink: 0;
  transition: border-color 0.2s, background 0.2s;
}

.assistant-composer__websearch:hover {
  border-color: #409eff;
}

.assistant-composer__websearch-icon {
  font-size: 14px;
  color: #86909c;
}

:deep(.assistant-composer__websearch .el-switch) {
  --el-switch-on-color: #409eff;
}

.assistant-composer__model-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 10px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #fff;
  color: #1f2329;
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s, background 0.2s;
  flex-shrink: 0;
}

.assistant-composer__model-btn:hover:not(:disabled) {
  border-color: #409eff;
  color: #409eff;
}

.assistant-composer__model-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.assistant-composer__model-arrow {
  font-size: 10px;
  color: #86909c;
}

/* 模型选择两级菜单 */
.assistant-model-menu {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.assistant-model-menu__title {
  font-size: 12px;
  font-weight: 600;
  color: #86909c;
}

.assistant-model-menu__panels {
  display: grid;
  grid-template-columns: 150px 1fr;
  gap: 12px;
  max-height: 340px;
}

.assistant-model-menu__providers,
.assistant-model-menu__models {
  display: flex;
  flex-direction: column;
  gap: 6px;
  overflow-y: auto;
}

.assistant-model-menu__label {
  font-size: 11px;
  font-weight: 600;
  color: #86909c;
  padding: 0 2px;
  margin-bottom: 2px;
}

.assistant-model-menu__provider,
.assistant-model-menu__model {
  width: 100%;
  border: 1px solid transparent;
  border-radius: 8px;
  padding: 8px 10px;
  background: #f7f8fa;
  color: #1f2329;
  cursor: pointer;
  text-align: left;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  transition: background 0.18s, border-color 0.18s;
}

.assistant-model-menu__provider:hover,
.assistant-model-menu__model:hover {
  background: #ecf5ff;
}

.assistant-model-menu__provider.is-active {
  background: #ecf5ff;
  border-color: #409eff;
}

.assistant-model-menu__model.is-active {
  background: #ecf5ff;
  border-color: #409eff;
}

.assistant-model-menu__provider-name {
  font-size: 12px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.assistant-model-menu__provider-count {
  font-size: 11px;
  color: #86909c;
  flex-shrink: 0;
}

.assistant-model-menu__model-main {
  min-width: 0;
}

.assistant-model-menu__model-name {
  font-size: 12px;
  font-weight: 600;
}

.assistant-model-menu__model-id {
  margin-top: 2px;
  font-size: 11px;
  color: #86909c;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.assistant-model-menu__check {
  flex-shrink: 0;
  color: #409eff;
  font-weight: 700;
}

.assistant-model-menu__models {
  border-left: 1px solid #e8ebf0;
  padding-left: 12px;
}

.assistant-composer__tip {
  font-size: 12px;
  color: #86909c;
}

/* 文件上传：卡片 + 上传按钮 */
.assistant-composer__files {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}

.assistant-file-card {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #fafbfc;
  font-size: 12px;
  max-width: 320px;
  transition: border-color 0.2s, opacity 0.2s;
}

.assistant-file-card.is-uploading {
  opacity: 0.6;
  border-style: dashed;
}

.assistant-file-card__icon {
  flex-shrink: 0;
  font-size: 14px;
}

.assistant-file-card__name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #1f2329;
  font-weight: 500;
}

.assistant-file-card__size {
  flex-shrink: 0;
  color: #a9b1bc;
}

.assistant-file-card__remove {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 50%;
  background: #e8ebf0;
  color: #86909c;
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  transition: background 0.18s, color 0.18s;
}

.assistant-file-card__remove:hover {
  background: #f56c6c;
  color: #fff;
}

.assistant-composer__file-btn {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #fff;
  color: #86909c;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.18s ease;
  flex-shrink: 0;
}

.assistant-composer__file-btn:hover:not(:disabled) {
  border-color: #2563eb;
  color: #2563eb;
  background: #eef4ff;
}

.assistant-composer__file-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

@media (max-width: 768px) {
  :deep(.assistant-dialog) {
    width: calc(100vw - 16px) !important;
    max-width: calc(100vw - 16px);
  }

  .assistant-panel {
    height: 82vh;
  }

  .assistant-panel--fullscreen {
    height: calc(100vh - 92px);
  }

  .assistant-panel__header {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
    padding: 12px 44px 12px 16px;
  }

  .assistant-panel__tools {
    flex-wrap: wrap;
    row-gap: 6px;
  }

  .assistant-panel__body {
    flex-direction: column;
  }

  .assistant-session-list {
    width: auto;
    max-height: 40px;
    flex-direction: row;
    align-items: center;
    border-right: none;
    border-bottom: 1px solid #e8ebf0;
  }

  .assistant-message__main {
    max-width: calc(100% - 36px);
  }

  .assistant-message__bubble--user {
    max-width: 100%;
  }
}

.assistant-thinking-step {
  padding: 8px 12px;
  background: #fff;
  border-radius: 8px;
  border-left: 3px solid #409eff;
  box-shadow: 0 1px 3px rgba(31, 35, 41, 0.04);
}

.assistant-thinking-step--query_parse {
  border-left-color: #67c23a;
}

.assistant-thinking-step--retrieve {
  border-left-color: #409eff;
}

.assistant-thinking-step--rerank {
  border-left-color: #e6a23c;
}

.assistant-thinking-step--synthesize {
  border-left-color: #f56c6c;
}

.assistant-thinking-step__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  font-size: 13px;
  color: #1d2129;
  margin-bottom: 4px;
}

.assistant-thinking-step__title {
  flex: 1;
}

.assistant-thinking-step__score {
  font-size: 12px;
  color: #86909c;
  font-weight: 500;
}

.assistant-thinking-step__detail {
  font-size: 12px;
  color: #4e5969;
  line-height: 1.6;
}
</style>

