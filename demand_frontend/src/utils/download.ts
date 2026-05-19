const MIME_TYPE_MAP: Record<string, string> = {
  txt: 'text/plain;charset=utf-8',
  md: 'text/markdown;charset=utf-8',
  csv: 'text/csv;charset=utf-8',
  json: 'application/json;charset=utf-8',
  xml: 'application/xml;charset=utf-8',
  yml: 'text/yaml;charset=utf-8',
  yaml: 'text/yaml;charset=utf-8',
  pdf: 'application/pdf',
  doc: 'application/msword',
  docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  xls: 'application/vnd.ms-excel',
  xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  ppt: 'application/vnd.ms-powerpoint',
  pptx: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
  png: 'image/png',
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  gif: 'image/gif',
  bmp: 'image/bmp',
  webp: 'image/webp',
  svg: 'image/svg+xml',
  zip: 'application/zip',
  rar: 'application/vnd.rar',
  '7z': 'application/x-7z-compressed',
}

interface SaveFilePickerAcceptType {
  description?: string
  accept: Record<string, string[]>
}

interface SaveFilePickerOptions {
  suggestedName?: string
  startIn?: 'desktop' | 'documents' | 'downloads' | 'music' | 'pictures' | 'videos'
  types?: SaveFilePickerAcceptType[]
}

interface FileSystemWritableFileStreamLike {
  write(data: Blob): Promise<void>
  close(): Promise<void>
}

interface FileSystemFileHandleLike {
  createWritable(): Promise<FileSystemWritableFileStreamLike>
}

interface WindowWithPicker extends Window {
  showSaveFilePicker?: (options?: SaveFilePickerOptions) => Promise<FileSystemFileHandleLike>
}

function getExtension(fileName: string) {
  const segments = fileName.split('.')
  if (segments.length < 2) return ''
  return segments.at(-1)?.toLowerCase() || ''
}

function inferMimeType(fileName: string, mimeType?: string) {
  if (mimeType) return mimeType
  const extension = getExtension(fileName)
  return MIME_TYPE_MAP[extension] || 'application/octet-stream'
}

function buildPickerTypes(fileName: string, mimeType: string): SaveFilePickerAcceptType[] | undefined {
  const extension = getExtension(fileName)
  if (!extension) return undefined
  return [{
    description: `${extension.toUpperCase()} 文件`,
    accept: {
      [mimeType]: [`.${extension}`],
    },
  }]
}

function triggerAnchorDownload(blob: Blob, fileName: string) {
  const objectUrl = window.URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = objectUrl
  anchor.download = fileName
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  window.URL.revokeObjectURL(objectUrl)
}

function isUserAbort(error: unknown) {
  return error instanceof DOMException && error.name === 'AbortError'
}

async function saveWithPicker(blob: Blob, fileName: string, mimeType: string) {
  const pickerWindow = window as WindowWithPicker
  if (!pickerWindow.showSaveFilePicker) {
    return false
  }

  const handle = await pickerWindow.showSaveFilePicker({
    suggestedName: fileName,
    startIn: 'downloads',
    types: buildPickerTypes(fileName, mimeType),
  })
  const writable = await handle.createWritable()
  await writable.write(blob)
  await writable.close()
  return true
}

export async function saveBlob(blob: Blob, fileName: string, mimeType?: string) {
  const resolvedMimeType = inferMimeType(fileName, mimeType || blob.type)
  const resolvedBlob = blob.type === resolvedMimeType ? blob : new Blob([blob], { type: resolvedMimeType })

  try {
    const saved = await saveWithPicker(resolvedBlob, fileName, resolvedMimeType)
    if (saved) return true
  } catch (error) {
    if (isUserAbort(error)) {
      return false
    }
  }

  triggerAnchorDownload(resolvedBlob, fileName)
  return true
}

export async function downloadFromUrl(url: string, fileName: string, init?: RequestInit) {
  const response = await fetch(url, init)
  if (!response.ok) {
    throw new Error(`下载失败: ${response.status}`)
  }
  const blob = await response.blob()
  return saveBlob(blob, fileName)
}
