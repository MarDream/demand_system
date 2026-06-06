<template>
  <div class="preview-loading-card">
    <div class="preview-loading-card__stage">
      <img class="preview-loading-card__image" :src="loadingBear" alt="正在整理资料" />
    </div>
    <div class="preview-loading-card__title">{{ title }}</div>
    <div class="preview-loading-card__desc">{{ description }}</div>
    <div class="preview-loading-card__meta">
      <span v-if="showElapsed" class="preview-loading-card__pill">已等待 {{ formattedElapsed }}</span>
      <span class="preview-loading-card__pill preview-loading-card__pill--progress">已完成 {{ displayProgress }}%</span>
    </div>
    <div v-if="showSlowNotice" class="preview-loading-card__notice">{{ slowNotice }}</div>
    <div class="preview-loading-card__dots" aria-hidden="true">
      <span></span>
      <span></span>
      <span></span>
    </div>
    <div class="preview-loading-card__track">
      <div class="preview-loading-card__bar" :style="{ width: `${displayProgress}%` }"></div>
    </div>
    <div class="preview-loading-card__caption">{{ caption }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import loadingBear from '@/assets/loading-bear.png'
import { clampProgress } from '@/utils/previewLoading'

const props = withDefaults(defineProps<{
  title: string
  description: string
  progress: number
  elapsedSeconds?: number
  showElapsed?: boolean
  showSlowNotice?: boolean
  slowNotice?: string
  caption?: string
}>(), {
  elapsedSeconds: 0,
  showElapsed: true,
  showSlowNotice: false,
  slowNotice: '等待时间较长，建议直接下载原文件查看。',
  caption: '马上就好啦！',
})

const displayProgress = computed(() => clampProgress(props.progress))

const formattedElapsed = computed(() => {
  const minutes = Math.floor(props.elapsedSeconds / 60)
  const seconds = props.elapsedSeconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
})
</script>

<style scoped>
.preview-loading-card {
  width: min(680px, calc(100% - 32px));
  padding: 34px 44px 40px;
  border-radius: 40px;
  background: rgba(255, 252, 246, 0.94);
  border: 1px solid rgba(243, 207, 153, 0.5);
  box-shadow:
    0 24px 64px rgba(212, 146, 58, 0.16),
    inset 0 1px 0 rgba(255, 255, 255, 0.9);
  text-align: center;
  backdrop-filter: blur(8px);
}

.preview-loading-card__stage {
  position: relative;
  display: inline-flex;
  justify-content: center;
  align-items: center;
  width: min(360px, 100%);
  margin: 0 auto 10px;
}

.preview-loading-card__stage::before {
  content: '';
  position: absolute;
  inset: 16px 30px 30px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 196, 96, 0.34), rgba(255, 196, 96, 0) 72%);
  filter: blur(10px);
  animation: preview-card-glow 3s ease-in-out infinite;
}

.preview-loading-card__image {
  position: relative;
  z-index: 1;
  width: min(300px, 72vw);
  max-width: 100%;
  user-select: none;
  pointer-events: none;
  filter: drop-shadow(0 22px 26px rgba(201, 135, 52, 0.18));
  transform-origin: center bottom;
  animation: preview-card-float 3.2s ease-in-out infinite;
}

.preview-loading-card__title {
  margin-top: 6px;
  font-size: clamp(30px, 4vw, 44px);
  font-weight: 700;
  line-height: 1.2;
  color: #4f2a12;
  letter-spacing: 0.02em;
}

.preview-loading-card__desc {
  margin-top: 8px;
  font-size: clamp(15px, 2vw, 18px);
  line-height: 1.7;
  color: #9b6844;
}

.preview-loading-card__meta {
  display: flex;
  justify-content: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 16px;
}

.preview-loading-card__pill {
  padding: 5px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.84);
  border: 1px solid rgba(222, 195, 154, 0.72);
  color: #8b5b34;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.preview-loading-card__pill--progress {
  color: #b95b1d;
  background: rgba(255, 241, 221, 0.9);
}

.preview-loading-card__notice {
  width: min(460px, 100%);
  margin: 14px auto 0;
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(251, 146, 60, 0.12);
  border: 1px solid rgba(251, 146, 60, 0.2);
  color: #c2410c;
  font-size: 12px;
  line-height: 1.7;
}

.preview-loading-card__dots {
  display: inline-flex;
  gap: 10px;
  margin-top: 18px;
}

.preview-loading-card__dots span {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: linear-gradient(180deg, #ffbc68 0%, #ff8a3d 100%);
  box-shadow: 0 6px 14px rgba(255, 147, 52, 0.28);
  animation: preview-card-dot-bounce 1.3s ease-in-out infinite;
}

.preview-loading-card__dots span:nth-child(2) {
  animation-delay: 0.16s;
}

.preview-loading-card__dots span:nth-child(3) {
  animation-delay: 0.32s;
}

.preview-loading-card__track {
  position: relative;
  width: min(420px, 100%);
  height: 16px;
  margin: 28px auto 16px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.88);
  border: 2px solid rgba(175, 128, 90, 0.38);
  overflow: hidden;
  box-shadow: inset 0 2px 5px rgba(124, 74, 28, 0.08);
}

.preview-loading-card__bar {
  position: relative;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #ff8f49 0%, #ffb95e 56%, #ffd57a 100%);
  transition: width 0.3s ease;
  box-shadow: 0 8px 20px rgba(255, 146, 66, 0.3);
}

.preview-loading-card__bar::after {
  content: '';
  position: absolute;
  top: 0;
  right: -22%;
  width: 22%;
  height: 100%;
  background: linear-gradient(90deg, rgba(255, 255, 255, 0), rgba(255, 255, 255, 0.72), rgba(255, 255, 255, 0));
  transform: skewX(-18deg);
  animation: preview-card-progress-shine 1.6s linear infinite;
}

.preview-loading-card__caption {
  font-size: clamp(24px, 3.6vw, 34px);
  font-weight: 700;
  color: #4f2a12;
  letter-spacing: 0.03em;
}

@keyframes preview-card-float {
  0%, 100% {
    transform: translateY(0) rotate(-1deg) scale(1);
  }
  50% {
    transform: translateY(-10px) rotate(1.2deg) scale(1.015);
  }
}

@keyframes preview-card-glow {
  0%, 100% {
    opacity: 0.78;
    transform: scale(0.98);
  }
  50% {
    opacity: 1;
    transform: scale(1.04);
  }
}

@keyframes preview-card-dot-bounce {
  0%, 80%, 100% {
    transform: translateY(0);
    opacity: 0.58;
  }
  40% {
    transform: translateY(-7px);
    opacity: 1;
  }
}

@keyframes preview-card-progress-shine {
  0% {
    transform: translateX(0) skewX(-18deg);
  }
  100% {
    transform: translateX(-520%) skewX(-18deg);
  }
}

@media (max-width: 640px) {
  .preview-loading-card {
    width: calc(100vw - 32px);
    padding: 26px 22px 30px;
    border-radius: 28px;
  }

  .preview-loading-card__stage {
    margin-bottom: 2px;
  }

  .preview-loading-card__track {
    margin-top: 22px;
    margin-bottom: 12px;
  }
}
</style>
