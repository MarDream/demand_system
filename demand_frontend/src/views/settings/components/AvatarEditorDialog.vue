<template>
  <el-dialog
    :model-value="visible"
    title="设置头像"
    width="520px"
    :close-on-click-modal="false"
    @close="handleClose"
    @open="handleOpen"
  >
    <!-- ============ Tab：预设 / 上传 ============ -->
    <el-tabs v-model="activeTab" stretch>
      <!-- 预设头像 -->
      <el-tab-pane label="预设头像" name="preset">
        <div class="avatar-preset-groups">
          <button
            v-for="g in presetGroups"
            :key="g.key"
            type="button"
            class="avatar-preset-group-btn"
            :class="{ 'is-active': presetFilter === g.key }"
            @click="presetFilter = g.key"
          >{{ g.label }}</button>
        </div>
        <div class="avatar-preset-grid">
          <div
            v-for="p in filteredPresets"
            :key="p.id"
            class="avatar-preset-item"
            :class="{ 'is-active': selectedPreset === p.id }"
            @click="selectPreset(p.id)"
          >
            <!-- 图片素材（卡通人物） -->
            <span v-if="p.url" class="avatar-preset-img">
              <img :src="p.url" :alt="p.name" loading="lazy" />
            </span>
            <!-- 内联 SVG，避免外部图片依赖 -->
            <span v-else class="avatar-preset-svg" v-html="p.svg"></span>
          </div>
        </div>
      </el-tab-pane>

      <!-- 自定义上传 + 裁剪 -->
      <el-tab-pane label="上传头像" name="upload">
        <div v-if="!imageSrc" class="avatar-upload-empty" @click="triggerFileSelect">
          <el-icon :size="32"><Plus /></el-icon>
          <p>点击选择图片</p>
          <p class="avatar-upload-empty__hint">支持 JPG / PNG，不超过 5MB</p>
        </div>

        <div v-else class="avatar-crop">
          <div
            ref="cropStageRef"
            class="avatar-crop__stage"
            @pointerdown="onPointerDown"
            @pointermove="onPointerMove"
            @pointerup="onPointerUp"
            @pointercancel="onPointerUp"
            @wheel.prevent="onWheel"
          >
            <img
              ref="cropImgRef"
              :src="imageSrc"
              class="avatar-crop__img"
              :style="imgStyle"
              draggable="false"
              @load="onImgLoad"
            />
            <!-- 圆形视口遮罩 -->
            <div class="avatar-crop__mask"></div>
          </div>

          <div class="avatar-crop__toolbar">
            <el-slider v-model="zoomPercent" :min="20" :max="400" :step="1" style="flex: 1" />
            <el-button link type="primary" @click="triggerFileSelect">换一张</el-button>
          </div>
          <p class="avatar-crop__hint">拖动图片调整位置，滚轮或滑杆缩放</p>
        </div>

        <input
          ref="fileInputRef"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          style="display: none"
          @change="onFileChange"
        />
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!canConfirm" @click="handleConfirm">
        确定
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { uploadFile } from '@/api/modules/file'
import { buildPresetAvatarSvg, PRESET_AVATAR_DEFS, CARTOON_AVATAR_COUNT, cartoonAvatarUrl } from '@/utils/presetAvatars'
import { ANIME_AVATAR_DEFS, buildAnimeAvatarSvg } from '@/utils/animeAvatars'

const props = defineProps<{
  visible: boolean
  saving?: boolean
  /** 当前头像（preset:xxx 或 URL），用于初始选中态 */
  currentAvatar?: string | null
}>()

const emit = defineEmits<{
  close: []
  /** preset 模式：返回 preset:xxx；上传模式：先本地裁剪出 Blob 交给父级上传 */
  confirm: [payload: { kind: 'preset'; value: string } | { kind: 'crop'; blob: Blob }]
}>()

// ==================== 预设 ====================
/** 分组：卡通人物（图片素材）/ 动漫风女生/男生 + 经典扁平款 */
const presetGroups = [
  { key: 'cartoon', label: '卡通人物' },
  { key: 'anime-f', label: '插画 · 女生' },
  { key: 'anime-m', label: '插画 · 男生' },
  { key: 'classic', label: '经典' },
] as const
type PresetGroupKey = (typeof presetGroups)[number]['key']

const presetFilter = ref<PresetGroupKey>('cartoon')
interface PresetOption {
  id: string
  name: string
  /** 图片素材 URL（卡通人物） */
  url?: string
  /** 内联 SVG（插画/经典） */
  svg?: string
}
const filteredPresets = computed<PresetOption[]>(() => {
  if (presetFilter.value === 'cartoon') {
    return Array.from({ length: CARTOON_AVATAR_COUNT }, (_, i) => ({
      id: `cartoon-${i + 1}`,
      name: `卡通 ${i + 1}`,
      url: cartoonAvatarUrl(i + 1),
    }))
  }
  if (presetFilter.value === 'classic') {
    return PRESET_AVATAR_DEFS.map((def) => ({ id: def.id, name: def.name, svg: buildPresetAvatarSvg(def) }))
  }
  const gender = presetFilter.value === 'anime-f' ? 'female' : 'male'
  return ANIME_AVATAR_DEFS.filter((d) => d.gender === gender).map((def) => ({ id: def.id, name: def.name, svg: buildAnimeAvatarSvg(def) }))
})

const activeTab = ref<'preset' | 'upload'>('preset')
const selectedPreset = ref<string>('')
const saving = computed(() => props.saving ?? false)

// ==================== 上传 + 裁剪 ====================
const fileInputRef = ref<HTMLInputElement>()
const imageSrc = ref('')
const imgNatural = ref({ width: 0, height: 0 })

/** 视口尺寸（与 CSS 中 stage 尺寸一致，280px） */
const STAGE_SIZE = 280
/** 圆形视口直径 */
const VIEW_SIZE = 220

const offset = ref({ x: 0, y: 0 })
const zoom = ref(1)
const zoomPercent = computed({
  get: () => Math.round(zoom.value * 100),
  set: (v: number) => {
    setZoom(v / 100)
  },
})

const dragState = ref<{ startX: number; startY: number; baseX: number; baseY: number } | null>(null)

const cropStageRef = ref<HTMLElement>()
const cropImgRef = ref<HTMLImageElement>()

const imgStyle = computed(() => ({
  transform: `translate(${offset.value.x}px, ${offset.value.y}px) scale(${zoom.value})`,
}))

const canConfirm = computed(() => {
  if (activeTab.value === 'preset') return !!selectedPreset.value
  return !!imageSrc.value
})

function selectPreset(id: string) {
  selectedPreset.value = id
}

function triggerFileSelect() {
  fileInputRef.value?.click()
}

function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!/^image\/(jpeg|png|webp)$/.test(file.type)) {
    ElMessage.warning('仅支持 JPG / PNG / WebP 图片')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片不能超过 5MB')
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    imageSrc.value = String(reader.result)
    // 切到上传态后重置变换
    offset.value = { x: 0, y: 0 }
    zoom.value = 1
  }
  reader.readAsDataURL(file)
}

/** 图片加载完成后：计算初始缩放，让图片短边刚好填满圆形视口 */
function onImgLoad() {
  const img = cropImgRef.value
  if (!img) return
  imgNatural.value = { width: img.naturalWidth, height: img.naturalHeight }
  const minSide = Math.min(img.naturalWidth, img.naturalHeight)
  // 基准：图片原始尺寸渲染，短边覆盖视口所需的 scale
  const need = VIEW_SIZE / minSide
  zoom.value = Math.max(need, 0.2)
  centerImage()
}

function centerImage() {
  offset.value = { x: 0, y: 0 }
}

/** 缩放限制：保证图片始终覆盖圆形视口（不出白边） */
function setZoom(next: number) {
  const min = minZoom()
  zoom.value = Math.min(Math.max(next, min), 4)
}

function minZoom(): number {
  const { width, height } = imgNatural.value
  if (!width || !height) return 0.2
  return VIEW_SIZE / Math.min(width, height)
}

function onPointerDown(e: PointerEvent) {
  if (!imageSrc.value) return
  ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
  dragState.value = { startX: e.clientX, startY: e.clientY, baseX: offset.value.x, baseY: offset.value.y }
}

function onPointerMove(e: PointerEvent) {
  if (!dragState.value) return
  const dx = e.clientX - dragState.value.startX
  const dy = e.clientY - dragState.value.startY
  offset.value = clampOffset({
    x: dragState.value.baseX + dx,
    y: dragState.value.baseY + dy,
  })
}

function onPointerUp() {
  dragState.value = null
}

function onWheel(e: WheelEvent) {
  if (!imageSrc.value) return
  const delta = e.deltaY > 0 ? -0.05 : 0.05
  setZoom(zoom.value + delta)
}

/** 限制平移：图片边缘不允许进入视口内部 */
function clampOffset(next: { x: number; y: number }): { x: number; y: number } {
  const img = cropImgRef.value
  if (!img) return next
  const w = imgNatural.value.width * zoom.value
  const h = imgNatural.value.height * zoom.value
  const maxX = Math.max(0, (w - VIEW_SIZE) / 2)
  const maxY = Math.max(0, (h - VIEW_SIZE) / 2)
  return {
    x: Math.min(Math.max(next.x, -maxX), maxX),
    y: Math.min(Math.max(next.y, -maxY), maxY),
  }
}

/** 导出裁剪结果：圆形视口区域 → 256×256 PNG（方形画布，展示端用 border-radius 成圆） */
async function exportCropBlob(): Promise<Blob> {
  const img = cropImgRef.value
  if (!img) throw new Error('图片未加载')

  const canvas = document.createElement('canvas')
  const OUT = 256
  canvas.width = OUT
  canvas.height = OUT
  const ctx = canvas.getContext('2d')!
  ctx.imageSmoothingQuality = 'high'

  // 视口中心在 stage 中心；img 的 transform-origin 是 center
  // 视口左上角相对图片渲染中心的偏移：
  const stageCenter = STAGE_SIZE / 2
  const renderW = imgNatural.value.width * zoom.value
  const renderH = imgNatural.value.height * zoom.value
  // 图片渲染区左上角在 stage 坐标系中的位置
  const imgLeft = stageCenter - renderW / 2 + offset.value.x
  const imgTop = stageCenter - renderH / 2 + offset.value.y
  // 视口（圆形区域）左上角
  const viewLeft = stageCenter - VIEW_SIZE / 2
  const viewTop = stageCenter - VIEW_SIZE / 2

  // 源图坐标换算
  const sx = ((viewLeft - imgLeft) / renderW) * imgNatural.value.width
  const sy = ((viewTop - imgTop) / renderH) * imgNatural.value.height
  const sw = (VIEW_SIZE / renderW) * imgNatural.value.width
  const sh = (VIEW_SIZE / renderH) * imgNatural.value.height

  ctx.drawImage(img, sx, sy, sw, sh, 0, 0, OUT, OUT)

  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) resolve(blob)
      else reject(new Error('导出失败'))
    }, 'image/png')
  })
}

// ==================== 事件 ====================
function handleOpen() {
  activeTab.value = props.currentAvatar?.startsWith('preset:') || !props.currentAvatar ? 'preset' : 'upload'
  const presetId = props.currentAvatar?.startsWith('preset:') ? props.currentAvatar.slice(7) : ''
  selectedPreset.value = presetId
  // 打开时定位到当前头像所在分组
  presetFilter.value = /^cartoon-\d+$/.test(presetId)
    ? 'cartoon'
    : presetId.startsWith('anime-m')
      ? 'anime-m'
      : presetId.startsWith('anime-f')
        ? 'anime-f'
        : presetId ? 'classic' : 'cartoon'
  imageSrc.value = ''
}

function handleClose() {
  emit('close')
}

async function handleConfirm() {
  if (activeTab.value === 'preset') {
    if (!selectedPreset.value) return
    emit('confirm', { kind: 'preset', value: `preset:${selectedPreset.value}` })
    return
  }
  try {
    const blob = await exportCropBlob()
    emit('confirm', { kind: 'crop', blob })
  } catch {
    ElMessage.error('裁剪失败，请重试')
  }
}

// 弹框关闭时重置上传态
watch(
  () => props.visible,
  (v) => {
    if (!v) {
      imageSrc.value = ''
      dragState.value = null
    }
  },
)
</script>

<style scoped lang="scss">
.avatar-preset-groups {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-bottom: 4px;
}

.avatar-preset-group-btn {
  padding: 4px 14px;
  font-size: 12px;
  color: var(--color-text-secondary);
  background: var(--color-fill-secondary);
  border: 1px solid transparent;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.15s;

  &:hover {
    color: var(--color-primary);
  }

  &.is-active {
    color: var(--color-primary);
    background: var(--color-primary-subtle);
    border-color: var(--color-primary-light);
    font-weight: 600;
  }
}

.avatar-preset-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  padding: 8px 4px 4px;
}

.avatar-preset-item {
  cursor: pointer;
  display: flex;
  justify-content: center;
  padding: 4px;
  border-radius: 50%;
  border: 2px solid transparent;
  transition: border-color 0.15s, transform 0.15s;

  &:hover {
    transform: scale(1.06);
  }

  &.is-active {
    border-color: var(--color-primary, #2563eb);
    box-shadow: 0 0 0 3px var(--color-primary-bg, rgba(37, 99, 235, 0.12));
  }
}

.avatar-preset-svg {
  display: block;
  width: 64px;
  height: 64px;

  :deep(svg) {
    display: block;
    width: 100%;
    height: 100%;
    border-radius: 50%;
  }
}

.avatar-preset-img {
  display: block;
  width: 64px;
  height: 64px;

  img {
    display: block;
    width: 100%;
    height: 100%;
    border-radius: 50%;
    object-fit: cover;
    background: var(--color-fill-secondary);
  }
}

.avatar-upload-empty {
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 220px;
  border: 1.5px dashed var(--color-border, var(--color-border));
  border-radius: 12px;
  color: var(--color-text-secondary, var(--color-text-tertiary));
  transition: border-color 0.15s, color 0.15s;

  &:hover {
    border-color: var(--color-primary, #2563eb);
    color: var(--color-primary, var(--color-primary));
  }

  p {
    margin: 0;
    font-size: 13px;
  }

  &__hint {
    font-size: 12px !important;
    color: var(--color-text-tertiary, var(--color-text-tertiary));
  }
}

.avatar-crop {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.avatar-crop__stage {
  position: relative;
  width: 280px;
  height: 280px;
  overflow: hidden;
  border-radius: 12px;
  background: var(--color-fill-secondary);
  touch-action: none;
  cursor: grab;
  user-select: none;

  &:active {
    cursor: grabbing;
  }
}

.avatar-crop__img {
  position: absolute;
  left: 50%;
  top: 50%;
  transform-origin: center;
  will-change: transform;
  pointer-events: none;
  max-width: none;
}

/* 圆形视口遮罩：四周半透明，中心圆透出图片（视口直径 220px → 半径 110px） */
.avatar-crop__mask {
  $view-radius: 110px;
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: radial-gradient(
    circle at center,
    transparent 0,
    transparent #{$view-radius - 1px},
    rgba(0, 0, 0, 0.45) #{$view-radius},
    rgba(0, 0, 0, 0.45) 100%
  );
}

.avatar-crop__toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 280px;
  margin-top: 12px;
}

.avatar-crop__hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--color-text-tertiary, var(--color-text-tertiary));
}
</style>
