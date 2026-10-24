import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import { scanText, renderFailure } from '../tools/wb-dev-address-guard.mjs'

// 生产产物「dev 地址」防泄漏守卫 —— 与 demand_frontend/vite.config.ts 里同名插件共用
// ../tools/wb-dev-address-guard.mjs 的实现（2026-10-12 线上事故的产物，背景见该文件顶部注释）。
//
// ⚠️ H5 没配 manualChunks → 第三方库和业务代码全打进同一个 index-*.js，
//    因此插件会自动降到 strict 级别（只抓带端口的回环地址 + 指向 dev/私网的绝对 URL），
//    这样 axios 里那句 `window.location.href || "http://localhost"`（不带端口）不会误报，
//    而 `ws://localhost:8081` 这种真实故障一定能抓到。
function guardNoDevAddress() {
  return {
    name: 'guard-no-dev-address',
    apply: 'build' as const,
    enforce: 'post' as const,
    generateBundle(_options: unknown, bundle: Record<string, any>) {
      const findings: { fileName: string; level: 'strict' | 'broad'; code: string; hits: any[] }[] = []

      for (const [fileName, item] of Object.entries(bundle)) {
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
        if (businessCount === 0) continue
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

function printBuildEnv() {
  return {
    name: 'print-build-env',
    apply: 'build' as const,
    configResolved(config: { mode: string; env: Record<string, unknown>; base?: string }) {
      const keys = ['VITE_API_BASE_URL', 'VITE_APP_TITLE']
      const rows = keys.map((k) => `    ${k} = ${JSON.stringify(config.env[k] ?? '(未设置)')}`)
      // base 一并打印：H5 必须是 /h5/，否则产物引用 /assets/... → 白屏（见 defineConfig 处注释）
      console.log(`\n[build-env] mode=${config.mode}  base=${config.base ?? '(未设置)'}`)
      console.log(rows.join('\n') + '\n')
    },
  }
}

// https://vitejs.dev/config/
export default defineConfig(({ command }) => ({
  // ⚠️ H5 部署在内网 nginx 的**子路径** `/h5/` 下（见 dg.gov.demand.cn.conf 的 location /h5/），
  //    生产构建必须带 base，否则 index.html 会生成 `/assets/...` **绝对路径** ——
  //    浏览器会去**根路径**取资源（那里是 PC 端的 assets），拿回一段 HTML 当 JS 执行 → 整个 H5 白屏。
  //    2026-10-12 踩过：直接跑 `npm run build`（脚本里没有 --base）导致 H5 白屏，
  //    而 vite 不报任何错、check-dist 也查不出来。故固化在配置里，不再依赖命令行参数。
  base: command === 'build' ? '/h5/' : '/',
  plugins: [vue(), guardNoDevAddress(), printBuildEnv()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5175,
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY_TARGET || 'http://localhost:8081',
        changeOrigin: true,
      },
      '/ws': {
        target: process.env.VITE_API_PROXY_TARGET || 'http://localhost:8081',
        changeOrigin: true,
        ws: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1500,
  },
}))
