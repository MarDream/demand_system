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
function isIgnorableExtensionError(message: string | undefined): boolean {
  if (!message) return false
  return /Could not establish connection\. Receiving end does not exist/i.test(message)
}

window.addEventListener('error', (event) => {
  if (isIgnorableExtensionError(event.message)) {
    event.preventDefault()
  }
})

window.addEventListener('unhandledrejection', (event) => {
  const reason = event.reason as { message?: string } | string | undefined
  const message = typeof reason === 'string'
    ? reason
    : reason?.message
  if (isIgnorableExtensionError(message)) {
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
