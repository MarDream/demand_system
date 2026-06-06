import { computed, onBeforeUnmount, ref } from 'vue'

interface UseCollapsibleSidebarOptions {
  defaultWidth: number
  widthVar: string
  minWidth?: number
  maxWidth?: number
  resizerWidth?: number
  resizerWidthVar?: string
}

export function useCollapsibleSidebar(options: UseCollapsibleSidebarOptions) {
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

  return {
    collapsed,
    styleVars,
    visibleWidth,
    startResize,
    setWidth,
    collapse,
    expand,
    toggle,
  }
}
