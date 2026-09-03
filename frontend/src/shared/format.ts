export const formatNumber = (value: string | number) => {
  const numericValue = typeof value === 'number' ? value : Number(value)
  return new Intl.NumberFormat('zh-TW', { maximumFractionDigits: 2 }).format(numericValue)
}

export const formatDecimal = (value: string | number) => {
  const numericValue = typeof value === 'number' ? value : Number(value)
  return new Intl.NumberFormat('zh-TW', { maximumFractionDigits: 2 }).format(numericValue)
}

export const formatVersionStatus = (value: string) =>
  ({ ACTIVE: '啟用', ARCHIVED: '停用', DRAFT: '草稿' })[value] ?? value

export const readErrorMessage = async (response: Response, fallback: string) => {
  const text = await response.text()
  return text.trim() || `${fallback}（${response.status}）`
}

const sanitizeCsvCell = (value: unknown) => {
  const text = String(value ?? '')
  return `"${text.replace(/"/g, '""')}"`
}

export const downloadCsv = (fileName: string, headers: string[], rows: unknown[][]) => {
  const lines = [
    headers.map(sanitizeCsvCell).join(','),
    ...rows.map((row) => row.map(sanitizeCsvCell).join(',')),
  ]
  const blob = new Blob(['\uFEFF', lines.join('\r\n')], { type: 'text/csv;charset=utf-8' })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.URL.revokeObjectURL(url)
}

const rowSearchText = (row: Record<string, unknown>) =>
  Object.values(row)
    .flatMap((value) => {
      if (value == null) {
        return []
      }
      if (Array.isArray(value)) {
        return value.map((entry) => String(entry ?? ''))
      }
      if (typeof value === 'object') {
        return Object.values(value as Record<string, unknown>).map((entry) => String(entry ?? ''))
      }
      return [String(value)]
    })
    .join(' ')
    .toLowerCase()

export const matchesRowSearch = (row: Record<string, unknown>, searchText: string) => {
  const normalizedSearch = searchText.trim().toLowerCase()
  if (!normalizedSearch) {
    return true
  }
  return rowSearchText(row).includes(normalizedSearch)
}

const collator = new Intl.Collator('zh-Hant', { numeric: true, sensitivity: 'base' })

export const sortRowsByCode = <T extends Record<string, unknown>>(rows: T[], codeField: string) =>
  [...rows].sort((left, right) => collator.compare(String(left[codeField] ?? ''), String(right[codeField] ?? '')))
