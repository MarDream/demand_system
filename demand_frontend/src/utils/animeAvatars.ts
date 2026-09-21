/**
 * 动漫风预设头像（参考柔和插画风格：粉彩渐变背景 + 光斑、大眼睛高光、腮红、
 * 层次头发 + 高光发丝、发饰）。
 *
 * 与 presetAvatars.ts 相同的接入方式：内联 SVG data URL，`preset:<id>` 存入 user.avatar，
 * 不产生外部图片依赖。10 款男生 + 10 款女生。
 */

export type AnimeHairFemale = 'bun' | 'long' | 'twin' | 'bob' | 'ponytail'
export type AnimeHairMale = 'spiky' | 'sidepart' | 'messy' | 'fringe'
export type AnimeAccessory = 'ribbon' | 'glasses' | 'hairpin' | 'earrings' | 'headset' | 'none'

export interface AnimeAvatarDef {
  id: string
  name: string
  gender: 'male' | 'female'
  /** 背景渐变（上→下） */
  bg1: string
  bg2: string
  /** 发色 + 暗部 */
  hair: string
  hairDark: string
  skin: string
  skinDark: string
  /** 虹膜色 + 暗部 */
  eye: string
  eyeDark: string
  cloth: string
  clothDark: string
  hairStyle: AnimeHairFemale | AnimeHairMale
  accessory: AnimeAccessory
}

const SKIN_LIGHT = '#FFE8D6'
const SKIN_LIGHT_DARK = '#F5CBA8'
const SKIN_MID = '#F7D4B0'
const SKIN_MID_DARK = '#E9BC92'
const SKIN_TAN = '#EFBE93'
const SKIN_TAN_DARK = '#DCA675'

export const ANIME_AVATAR_DEFS: AnimeAvatarDef[] = [
  // ===== 女生 ×10 =====
  { id: 'anime-f1', name: '银发绾', gender: 'female', bg1: '#F3F6F4', bg2: '#DCE7DE', hair: '#9D9AAB', hairDark: '#7E7B90', skin: SKIN_LIGHT, skinDark: SKIN_LIGHT_DARK, eye: '#8FA8C8', eyeDark: '#6C86AB', cloth: '#EFE6DA', clothDark: '#D9CCBA', hairStyle: 'bun', accessory: 'ribbon' },
  { id: 'anime-f2', name: '栗语', gender: 'female', bg1: '#FBF1E6', bg2: '#F0DFC8', hair: '#8A5A3B', hairDark: '#6E4429', skin: SKIN_LIGHT, skinDark: SKIN_LIGHT_DARK, eye: '#9C7B4D', eyeDark: '#7A5D36', cloth: '#E8B4B8', clothDark: '#D19AA0', hairStyle: 'long', accessory: 'none' },
  { id: 'anime-f3', name: '樱鲤', gender: 'female', bg1: '#FDEEF2', bg2: '#F8D8E2', hair: '#E8A0B4', hairDark: '#CE7F97', skin: SKIN_LIGHT, skinDark: SKIN_LIGHT_DARK, eye: '#C25E7E', eyeDark: '#9E4563', cloth: '#FFF6F0', clothDark: '#EBD9CC', hairStyle: 'twin', accessory: 'hairpin' },
  { id: 'anime-f4', name: '黛螺', gender: 'female', bg1: '#EEF0F6', bg2: '#D8DDEA', hair: '#3B3F52', hairDark: '#282B3A', skin: SKIN_MID, skinDark: SKIN_MID_DARK, eye: '#5B7BA8', eyeDark: '#435D85', cloth: '#46557A', clothDark: '#374464', hairStyle: 'long', accessory: 'earrings' },
  { id: 'anime-f5', name: '金铃', gender: 'female', bg1: '#FDF3DC', bg2: '#F3E1B4', hair: '#D9A94E', hairDark: '#B98B34', skin: SKIN_LIGHT, skinDark: SKIN_LIGHT_DARK, eye: '#7E9A56', eyeDark: '#61793F', cloth: '#7FA8C9', clothDark: '#668FB2', hairStyle: 'bob', accessory: 'hairpin' },
  { id: 'anime-f6', name: '蓝汐', gender: 'female', bg1: '#E8F2FA', bg2: '#CFE3F2', hair: '#3F5570', hairDark: '#2C3D53', skin: SKIN_LIGHT, skinDark: SKIN_LIGHT_DARK, eye: '#4E7FA6', eyeDark: '#3A6284', cloth: '#E5EBF2', clothDark: '#CBD5E1', hairStyle: 'bob', accessory: 'none' },
  { id: 'anime-f7', name: '紫穗', gender: 'female', bg1: '#F1EBFA', bg2: '#DFD2F0', hair: '#7B5EA7', hairDark: '#5F4684', skin: SKIN_LIGHT, skinDark: SKIN_LIGHT_DARK, eye: '#8A5FC0', eyeDark: '#6C4499', cloth: '#F5F0FA', clothDark: '#DDD2EA', hairStyle: 'ponytail', accessory: 'ribbon' },
  { id: 'anime-f8', name: '茶茶', gender: 'female', bg1: '#EDF5EA', bg2: '#D6E7D0', hair: '#5E7050', hairDark: '#465540', skin: SKIN_MID, skinDark: SKIN_MID_DARK, eye: '#6B7B45', eyeDark: '#525F33', cloth: '#F6F1E7', clothDark: '#E0D7C4', hairStyle: 'bun', accessory: 'none' },
  { id: 'anime-f9', name: '霜华', gender: 'female', bg1: '#F2F4F8', bg2: '#DDE3EC', hair: '#C4BFCE', hairDark: '#A9A3B6', skin: SKIN_LIGHT, skinDark: SKIN_LIGHT_DARK, eye: '#7FA3B8', eyeDark: '#618197', cloth: '#B7C6D9', clothDark: '#9BB0C8', hairStyle: 'twin', accessory: 'ribbon' },
  { id: 'anime-f10', name: '朱砂', gender: 'female', bg1: '#FBEDE8', bg2: '#F4D5CB', hair: '#6E3A32', hairDark: '#54291F', skin: SKIN_TAN, skinDark: SKIN_TAN_DARK, eye: '#A65B3F', eyeDark: '#84442C', cloth: '#C96A5B', clothDark: '#AE5244', hairStyle: 'ponytail', accessory: 'earrings' },

  // ===== 男生 ×10 =====
  { id: 'anime-m1', name: '玄墨', gender: 'male', bg1: '#EEF1F4', bg2: '#D9DEE5', hair: '#2E3138', hairDark: '#1C1E24', skin: SKIN_MID, skinDark: SKIN_MID_DARK, eye: '#4E6E96', eyeDark: '#3A557A', cloth: '#3D4C63', clothDark: '#2F3C50', hairStyle: 'spiky', accessory: 'none' },
  { id: 'anime-m2', name: '松风', gender: 'male', bg1: '#EFF3EC', bg2: '#D9E3D0', hair: '#4A3A2A', hairDark: '#332719', skin: SKIN_MID, skinDark: SKIN_MID_DARK, eye: '#6B5A3E', eyeDark: '#51432C', cloth: '#E9E4DA', clothDark: '#D2CBBB', hairStyle: 'sidepart', accessory: 'none' },
  { id: 'anime-m3', name: '青野', gender: 'male', bg1: '#EAF2F0', bg2: '#D2E4DF', hair: '#2F4A42', hairDark: '#1F342E', skin: SKIN_MID, skinDark: SKIN_MID_DARK, eye: '#3E6B5C', eyeDark: '#2C5245', cloth: '#5E7C72', clothDark: '#4A655C', hairStyle: 'spiky', accessory: 'headset' },
  { id: 'anime-m4', name: '灰羽', gender: 'male', bg1: '#F0F1F5', bg2: '#DBDDE6', hair: '#8B8F9C', hairDark: '#6E7280', skin: SKIN_LIGHT, skinDark: SKIN_LIGHT_DARK, eye: '#5E7A8E', eyeDark: '#475E70', cloth: '#495464', clothDark: '#39424F', hairStyle: 'messy', accessory: 'glasses' },
  { id: 'anime-m5', name: '金乌', gender: 'male', bg1: '#FBF2E2', bg2: '#F3E0BC', hair: '#B07F3C', hairDark: '#8D6229', skin: SKIN_TAN, skinDark: SKIN_TAN_DARK, eye: '#7A5B2E', eyeDark: '#5E441F', cloth: '#C97F35', clothDark: '#AC6624', hairStyle: 'messy', accessory: 'none' },
  { id: 'anime-m6', name: '湛卢', gender: 'male', bg1: '#E9EFF8', bg2: '#CFDDF0', hair: '#232B3D', hairDark: '#151B29', skin: SKIN_LIGHT, skinDark: SKIN_LIGHT_DARK, eye: '#3E5E92', eyeDark: '#2C4771', cloth: '#E2E8F2', clothDark: '#C6D1E2', hairStyle: 'sidepart', accessory: 'earrings' },
  { id: 'anime-m7', name: '棕榈', gender: 'male', bg1: '#F5EFE6', bg2: '#E8DCC6', hair: '#5C4630', hairDark: '#443321', skin: SKIN_TAN, skinDark: SKIN_TAN_DARK, eye: '#5E4A32', eyeDark: '#463624', cloth: '#6E8F5E', clothDark: '#587749', hairStyle: 'fringe', accessory: 'headset' },
  { id: 'anime-m8', name: '石青', gender: 'male', bg1: '#EBF1F3', bg2: '#D3E1E6', hair: '#34505C', hairDark: '#23393F', skin: SKIN_MID, skinDark: SKIN_MID_DARK, eye: '#42707E', eyeDark: '#305660', cloth: '#37596B', clothDark: '#2A4655', hairStyle: 'spiky', accessory: 'none' },
  { id: 'anime-m9', name: '烈阳', gender: 'male', bg1: '#FDF0E4', bg2: '#F8DCC4', hair: '#7A4A2C', hairDark: '#5E3620', skin: SKIN_TAN, skinDark: SKIN_TAN_DARK, eye: '#8A5A32', eyeDark: '#6C4324', cloth: '#E8EAF2', clothDark: '#CFD3E2', hairStyle: 'messy', accessory: 'none' },
  { id: 'anime-m10', name: '静水', gender: 'male', bg1: '#EFF2F7', bg2: '#DBE1EC', hair: '#1F2229', hairDark: '#111318', skin: SKIN_LIGHT, skinDark: SKIN_LIGHT_DARK, eye: '#54748E', eyeDark: '#3F5A70', cloth: '#5B6B85', clothDark: '#48566D', hairStyle: 'fringe', accessory: 'glasses' },
]

/** 构建动漫风 SVG（64×64 viewBox，圆形裁切由展示端完成） */
export function buildAnimeAvatarSvg(def: AnimeAvatarDef): string {
  const female = def.gender === 'female'
  const accessories: Record<AnimeAccessory, string> = {
    ribbon: `
      <!-- 侧发带（参考图风格粉丝带） -->
      <g>
        <path d="M44.5 17.5 L49.5 14.5 Q51 18 49.8 21.5 L45.5 20.5 Z" fill="#F2A9BC"/>
        <path d="M45 20.8 L50 21.8 Q48.5 25 45.5 25.5 L43.5 22 Z" fill="#E88FA6"/>
        <circle cx="45" cy="20" r="1.8" fill="#F8C6D4"/>
      </g>`,
    glasses: `
      <g fill="none" stroke="#3A4252" stroke-width="1.6">
        <rect x="20" y="29.5" width="10" height="8" rx="3.4"/>
        <rect x="34" y="29.5" width="10" height="8" rx="3.4"/>
        <path d="M30 33.2 Q32 32 34 33.2"/>
        <path d="M20 32.5 L16.5 33.8"/>
        <path d="M44 32.5 L47.5 33.8"/>
      </g>`,
    hairpin: `
      <g>
        <rect x="42.6" y="22.6" width="7" height="2.6" rx="1.3" transform="rotate(18 46 24)" fill="#F5C64F"/>
        <circle cx="49" cy="21.6" r="1.5" fill="#F0DFAE"/>
      </g>`,
    earrings: `
      <circle cx="17.8" cy="37.5" r="1.5" fill="#F2C94C"/>
      <circle cx="46.2" cy="37.5" r="1.5" fill="#F2C94C"/>`,
    headset: `
      <path d="M15.5 31 Q15.5 15.5 32 15.5 Q48.5 15.5 48.5 31" fill="none" stroke="#39424F" stroke-width="2.6" stroke-linecap="round"/>
      <rect x="12.6" y="29" width="5.6" height="9" rx="2.8" fill="#39424F"/>
      <rect x="45.8" y="29" width="5.6" height="9" rx="2.8" fill="#39424F"/>`,
    none: '',
  }

  // ---- 后层头发（体前主体后面的长发/发束） ----
  const backHair = {
    long: `<path d="M14 30 Q13 12 32 12 Q51 12 50 30 L51.5 52 Q48 54 45.5 52 L45 40 Q44 44 32 44 Q20 44 19 40 L18.5 52 Q16 54 12.5 52 Z" fill="${def.hairDark}"/>`,
    twin: `
      <path d="M14 30 Q13 12 32 12 Q51 12 50 30 L50.5 38 Q48 40 45 38 L45 36 Q44 40 32 40 Q20 40 19 36 L19 38 Q16 40 13.5 38 Z" fill="${def.hairDark}"/>
      <path d="M13.5 24 Q8 28 8.5 42 Q8.5 47 12 46.5 Q14.5 46 14 40 Q14.5 32 16.5 27 Z" fill="${def.hairDark}"/>
      <path d="M50.5 24 Q56 28 55.5 42 Q55.5 47 52 46.5 Q49.5 46 50 40 Q49.5 32 47.5 27 Z" fill="${def.hairDark}"/>`,
    ponytail: `
      <path d="M14 30 Q13 12 32 12 Q51 12 50 30 L50.5 37 Q48 39 45.5 37.5 L45 35 Q44 39 32 39 Q20 39 19 35 L19 37.5 Q16 39 13.5 37.5 Z" fill="${def.hairDark}"/>
      <path d="M47 16 Q57 20 55 40 Q54 46 50.5 44.5 Q48.5 43.5 49.5 38 Q50.5 26 44.5 19.5 Z" fill="${def.hairDark}"/>`,
    bun: `
      <circle cx="41" cy="12.5" r="6.2" fill="${def.hairDark}"/>
      <circle cx="41" cy="12.5" r="6.2" fill="none" stroke="${def.hair}" stroke-width="1.2" opacity="0.5"/>
      <path d="M14 31 Q13 13.5 32 13.5 Q51 13.5 50 31 L50.2 36 Q47.5 38 45.2 36.5 L44.6 34.5 Q43.5 38.5 32 38.5 Q20.5 38.5 19.4 34.5 L18.8 36.5 Q16.5 38 13.8 36 Z" fill="${def.hairDark}"/>`,
    bob: `<path d="M13.5 31 Q12.5 12 32 12 Q51.5 12 50.5 31 L51 42 Q48.5 44.5 45.5 42.5 L45 37 Q44 41 32 41 Q20 41 19 37 L18.5 42.5 Q15.5 44.5 13 42 Z" fill="${def.hairDark}"/>`,
    spiky: `<path d="M14.5 30 L11.5 24 L16 24.5 L14.5 18.5 L20 21 L21 15.5 L25.5 19 L28 13.5 L31.5 17.5 L35.5 12.5 L37.5 18 L43 14.5 L43.5 20.5 L49 18.5 L47.5 24.5 L52 24.5 L49.5 30 Q46 38.5 32 38.5 Q18 38.5 14.5 30 Z" fill="${def.hairDark}"/>`,
    sidepart: `<path d="M14.5 31 Q13 13 32 13 Q51 13 49.5 31 L50 36 Q47 38 44.8 35.5 Q47.5 26 42.5 21.5 Q40 26 32 26.5 Q22.5 27 18.5 32 Q16.5 34.5 15 36 Q13.5 34 14 31 Z" fill="${def.hairDark}"/>`,
    messy: `<path d="M14.5 30 L12 25.5 L15.5 25 L14 19 L18.5 21.5 L19.5 15 L24 18.5 L27 13 L31 17 L35 12.5 L37.5 17.5 L42.5 14 L43.5 20 L48.5 17.5 L47.5 24 L51.5 25 L49.5 30 Q45.5 38 32 38 Q18.5 38 14.5 30 Z" fill="${def.hairDark}"/>`,
    fringe: `<path d="M14.5 31 Q13.5 13 32 13 Q50.5 13 49.5 31 L49.8 35.5 Q47 37.5 44.8 35 Q46.5 27.5 42.5 23.5 Q34 26.5 25 23.5 Q21 26 19.5 31.5 Q18 35.5 15.2 35.5 Q14 34 14.5 31 Z" fill="${def.hairDark}"/>`,
  }
  const backKey = def.hairStyle as keyof typeof backHair

  // ---- 前发（刘海） ----
  const frontHair = {
    long: `<path d="M18.5 31 Q17 16.5 32 16.5 Q47 16.5 45.5 31 Q45 23.5 39.5 22 Q40.5 26 37 27.5 Q37.5 23.5 33.5 22.5 Q27 21.5 22.5 26.5 Q19.5 28.5 18.5 31 Z" fill="${def.hair}"/>`,
    twin: `<path d="M18.5 31 Q17 16.5 32 16.5 Q47 16.5 45.5 31 Q45.5 24 41 21.8 Q40 25.5 36 26 Q37 22.5 33 21.5 Q26.5 20.5 22 26 Q19 28 18.5 31 Z" fill="${def.hair}"/>`,
    ponytail: `<path d="M18.5 31 Q17 16.5 32 16.5 Q47 16.5 45.5 31 Q44.5 23 38.5 21.5 Q34 20.5 29 22.5 Q22.5 24.5 20 29 Q19 30.5 18.5 31 Z" fill="${def.hair}"/>`,
    bun: `<path d="M18.5 31 Q17.5 17 32 17 Q46.5 17 45.5 31 Q44.5 23.5 39 22 Q39.5 25.5 36 26.5 Q36.5 23 32.5 22.5 Q26 22 22.5 26.5 Q19.5 28.5 18.5 31 Z" fill="${def.hair}"/>`,
    bob: `<path d="M18.5 31 Q17 16.5 32 16.5 Q47 16.5 45.5 31 Q45 22.5 38 21.5 Q30.5 20.5 25 24.5 Q21 26.5 19.5 30 Q18.5 31 18.5 31 Z" fill="${def.hair}"/>`,
    spiky: `<path d="M18.5 29.5 Q17.5 17 32 17 Q46.5 17 45.5 29.5 Q44.5 22.5 39.5 20.5 L38.5 24 L34.5 19.5 L31.5 23 L28 19 L25.5 23.5 L22 20.5 L21.5 25 Q19 26 18.5 29.5 Z" fill="${def.hair}"/>`,
    sidepart: `<path d="M18.5 30.5 Q17.5 17 32 17 Q46.5 17 45.5 30.5 Q44.5 22 37.5 19.5 Q33 18.5 27.5 21 Q21.5 23.5 19.5 30 Q18.8 30.5 18.5 30.5 Z" fill="${def.hair}"/>`,
    messy: `<path d="M18.5 30 Q17.5 16.5 32 16.5 Q46.5 16.5 45.5 30 Q44 22.5 38.5 20.5 L36.5 24 L32.5 19.5 L29.5 23 L25.5 19.5 L23 23.5 Q19.5 25 18.5 30 Z" fill="${def.hair}"/>`,
    fringe: `<path d="M18.5 30.5 Q17.5 17 32 17 Q46.5 17 45.5 30.5 Q44.5 23.5 40 21.5 Q35 26.5 28 24 Q22.5 25.5 20 30.5 Q19 31 18.5 30.5 Z" fill="${def.hair}"/>`,
  }
  const frontKey = def.hairStyle as keyof typeof frontHair

  // 眼睛：女生更大更圆
  const eyeRx = female ? 3.1 : 2.7
  const eyeRy = female ? 3.6 : 3.0
  const eyeY = female ? 33.4 : 33.8
  const eye = (cx: number) => `
      <g>
        <ellipse cx="${cx}" cy="${eyeY}" rx="${(eyeRx + 0.7).toFixed(1)}" ry="${(eyeRy + 0.6).toFixed(1)}" fill="#FFFFFF"/>
        <ellipse cx="${cx}" cy="${eyeY + 0.2}" rx="${eyeRx}" ry="${eyeRy}" fill="url(#iris)"/>
        <ellipse cx="${cx}" cy="${eyeY + 0.9}" rx="${(eyeRx * 0.52).toFixed(1)}" ry="${(eyeRy * 0.55).toFixed(1)}" fill="${def.eyeDark}"/>
        <circle cx="${cx}" cy="${eyeY + 0.2}" r="${(eyeRx * 0.34).toFixed(1)}" fill="#1E2430"/>
        <circle cx="${cx - eyeRx * 0.35}" cy="${eyeY - eyeRy * 0.4}" r="1.05" fill="#FFFFFF" opacity="0.95"/>
        <circle cx="${cx + eyeRx * 0.32}" cy="${eyeY + eyeRy * 0.42}" r="0.55" fill="#FFFFFF" opacity="0.75"/>
        <!-- 上睫毛 -->
        <path d="M${(cx - eyeRx - 0.8).toFixed(1)} ${(eyeY - eyeRy + 0.4).toFixed(1)} Q${cx} ${(eyeY - eyeRy - 1.6).toFixed(1)} ${(cx + eyeRx + 0.8).toFixed(1)} ${(eyeY - eyeRy + 0.4).toFixed(1)}" fill="none" stroke="#33302E" stroke-width="${female ? 1.5 : 1.2}" stroke-linecap="round"/>
      </g>`

  return `<svg viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg" aria-label="${def.name}">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="${def.bg1}"/>
      <stop offset="1" stop-color="${def.bg2}"/>
    </linearGradient>
    <linearGradient id="iris" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="${def.eyeDark}"/>
      <stop offset="0.55" stop-color="${def.eye}"/>
      <stop offset="1" stop-color="${def.eyeDark}"/>
    </linearGradient>
  </defs>
  <rect width="64" height="64" fill="url(#bg)"/>
  <!-- 光斑 -->
  <circle cx="12" cy="12" r="4.5" fill="#FFFFFF" opacity="0.35"/>
  <circle cx="53" cy="10" r="2.8" fill="#FFFFFF" opacity="0.4"/>
  <circle cx="55" cy="24" r="1.8" fill="#FFFFFF" opacity="0.3"/>
  <circle cx="9" cy="26" r="1.6" fill="#FFFFFF" opacity="0.3"/>
  <!-- 后发 -->
  ${backHair[backKey]}
  <!-- 脖子与身体 -->
  <path d="M27 40 L37 40 L37 47 Q42 47.5 46 50.5 L46 64 L18 64 L18 50.5 Q22 47.5 27 47 Z" fill="${def.skin}"/>
  <path d="M27 42.5 Q32 45.5 37 42.5 L37 40 L27 40 Z" fill="${def.skinDark}"/>
  <path d="M18 64 L18 50.5 Q22 47.5 27 47 L32 55 L37 47 Q42 47.5 46 50.5 L46 64 Z" fill="${def.cloth}"/>
  <path d="M27 47 L32 55 L37 47 L39.5 48.2 L32 58.5 L24.5 48.2 Z" fill="${def.clothDark}"/>
  <!-- 脸 -->
  <path d="M19.5 30 Q19.5 17.5 32 17.5 Q44.5 17.5 44.5 30 Q44.5 37.5 40.5 42 Q37.5 45.8 32 45.8 Q26.5 45.8 23.5 42 Q19.5 37.5 19.5 30 Z" fill="${def.skin}"/>
  <!-- 腮红 -->
  <ellipse cx="23.4" cy="37.2" rx="2.6" ry="1.5" fill="#F2A092" opacity="0.5"/>
  <ellipse cx="40.6" cy="37.2" rx="2.6" ry="1.5" fill="#F2A092" opacity="0.5"/>
  ${eye(25.6)}
  ${eye(38.4)}
  <!-- 眉毛 -->
  <path d="M22.6 27.4 Q25.4 25.8 28.2 27" fill="none" stroke="${def.hairDark}" stroke-width="1.1" stroke-linecap="round" opacity="0.85"/>
  <path d="M35.8 27 Q38.6 25.8 41.4 27.4" fill="none" stroke="${def.hairDark}" stroke-width="1.1" stroke-linecap="round" opacity="0.85"/>
  <!-- 鼻与嘴 -->
  <path d="M31.2 38.6 Q32.2 39.3 33 38.8" fill="none" stroke="${def.skinDark}" stroke-width="0.9" stroke-linecap="round"/>
  <path d="M30 41.3 Q32 42.8 34 41.3" fill="none" stroke="#C97B63" stroke-width="1.2" stroke-linecap="round"/>
  <!-- 前发 -->
  ${frontHair[frontKey]}
  <!-- 高光发丝 -->
  <path d="M22 20.5 Q26 17.5 31 17.8" fill="none" stroke="#FFFFFF" stroke-width="1.3" stroke-linecap="round" opacity="0.4"/>
  <path d="M37 18.5 Q41.5 19.5 43.5 23.5" fill="none" stroke="#FFFFFF" stroke-width="1.1" stroke-linecap="round" opacity="0.3"/>
  ${accessories[def.accessory]}
</svg>`
}
