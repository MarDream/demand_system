import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import VxeUIAll from 'vxe-pc-ui'
import 'vxe-pc-ui/lib/style.css'
import VxeUITable from 'vxe-table'
import 'vxe-table/lib/style.css'
import App from './App.vue'
import router from './router'
import '@/styles/global.scss'
import '@/styles/column-config.scss'
import 'remixicon/fonts/remixicon.css'
import { permission } from '@/directives/permission'
import { setupDialogEnhancer } from '@/utils/dialogEnhancer'
import { ElMessage } from 'element-plus'
// 注册多维表格自定义单元格渲染器（进度条 / 彩色标签 / 复选框 / 评分星级）
import '@/utils/bitableCellRenderers'

// ECharts 按需引入 graphic 组件（修复 [ECharts] Component graphic is used but not imported 报错）
import * as echarts from 'echarts/core'
import { GraphicComponent } from 'echarts/components'
import { PieChart, BarChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { LabelLayout, UniversalTransition } from 'echarts/features'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  GraphicComponent,
  PieChart,
  BarChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
  LabelLayout,
  UniversalTransition,
  CanvasRenderer,
])

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
app.use(VxeUIAll)
app.use(VxeUITable)
app.directive('permission', permission)

// C3: 全局错误边界 - 兜底渲染期未捕获异常，避免整页白屏
// 注意：生产构建已通过 vite esbuild.drop 剥离 console，此处 console.error 仅在 dev 可见；
// 用户侧反馈依赖 ElMessage（不被剥离）。节流 3s 避免同一异常连续刷屏。
let lastErrorToastAt = 0
app.config.errorHandler = (err, _instance, info) => {
  console.error('[GlobalErrorHandler]', err, info)
  const now = Date.now()
  if (now - lastErrorToastAt > 3000) {
    lastErrorToastAt = now
    ElMessage.error('页面发生异常，请刷新后重试')
  }
}

app.mount('#app')
setupDialogEnhancer()
