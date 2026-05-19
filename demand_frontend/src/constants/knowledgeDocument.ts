const KKFILEVIEW_SUPPORTED_EXTENSIONS = [
  'docx', 'wps', 'doc', 'docm', 'xls', 'xlsx', 'csv', 'xlsm', 'ppt', 'pptx', 'vsd', 'rtf',
  'odt', 'wmf', 'emf', 'dps', 'et', 'ods', 'ots', 'tsv', 'odp', 'otp', 'sxi', 'ott',
  'vsdx', 'fodt', 'fods', 'xltx', 'tga', 'psd', 'dotm', 'ett', 'xlt', 'xltm', 'wpt',
  'dot', 'xlam', 'dotx', 'xla', 'pages', 'eps', 'pptm',
  'jpg', 'jpeg', 'png', 'gif', 'bmp', 'ico', 'jfif', 'webp', 'heic', 'avif', 'heif',
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

const IMAGE_PREVIEW_EXTENSIONS = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'ico', 'jfif', 'webp', 'svg']

const TEXT_PREVIEW_EXTENSIONS = [
  'txt', 'md', 'csv', 'tsv', 'json', 'xml', 'xbrl', 'log', 'yml', 'yaml', 'html', 'htm',
  'asp', 'jsp', 'properties', 'gitignore', 'java', 'py', 'python', 'c', 'cpp', 'sql',
  'sh', 'bat', 'm', 'bas', 'prg', 'cmd', 'php', 'go', 'js', 'ftl', 'css', 'lua',
  'rb', 'h', 'cs', 'aspx',
]

export const KKFILEVIEW_SUPPORTED_EXTENSION_SET = new Set(KKFILEVIEW_SUPPORTED_EXTENSIONS)
export const KKFILEVIEW_IMAGE_PREVIEW_SET = new Set(IMAGE_PREVIEW_EXTENSIONS)
export const KKFILEVIEW_TEXT_PREVIEW_SET = new Set(TEXT_PREVIEW_EXTENSIONS)
export const KKFILEVIEW_SUPPORTED_EXTENSION_COUNT = KKFILEVIEW_SUPPORTED_EXTENSION_SET.size

export function normalizeFileExtension(fileNameOrType: string | null | undefined): string {
  if (!fileNameOrType) return ''
  const value = fileNameOrType.trim().toLowerCase()
  const dotIndex = value.lastIndexOf('.')
  return dotIndex >= 0 ? value.slice(dotIndex + 1) : value
}
