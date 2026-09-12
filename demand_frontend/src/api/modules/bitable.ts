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

// ==================== 公开表单发布 ====================

export interface FormPublishInfo {
  id: number
  viewId: number
  tableId: number
  token: string
  status: string
  hasPassword: boolean
  expireAt: string | null
  submitCount: number
  submitLimit: number | null
  successMessage: string | null
  redirectUrl: string | null
}

export function publishForm(tableId: number, viewId: number, data: Partial<FormPublishInfo> & { password?: string }) {
  return request.post<ApiResponse<FormPublishInfo>>(`/v1/bitable/tables/${tableId}/views/${viewId}/publish`, data) as unknown as Promise<FormPublishInfo>
}

export function getFormPublish(viewId: number) {
  return request.get<ApiResponse<FormPublishInfo>>(`/v1/bitable/views/${viewId}/publish`) as unknown as Promise<FormPublishInfo>
}

export function updateFormPublishStatus(viewId: number, enabled: boolean) {
  return request.post<ApiResponse<void>>(`/v1/bitable/views/${viewId}/publish/status`, { enabled }) as unknown as Promise<void>
}

// ==================== 视图分享 ====================

export interface ViewShareInfo {
  id: number
  viewId: number
  tableId: number
  token: string
  status: string
  expireAt: string | null
  allowDownload: boolean
}

export function shareView(viewId: number, data: { expireAt?: string | null; allowDownload?: boolean }) {
  return request.post<ApiResponse<ViewShareInfo>>(`/v1/bitable/views/${viewId}/share`, data) as unknown as Promise<ViewShareInfo>
}

export function getViewShare(viewId: number) {
  return request.get<ApiResponse<ViewShareInfo>>(`/v1/bitable/views/${viewId}/share`) as unknown as Promise<ViewShareInfo>
}

export function updateViewShareStatus(viewId: number, enabled: boolean) {
  return request.post<ApiResponse<void>>(`/v1/bitable/views/${viewId}/share/status`, { enabled }) as unknown as Promise<void>
}

// ==================== 仪表盘 ====================

export interface BitableDashboardInfo {
  id: number
  baseId: number
  name: string
  status: string
  createdAt: string
}

export interface DashboardWidgetInput {
  type: 'kpi' | 'bar' | 'line' | 'pie'
  title: string
  dataSourceConfig: { tableId: number; fieldId?: number; aggregation: string; groupByFieldId?: number; filterConfig?: unknown }
  displayConfig?: Record<string, unknown>
  sortNo?: number
}

export function listDashboards(baseId: number) {
  return request.get<ApiResponse<BitableDashboardInfo[]>>(`/v1/bitable/bases/${baseId}/dashboards`) as unknown as Promise<BitableDashboardInfo[]>
}

export function createDashboard(baseId: number, name: string) {
  return request.post<ApiResponse<number>>(`/v1/bitable/bases/${baseId}/dashboards`, { name }) as unknown as Promise<number>
}

export function renameDashboard(id: number, name: string) {
  return request.put<ApiResponse<void>>(`/v1/bitable/dashboards/${id}`, { name }) as unknown as Promise<void>
}

export function deleteDashboard(id: number) {
  return request.delete<ApiResponse<void>>(`/v1/bitable/dashboards/${id}`) as unknown as Promise<void>
}

export function listDashboardWidgets(id: number) {
  return request.get<ApiResponse<any[]>>(`/v1/bitable/dashboards/${id}/widgets`) as unknown as Promise<any[]>
}

export function saveDashboardWidgets(id: number, widgets: DashboardWidgetInput[]) {
  return request.post<ApiResponse<void>>(`/v1/bitable/dashboards/${id}/widgets`, { widgets }) as unknown as Promise<void>
}

export function getDashboardData(id: number) {
  return request.get<ApiResponse<any[]>>(`/v1/bitable/dashboards/${id}/data`) as unknown as Promise<any[]>
}

// ==================== 开放 API 凭证 ====================

export interface ApiKeyInfo {
  id: number
  name: string
  keyId: string
  scopes: string[]
  status: string
  expireAt: string | null
  lastUsedAt: string | null
}

export function createApiKey(baseId: number, data: { name?: string; scopes?: string[]; expireAt?: string | null }) {
  return request.post<ApiResponse<Record<string, unknown>>>(`/v1/bitable/bases/${baseId}/api-keys`, data) as unknown as Promise<Record<string, unknown>>
}

export function listApiKeys(baseId: number) {
  return request.get<ApiResponse<ApiKeyInfo[]>>(`/v1/bitable/bases/${baseId}/api-keys`) as unknown as Promise<ApiKeyInfo[]>
}

export function revokeApiKey(id: number) {
  return request.delete<ApiResponse<void>>(`/v1/bitable/api-keys/${id}`) as unknown as Promise<void>
}

// ==================== 匿名公开访问（无需登录） ====================

export interface PublicFormSchema {
  title: string
  description: string | null
  hasPassword: boolean
  successMessage: string | null
  redirectUrl: string | null
  fields: Array<{
    id: number
    name: string
    fieldType: string
    required: boolean
    description: string | null
    options?: Array<{ label: string; color?: string }>
    placeholder?: string
  }>
}

export function getPublicFormSchema(token: string) {
  return request.get<ApiResponse<PublicFormSchema>>(`/v1/public/bitable/forms/${token}/schema`) as unknown as Promise<PublicFormSchema>
}

export function submitPublicForm(token: string, values: Record<number, Record<string, unknown>>, password?: string) {
  return request.post<ApiResponse<Record<string, unknown>>>(`/v1/public/bitable/forms/${token}/submit`, { values, password }) as unknown as Promise<Record<string, unknown>>
}

export function getPublicViewData(token: string, params?: { pageNum?: number; pageSize?: number }) {
  return request.get<ApiResponse<Record<string, unknown>>>(`/v1/public/bitable/views/${token}/data`, { params }) as unknown as Promise<Record<string, unknown>>
}

// ==================== Webhook 订阅 ====================

export interface WebhookSubscriptionInfo {
  id: number
  baseId: number
  tableId: number | null
  name: string
  eventTypes: string[]
  url: string
  status: string
  lastStatus: string | null
  lastDeliveredAt: string | null
}

export function listWebhooks(baseId: number) {
  return request.get<ApiResponse<WebhookSubscriptionInfo[]>>(`/v1/bitable/bases/${baseId}/webhooks`) as unknown as Promise<WebhookSubscriptionInfo[]>
}

export function createWebhook(baseId: number, data: { name?: string; tableId?: number | null; eventTypes?: string[]; url: string }) {
  return request.post<ApiResponse<Record<string, unknown>>>(`/v1/bitable/bases/${baseId}/webhooks`, data) as unknown as Promise<Record<string, unknown>>
}

export function updateWebhookStatus(id: number, enabled: boolean) {
  return request.post<ApiResponse<void>>(`/v1/bitable/webhooks/${id}/status`, { enabled }) as unknown as Promise<void>
}

export function deleteWebhook(id: number) {
  return request.delete<ApiResponse<void>>(`/v1/bitable/webhooks/${id}`) as unknown as Promise<void>
}
