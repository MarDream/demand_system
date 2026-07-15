import request from '@/api/request'
import type { ApiResponse } from '@/types/api'

export interface BitableTemplateVO {
  code: string
  name: string
  description: string
  icon: string
  fieldCount: number
}

/**
 * 列出所有预设模板
 */
export function listTemplates() {
  return request.get<ApiResponse<BitableTemplateVO[]>>('/v1/bitable/templates') as unknown as Promise<BitableTemplateVO[]>
}

/**
 * 从模板创建 Base
 */
export function createFromTemplate(code: string) {
  return request.post<ApiResponse<number>>(`/v1/bitable/templates/${code}/create`) as unknown as Promise<number>
}

/**
 * 导出数据表为 Excel
 */
export function exportExcel(tableId: number) {
  return request.get(`/v1/bitable/tables/${tableId}/export/excel`, {
    responseType: 'blob',
  }) as unknown as Promise<Blob>
}

/**
 * 导出数据表为 CSV
 */
export function exportCsv(tableId: number) {
  return request.get(`/v1/bitable/tables/${tableId}/export/csv`, {
    responseType: 'blob',
  }) as unknown as Promise<Blob>
}

/**
 * 从 Excel 导入记录
 */
export function importExcel(tableId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<ApiResponse<number[]>>(
    `/v1/bitable/tables/${tableId}/import/excel`,
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } }
  ) as unknown as Promise<number[]>
}

/**
 * 从 CSV 导入记录
 */
export function importCsv(tableId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<ApiResponse<number[]>>(
    `/v1/bitable/tables/${tableId}/import/csv`,
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } }
  ) as unknown as Promise<number[]>
}
