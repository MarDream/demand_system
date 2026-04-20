export interface SelectOption {
  label: string
  value: string | number
}

export interface TreeNode<T = unknown> {
  id: number
  label?: string
  children?: TreeNode<T>[]
  [key: string]: unknown
}

export type StatusType = 'success' | 'warning' | 'danger' | 'info' | 'primary'
