import * as XLSX from 'xlsx'

export function exportToExcel(
  data: Record<string, any>[],
  sheetName: string,
  fileNamePrefix: string,
  columnWidths?: { wch: number }[]
) {
  const ws = XLSX.utils.json_to_sheet(data)
  if (columnWidths) {
    ws['!cols'] = columnWidths
  }
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, sheetName)
  const date = new Date().toISOString().slice(0, 10)
  XLSX.writeFile(wb, `${fileNamePrefix}_${date}.xlsx`)
}
