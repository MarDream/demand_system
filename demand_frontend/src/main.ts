import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import App from './App.vue'
import router from './router'
import '@/styles/global.scss'
import '@/styles/column-config.scss'
import 'remixicon/fonts/remixicon.css'
import { permission } from '@/directives/permission'
import { setupDialogEnhancer } from '@/utils/dialogEnhancer'

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
