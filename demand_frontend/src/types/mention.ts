/** 评论 @ 提及相关类型定义 */

/** @ 候选用户（来自系统活跃用户列表） */
export interface MentionCandidate {
  id: number
  username: string
  realName: string
  avatar?: string | null
}

/** 选中某位用户时的回调载荷 */
export interface MentionSelectPayload {
  id: number
  label: string
}

/** 插入 mention 节点的命令（由 tiptap Suggestion 提供） */
export type MentionCommand = (payload: MentionSelectPayload) => void
