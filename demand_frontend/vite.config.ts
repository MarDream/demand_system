import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { resolve } from 'path'
import { transformWithEsbuild } from 'vite'

const apiProxyTarget = process.env.VITE_API_PROXY_TARGET || 'http://localhost:8081'

// 后端不可用时的代理错误治理：把 ECONNREFUSED 三行堆栈风暴收敛为限频一行提示，
// 并向浏览器返回规范 503 JSON（request.ts 拦截器按"网络异常"分支处理，不弹全局 toast）。
let lastProxyErrorLogAt = 0
function handleProxyError(err: Error, req: { url?: string }, res?: { writeHead?: Function; end?: Function; headersSent?: boolean }) {
  if (Date.now() - lastProxyErrorLogAt > 10_000) {
    lastProxyErrorLogAt = Date.now()
    console.warn(`[api-proxy] 后端 ${apiProxyTarget} 不可用（${err.code || err.message}），请求返回 503：${req?.url || ''}`)
  }
  if (res && typeof res.writeHead === 'function' && !res.headersSent) {
    res.writeHead(503, { 'Content-Type': 'application/json; charset=utf-8' })
    res.end(JSON.stringify({ code: 503, message: '后端服务不可用（未启动或重启中）', data: null }))
  }
}

// C1: 生产构建剥离 console / debugger 的 Vite 插件
// 通过 transformWithEsbuild 在 build 阶段对每个业务模块显式 drop，避免 console.error 随产物上线
function stripConsoleInProd() {
  return {
    name: 'strip-console-in-prod',
    apply: 'build' as const,
    enforce: 'post' as const,
    async transform(code: string, id: string) {
      if (process.env.NODE_ENV !== 'production') return null
      // 跳过第三方库（node_modules），只处理业务源码
      if (id.includes('node_modules')) return null
      // 跳过无 console 的模块，减少无谓转换
      if (!code.includes('console.') && !code.includes('debugger')) return null
      const result = await transformWithEsbuild(code, id, {
        drop: ['console', 'debugger'],
      })
      return { code: result.code, map: result.map }
    },
  }
}

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia'],
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [ElementPlusResolver()],
    }),
    // C1: 生产构建剥离 console.* / debugger（零依赖，显式 drop，比 build.esbuild.drop 更可靠）
    // 仅作用于业务源码，跳过 node_modules 第三方库以免破坏其运行逻辑
    stripConsoleInProd(),
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    host: '127.0.0.1',
    port: 5170,
    proxy: {
      '/api': {
        target: apiProxyTarget,
        changeOrigin: true,
        configure(proxy) {
          proxy.on('error', handleProxyError)
        },
      },
      '/ws': {
        target: apiProxyTarget,
        ws: true,
        changeOrigin: true,
        configure(proxy) {
          proxy.on('error', handleProxyError)
        },
      },
    },
  },
  build: {
    chunkSizeWarningLimit: 1200,
    // C1: 生产构建剥离 console.* / debugger（仅 prod 生效，dev 不受影响）
    // 避免 25 处 console.error 随产物上线刷屏控制台或泄露内部信息
    esbuild: {
      drop: ['console', 'debugger'],
    },
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined
          if (id.includes('node_modules/vue') || id.includes('node_modules/pinia') || id.includes('node_modules/vue-router')) {
            return 'vendor-vue'
          }
          if (id.includes('node_modules/element-plus') || id.includes('node_modules/@element-plus')) {
            return 'vendor-element'
          }
          if (id.includes('node_modules/@isle-editor') || id.includes('node_modules/@tiptap')) {
            return 'vendor-editor'
          }
          if (id.includes('node_modules/@logicflow')) {
            return 'vendor-workflow'
          }
          if (id.includes('node_modules/vxe-table') || id.includes('node_modules/vxe-pc-ui') || id.includes('node_modules/echarts')) {
            return 'vendor-data'
          }
          if (id.includes('node_modules/xlsx')) {
            return 'vendor-xlsx'
          }
          return 'vendor'
        },
      },
    },
  },
  css: {
    preprocessorOptions: {
      scss: {
        api: 'modern-compiler',
        additionalData: `@use "@/styles/variables.scss" as *;`,
      },
    },
  },
})
