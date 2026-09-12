# demand_h5 — 需求管理系统 手机 H5 端

基于 **Vue 3 + Vite + Vant 4** 的移动端 Web 应用，复用后端 `demand_backend` 的 `/api/v1` 接口，
方便在手机浏览器中查看与处理需求。

## 功能范围

| 页面 | 路由 | 说明 |
|---|---|---|
| 登录 | `/login` | 账号密码登录，支持 token 无感刷新；无组织用户首次登录强制选择组织 |
| 需求列表 | `/tasks` | 四个标签：待我处理 / 我关注的 / 已办结 / 全部需求；关键字搜索、下拉刷新、上拉分页 |
| 需求详情 | `/requirement/:id` | 基本信息、需求描述、评论（查看+发表）、审批流转（候选人选择、必填意见校验）、流转记录；关注/取关 |
| AI 助手 | `/assistant` | 多会话管理（新建/切换/滑动删除）、SSE 流式对话、停止生成；复用后端 `/v1/assistant` 能力 |
| 通知中心 | `/notifications` | 列表、未读数、单条/全部已读，需求类通知点击直达需求详情 |
| 我的 | `/profile` | 个人信息展示、退出登录 |

> 多维表格、知识库、系统管理等重交互功能请使用 PC 端（`demand_frontend`）。

## 本地开发

前置：后端已运行在 `http://localhost:8081`（见根目录 `start-all.bat`）。

```bash
cd demand_h5
npm install
npm run dev     # http://localhost:5175 ，手机与电脑同一局域网时可用Network地址直接访问
```

构建产物：

```bash
npm run build   # 输出 dist/
npm run preview
```

## 接口与认证约定

- 请求前缀 `VITE_API_BASE_URL=/api`，Vite 代理 `/api`、`/ws` → `http://localhost:8081`（可用
  `VITE_API_PROXY_TARGET` 覆盖）。
- 统一返回 `Result<T>`（`code/message/data`），成功码 200；封装见 `src/api/request.ts`。
- 登录 token 存 `localStorage`（key：`h5_access_token` / `h5_refresh_token`），请求自动携带
  `Bearer`；HTTP 401 时自动用 `/v1/auth/refresh` 刷新并重放一次请求。

## 目录结构

```
demand_h5/
├── src/
│   ├── api/           # request 封装 + auth/requirement/notification/assistant 接口
│   ├── router/        # 路由与登录守卫
│   ├── stores/        # Pinia 用户信息
│   ├── utils/         # token 存取、状态/优先级/类型映射与时间格式化
│   ├── views/         # Login / tasks / requirement / assistant / notifications / profile
│   ├── App.vue        # 根组件 + 底部 TabBar（需求/助手/通知/我的）
│   └── main.ts        # 入口（Vant 全量引入）
├── vite.config.ts     # 端口 5175、/api 代理
└── index.html         # 移动端 viewport 配置
```
