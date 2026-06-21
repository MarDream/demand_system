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

// 过滤浏览器扩展引起的"Could not establish connection. Receiving end does not exist."
// 这些错误来自 chrome.runtime.sendMessage（Vue Devtools / Pinia 等扩展），
// 与应用代码无关，控制台高频输出影响排查体验。
const EXTENSION_ERROR_PATTERNS = [
  /Could not establish connection\. Receiving end does not exist/i,
  /Extension context invalidated/i,
  /A listener indicated an asynchronous response by returning true/i,
]

function isIgnorableExtensionError(reason: unknown): boolean {
  if (!reason) return false
  // Error 对象：检查 message
  if (reason instanceof Error) {
    return EXTENSION_ERROR_PATTERNS.some(p => p.test(reason.message))
  }
  // 字符串：直接匹配
  if (typeof reason === 'string') {
    return EXTENSION_ERROR_PATTERNS.some(p => p.test(reason))
  }
  // 其他对象：尝试取 message 属性
  const message = (reason as { message?: string })?.message
  if (message) {
    return EXTENSION_ERROR_PATTERNS.some(p => p.test(message))
  }
  // ErrorEvent：检查 message
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
    event.preventDefault()
  }
})

window.addEventListener('unhandledrejection', (event) => {
  if (isIgnorableExtensionError(event.reason)) {
    event.preventDefault()
  }
})

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.directive('permission', permission)
app.mount('#app')
setupDialogEnhancer()
