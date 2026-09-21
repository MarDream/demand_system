/**
 * 预设头像定义与 SVG 构建。
 *
 * 设计原则（与用户偏好一致）：内联 SVG 演示，避免外部图片依赖/外链问题。
 * 经典 8 款扁平风 + animeAvatars.ts 动漫风 20 款（10 男 10 女），统一 64×64 viewBox，
 * 圆形裁切由展示端 border-radius 完成。
 */

import { ANIME_AVATAR_DEFS, buildAnimeAvatarSvg } from './animeAvatars'

export interface PresetAvatarDef {
  id: string
  name: string
  /** 背景色 */
  bg: string
  /** 头发色 */
  hair: string
  /** 肤色 */
  skin: string
  /** 衣服色 */
  shirt: string
  /** 装饰类型 */
  accessory: 'glasses' | 'earrings' | 'headset' | 'beanie' | 'none'
}

export const PRESET_AVATAR_DEFS: PresetAvatarDef[] = [
  { id: 'sunrise',  name: '晨曦', bg: '#FFE8CC', hair: '#4A3728', skin: '#FFD5B3', shirt: '#FF8A5C', accessory: 'none' },
  { id: 'ocean',    name: '海蓝', bg: '#D6EBFF', hair: '#2C3E50', skin: '#FFDCC5', shirt: '#3391FF', accessory: 'glasses' },
  { id: 'forest',   name: '森绿', bg: '#DDF3E4', hair: '#3E2723', skin: '#F5C9A0', shirt: '#34A853', accessory: 'beanie' },
  { id: 'lavender', name: '薰衣草', bg: '#EAE3FF', hair: '#5D4037', skin: '#FFDCC5', shirt: '#7A5AF8', accessory: 'earrings' },
  { id: 'sunset',   name: '晚霞', bg: '#FFE3E3', hair: '#212121', skin: '#E8B98A', shirt: '#F45B69', accessory: 'none' },
  { id: 'mint',     name: '薄荷', bg: '#D8F7F0', hair: '#513C2A', skin: '#FFD5B3', shirt: '#1FBF9C', accessory: 'headset' },
  { id: 'slate',    name: '石墨', bg: '#E5EAF0', hair: '#37474F', skin: '#F0C8A0', shirt: '#546E7A', accessory: 'glasses' },
  { id: 'amber',    name: '琥珀', bg: '#FFF3D6', hair: '#3E2723', skin: '#C68642', shirt: '#F5A623', accessory: 'beanie' },
]

/**
 * 构建 SVG 字符串。结构：
 * 背景圆 + 后层头发 + 脖子 + 衣服（肩部弧） + 脸 + 前发 + 五官 + 装饰
 */
export function buildPresetAvatarSvg(def: PresetAvatarDef): string {
  const accessories = {
    glasses: `
      <g>
        <circle cx="23" cy="33" r="6.5" fill="none" stroke="#37474F" stroke-width="2"/>
        <circle cx="41" cy="33" r="6.5" fill="none" stroke="#37474F" stroke-width="2"/>
        <path d="M29.5 33 Q32 31.5 34.5 33" fill="none" stroke="#37474F" stroke-width="2"/>
      </g>`,
    earrings: `
      <circle cx="17.5" cy="36" r="2" fill="#F5A623"/>
      <circle cx="46.5" cy="36" r="2" fill="#F5A623"/>`,
    headset: `
      <path d="M15 32 Q15 18 32 18 Q49 18 49 32" fill="none" stroke="#37474F" stroke-width="3" stroke-linecap="round"/>
      <rect x="11.5" y="30" width="6" height="10" rx="3" fill="#37474F"/>
      <rect x="46.5" y="30" width="6" height="10" rx="3" fill="#37474F"/>`,
    beanie: `
      <path d="M16 27 Q16 13 32 13 Q48 13 48 27 L48 29 L16 29 Z" fill="${def.shirt}"/>
      <rect x="15" y="27" width="34" height="4.5" rx="2.25" fill="#FFFFFF" opacity="0.85"/>`,
    none: '',
  }

  return `<svg viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg" aria-label="${def.name}">
  <rect width="64" height="64" fill="${def.bg}"/>
  <!-- 后层头发 -->
  <path d="M14 34 Q14 15 32 15 Q50 15 50 34 L50 44 Q50 48 46 48 L18 48 Q14 48 14 44 Z" fill="${def.hair}"/>
  <!-- 脖子 -->
  <rect x="27" y="40" width="10" height="9" fill="${def.skin}"/>
  <!-- 衣服（肩部） -->
  <path d="M10 64 Q10 50 24 49 L40 49 Q54 50 54 64 Z" fill="${def.shirt}"/>
  <!-- 脸 -->
  <ellipse cx="32" cy="32" rx="13.5" ry="14.5" fill="${def.skin}"/>
  <!-- 前发 -->
  <path d="M18.5 30 Q17 17 32 17 Q47 17 45.5 30 Q44 22 38 21.5 Q33 21 30 23 Q24 24.5 21.5 28 Q19.5 30 18.5 30 Z" fill="${def.hair}"/>
  <!-- 眉眼 -->
  <path d="M24.5 29.5 Q26.5 28 28.5 29.5" fill="none" stroke="#5B4A3F" stroke-width="1.4" stroke-linecap="round"/>
  <path d="M35.5 29.5 Q37.5 28 39.5 29.5" fill="none" stroke="#5B4A3F" stroke-width="1.4" stroke-linecap="round"/>
  <circle cx="26.5" cy="33" r="1.8" fill="#3B2E26"/>
  <circle cx="37.5" cy="33" r="1.8" fill="#3B2E26"/>
  <!-- 腮红 -->
  <ellipse cx="22.5" cy="37" rx="2.4" ry="1.4" fill="#F09A8B" opacity="0.5"/>
  <ellipse cx="41.5" cy="37" rx="2.4" ry="1.4" fill="#F09A8B" opacity="0.5"/>
  <!-- 微笑 -->
  <path d="M28.5 40.5 Q32 43.5 35.5 40.5" fill="none" stroke="#C97B63" stroke-width="1.6" stroke-linecap="round"/>
  ${accessories[def.accessory]}
</svg>`
}

/**
 * 卡通人物头像（图片素材）：E:/Images/素材 26 张，已处理为 256×256 居中裁方
 * （public/avatars/cartoon/cartoon-N.webp）。ID 为 cartoon-1 ~ cartoon-26。
 */
export const CARTOON_AVATAR_COUNT = 26

export function cartoonAvatarUrl(n: number): string {
  return `/avatars/cartoon/cartoon-${n}.webp`
}

/**
 * 解析头像地址为可渲染 URL：
 * - preset:cartoon-N → /avatars/cartoon/cartoon-N.webp（图片素材）
 * - preset:xxx → 生成对应 SVG data URL（经典 8 款 / 动漫风 20 款）
 * - /xxx → 后端相对路径（走同域）
 * - http(s) → 完整外链
 * - 其他/空 → null（展示端回退姓名首字）
 */
export function resolveAvatarUrl(avatar?: string | null): string | null {
  if (!avatar) return null
  if (avatar.startsWith('preset:')) {
    const id = avatar.slice(7)
    if (/^cartoon-(\d+)$/.test(id)) {
      const n = Number(id.slice(8))
      return n >= 1 && n <= CARTOON_AVATAR_COUNT ? cartoonAvatarUrl(n) : null
    }
    const flatDef = PRESET_AVATAR_DEFS.find((d) => d.id === id)
    if (flatDef) {
      const svg = buildPresetAvatarSvg(flatDef)
      return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
    }
    const animeDef = ANIME_AVATAR_DEFS.find((d) => d.id === id)
    if (animeDef) {
      const svg = buildAnimeAvatarSvg(animeDef)
      return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
    }
    return null
  }
  if (avatar.startsWith('http://') || avatar.startsWith('https://') || avatar.startsWith('data:')) {
    return avatar
  }
  if (avatar.startsWith('/')) return avatar
  return null
}
