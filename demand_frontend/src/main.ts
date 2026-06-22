import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import App from './App.vue'
import router from './router'
import '@/styles/global.scss'
import 'remixicon/fonts/remixicon.css'
import { permission } from '@/directives/permission'
import { setupDialogEnhancer } from '@/utils/dialogEnhancer'

// 过滤浏览器扩展引起的错误（双保险：index.html 中的内联脚本已做第一层拦截）
// 这些错误来自 chrome.runtime.sendMessage（Vue Devtools / Pinia 等扩展），
// 与应用代码无关，控制台高频输出影响排查体验。
const EXTENSION_ERROR_PATTERNS = [
  /Could not establish connection\. Receiving end does not exist/i,
  /Extension context invalidated/i,
  /A listener indicated an asynchronous response by returning true/i,
]

function isIgnorableExtensionError(reason: unknown): boolean {
  if (!reason) return false
  if (reason instanceof Error) {
    return EXTENSION_ERROR_PATTERNS.some(p => p.test(reason.message))
  }
  if (typeof reason === 'string') {
    return EXTENSION_ERROR_PATTERNS.some(p => p.test(reason))
  }
  const message = (reason as { message?: string })?.message
  if (message) {
    return EXTENSION_ERROR_PATTERNS.some(p => p.test(message))
  }
  if (reason instanceof Event) {
    const eventMessage = (reason as ErrorEvent).message
    if (eventMessage) {
      return EXTENSION_ERROR_PATTERNS.some(p => p.test(eventMessage))
    }
  }
  return false
}

window.addEventListener('error', (event) => {
  if (isIgnorableExtensionError(event) || isIgnorableExtensionError(event.message)) {
    event.stopImmediatePropagation()
    event.preventDefault()
  }
}, true)

window.addEventListener('unhandledrejection', (event) => {
  if (isIgnorableExtensionError(event.reason)) {
    event.stopImmediatePropagation()
    event.preventDefault()
  }
}, true)

// 修复 zrender (ECharts 底层) 对 wheel/mousewheel 事件未标记 passive 的 Chrome Violation 警告
// zrender 5.x 内部注册 scroll-blocking 事件时未传 { passive: true }，属于上游已知问题
// 此补丁仅对 wheel/mousewheel/touchstart/touchmove 自动注入 passive，不影响其他事件
const PASSIVE_EVENTS = new Set(['wheel', 'mousewheel', 'touchstart', 'touchmove'])
const originalAddEventListener = EventTarget.prototype.addEventListener
EventTarget.prototype.addEventListener = function (
  type: string,
  listener: EventListenerOrEventListenerObject | null,
  options?: boolean | AddEventListenerOptions,
) {
  if (PASSIVE_EVENTS.has(type) && (typeof options === 'undefined' || options === false)) {
    return originalAddEventListener.call(this, type, listener, { passive: true })
  }
  return originalAddEventListener.call(this, type, listener, options)
}

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.directive('permission', permission)
app.mount('#app')
setupDialogEnhancer()
