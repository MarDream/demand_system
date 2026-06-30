import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getColumnConfig, saveColumnConfig } from '@/api/modules/columnConfig'

/**
 * 单个可配置列定义。
 *
 * - key: 列唯一标识（后端持久化按 key 存储）
 * - label: 备选/已选区显示的中文名
 * - group: 可选分组名（"基础字段"、"状态信息"等），用于备选区分组
 * - prop: 透传给 el-table-column 的 prop（缺省回退到 key）
 * - width / minWidth / align / fixed / showOverflowTooltip: 透传给 el-table-column
 * - disabled: 必选列（如 "操作"），不允许在备选区勾掉
 */
export interface ColumnDef {
  key: string
  label: string
  group?: string
  prop?: string
  width?: number
  minWidth?: number
  align?: 'left' | 'center' | 'right'
  fixed?: string | boolean
  showOverflowTooltip?: boolean
  disabled?: boolean
  sortable?: boolean
}

export interface UseColumnConfigOptions {
  /** 后端 pageKey，命名空间 */
  pageKey: string
  /** 全部可用列定义（必含 "操作" 列并把 key 设为 'operations'） */
  columns: ColumnDef[]
  /** 默认勾选列 keys（未登录/未保存时使用） */
  defaultKeys: string[]
  /** 必选列的 keys，即使未勾选也要永远出现（如 "operations"） */
  requiredKeys?: string[]
}

export function useColumnConfig(options: UseColumnConfigOptions) {
  const { pageKey, columns, defaultKeys, requiredKeys = ['operations'] } = options

  const requiredSet = new Set(requiredKeys)

  const selectedColumnKeys = ref<string[]>([...new Set([...defaultKeys, ...requiredKeys])])
  const draftColumnKeys = ref<string[]>([])
  const showColumnConfig = ref(false)

  /** 可在备选区勾选的列（disabled / 必选列不参与） */
  const configurableColumns = computed(() =>
    columns.filter((c) => !c.disabled && !requiredSet.has(c.key))
  )

  /** 备选区分组渲染用（按出现顺序） */
  const columnGroups = computed(() => {
    const grouped = new Map<string, ColumnDef[]>()
    const order: string[] = []
    configurableColumns.value.forEach((column) => {
      const groupName = column.group || '其他字段'
      if (!grouped.has(groupName)) {
        grouped.set(groupName, [])
        order.push(groupName)
      }
      grouped.get(groupName)!.push(column)
    })
    return order.map((name) => ({ title: name, columns: grouped.get(name)! }))
  })

  /** 当前弹窗中按 draftColumnKeys 顺序展示的列 */
  const draftSelectedColumns = computed(() => {
    const map = new Map(configurableColumns.value.map((c) => [c.key, c]))
    return draftColumnKeys.value
      .map((k) => map.get(k))
      .filter((c): c is ColumnDef => Boolean(c))
  })

  /** 当前已保存的、用于 el-table 渲染的列（必选列永远存在） */
  const visibleColumns = computed(() => {
    const map = new Map(columns.map((c) => [c.key, c]))
    const cols = selectedColumnKeys.value
      .map((k) => map.get(k))
      .filter((c): c is ColumnDef => Boolean(c))
    // 补齐必选列
    for (const required of requiredKeys) {
      if (!cols.find((c) => c.key === required)) {
        const col = columns.find((c) => c.key === required)
        if (col) cols.push(col)
      }
    }
    return cols
  })

  function normalizeColumnKeys(keys: string[]): string[] {
    const allowed = new Set(configurableColumns.value.map((c) => c.key))
    return Array.from(new Set(keys.filter((k) => allowed.has(k))))
  }

  function openColumnConfig() {
    draftColumnKeys.value = selectedColumnKeys.value
      .filter((k) => !requiredSet.has(k))
      .filter((k) => configurableColumns.value.some((c) => c.key === k))
    showColumnConfig.value = true
  }

  function removeDraftColumn(key: string) {
    draftColumnKeys.value = draftColumnKeys.value.filter((k) => k !== key)
  }

  /** 页面挂载时调用：拉取后端保存的列配置并覆盖默认值 */
  async function loadColumnConfig() {
    try {
      const res = await getColumnConfig(pageKey)
      if (res && Array.isArray(res) && res.length > 0) {
        const valid = normalizeColumnKeys(res)
        if (valid.length > 0) {
          selectedColumnKeys.value = [...valid, ...requiredKeys]
        }
      }
    } catch {
      // 静默回退到默认
    }
  }

  async function saveColumns() {
    try {
      const keys = normalizeColumnKeys(draftColumnKeys.value)
      await saveColumnConfig(pageKey, keys)
      selectedColumnKeys.value = [...keys, ...requiredKeys]
      ElMessage.success('列配置已保存')
      showColumnConfig.value = false
      return true
    } catch {
      ElMessage.error('保存列配置失败')
      return false
    }
  }

  return {
    // state
    selectedColumnKeys,
    draftColumnKeys,
    showColumnConfig,
    // computed
    columnGroups,
    draftSelectedColumns,
    visibleColumns,
    configurableColumns,
    // actions
    openColumnConfig,
    removeDraftColumn,
    loadColumnConfig,
    saveColumns,
    // helpers
    normalizeColumnKeys,
  }
}
