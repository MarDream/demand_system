<template>
  <el-card shadow="hover" class="stat-card-pro" @click="$emit('click')">
    <div class="stat-card-pro__content">
      <!-- 图标区域：渐变背景 -->
      <div class="stat-icon-wrap" :style="iconStyle">
        <slot name="icon">
          <el-icon :size="24" color="var(--color-on-primary)">
            <component :is="icon" />
          </el-icon>
        </slot>
      </div>

      <!-- 数据区域 -->
      <div class="stat-info">
        <div class="stat-value-row">
          <span class="stat-value">{{ displayValue }}</span>
          <!-- 趋势指示器 -->
          <span
            v-if="trend !== undefined && trend !== null"
            class="stat-trend"
            :class="trend >= 0 ? 'up' : 'down'"
          >
            <el-icon :size="12">
              <ArrowUp v-if="trend >= 0" />
              <ArrowDown v-else />
            </el-icon>
            {{ Math.abs(trend) }}%
          </span>
        </div>
        <div class="stat-label">{{ label }}</div>
        <div v-if="tip" class="stat-tip">{{ tip }}</div>
      </div>
    </div>

    <!-- 迷你图表 -->
    <div v-if="sparkline && sparkline.length > 1" class="stat-sparkline">
      <svg :width="sparklineWidth" :height="sparklineHeight" :viewBox="`0 0 ${sparklineWidth} ${sparklineHeight}`" preserveAspectRatio="none">
        <polyline
          :points="sparklinePoints"
          fill="none"
          :stroke="gradientStart"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
        <!-- 渐变填充区域 -->
        <polygon
          :points="sparklineAreaPoints"
          :fill="`url(#sparkline-gradient-${instanceId})`"
        />
        <defs>
          <linearGradient :id="`sparkline-gradient-${instanceId}`" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" :stop-color="gradientStart" stop-opacity="0.3" />
            <stop offset="100%" :stop-color="gradientStart" stop-opacity="0.02" />
          </linearGradient>
        </defs>
      </svg>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed, ref, watch, onMounted, getCurrentInstance, type Component } from 'vue'
import { ArrowUp, ArrowDown } from '@element-plus/icons-vue'

interface Props {
  label: string
  value: number | string
  icon: Component
  trend?: number | null
  tip?: string
  gradientStart?: string
  gradientEnd?: string
  sparkline?: number[]
}

const props = withDefaults(defineProps<Props>(), {
  trend: null,
  tip: '',
  gradientStart: '#0369A1',
  gradientEnd: '#0284C7',
})

defineEmits<{ click: [] }>()

const instanceId = getCurrentInstance()?.uid ?? Math.random().toString(36).slice(2)

const iconStyle = computed(() => ({
  background: `linear-gradient(135deg, ${props.gradientStart} 0%, ${props.gradientEnd} 100%)`,
}))

// Sparkline 计算
const sparklineWidth = 200
const sparklineHeight = 40

const sparklinePoints = computed(() => {
  if (!props.sparkline || props.sparkline.length < 2) return ''
  const data = props.sparkline
  const max = Math.max(...data)
  const min = Math.min(...data)
  const range = max - min || 1
  const stepX = sparklineWidth / (data.length - 1)
  return data.map((v, i) => `${i * stepX},${sparklineHeight - ((v - min) / range) * (sparklineHeight - 4) - 2}`).join(' ')
})

const sparklineAreaPoints = computed(() => {
  if (!sparklinePoints.value) return ''
  return `${sparklinePoints.value} ${sparklineWidth},${sparklineHeight} 0,${sparklineHeight}`
})

// 数值动画（支持中断重启）
const displayValue = ref<string>(typeof props.value === 'number' ? '0' : props.value)
let animationFrameId: number | null = null

function animateNumber(target: number) {
  // 中断现有动画
  if (animationFrameId !== null) {
    cancelAnimationFrame(animationFrameId)
    animationFrameId = null
  }

  const duration = 600
  const startTime = performance.now()
  const startVal = typeof displayValue.value === 'string' ? parseFloat(displayValue.value.replace(/,/g, '')) : 0
  const effectiveStart = Number.isFinite(startVal) ? startVal : 0

  function tick(now: number) {
    const elapsed = now - startTime
    const progress = Math.min(elapsed / duration, 1)
    const eased = 1 - Math.pow(1 - progress, 3)
    const current = Math.round(effectiveStart + (target - effectiveStart) * eased)
    displayValue.value = current.toLocaleString()
    if (progress < 1) {
      animationFrameId = requestAnimationFrame(tick)
    } else {
      displayValue.value = target.toLocaleString()
      animationFrameId = null
    }
  }

  animationFrameId = requestAnimationFrame(tick)
}

onMounted(() => {
  if (typeof props.value === 'number') {
    animateNumber(props.value)
  }
})

watch(() => props.value, (newVal) => {
  if (typeof newVal === 'number') {
    animateNumber(newVal)
  } else {
    displayValue.value = newVal
  }
})
</script>

<style lang="scss" scoped>
.stat-card-pro {
  border: 1px solid var(--color-border) !important;
  border-radius: 12px !important;
  cursor: pointer;
  transition:
    transform 0.3s cubic-bezier(0.4, 0, 0.2, 1),
    box-shadow 0.3s cubic-bezier(0.4, 0, 0.2, 1),
    border-color 0.3s ease;
  overflow: hidden !important;
  position: relative;
  background: var(--color-surface) !important;

  // 顶部发光线
  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 2px;
    background: linear-gradient(90deg, transparent, var(--color-accent, #2563EB), transparent);
    opacity: 0;
    transition: opacity 0.3s ease;
  }

  &:hover {
    transform: translateY(-4px);
    box-shadow:
      0 12px 32px rgba(0, 0, 0, 0.08),
      0 0 0 1px rgba(37, 99, 235, 0.08),
      0 0 48px rgba(37, 99, 235, 0.06);
    border-color: rgba(37, 99, 235, 0.3) !important;

    &::before { opacity: 1; }

    .stat-icon-wrap {
      transform: scale(1.08);
      box-shadow: 0 4px 16px rgba(37, 99, 235, 0.3);
    }
  }

  :deep(.el-card__body) {
    padding: 20px;
  }

  &__content {
    display: flex;
    align-items: flex-start;
    gap: 16px;
  }
}

.stat-icon-wrap {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  transition: transform 0.3s ease, box-shadow 0.3s ease;

  .el-icon {
    filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.15));
  }
}

.stat-info {
  flex: 1;
  min-width: 0;
}

.stat-value-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-text-primary);
  line-height: 1.2;
  letter-spacing: -0.03em;
  font-variant-numeric: tabular-nums;
}

.stat-trend {
  font-size: 11px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 2px 6px;
  border-radius: 6px;

  &.up {
    color: var(--color-success, #059669);
    background: rgba(5, 150, 105, 0.1);
  }

  &.down {
    color: var(--color-danger, #DC2626);
    background: rgba(220, 38, 38, 0.1);
  }
}

.stat-label {
  font-size: 13px;
  color: var(--color-text-secondary);
  font-weight: 500;
}

.stat-tip {
  font-size: 11px;
  color: var(--color-muted-text);
  margin-top: 2px;
}

.stat-sparkline {
  margin-top: 12px;
  width: 100%;
  opacity: 0.6;
  transition: opacity 0.2s ease;

  svg {
    width: 100%;
    height: auto;
  }
}

.stat-card-pro:hover .stat-sparkline {
  opacity: 1;
}
</style>

