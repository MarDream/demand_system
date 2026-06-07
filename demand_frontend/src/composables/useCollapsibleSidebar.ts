import { computed, onBeforeUnmount, reactive, ref } from 'vue'

export interface CollapsibleSidebarHandle {
  collapsed: boolean
  styleVars: Record<string, string>
  visibleWidth: number
  startResize: (event: MouseEvent) => void
  setWidth: (nextWidth: number) => void
  collapse: () => void
  expand: () => void
  toggle: () => void
}

interface UseCollapsibleSidebarOptions {
  defaultWidth: number
  widthVar: string
  minWidth?: number
  maxWidth?: number
  resizerWidth?: number
  resizerWidthVar?: string
}

export function useCollapsibleSidebar(options: UseCollapsibleSidebarOptions): CollapsibleSidebarHandle {
  const minWidth = options.minWidth ?? options.defaultWidth
  const maxWidth = options.maxWidth ?? options.defaultWidth
  const resizerWidth = options.resizerWidth ?? 0

  const width = ref(clampWidth(options.defaultWidth))
  const lastExpandedWidth = ref(width.value)
  const collapsed = ref(false)

  let detachResizeListeners: (() => void) | null = null

  const visibleWidth = computed(() => (collapsed.value ? 0 : width.value))
  const visibleResizerWidth = computed(() => {
    return collapsed.value || resizerWidth <= 0 ? 0 : resizerWidth
  })

  const styleVars = computed<Record<string, string>>(() => {
    const vars: Record<string, string> = {
      [options.widthVar]: `${visibleWidth.value}px`,
    }

    if (options.resizerWidthVar) {
      vars[options.resizerWidthVar] = `${visibleResizerWidth.value}px`
    }

    return vars
  })

  function clampWidth(nextWidth: number) {
    return Math.min(Math.max(nextWidth, minWidth), maxWidth)
  }

  function setWidth(nextWidth: number) {
    const normalizedWidth = clampWidth(nextWidth)
    width.value = normalizedWidth
    lastExpandedWidth.value = normalizedWidth
  }

  function collapse() {
    clearResizeListeners()
    collapsed.value = true
  }

  function expand() {
    clearResizeListeners()
    collapsed.value = false
    width.value = lastExpandedWidth.value
  }

  function toggle() {
    if (collapsed.value) {
      expand()
      return
    }

    collapse()
  }

  function clearResizeListeners() {
    if (!detachResizeListeners) return
    detachResizeListeners()
    detachResizeListeners = null
  }

  function startResize(event: MouseEvent) {
    if (collapsed.value || resizerWidth <= 0) return

    event.preventDefault()
    clearResizeListeners()

    const startX = event.clientX
    const startWidth = width.value

    const onMouseMove = (moveEvent: MouseEvent) => {
      const delta = moveEvent.clientX - startX
      setWidth(startWidth + delta)
    }

    const onMouseUp = () => {
      clearResizeListeners()
    }

    document.addEventListener('mousemove', onMouseMove)
    document.addEventListener('mouseup', onMouseUp)

    detachResizeListeners = () => {
      document.removeEventListener('mousemove', onMouseMove)
      document.removeEventListener('mouseup', onMouseUp)
    }
  }

  onBeforeUnmount(() => {
    clearResizeListeners()
  })

  // 使用 reactive 包装返回对象，让嵌套的 ref / computed 在模板中自动 unwrap，
  // 避免 `:class="{ 'is-collapsed': roleSidebar.collapsed }"` 因 ref 对象本身 truthy 而误判。
  // 模板里访问 `.collapsed` / `.styleVars` 时，reactive 会返回 ref/computed 当前的值（与 .value 等价），
  // 因此调用方在模板中可以直接使用 `sidebar.collapsed`、`sidebar.styleVars`，
  // 在 script 中也直接使用 `sidebar.collapsed`、`sidebar.styleVars`（无需 .value）。
  return reactive({
    collapsed,
    styleVars,
    visibleWidth,
    startResize,
    setWidth,
    collapse,
    expand,
    toggle,
  }) as unknown as CollapsibleSidebarHandle
}
