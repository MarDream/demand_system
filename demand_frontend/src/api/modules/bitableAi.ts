import request from '@/api/request'
import type { ApiResponse } from '@/types/api'

// AI 建表结果
export interface AiBuildTableField {
  name: string
  fieldType: string
  config?: string
  description?: string
}

export interface AiBuildTableResult {
  tableName: string
  tableDescription?: string
  fields: AiBuildTableField[]
}

// AI 查询结果
export interface AiRecordMatch {
  recordId: number
  displayText?: string
  cells?: Record<number, unknown>
}

export interface AiQueryResult {
  answer: string
  matchedRecords: AiRecordMatch[]
}

// AI 请求 DTO
export interface AiFillRequest {
  tableId: number
  recordId?: number
  fieldId: number
}

export interface AiQueryRequest {
  baseId: number
  tableId?: number
  question: string
}

export interface AiClassifyRequest {
  tableId: number
  sourceFieldId: number
  targetFieldName: string
}

export type AiSummarizeRequest = AiClassifyRequest

// API 封装
export function previewBuildTable(description: string) {
  return request.post<ApiResponse<AiBuildTableResult>>(`/v1/bitable/ai/build-table`, { description }) as unknown as Promise<AiBuildTableResult>
}

export function confirmBuildTable(baseId: number, data: AiBuildTableResult) {
  return request.post<ApiResponse<number>>(`/v1/bitable/ai/build-table/confirm`, data, { params: { baseId } }) as unknown as Promise<number>
}

export function fillCell(tableId: number, recordId: number, fieldId: number) {
  return request.post<ApiResponse<unknown>>(`/v1/bitable/ai/fill`, { tableId, recordId, fieldId }) as unknown as Promise<unknown>
}

export function fillBatch(tableId: number, fieldId: number) {
  return request.post<ApiResponse<void>>(`/v1/bitable/ai/fill-batch`, { tableId, fieldId }) as unknown as Promise<void>
}

export function query(baseId: number, tableId: number | undefined, question: string) {
  return request.post<ApiResponse<AiQueryResult>>(`/v1/bitable/ai/query`, { baseId, tableId, question }) as unknown as Promise<AiQueryResult>
}

export function classify(tableId: number, sourceFieldId: number, targetFieldName: string) {
  return request.post<ApiResponse<void>>(`/v1/bitable/ai/classify`, { tableId, sourceFieldId, targetFieldName }) as unknown as Promise<void>
}

export function summarize(tableId: number, sourceFieldId: number, targetFieldName: string) {
  return request.post<ApiResponse<void>>(`/v1/bitable/ai/summarize`, { tableId, sourceFieldId, targetFieldName }) as unknown as Promise<void>
}
