import request from '@/api/request'
import type { PageResult } from '@/types/api'
import type { HrRecord, HrRecordStatus, HrRecordType, HrOverview } from '@/types/user'

/** 场景统计：待入职/试用期/待离职/未完善手机号/未签合同/本月生日 */
export function getHrOverview() {
  return request.get<HrOverview>('/v1/hr/records/overview') as unknown as Promise<HrOverview>
}

/** 人事事件台账分页（入职/新人成长/转正/异动/离职/合同/退休/员工关怀/用工安全） */
export function getHrRecords(params: {
  recordType?: HrRecordType
  status?: HrRecordStatus | ''
  userId?: number
  pageNum?: number
  pageSize?: number
}) {
  return request.get<PageResult<HrRecord>>('/v1/hr/records', { params }) as unknown as Promise<PageResult<HrRecord>>
}

/** 事件类型统计：processing/done/cancelled + 合同模块的 expiringSoon/expired */
export function getHrRecordSummary(recordType?: HrRecordType) {
  return request.get<Record<string, number>>('/v1/hr/records/summary', {
    params: recordType ? { recordType } : {},
  }) as unknown as Promise<Record<string, number>>
}

export function createHrRecord(data: {
  recordType: HrRecordType
  userId: number
  title: string
  detail?: Record<string, any>
  recordDate?: string
  status?: HrRecordStatus
}) {
  return request.post<HrRecord>('/v1/hr/records', data) as unknown as Promise<HrRecord>
}

export function updateHrRecord(id: number, data: {
  title?: string
  detail?: Record<string, any>
  recordDate?: string
  status?: HrRecordStatus
}) {
  return request.put<HrRecord>(`/v1/hr/records/${id}`, data) as unknown as Promise<HrRecord>
}

export function deleteHrRecord(id: number) {
  return request.delete<void>(`/v1/hr/records/${id}`)
}
