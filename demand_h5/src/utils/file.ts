/** 在线预览支持的文件类型集合（与 PC 端 constants/knowledgeDocument.ts 同口径） */
const OFFICE_PREVIEW_EXTENSIONS = [
  'docx', 'wps', 'doc', 'docm', 'xls', 'xlsx', 'csv', 'xlsm', 'ppt', 'pptx', 'vsd', 'rtf',
  'odt', 'wmf', 'emf', 'dps', 'et', 'ods', 'ots', 'tsv', 'odp', 'otp', 'sxi', 'ott',
  'vsdx', 'fodt', 'fods', 'xltx', 'tga', 'psd', 'dotm', 'ett', 'xlt', 'xltm', 'wpt',
  'dot', 'xlam', 'dotx', 'xla', 'pages', 'eps', 'pptm',
  'rar', 'zip', 'jar', '7-zip', 'tar', 'gzip', '7z',
  'obj', '3ds', 'stl', 'ply', 'off', '3dm', 'fbx', 'dae', 'wrl', '3mf', 'ifc', 'glb',
  'o3dv', 'gltf', 'stp', 'bim', 'fcstd', 'step', 'iges', 'brep',
  'eml', 'msg', 'xmind', 'epub', 'dcm', 'drawio',
  'xml', 'xbrl', 'json', 'tif', 'tiff', 'ofd', 'svg',
  'dwg', 'dxf', 'dwf', 'igs', 'dwt', 'dng', 'dwfx', 'cf2', 'plt',
  'txt', 'html', 'htm', 'asp', 'jsp', 'properties', 'md', 'gitignore', 'log', 'java',
  'py', 'c', 'cpp', 'sql', 'sh', 'bat', 'm', 'bas', 'prg', 'cmd',
  'php', 'go', 'python', 'js', 'ftl', 'css', 'lua', 'rb', 'yaml', 'yml', 'h', 'cs',
  'aspx', 'pdf', 'bpmn', 'mp3', 'wav', 'mp4', 'flv', 'mpd', 'm3u8', 'ts', 'm4a',
  '3gp', 'avi', 'mkv', 'mov', 'mpeg', 'rm', 'wmv',
]

const IMAGE_EXTENSIONS = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'ico', 'jfif', 'webp', 'svg']

const TEXT_EXTENSIONS = [
  'txt', 'md', 'csv', 'tsv', 'json', 'xml', 'xbrl', 'log', 'yml', 'yaml', 'html', 'htm',
  'asp', 'jsp', 'properties', 'gitignore', 'java', 'py', 'python', 'c', 'cpp', 'sql',
  'sh', 'bat', 'm', 'bas', 'prg', 'cmd', 'php', 'go', 'js', 'ftl', 'css', 'lua',
  'rb', 'h', 'cs', 'aspx',
]

const OFFICE_EXTENSION_SET = new Set(OFFICE_PREVIEW_EXTENSIONS)
const IMAGE_EXTENSION_SET = new Set(IMAGE_EXTENSIONS)
const TEXT_EXTENSION_SET = new Set(TEXT_EXTENSIONS)

export type PreviewKind = 'image' | 'text' | 'office' | 'unsupported'

/** 取小写扩展名（不含点）；无扩展名时返回空串 */
export function normalizeFileExtension(fileNameOrType?: string | null): string {
  if (!fileNameOrType) return ''
  const name = fileNameOrType.trim().toLowerCase()
  // contentType 形态（image/png）取子类型
  const slash = name.lastIndexOf('/')
  const base = slash >= 0 ? name.slice(slash + 1) : name
  const dot = base.lastIndexOf('.')
  return dot >= 0 ? base.slice(dot + 1) : base
}

/**
 * 判定预览方式：
 * - image：图片，走 /files/{id}/preview 内联流，van-image-preview 展示
 * - text：文本/代码类，内联流取文本展示
 * - office：文档/压缩包/音视频等，走后端 kkFileView 链路
 * - unsupported：其余，仅提供下载
 */
export function previewKindOf(fileNameOrType?: string | null, contentType?: string | null): PreviewKind {
  const ext = normalizeFileExtension(fileNameOrType) || normalizeFileExtension(contentType)
  if (!ext) return 'unsupported'
  if (IMAGE_EXTENSION_SET.has(ext)) return 'image'
  if (TEXT_EXTENSION_SET.has(ext)) return 'text'
  if (OFFICE_EXTENSION_SET.has(ext)) return 'office'
  return 'unsupported'
}

/** 附件大小人类可读化（B / KB / MB / GB） */
export function formatFileSize(size?: number | null): string {
  if (size == null || Number.isNaN(size) || size < 0) return ''
  if (size < 1024) return `${size}B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)}K`
  if (size < 1024 * 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)}M`
  return `${(size / 1024 / 1024 / 1024).toFixed(1)}G`
}

/**
 * 浏览器保存 Blob（H5 端用 a[download]）。
 * 移动端部分浏览器不支持 download 属性，失败时退回新窗口打开。
 */
export function saveBlob(blob: Blob, fileName: string) {
  const objectUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = fileName || 'download'
  link.rel = 'noopener'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  // 延迟回收，确保 click 已生效
  setTimeout(() => URL.revokeObjectURL(objectUrl), 5000)
}
