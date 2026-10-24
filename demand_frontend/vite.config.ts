import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { resolve } from 'path'
import { transformWithEsbuild } from 'vite'
import { scanText, renderFailure } from '../tools/wb-dev-address-guard.mjs'

const apiProxyTarget = process.env.VITE_API_PROXY_TARGET || 'http://localhost:8081'
const kkfileviewProxyTarget = process.env.VITE_KKFILEVIEW_PROXY_TARGET || 'http://localhost:8012'

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

// C2: 生产产物「dev 地址」防泄漏守卫（构建期拦截，权威）
//
// 背景：2026-10-12 线上事故 —— useBitableWebSocket.ts 里 `|| 'ws://localhost:8081'`
//   的 dev 兜底值被原样打进生产产物，用户浏览器去连自己的机器 → ERR_CONNECTION_REFUSED。
//   详见 ../tools/wb-dev-address-guard.mjs 顶部注释。
//
// 判定级别按「这段代码是谁写的」来选：
//   · 纯业务 chunk（不含任何 node_modules 模块）→ broad（连裸的 localhost 字面量也抓）
//   · 第三方与业务混装的 chunk                    → strict（只抓带端口的地址，避免误报）
//   · 纯第三方库 chunk                            → 跳过
//     ⚠️ 不能全量 grep：vendor-CTXUQT0Q.js 自带 3 处 localhost（正则源码、axios 占位、
//        词法分析器关键字），全量扫必然误报。
// 逃生阀：ALLOW_DEV_ADDR=1 npm run build（仅限本地验证，勿用于部署）。
function guardNoDevAddress() {
  return {
    name: 'guard-no-dev-address',
    apply: 'build' as const,
    enforce: 'post' as const,
    generateBundle(_options: unknown, bundle: Record<string, any>) {
      const findings: { fileName: string; level: 'strict' | 'broad'; code: string; hits: any[] }[] = []

      for (const [fileName, item] of Object.entries(bundle)) {
        // index.html 完全由我们自己写 → 用 broad
        if (item.type === 'asset') {
          if (!fileName.endsWith('.html')) continue
          const code = String(item.source)
          const hits = scanText(code, 'broad')
          if (hits.length) findings.push({ fileName, level: 'broad', code, hits })
          continue
        }
        if (item.type !== 'chunk') continue

        const moduleIds: string[] = Object.keys(item.modules || {})
        if (!moduleIds.length) continue
        const businessCount = moduleIds.filter((id) => !id.includes('node_modules')).length
        if (businessCount === 0) continue // 纯第三方库 chunk → 跳过
        const level: 'strict' | 'broad' = businessCount === moduleIds.length ? 'broad' : 'strict'

        const hits = scanText(item.code, level)
        if (hits.length) findings.push({ fileName, level, code: item.code, hits })
      }

      if (!findings.length) return
      const message = renderFailure(findings as any)
      if (process.env.ALLOW_DEV_ADDR === '1') {
        console.warn(`\n[guard-no-dev-address] ⚠️ ALLOW_DEV_ADDR=1，已放行以下问题：\n\n${message}\n`)
        return
      }
      throw new Error(message)
    },
  }
}

// 构建前把真正生效的前端环境变量打出来 —— 「部署后才发现配错」多半是因为没人看见实际取值
function printBuildEnv() {
  return {
    name: 'print-build-env',
    apply: 'build' as const,
    configResolved(config: { mode: string; env: Record<string, unknown> }) {
      const keys = ['VITE_API_BASE_URL', 'VITE_WS_URL', 'VITE_APP_TITLE']
      const rows = keys.map((k) => `    ${k} = ${JSON.stringify(config.env[k] ?? '(未设置)')}`)
      console.log(`\n[build-env] mode=${config.mode}（未设置的变量会用代码里的兜底值，务必确认兜底值不是 localhost）`)
      console.log(rows.join('\n') + '\n')
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
    // C2: 生产产物 dev 地址防泄漏守卫（命中即构建失败）
    guardNoDevAddress(),
    printBuildEnv(),
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
      // 文件预览：后端返回的是**同源相对路径** `/kkfileview/onlinePreview?...`（iframe src），
      // dev 下由这里转给本机 kkFileView；生产由 nginx 的 `location /kkfileview/` 承担。
      // ⚠️ 必须 strip 前缀 —— kkFileView 的页面用相对路径引用静态资源，
      //    不剥前缀的话 /kkfileview/css/... 会被当成 kkFileView 自己的 /kkfileview/css/...。
      '/kkfileview': {
        target: kkfileviewProxyTarget,
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/kkfileview/, ''),
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
