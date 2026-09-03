type DataStatusProps = {
  active?: boolean
  status?: string
}

const STATUS_LABELS: Record<string, string> = {
  ACTIVE: '啟用',
  ARCHIVED: '停用',
  INACTIVE: '停用',
  DRAFT: '草稿',
}

export function DataStatus({ active, status }: DataStatusProps) {
  const normalizedStatus = status?.toUpperCase()
  const label = active == null ? STATUS_LABELS[normalizedStatus ?? ''] ?? status ?? '-' : active ? '啟用' : '停用'
  const tone = active != null
    ? active ? 'is-active' : 'is-inactive'
    : normalizedStatus === 'ACTIVE' ? 'is-active' : normalizedStatus === 'DRAFT' ? 'is-draft' : 'is-inactive'

  return <span className={`data-status ${tone}`}>{label}</span>
}
