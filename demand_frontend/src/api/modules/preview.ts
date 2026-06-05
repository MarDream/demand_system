import request from '@/api/request'

/**
 * 调用后端统一预览接口，生成外部文件预览服务的访问地址。
 *
 * <p>前端不感知底层预览服务实现（如 kkFileView、OnlyOffice 等），
 * 由后端 {@code /api/v1/preview/office} 负责协议转换与参数封装。</p>
 *
 * @param fileUrl 原始文件可访问 URL（http/https）
 * @param watermarkTxt 可选水印文本
 * @returns 包含 {@code previewUrl} 的对象
 */
export function getOfficePreviewUrl(fileUrl: string, watermarkTxt?: string) {
  return request.get<{ previewUrl: string }>('/v1/preview/office', {
    params: { fileUrl, watermarkTxt },
  })
}
