<template>
  <svg
    class="assistant-fab__icon"
    :class="`assistant-fab__icon--${variant}`"
    viewBox="0 0 48 48"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
  >
    <!-- ============ 共同层 ============ -->

    <!-- A. 圆脸少女（双马尾） -->
    <g v-if="variant === 'girl'" class="avatar-girl">
      <!-- 头顶光圈 -->
      <g class="assistant-fab__signal">
        <circle cx="24" cy="5" r="1.4" fill="currentColor" opacity="0.85" />
        <circle cx="18" cy="6.5" r="1" fill="currentColor" opacity="0.55" />
        <circle cx="30" cy="6.5" r="1" fill="currentColor" opacity="0.55" />
      </g>
      <!-- 头发底色（披发） -->
      <path
        class="avatar-girl__hair-back"
        d="M10 28 C10 16, 38 16, 38 28 L38 38 C38 42, 35 44, 32 44 L16 44 C13 44, 10 42, 10 38 Z"
        fill="#7c4dff"
      />
      <!-- 脸 -->
      <ellipse class="assistant-fab__face" cx="24" cy="26" rx="11" ry="12" fill="#fff" />
      <!-- 刘海 -->
      <path
        d="M13 20 C14 14, 20 11, 24 11 C28 11, 34 14, 35 20 C33 18, 28 17, 24 17 C20 17, 15 18, 13 20 Z"
        fill="#7c4dff"
      />
      <!-- 双马尾（左右各一束） -->
      <path
        class="avatar-girl__pony avatar-girl__pony--left"
        d="M11 22 C8 24, 7 32, 9 38 C10 41, 12 41, 13 39 C13 35, 12 28, 14 24 Z"
        fill="#7c4dff"
      />
      <path
        class="avatar-girl__pony avatar-girl__pony--right"
        d="M37 22 C40 24, 41 32, 39 38 C38 41, 36 41, 35 39 C35 35, 36 28, 34 24 Z"
        fill="#7c4dff"
      />
      <!-- 蝴蝶结 -->
      <path d="M21 11 L23 13 L25 11 L25 14 L21 14 Z" fill="#ff7ab0" />
      <!-- 红晕 -->
      <ellipse class="assistant-fab__cheek" cx="16" cy="29" rx="2.2" ry="1.2" fill="#ff9bb3" opacity="0.55" />
      <ellipse class="assistant-fab__cheek" cx="32" cy="29" rx="2.2" ry="1.2" fill="#ff9bb3" opacity="0.55" />
      <!-- 大眼睛（圆眼 + 高光） -->
      <ellipse class="assistant-fab__eye" cx="19" cy="26" rx="1.6" ry="2.2" fill="#1f2329" />
      <ellipse class="assistant-fab__eye" cx="29" cy="26" rx="1.6" ry="2.2" fill="#1f2329" />
      <circle class="assistant-fab__twinkle" cx="19.6" cy="25.2" r="0.6" fill="#fff" />
      <circle class="assistant-fab__twinkle" cx="29.6" cy="25.2" r="0.6" fill="#fff" />
      <!-- 嘴（O 形惊讶） -->
      <ellipse class="assistant-fab__mouth" cx="24" cy="32" rx="1.2" ry="1.5" fill="#1f2329" opacity="0.85" />
    </g>

    <!-- B. 小狐狸（程序员风） -->
    <g v-else-if="variant === 'fox'" class="avatar-fox">
      <!-- 头顶耳朵尖 -->
      <path class="avatar-fox__ear avatar-fox__ear--left" d="M14 14 L17 6 L21 13 Z" fill="#ff8a3d" />
      <path class="avatar-fox__ear avatar-fox__ear--right" d="M34 14 L31 6 L27 13 Z" fill="#ff8a3d" />
      <path d="M16 12 L17.5 8 L19 12 Z" fill="#fff5e6" />
      <path d="M32 12 L30.5 8 L29 12 Z" fill="#fff5e6" />
      <!-- 脸（尖下巴的狐狸脸） -->
      <path
        class="assistant-fab__face"
        d="M14 22 C14 16, 34 16, 34 22 L31 32 C30 36, 27 38, 24 38 C21 38, 18 36, 17 32 Z"
        fill="#fff5e6"
      />
      <!-- 头顶呆毛 -->
      <path d="M24 12 Q22 9, 23 6 Q25 9, 24 12" fill="#ff8a3d" />
      <!-- 红晕 -->
      <ellipse class="assistant-fab__cheek" cx="17" cy="28" rx="1.8" ry="1" fill="#ff9b6a" opacity="0.55" />
      <ellipse class="assistant-fab__cheek" cx="31" cy="28" rx="1.8" ry="1" fill="#ff9b6a" opacity="0.55" />
      <!-- 眯眼（弯月） -->
      <path class="assistant-fab__eye" d="M16 25 Q19 22 22 25" stroke="#1f2329" stroke-width="1.6" stroke-linecap="round" fill="none" />
      <path class="assistant-fab__eye" d="M26 25 Q29 22 32 25" stroke="#1f2329" stroke-width="1.6" stroke-linecap="round" fill="none" />
      <!-- 小鼻子 -->
      <path d="M24 28 L22.5 30 L25.5 30 Z" fill="#1f2329" />
      <!-- 小嘴（上扬微笑） -->
      <path class="assistant-fab__mouth" d="M22 31 Q24 33 26 31" stroke="#1f2329" stroke-width="1.4" stroke-linecap="round" fill="none" />
      <!-- 眼镜 -->
      <g class="avatar-fox__glasses" opacity="0.85">
        <circle cx="19" cy="25" r="3.5" stroke="#1f2329" stroke-width="1.1" fill="none" />
        <circle cx="29" cy="25" r="3.5" stroke="#1f2329" stroke-width="1.1" fill="none" />
        <path d="M22.5 25 L25.5 25" stroke="#1f2329" stroke-width="1.1" />
      </g>
    </g>

    <!-- C. 小猫咪 -->
    <g v-else-if="variant === 'cat'" class="avatar-cat">
      <!-- 猫耳朵（三角 + 内粉） -->
      <path d="M11 16 L16 6 L20 17 Z" fill="#b388ff" />
      <path d="M37 16 L32 6 L28 17 Z" fill="#b388ff" />
      <path d="M14 14 L16 9 L17 15 Z" fill="#ffc0e0" />
      <path d="M34 14 L32 9 L31 15 Z" fill="#ffc0e0" />
      <!-- 脸 -->
      <ellipse class="assistant-fab__face" cx="24" cy="27" rx="12" ry="13" fill="#fff" />
      <!-- 刘海（齐刘海） -->
      <path
        d="M13 21 Q14 16, 24 16 Q34 16, 35 21 Q33 19, 30 20 Q27 18, 24 20 Q21 18, 18 20 Q15 19, 13 21 Z"
        fill="#b388ff"
      />
      <!-- 红晕 -->
      <ellipse class="assistant-fab__cheek" cx="16" cy="30" rx="2.2" ry="1.2" fill="#ff9bb3" opacity="0.55" />
      <ellipse class="assistant-fab__cheek" cx="32" cy="30" rx="2.2" ry="1.2" fill="#ff9bb3" opacity="0.55" />
      <!-- 猫眼（椭圆竖瞳） -->
      <ellipse class="assistant-fab__eye" cx="19" cy="27" rx="1.6" ry="2.2" fill="#1f2329" />
      <ellipse class="assistant-fab__eye" cx="29" cy="27" rx="1.6" ry="2.2" fill="#1f2329" />
      <ellipse class="assistant-fab__twinkle" cx="19" cy="26" rx="0.5" ry="0.9" fill="#fff" />
      <ellipse class="assistant-fab__twinkle" cx="29" cy="26" rx="0.5" ry="0.9" fill="#fff" />
      <!-- 鼻子 + 小嘴（w 形猫嘴） -->
      <path d="M24 31 L22.5 33 L25.5 33 Z" fill="#ff7ab0" />
      <path class="assistant-fab__mouth" d="M22 33 Q23 34 24 33 Q25 34 26 33" stroke="#1f2329" stroke-width="1.2" stroke-linecap="round" fill="none" />
      <!-- 胡须 -->
      <path d="M14 31 L18 31 M14 33 L18 32" stroke="#909399" stroke-width="0.6" stroke-linecap="round" />
      <path d="M34 31 L30 31 M34 33 L30 32" stroke="#909399" stroke-width="0.6" stroke-linecap="round" />
    </g>

    <!-- D. 魔法少女（精灵） -->
    <g v-else-if="variant === 'elf'" class="avatar-elf">
      <!-- 精灵长尖耳 -->
      <path class="avatar-elf__ear avatar-elf__ear--left" d="M11 26 Q5 25, 6 32 Q9 30, 11 28 Z" fill="#ffd5ec" />
      <path class="avatar-elf__ear avatar-elf__ear--right" d="M37 26 Q43 25, 42 32 Q39 30, 37 28 Z" fill="#ffd5ec" />
      <!-- 头顶皇冠 -->
      <path d="M18 11 L20 7 L22 11 L24 5 L26 11 L28 7 L30 11 L30 14 L18 14 Z" fill="#ffd54a" />
      <circle cx="24" cy="8" r="1.2" fill="#ff5252" />
      <!-- 头发 -->
      <path
        class="avatar-elf__hair"
        d="M11 22 C12 14, 18 10, 24 10 C30 10, 36 14, 37 22 C34 19, 30 18, 24 18 C18 18, 14 19, 11 22 Z"
        fill="#ff80ab"
      />
      <!-- 脸 -->
      <ellipse class="assistant-fab__face" cx="24" cy="26" rx="11" ry="12" fill="#fff" />
      <!-- 红晕 -->
      <ellipse class="assistant-fab__cheek" cx="16" cy="29" rx="2" ry="1.1" fill="#ff9bb3" opacity="0.55" />
      <ellipse class="assistant-fab__cheek" cx="32" cy="29" rx="2" ry="1.1" fill="#ff9bb3" opacity="0.55" />
      <!-- 星形大眼 -->
      <path
        class="assistant-fab__eye"
        d="M19 26 L19.7 27.3 L21 28 L19.7 28.7 L19 30 L18.3 28.7 L17 28 L18.3 27.3 Z"
        fill="#7c4dff"
      />
      <path
        class="assistant-fab__eye"
        d="M29 26 L29.7 27.3 L31 28 L29.7 28.7 L29 30 L28.3 28.7 L27 28 L28.3 27.3 Z"
        fill="#7c4dff"
      />
      <circle class="assistant-fab__twinkle" cx="19" cy="26.4" r="0.5" fill="#fff" />
      <circle class="assistant-fab__twinkle" cx="29" cy="26.4" r="0.5" fill="#fff" />
      <!-- 微笑 -->
      <path class="assistant-fab__mouth" d="M21 32 Q24 35 27 32" stroke="#1f2329" stroke-width="1.4" stroke-linecap="round" fill="none" />
      <!-- 头顶星星闪烁 -->
      <g class="assistant-fab__twinkle-star">
        <path d="M24 3 L24.7 4.5 L26 5 L24.7 5.5 L24 7 L23.3 5.5 L22 5 L23.3 4.5 Z" fill="#ffd54a" />
      </g>
    </g>

    <!-- E. 兜底：原数字人头像 -->
    <g v-else class="avatar-default">
      <!-- 头顶信号圈 -->
      <g class="assistant-fab__signal">
        <circle cx="24" cy="6" r="1.6" fill="currentColor" opacity="0.85" />
        <circle cx="18" cy="7.5" r="1.1" fill="currentColor" opacity="0.55" />
        <circle cx="30" cy="7.5" r="1.1" fill="currentColor" opacity="0.55" />
      </g>
      <ellipse class="assistant-fab__cheek" cx="14" cy="29" rx="3" ry="1.6" fill="#ff9bb3" opacity="0.55" />
      <ellipse class="assistant-fab__cheek" cx="34" cy="29" rx="3" ry="1.6" fill="#ff9bb3" opacity="0.55" />
      <ellipse class="assistant-fab__face" cx="24" cy="26" rx="13" ry="14" fill="#fff" />
      <path
        d="M11 22 C12 14, 18 10, 24 10 C30 10, 36 14, 37 22 C34 19, 30 18, 24 18 C18 18, 14 19, 11 22 Z"
        fill="#7c4dff"
      />
      <path class="assistant-fab__eye" d="M18 25 Q20 22 22 25" stroke="#1f2329" stroke-width="1.6" stroke-linecap="round" fill="none" />
      <path class="assistant-fab__eye" d="M26 25 Q28 22 30 25" stroke="#1f2329" stroke-width="1.6" stroke-linecap="round" fill="none" />
      <circle class="assistant-fab__twinkle" cx="21" cy="24" r="0.6" fill="#fff" opacity="0.9" />
      <circle class="assistant-fab__twinkle" cx="29" cy="24" r="0.6" fill="#fff" opacity="0.9" />
      <path class="assistant-fab__mouth" d="M21 31 Q24 34 27 31" stroke="#1f2329" stroke-width="1.4" stroke-linecap="round" fill="none" />
      <path class="assistant-fab__bangs" d="M13 19 Q15 16 17 18" stroke="#5b3acc" stroke-width="1.2" stroke-linecap="round" fill="none" />
      <path class="assistant-fab__bangs" d="M31 18 Q33 16 35 19" stroke="#5b3acc" stroke-width="1.2" stroke-linecap="round" fill="none" />
    </g>
  </svg>
</template>

<script setup lang="ts">
defineProps<{
  variant: 'default' | 'girl' | 'fox' | 'cat' | 'elf'
}>()
</script>

<style scoped>
.assistant-fab__icon {
  width: 40px;
  height: 40px;
  color: #fff;
  overflow: visible;
}

/* ===== 通用动画 ===== */
.assistant-fab__face {
  transform-origin: 24px 26px;
  animation: avatar-face 3s ease-in-out infinite;
}
.assistant-fab__cheek {
  transform-origin: center;
  animation: avatar-blush 3.5s ease-in-out infinite;
}
.assistant-fab__eye {
  transform-origin: center;
  transform-box: fill-box;
  animation: avatar-blink 4.5s ease-in-out infinite;
}
.assistant-fab__twinkle {
  transform-origin: center;
  transform-box: fill-box;
  animation: avatar-twinkle 2s ease-in-out infinite;
}
.assistant-fab__mouth {
  transform-origin: center;
  transform-box: fill-box;
  animation: avatar-mouth 3.5s ease-in-out infinite;
}
.assistant-fab__signal {
  transform-origin: 24px 6px;
  animation: avatar-signal 2.4s ease-in-out infinite;
}

/* 少女：双马尾摆动 */
.avatar-girl__pony {
  transform-origin: center top;
  transform-box: fill-box;
}
.avatar-girl__pony--left {
  animation: avatar-pony-left 3s ease-in-out infinite;
}
.avatar-girl__pony--right {
  animation: avatar-pony-right 3s ease-in-out infinite;
}

/* 狐狸：耳朵动 + 呆毛摆 */
.avatar-fox__ear {
  transform-origin: bottom center;
  transform-box: fill-box;
}
.avatar-fox__ear--left {
  animation: avatar-ear 2.8s ease-in-out infinite;
}
.avatar-fox__ear--right {
  animation: avatar-ear 2.8s ease-in-out infinite reverse;
}
.avatar-fox__glasses {
  transform-origin: center;
  animation: avatar-glasses 4s ease-in-out infinite;
}

/* 猫：耳朵微动 */
.avatar-cat {
  animation: avatar-cat 2.6s ease-in-out infinite;
  transform-origin: 24px 40px;
}

/* 精灵：长耳轻摆 + 头发动 + 星闪 */
.avatar-elf__ear {
  transform-origin: center;
  transform-box: fill-box;
}
.avatar-elf__ear--left {
  animation: avatar-elf-ear-l 3s ease-in-out infinite;
}
.avatar-elf__ear--right {
  animation: avatar-elf-ear-r 3s ease-in-out infinite;
}
.avatar-elf__hair {
  transform-origin: 24px 22px;
  animation: avatar-elf-hair 4s ease-in-out infinite;
}
.assistant-fab__twinkle-star {
  transform-origin: 24px 5px;
  animation: avatar-star 1.6s ease-in-out infinite;
}

/* ===== Keyframes ===== */
@keyframes avatar-face {
  0%, 100% { transform: translateY(0) scale(1); }
  50% { transform: translateY(-0.5px) scale(1.02); }
}
@keyframes avatar-blush {
  0%, 100% { opacity: 0.35; }
  50% { opacity: 0.75; }
}
@keyframes avatar-blink {
  0%, 38%, 46%, 100% { transform: scaleY(1); }
  42% { transform: scaleY(0.1); }
}
@keyframes avatar-twinkle {
  0%, 100% { opacity: 0.5; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.4); }
}
@keyframes avatar-mouth {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.1, 1.05); }
}
@keyframes avatar-signal {
  0%, 100% { transform: translateY(0); opacity: 0.6; }
  50% { transform: translateY(-1.5px); opacity: 1; }
}
@keyframes avatar-pony-left {
  0%, 100% { transform: rotate(-2deg); }
  50% { transform: rotate(2deg); }
}
@keyframes avatar-pony-right {
  0%, 100% { transform: rotate(2deg); }
  50% { transform: rotate(-2deg); }
}
@keyframes avatar-ear {
  0%, 100% { transform: rotate(0deg); }
  50% { transform: rotate(4deg); }
}
@keyframes avatar-glasses {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-0.3px); }
}
@keyframes avatar-cat {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.02); }
}
@keyframes avatar-elf-ear-l {
  0%, 100% { transform: rotate(0deg); }
  50% { transform: rotate(-3deg); }
}
@keyframes avatar-elf-ear-r {
  0%, 100% { transform: rotate(0deg); }
  50% { transform: rotate(3deg); }
}
@keyframes avatar-elf-hair {
  0%, 100% { transform: rotate(0deg); }
  50% { transform: rotate(2deg); }
}
@keyframes avatar-star {
  0%, 100% { opacity: 0.5; transform: scale(0.9) rotate(0deg); }
  50% { opacity: 1; transform: scale(1.2) rotate(45deg); }
}
</style>