<script setup lang="ts">
/**
 * 统一的人员头像组件。
 *
 * 背景：后端在多个接口里返回的是**原始** avatar 串（形如 `preset:cartoon-9`），
 * 直接绑到 `el-avatar` 的 `:src` 上，浏览器会把它当 URL 去请求，控制台报
 * `GET preset:cartoon-9 net::ERR_UNKNOWN_URL_SCHEME`，头像也不显示。
 *
 * 这个坑在项目里已反复出现多次（DefaultLayout / users / profile / bitable 各处
 * 都单独打过补丁），根因是"约定靠自觉、新增页面必漏"。因此收敛到本组件：
 * 调用方**只传原始值**，解析统一在这里做。
 *
 * `resolveAvatarUrl` 是幂等的（`data:` / `http(s):` / `/` 开头一律原样返回），
 * 所以即使调用方传的是已解析过的值也安全。
 */
import { computed } from 'vue'
import { resolveAvatarUrl } from '@/utils/presetAvatars'

const props = withDefaults(
  defineProps<{
    /** 头像原始值：`preset:xxx` / `/path` / `http(s):` / `data:`；空值走文字回退 */
    avatar?: string | null
    /** 回退显示名（无图时取首字） */
    name?: string | null
    /** 尺寸（px） */
    size?: number
    /** 无图时的底色（有图时不生效） */
    color?: string
  }>(),
  { avatar: null, name: '', size: 32, color: '' },
)

const src = computed(() => resolveAvatarUrl(props.avatar) || undefined)

/** 用 Array.from 取首字符，避免 emoji / 代理对被劈成半个字符 */
const fallbackText = computed(() => {
  const text = (props.name || '').trim()
  return text ? (Array.from(text)[0] as string).toUpperCase() : '?'
})
</script>

<template>
  <el-avatar :size="size" :src="src" :style="color ? { backgroundColor: color } : undefined">
    {{ fallbackText }}
  </el-avatar>
</template>
