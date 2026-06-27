export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

export interface PageQuery {
  pageNum: number
  pageSize: number
}

export interface PageResult<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
  /** 游标分页时返回下一页游标值，OFFSET 分页时为 null */
  nextCursor?: string
  /** 游标分页时指示是否还有更多数据，OFFSET 分页时为 null */
  hasMore?: boolean
}
