import request from '@/api/request'
import type { ApiResponse, PageResult } from '@/types/api'
import type {
  BitableAutomation,
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
  RecordGroupVO,
  RecordQueryDTO,
  ViewConfig,
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

/** 高级查询（支持筛选排序分组） */
export function queryRecords(tableId: number, data: RecordQueryDTO) {
  return request.post<ApiResponse<PageResult<BitableRecord>>>(`/v1/bitable/tables/${tableId}/records/query`, data) as unknown as Promise<PageResult<BitableRecord>>
}

/** 分组查询 */
export function queryGroupedRecords(tableId: number, data: RecordQueryDTO) {
  return request.post<ApiResponse<RecordGroupVO[]>>(`/v1/bitable/tables/${tableId}/records/grouped`, data) as unknown as Promise<RecordGroupVO[]>
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

export function updateCell(recordId: number, fieldId: number, data: CellUpdateDTO): Promise<number> {
  return request.put<ApiResponse<number>>(`/v1/bitable/records/${recordId}/cells/${fieldId}`, data) as unknown as Promise<number>
}

export function batchCreateRecords(tableId: number, records: BitableRecordCreateDTO[]) {
  return request.post<ApiResponse<BitableRecord[]>>(`/v1/bitable/tables/${tableId}/records/batch`, records) as unknown as Promise<BitableRecord[]>
}

// Link field
export function listLinkableRecords(tableId: number, params?: { keyword?: string; pageSize?: number }) {
  return request.get<ApiResponse<BitableRecord[]>>(`/v1/bitable/tables/${tableId}/linkable-records`, { params }) as unknown as Promise<BitableRecord[]>
}

export function linkRecords(fieldId: number, data: { recordId: number; targetRecordIds: number[] }) {
  return request.post<ApiResponse<void>>(`/v1/bitable/fields/${fieldId}/link`, data) as unknown as Promise<void>
}

export function getLinkedRecordIds(fieldId: number, recordId: number) {
  return request.get<ApiResponse<number[]>>(`/v1/bitable/fields/${fieldId}/records/${recordId}/linked`) as unknown as Promise<number[]>
}

// View
export function listViews(tableId: number) {
  return request.get<ApiResponse<BitableView[]>>(`/v1/bitable/tables/${tableId}/views`) as unknown as Promise<BitableView[]>
}

export function createView(tableId: number, data: BitableViewCreateDTO) {
  return request.post<ApiResponse<BitableView>>(`/v1/bitable/tables/${tableId}/views`, data) as unknown as Promise<BitableView>
}

export function updateView(id: number, data: Partial<BitableView>) {
  return request.patch<ApiResponse<BitableView>>(`/v1/bitable/views/${id}`, data) as unknown as Promise<BitableView>
}

export function deleteView(id: number) {
  return request.delete<ApiResponse<void>>(`/v1/bitable/views/${id}`) as unknown as Promise<void>
}

export function duplicateView(viewId: number) {
  return request.post<ApiResponse<number>>(`/v1/bitable/views/${viewId}/duplicate`) as unknown as Promise<number>
}

export function setDefaultView(tableId: number, viewId: number) {
  return request.post<ApiResponse<void>>(`/v1/bitable/tables/${tableId}/default-view/${viewId}`) as unknown as Promise<void>
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

// Automation
export function listAutomations(baseId: number) {
  return request.get<ApiResponse<BitableAutomation[]>>(`/v1/bitable/bases/${baseId}/automations`) as unknown as Promise<BitableAutomation[]>
}

export function createAutomation(baseId: number, data: Partial<BitableAutomation>) {
  return request.post<ApiResponse<number>>(`/v1/bitable/bases/${baseId}/automations`, data) as unknown as Promise<number>
}

export function updateAutomation(id: number, data: Partial<BitableAutomation>) {
  return request.patch<ApiResponse<void>>(`/v1/bitable/automations/${id}`, data) as unknown as Promise<void>
}

export function deleteAutomation(id: number) {
  return request.delete<ApiResponse<void>>(`/v1/bitable/automations/${id}`) as unknown as Promise<void>
}

export function toggleAutomation(id: number, enabled: boolean) {
  return request.post<ApiResponse<void>>(`/v1/bitable/automations/${id}/toggle`, null, { params: { enabled } }) as unknown as Promise<void>
}

export function listAutomationRuns(id: number, params?: { pageNum?: number; pageSize?: number }) {
  return request.get<ApiResponse<PageResult<any>>>(`/v1/bitable/automations/${id}/runs`, { params }) as unknown as Promise<PageResult<any>>
}
