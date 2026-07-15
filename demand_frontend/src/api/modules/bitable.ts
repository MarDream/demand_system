import request from '@/api/request'
import type { ApiResponse, PageResult } from '@/types/api'
import type {
  BitableBase,
  BitableBaseCreateDTO,
  BitableBaseMember,
  BitableComment,
  BitableField,
  BitableFieldCreateDTO,
  BitableOperation,
  BitableRecord,
  BitableRecordCreateDTO,
  BitableTable,
  BitableTableCreateDTO,
  BitableView,
  BitableViewCreateDTO,
  CellUpdateDTO,
  CellValue,
  MemberRole,
} from '@/types/bitable'

// Base
export function listBases() {
  return request.get<ApiResponse<BitableBase[]>>(`/v1/bitable/bases`) as unknown as Promise<BitableBase[]>
}

export function getBase(id: number) {
  return request.get<ApiResponse<BitableBase>>(`/v1/bitable/bases/${id}`) as unknown as Promise<BitableBase>
}

export function createBase(data: BitableBaseCreateDTO) {
  return request.post<ApiResponse<BitableBase>>(`/v1/bitable/bases`, data) as unknown as Promise<BitableBase>
}

export function updateBase(id: number, data: Partial<BitableBaseCreateDTO>) {
  return request.put<ApiResponse<BitableBase>>(`/v1/bitable/bases/${id}`, data) as unknown as Promise<BitableBase>
}

export function deleteBase(id: number) {
  return request.delete<ApiResponse<void>>(`/v1/bitable/bases/${id}`) as unknown as Promise<void>
}

// Base 成员
export function listBaseMembers(baseId: number) {
  return request.get<ApiResponse<BitableBaseMember[]>>(`/v1/bitable/bases/${baseId}/members`) as unknown as Promise<BitableBaseMember[]>
}

export function addBaseMember(baseId: number, data: { userId: number; role: MemberRole }) {
  return request.post<ApiResponse<BitableBaseMember>>(`/v1/bitable/bases/${baseId}/members`, data) as unknown as Promise<BitableBaseMember>
}

export function updateBaseMemberRole(baseId: number, userId: number, role: MemberRole) {
  return request.put<ApiResponse<BitableBaseMember>>(`/v1/bitable/bases/${baseId}/members/${userId}`, { role }) as unknown as Promise<BitableBaseMember>
}

export function removeBaseMember(baseId: number, userId: number) {
  return request.delete<ApiResponse<void>>(`/v1/bitable/bases/${baseId}/members/${userId}`) as unknown as Promise<void>
}

// Table
export function listTables(baseId: number) {
  return request.get<ApiResponse<BitableTable[]>>(`/v1/bitable/bases/${baseId}/tables`) as unknown as Promise<BitableTable[]>
}

export function createTable(baseId: number, data: BitableTableCreateDTO) {
  return request.post<ApiResponse<BitableTable>>(`/v1/bitable/bases/${baseId}/tables`, data) as unknown as Promise<BitableTable>
}

export function updateTable(id: number, data: Partial<BitableTableCreateDTO>) {
  return request.put<ApiResponse<BitableTable>>(`/v1/bitable/tables/${id}`, data) as unknown as Promise<BitableTable>
}

export function deleteTable(id: number) {
  return request.delete<ApiResponse<void>>(`/v1/bitable/tables/${id}`) as unknown as Promise<void>
}

// Field
export function listFields(tableId: number) {
  return request.get<ApiResponse<BitableField[]>>(`/v1/bitable/tables/${tableId}/fields`) as unknown as Promise<BitableField[]>
}

export function createField(tableId: number, data: BitableFieldCreateDTO) {
  return request.post<ApiResponse<BitableField>>(`/v1/bitable/tables/${tableId}/fields`, data) as unknown as Promise<BitableField>
}

export function updateField(id: number, data: Partial<BitableFieldCreateDTO>) {
  return request.put<ApiResponse<BitableField>>(`/v1/bitable/fields/${id}`, data) as unknown as Promise<BitableField>
}

export function deleteField(id: number) {
  return request.delete<ApiResponse<void>>(`/v1/bitable/fields/${id}`) as unknown as Promise<void>
}

export function sortFields(tableId: number, fieldIds: number[]) {
  return request.put<ApiResponse<void>>(`/v1/bitable/tables/${tableId}/fields/sort`, fieldIds) as unknown as Promise<void>
}

// Record
export function listRecords(
  tableId: number,
  params: { pageNum?: number; pageSize?: number; filterConfig?: string; sortConfig?: string }
) {
  return request.get<ApiResponse<PageResult<BitableRecord>>>(`/v1/bitable/tables/${tableId}/records`, { params }) as unknown as Promise<PageResult<BitableRecord>>
}

export function createRecord(tableId: number, data: BitableRecordCreateDTO) {
  return request.post<ApiResponse<BitableRecord>>(`/v1/bitable/tables/${tableId}/records`, data) as unknown as Promise<BitableRecord>
}

export function updateRecord(id: number, data: { cells: Record<number, unknown> }) {
  return request.put<ApiResponse<BitableRecord>>(`/v1/bitable/records/${id}`, data) as unknown as Promise<BitableRecord>
}

export function deleteRecord(id: number) {
  return request.delete<ApiResponse<void>>(`/v1/bitable/records/${id}`) as unknown as Promise<void>
}

export function updateCell(recordId: number, fieldId: number, data: CellUpdateDTO) {
  return request.put<ApiResponse<CellValue>>(`/v1/bitable/records/${recordId}/cells/${fieldId}`, data) as unknown as Promise<CellValue>
}

export function batchCreateRecords(tableId: number, records: BitableRecordCreateDTO[]) {
  return request.post<ApiResponse<BitableRecord[]>>(`/v1/bitable/tables/${tableId}/records/batch`, records) as unknown as Promise<BitableRecord[]>
}

// View
export function listViews(tableId: number) {
  return request.get<ApiResponse<BitableView[]>>(`/v1/bitable/tables/${tableId}/views`) as unknown as Promise<BitableView[]>
}

export function createView(tableId: number, data: BitableViewCreateDTO) {
  return request.post<ApiResponse<BitableView>>(`/v1/bitable/tables/${tableId}/views`, data) as unknown as Promise<BitableView>
}

export function updateView(id: number, data: Partial<BitableView>) {
  return request.put<ApiResponse<BitableView>>(`/v1/bitable/views/${id}`, data) as unknown as Promise<BitableView>
}

export function deleteView(id: number) {
  return request.delete<ApiResponse<void>>(`/v1/bitable/views/${id}`) as unknown as Promise<void>
}

// Comment
export function listComments(recordId: number) {
  return request.get<ApiResponse<BitableComment[]>>(`/v1/bitable/records/${recordId}/comments`) as unknown as Promise<BitableComment[]>
}

export function createComment(recordId: number, data: { content: string; tableId: number; quoteFieldId?: number; parentId?: number }) {
  return request.post<ApiResponse<BitableComment>>(`/v1/bitable/records/${recordId}/comments`, data) as unknown as Promise<BitableComment>
}

export function deleteComment(id: number) {
  return request.delete<ApiResponse<void>>(`/v1/bitable/comments/${id}`) as unknown as Promise<void>
}

// Operation history
export function listOperations(
  baseId: number,
  params?: { tableId?: number; pageNum?: number; pageSize?: number }
) {
  return request.get<ApiResponse<PageResult<BitableOperation>>>(`/v1/bitable/bases/${baseId}/operations`, { params }) as unknown as Promise<PageResult<BitableOperation>>
}
