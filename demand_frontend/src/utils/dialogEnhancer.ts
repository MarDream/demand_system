const enhancedDialogs = new WeakSet<HTMLElement>()

const dialogSelectors = ['.el-dialog', '.el-message-box']
const headerSelectors = ['.el-dialog__header', '.el-message-box__header']
const ignoredDragTargets = 'button, a, input, textarea, select, .el-dialog__headerbtn, .el-message-box__headerbtn'

export function setupDialogEnhancer() {
  if (typeof window === 'undefined') return

  enhanceExistingDialogs()

  // 使用 requestAnimationFrame 合并同一帧内的多次 DOM 变更通知，
  // 避免 Vue 初始化时大量 DOM 节点挂载导致回调频繁触发阻塞主线程
  let rafId = 0
  const observer = new MutationObserver(() => {
    if (rafId) return
    rafId = requestAnimationFrame(() => {
      rafId = 0
      enhanceExistingDialogs()
    })
  })

  observer.observe(document.body, {
    childList: true,
    subtree: true,
  })
}

function enhanceExistingDialogs() {
  document.querySelectorAll<HTMLElement>(dialogSelectors.join(',')).forEach(enhanceDialog)
}

function enhanceDialog(dialog: HTMLElement) {
  if (enhancedDialogs.has(dialog)) return

  const header = headerSelectors
    .map(selector => dialog.querySelector<HTMLElement>(selector))
    .find((item): item is HTMLElement => Boolean(item))

  if (!header) return

  enhancedDialogs.add(dialog)
  dialog.classList.add('app-draggable-dialog')
  header.classList.add('app-draggable-dialog__header')

  header.addEventListener('pointerdown', event => {
    if (event.button !== 0) return
    if ((event.target as HTMLElement | null)?.closest(ignoredDragTargets)) return

    beginDrag(dialog, event)
  })
}

function beginDrag(dialog: HTMLElement, event: PointerEvent) {
  const rect = dialog.getBoundingClientRect()
  const viewportWidth = window.innerWidth
  const viewportHeight = window.innerHeight
  const offsetX = event.clientX - rect.left
  const offsetY = event.clientY - rect.top

  dialog.style.position = 'fixed'
  dialog.style.left = `${rect.left}px`
  dialog.style.top = `${rect.top}px`
  dialog.style.width = `${rect.width}px`
  dialog.style.margin = '0'

  const moveDialog = (moveEvent: PointerEvent) => {
    const maxLeft = Math.max(0, viewportWidth - rect.width)
    const maxTop = Math.max(0, viewportHeight - rect.height)
    const nextLeft = clamp(moveEvent.clientX - offsetX, 0, maxLeft)
    const nextTop = clamp(moveEvent.clientY - offsetY, 0, maxTop)

    dialog.style.left = `${nextLeft}px`
    dialog.style.top = `${nextTop}px`
  }

  const stopDrag = () => {
    window.removeEventListener('pointermove', moveDialog)
    window.removeEventListener('pointerup', stopDrag)
    window.removeEventListener('pointercancel', stopDrag)
  }

  event.preventDefault()
  window.addEventListener('pointermove', moveDialog)
  window.addEventListener('pointerup', stopDrag)
  window.addEventListener('pointercancel', stopDrag)
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max)
}
