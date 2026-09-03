import { Fragment, useEffect, useMemo, useRef, useState } from 'react'
import { formatDecimal, formatVersionStatus, readErrorMessage } from './shared/format'
import { DataStatus } from './shared/DataStatus'

type Recipe = {
  id: number
  recipeCode: string
  recipeName: string
  active: boolean
  description: string | null
}

type Material = {
  id: number
  materialCode: string
  materialName: string
}

type RecipeVersionItem = {
  id: number
  materialId: number
  materialCode: string
  materialName: string
  ratio: string
  displayOrder: number
}

type RecipeVersion = {
  id: number
  recipeId: number
  recipeCode: string
  recipeName: string
  versionDate: string
  baseWeightG: string
  status: string
  createdBy: string | null
  items: RecipeVersionItem[]
}

type RecipeVersionItemDetail = {
  id: number
  recipeVersionId: number
  recipeId: number
  recipeCode: string
  recipeName: string
  versionDate: string
  versionStatus: string
  baseWeightG: string
  materialId: number
  materialCode: string
  materialName: string
  ratio: string
  displayOrder: number
  createdAt: string | null
}

type RecipeVersionItemImportResult = {
  importedVersionCount: number
  importedRowCount: number
}

type EditorRow = {
  clientId: string
  materialId: number | null
  ratio: string
}

type QueryEditForm = {
  materialId: string
  ratio: string
}

type RecipeEditForm = {
  recipeCode: string
  recipeName: string
  active: boolean
  description: string
}

type VersionEditForm = {
  versionDate: string
  baseWeightG: string
  status: string
  createdBy: string
}

type QueryVersionGroup = {
  recipeVersionId: number
  versionDate: string
  versionStatus: string
  baseWeightG: string
  items: RecipeVersionItemDetail[]
}

type QueryRecipeGroup = {
  recipeId: number
  recipeCode: string
  recipeName: string
  versions: QueryVersionGroup[]
}

const todayIso = () => new Date().toISOString().slice(0, 10)

const downloadBlob = (blob: Blob, fileName: string) => {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.URL.revokeObjectURL(url)
}

const downloadText = (text: string, fileName: string) => {
  downloadBlob(new Blob(['\ufeff', text], { type: 'text/csv;charset=utf-8' }), fileName)
}

const csvEscape = (value: string | number | boolean | null | undefined) => {
  const text = value == null ? '' : String(value)
  return `"${text.replace(/"/g, '""')}"`
}

const toCsv = (headers: string[], rows: Array<Array<string | number | boolean | null | undefined>>) =>
  [headers, ...rows].map((row) => row.map(csvEscape).join(',')).join('\r\n')

const createRow = (): EditorRow => ({
  clientId: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
  materialId: null,
  ratio: '',
})

const createBlankEditor = (recipeId: number | null) => ({
  recipeId,
  versionDate: todayIso(),
  baseWeightG: '',
  status: 'ACTIVE',
  createdBy: '',
  rows: [createRow()],
})

async function fetchJson<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init)
  if (!response.ok) {
    throw new Error(await readErrorMessage(response, '請求失敗'))
  }
  return (await response.json()) as T
}

function SectionToggle({
  label,
  expanded,
  onClick,
}: {
  label: string
  expanded: boolean
  onClick: () => void
}) {
  return (
    <button type="button" className={`section-toggle-btn ${expanded ? 'expanded' : ''}`} onClick={onClick} aria-expanded={expanded}>
      <span>{expanded ? `隱藏${label}` : `顯示${label}`}</span>
    </button>
  )
}

export function RecipeVersionBatchManager() {
  const [recipes, setRecipes] = useState<Recipe[]>([])
  const [materials, setMaterials] = useState<Material[]>([])
  const [selectedRecipeId, setSelectedRecipeId] = useState<number | null>(null)
  const [querySearchText, setQuerySearchText] = useState('')
  const [queryItems, setQueryItems] = useState<RecipeVersionItemDetail[]>([])
  const [recipeEditingId, setRecipeEditingId] = useState<number | null>(null)
  const [recipeEditForm, setRecipeEditForm] = useState<RecipeEditForm>({
    recipeCode: '',
    recipeName: '',
    active: true,
    description: '',
  })
  const [versionEditingId, setVersionEditingId] = useState<number | null>(null)
  const [versionEditForm, setVersionEditForm] = useState<VersionEditForm>({
    versionDate: '',
    baseWeightG: '',
    status: 'ACTIVE',
    createdBy: '',
  })
  const [queryEditingId, setQueryEditingId] = useState<number | null>(null)
  const [queryEditForm, setQueryEditForm] = useState<QueryEditForm>({
    materialId: '',
    ratio: '',
  })
  const [expandedRecipeIds, setExpandedRecipeIds] = useState<number[]>([])
  const [expandedVersionIds, setExpandedVersionIds] = useState<number[]>([])
  const [importFile, setImportFile] = useState<File | null>(null)
  const [showCreatePanel, setShowCreatePanel] = useState(false)
  const [showImportPanel, setShowImportPanel] = useState(false)
  const [editor, setEditor] = useState(createBlankEditor(null))
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [importing, setImporting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const importInputRef = useRef<HTMLInputElement | null>(null)

  const resetToNewVersion = (recipeId: number | null) => {
    setEditor(createBlankEditor(recipeId))
  }

  const loadQueryItems = async (override?: { search?: string }) => {
    setLoading(true)
    setError(null)
    try {
      const query = new URLSearchParams()
      const search = (override?.search ?? querySearchText).trim()
      if (search) query.set('search', search)
      const data = await fetchJson<RecipeVersionItemDetail[]>(`/api/recipe-version-items${query.toString() ? `?${query}` : ''}`)
      setQueryItems(data)
      setExpandedRecipeIds([])
      setExpandedVersionIds([])
    } catch (err) {
      setError(err instanceof Error ? err.message : '無法載入查詢結果')
    } finally {
      setLoading(false)
    }
  }

  const loadBaseData = async () => {
    setLoading(true)
    setError(null)
    try {
      const [recipeData, materialData] = await Promise.all([fetchJson<Recipe[]>('/api/recipes'), fetchJson<Material[]>('/api/materials')])
      setRecipes(recipeData)
      setMaterials(materialData)

      const initialRecipeId = recipeData[0]?.id ?? null
      setSelectedRecipeId(initialRecipeId)
      resetToNewVersion(initialRecipeId)

      await loadQueryItems({ search: '' })
    } catch (err) {
      setError(err instanceof Error ? err.message : '載入基礎資料失敗')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadBaseData()
  }, [])

  const updateRow = (clientId: string, field: keyof Pick<EditorRow, 'materialId' | 'ratio'>, value: string | number | null) => {
    setEditor((current) => ({
      ...current,
      rows: current.rows.map((row) =>
        row.clientId === clientId
          ? {
              ...row,
              [field]: value,
            }
          : row,
      ),
    }))
  }

  const addRow = () => {
    setEditor((current) => ({ ...current, rows: [...current.rows, createRow()] }))
  }

  const removeRow = (clientId: string) => {
    setEditor((current) => {
      if (current.rows.length <= 1) {
        return { ...current, rows: [createRow()] }
      }
      return { ...current, rows: current.rows.filter((row) => row.clientId !== clientId) }
    })
  }

  const validateRows = () => {
    const seenMaterials = new Set<number>()
    const items: Array<{ materialId: number; ratio: number; displayOrder: number }> = []

    editor.rows.forEach((row, index) => {
      if (!row.materialId) {
        throw new Error(`第 ${index + 1} 列請選擇原料`)
      }
      if (!row.ratio.trim() || Number.isNaN(Number(row.ratio))) {
        throw new Error(`第 ${index + 1} 列請輸入有效比例`)
      }
      if (seenMaterials.has(row.materialId)) {
        throw new Error('同一版本內不能重複使用相同原料')
      }
      seenMaterials.add(row.materialId)
      items.push({
        materialId: row.materialId,
        ratio: Number(row.ratio),
        displayOrder: index + 1,
      })
    })

    return items
  }

  const saveVersion = async () => {
    if (!selectedRecipeId) {
      setError('請先選擇配方')
      return
    }
    if (!editor.versionDate.trim()) {
      setError('請輸入版本日期')
      return
    }
    if (!editor.baseWeightG.trim() || Number.isNaN(Number(editor.baseWeightG))) {
      setError('請輸入有效的基準重量')
      return
    }

    let items: Array<{ materialId: number; ratio: number; displayOrder: number }>
    try {
      items = validateRows()
    } catch (err) {
      setError(err instanceof Error ? err.message : '明細資料不完整')
      return
    }

    try {
      setSaving(true)
      setError(null)
      const response = await fetch(`/api/recipes/${selectedRecipeId}/versions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          versionDate: editor.versionDate,
          baseWeightG: Number(editor.baseWeightG),
          status: editor.status,
          createdBy: editor.createdBy.trim() || null,
          items,
        }),
      })
      if (!response.ok) {
        throw new Error(await readErrorMessage(response, '儲存版本失敗'))
      }
      await response.json()
      setNotice('已建立新配方版本')
      resetToNewVersion(selectedRecipeId)
      await loadQueryItems({ search: querySearchText })
    } catch (err) {
      setError(err instanceof Error ? err.message : '儲存版本失敗')
    } finally {
      setSaving(false)
    }
  }

  const clearNewVersionForm = () => {
    setNotice(null)
    setError(null)
    resetToNewVersion(selectedRecipeId ?? recipes[0]?.id ?? null)
  }

  const exportCsv = async () => {
    try {
      setExporting(true)
      setError(null)
      const query = new URLSearchParams()
      const search = querySearchText.trim()
      if (search) query.set('search', search)
      const response = await fetch(`/api/recipe-version-items${query.toString() ? `?${query}` : ''}`)
      if (!response.ok) {
        throw new Error(await readErrorMessage(response, '匯出失敗'))
      }
      const rows = (await response.json()) as RecipeVersionItemDetail[]
      const csv = toCsv(
        ['配方代號', '配方名稱', '版本日期', '版本狀態', '基準重量(g)', '原料代號', '原料名稱', '比例', '建立時間'],
        rows.map((row) => [
          row.recipeCode,
          row.recipeName,
          row.versionDate ?? '',
          row.versionStatus,
          row.baseWeightG,
          row.materialCode,
          row.materialName,
          row.ratio,
          row.createdAt ? new Date(row.createdAt).toLocaleString('zh-TW') : '',
        ]),
      )
      downloadText(csv, 'recipe-version-items.csv')
      setNotice('已匯出 CSV')
    } catch (err) {
      setError(err instanceof Error ? err.message : '匯出失敗')
    } finally {
      setExporting(false)
    }
  }

  const startRecipeEdit = async (recipeId: number) => {
    try {
      setSaving(true)
      setError(null)
      const recipe = await fetchJson<Recipe>(`/api/recipes/${recipeId}`)
      setRecipeEditingId(recipeId)
      setRecipeEditForm({
        recipeCode: recipe.recipeCode,
        recipeName: recipe.recipeName,
        active: recipe.active,
        description: recipe.description ?? '',
      })
      setVersionEditingId(null)
      setQueryEditingId(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : '無法載入配方資料')
    } finally {
      setSaving(false)
    }
  }

  const cancelRecipeEdit = () => {
    setRecipeEditingId(null)
    setRecipeEditForm({ recipeCode: '', recipeName: '', active: true, description: '' })
  }

  const saveRecipeEdit = async () => {
    if (!recipeEditingId) {
      return
    }
    if (!recipeEditForm.recipeCode.trim() || !recipeEditForm.recipeName.trim()) {
      setError('請填寫配方代號與名稱')
      return
    }

    try {
      setSaving(true)
      setError(null)
      const response = await fetch(`/api/recipes/${recipeEditingId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          recipeCode: recipeEditForm.recipeCode,
          recipeName: recipeEditForm.recipeName,
          active: recipeEditForm.active,
          description: recipeEditForm.description || null,
        }),
      })
      if (!response.ok) {
        throw new Error(await readErrorMessage(response, '更新配方失敗'))
      }
      setNotice('已更新配方')
      cancelRecipeEdit()
      await loadBaseData()
      await loadQueryItems({ search: querySearchText })
    } catch (err) {
      setError(err instanceof Error ? err.message : '更新配方失敗')
    } finally {
      setSaving(false)
    }
  }

  const deleteRecipe = async (recipeId: number, recipeCode: string) => {
    if (!window.confirm(`確定要刪除配方「${recipeCode}」嗎？`)) {
      return
    }

    try {
      setSaving(true)
      setError(null)
      const response = await fetch(`/api/recipes/${recipeId}`, { method: 'DELETE' })
      if (!response.ok && response.status !== 204) {
        throw new Error(await readErrorMessage(response, '刪除配方失敗'))
      }
      setNotice('已刪除配方')
      if (recipeEditingId === recipeId) {
        cancelRecipeEdit()
      }
      await loadBaseData()
      await loadQueryItems({ search: querySearchText })
    } catch (err) {
      setError(err instanceof Error ? err.message : '刪除配方失敗')
    } finally {
      setSaving(false)
    }
  }

  const startVersionEdit = async (versionId: number) => {
    try {
      setSaving(true)
      setError(null)
      const version = await fetchJson<RecipeVersion>(`/api/recipe-versions/${versionId}`)
      setVersionEditingId(versionId)
      setVersionEditForm({
        versionDate: version.versionDate,
        baseWeightG: String(version.baseWeightG),
        status: version.status,
        createdBy: version.createdBy ?? '',
      })
      setRecipeEditingId(null)
      setQueryEditingId(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : '無法載入版本資料')
    } finally {
      setSaving(false)
    }
  }

  const cancelVersionEdit = () => {
    setVersionEditingId(null)
    setVersionEditForm({ versionDate: '', baseWeightG: '', status: 'ACTIVE', createdBy: '' })
  }

  const saveVersionEdit = async (versionGroup: QueryVersionGroup) => {
    if (!versionEditingId) {
      return
    }
    if (!versionEditForm.versionDate.trim() || !versionEditForm.baseWeightG.trim()) {
      setError('請填寫版本日期與基準重量')
      return
    }
    if (Number.isNaN(Number(versionEditForm.baseWeightG))) {
      setError('請輸入有效的基準重量')
      return
    }

    try {
      setSaving(true)
      setError(null)
      const response = await fetch(`/api/recipe-versions/${versionEditingId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          versionDate: versionEditForm.versionDate,
          baseWeightG: Number(versionEditForm.baseWeightG),
          status: versionEditForm.status,
          createdBy: versionEditForm.createdBy.trim() || null,
          items: versionGroup.items.map((item, index) => ({
            materialId: item.materialId,
            ratio: Number(item.ratio),
            displayOrder: index + 1,
          })),
        }),
      })
      if (!response.ok) {
        throw new Error(await readErrorMessage(response, '更新版本失敗'))
      }
      setNotice('已更新版本')
      cancelVersionEdit()
      await loadQueryItems({ search: querySearchText })
    } catch (err) {
      setError(err instanceof Error ? err.message : '更新版本失敗')
    } finally {
      setSaving(false)
    }
  }

  const deleteVersion = async (versionId: number, versionDate: string) => {
    if (!window.confirm(`確定要刪除版本「${versionDate}」嗎？`)) {
      return
    }

    try {
      setSaving(true)
      setError(null)
      const response = await fetch(`/api/recipe-versions/${versionId}`, { method: 'DELETE' })
      if (!response.ok && response.status !== 204) {
        throw new Error(await readErrorMessage(response, '刪除版本失敗'))
      }
      setNotice('已刪除版本')
      if (versionEditingId === versionId) {
        cancelVersionEdit()
      }
      await loadQueryItems({ search: querySearchText })
    } catch (err) {
      setError(err instanceof Error ? err.message : '刪除版本失敗')
    } finally {
      setSaving(false)
    }
  }

  const importExcel = async () => {
    if (!importFile) {
      setError('請先選擇 Excel 檔案')
      return
    }

    try {
      setImporting(true)
      setError(null)
      const formData = new FormData()
      formData.append('file', importFile)
      const response = await fetch('/api/recipe-version-items/import', {
        method: 'POST',
        body: formData,
      })
      if (!response.ok) {
        throw new Error(await readErrorMessage(response, '匯入失敗'))
      }
      const result = (await response.json()) as RecipeVersionItemImportResult
      setNotice(`已匯入 ${result.importedRowCount} 筆明細 / ${result.importedVersionCount} 個版本`)
      setImportFile(null)
      await loadBaseData()
    } catch (err) {
      setError(err instanceof Error ? err.message : '匯入失敗')
    } finally {
      setImporting(false)
    }
  }

  const startQueryEdit = (row: RecipeVersionItemDetail) => {
    setQueryEditingId(row.id)
    setQueryEditForm({
      materialId: String(row.materialId),
      ratio: String(row.ratio),
    })
    setNotice(null)
    setError(null)
  }

  const cancelQueryEdit = () => {
    setQueryEditingId(null)
    setQueryEditForm({ materialId: '', ratio: '' })
  }

  const saveQueryEdit = async () => {
    if (!queryEditingId) {
      return
    }
    if (!queryEditForm.materialId || !queryEditForm.ratio.trim()) {
      setError('請完整填寫查詢結果的原料與比例')
      return
    }

    const materialId = Number(queryEditForm.materialId)
    const ratio = Number(queryEditForm.ratio)
    if (!Number.isInteger(materialId) || Number.isNaN(ratio)) {
      setError('請輸入有效的原料與比例')
      return
    }

    try {
      setSaving(true)
      setError(null)
      const target = queryItems.find((item) => item.id === queryEditingId)
      if (!target) {
        throw new Error('找不到要更新的查詢結果')
      }
      const response = await fetch(`/api/recipe-version-items/${queryEditingId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          recipeVersionId: target.recipeVersionId,
          materialId,
          ratio,
          displayOrder: target.displayOrder,
        }),
      })
      if (!response.ok) {
        throw new Error(await readErrorMessage(response, '更新查詢結果失敗'))
      }
      setNotice('已更新查詢結果')
      cancelQueryEdit()
      await loadQueryItems()
    } catch (err) {
      setError(err instanceof Error ? err.message : '更新查詢結果失敗')
    } finally {
      setSaving(false)
    }
  }

  const deleteQueryItem = async (row: RecipeVersionItemDetail) => {
    if (!window.confirm(`確定要刪除「${row.materialCode} - ${row.materialName}」嗎？`)) {
      return
    }

    try {
      setSaving(true)
      setError(null)
      const response = await fetch(`/api/recipe-version-items/${row.id}`, {
        method: 'DELETE',
      })
      if (!response.ok) {
        throw new Error(await readErrorMessage(response, '刪除失敗'))
      }
      setNotice('已刪除查詢結果')
      if (queryEditingId === row.id) {
        cancelQueryEdit()
      }
      await loadQueryItems()
    } catch (err) {
      setError(err instanceof Error ? err.message : '刪除失敗')
    } finally {
      setSaving(false)
    }
  }

  const toggleRecipeExpanded = (recipeId: number) => {
    setExpandedRecipeIds((current) =>
      current.includes(recipeId) ? current.filter((value) => value !== recipeId) : [...current, recipeId],
    )
  }

  const toggleVersionExpanded = (versionId: number) => {
    setExpandedVersionIds((current) =>
      current.includes(versionId) ? current.filter((value) => value !== versionId) : [...current, versionId],
    )
  }

  const querySummary = useMemo(() => {
    const recipeCount = new Set(queryItems.map((item) => item.recipeId)).size
    const versionCount = new Set(queryItems.map((item) => item.recipeVersionId)).size
    return { itemCount: queryItems.length, recipeCount, versionCount }
  }, [queryItems])

  const queryGroups = useMemo(() => {
    const grouped: QueryRecipeGroup[] = []
    const recipeLookup = new Map<number, QueryRecipeGroup>()

    queryItems.forEach((item) => {
      let recipeGroup = recipeLookup.get(item.recipeId)
      if (!recipeGroup) {
        recipeGroup = {
          recipeId: item.recipeId,
          recipeCode: item.recipeCode,
          recipeName: item.recipeName,
          versions: [],
        }
        recipeLookup.set(item.recipeId, recipeGroup)
        grouped.push(recipeGroup)
      }

      let versionGroup = recipeGroup.versions.find((version) => version.recipeVersionId === item.recipeVersionId)
      if (!versionGroup) {
        versionGroup = {
          recipeVersionId: item.recipeVersionId,
          versionDate: item.versionDate,
          versionStatus: item.versionStatus,
          baseWeightG: item.baseWeightG,
          items: [],
        }
        recipeGroup.versions.push(versionGroup)
      }

      versionGroup.items.push(item)
    })

    return grouped
  }, [queryItems])

  return (
    <section className="management-shell recipe-version-items-shell">
      <header className="top-bar unified-top-bar recipe-version-top-bar">
        <div className="page-title">
          <h1>配方版本項目管理</h1>
          <p>動態配置配方明細之原料比例，透過基準重量與烘焙百分比核算原料佔比。</p>
        </div>
        <div className="top-bar-actions status-row">
          <span className="global-status recipe-global-status"><span className="status-dot" />啟用原料：{materials.length}</span>
        </div>
      </header>

      <section className="recipe-version-workspace-section">
        <div className="recipe-version-section-header">
          <div className="card-title">
            <h2>新增配方</h2>
            <p>這個區塊只會新增，不會更新既有版本。</p>
          </div>
          <SectionToggle label="新增配方" expanded={showCreatePanel} onClick={() => setShowCreatePanel((current) => !current)} />
        </div>

      {showCreatePanel ? (
        <div className="recipe-version-section-body">

          <div className="form-grid recipe-version-workbench-grid">
            <label className="field">
              <span>配方</span>
              <select
                value={selectedRecipeId ?? ''}
                onChange={(event) => {
                  const nextRecipeId = event.target.value ? Number(event.target.value) : null
                  setNotice(null)
                  setError(null)
                  setSelectedRecipeId(nextRecipeId)
                  if (!nextRecipeId) {
                    resetToNewVersion(null)
                    return
                  }
                  resetToNewVersion(nextRecipeId)
                }}
              >
                <option value="">請選擇配方</option>
                {recipes.map((recipe) => (
                  <option key={recipe.id} value={recipe.id}>
                    {recipe.recipeCode} - {recipe.recipeName}
                  </option>
                ))}
              </select>
            </label>

            <label className="field">
              <span>版本日期</span>
              <input
                type="date"
                value={editor.versionDate}
                onChange={(event) => setEditor((current) => ({ ...current, versionDate: event.target.value }))}
              />
            </label>

            <label className="field">
              <span>基準重量（g）</span>
              <input
                type="number"
                min="0"
                step="0.001"
                value={editor.baseWeightG}
                onChange={(event) => setEditor((current) => ({ ...current, baseWeightG: event.target.value }))}
              />
            </label>

            <label className="field">
              <span>狀態</span>
              <select value={editor.status} onChange={(event) => setEditor((current) => ({ ...current, status: event.target.value }))}>
                <option value="ACTIVE">啟用</option>
                <option value="ARCHIVED">停用</option>
                <option value="DRAFT">草稿</option>
              </select>
            </label>

            <label className="field">
              <span>建立者</span>
              <input
                type="text"
                value={editor.createdBy}
                onChange={(event) => setEditor((current) => ({ ...current, createdBy: event.target.value }))}
                placeholder="例如：Mary"
              />
            </label>

          </div>

          <div className="action-row recipe-version-workbench-actions">
            <button type="button" className="ghost-btn" onClick={clearNewVersionForm} disabled={!selectedRecipeId}>
              清空
            </button>
            <button type="button" className="ghost-btn" onClick={addRow}>
              新增原料列
            </button>
            <button type="button" className="primary-btn" onClick={() => void saveVersion()} disabled={saving || !selectedRecipeId}>
              {saving ? '儲存中...' : '儲存配方'}
            </button>
          </div>

          <div className="table-wrap batch-table-wrap">
            <table className="batch-table">
              <thead>
                <tr>
                  <th>原料</th>
                  <th>比例</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {editor.rows.map((row) => (
                  <tr key={row.clientId}>
                    <td className="batch-material-cell">
                      <select
                        value={row.materialId ?? ''}
                        onChange={(event) => updateRow(row.clientId, 'materialId', event.target.value ? Number(event.target.value) : null)}
                      >
                        <option value="">請選擇原料</option>
                        {materials.map((material) => (
                          <option key={material.id} value={material.id}>
                            {material.materialCode} - {material.materialName}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td className="batch-ratio-cell">
                      <input
                        type="number"
                        min="0"
                        step="0.001"
                        value={row.ratio}
                        onChange={(event) => updateRow(row.clientId, 'ratio', event.target.value)}
                      />
                    </td>
                    <td className="batch-action-cell">
                      <div className="table-actions">
                        <button type="button" className="mini-btn danger" onClick={() => removeRow(row.clientId)}>
                          {editor.rows.length > 1 ? '刪除' : '清空'}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="notice-row">
            {notice ? <span className="notice-pill">{notice}</span> : null}
            {error ? <div className="error-banner">{error}</div> : null}
          </div>
        </div>
      ) : null}
      </section>

      <section className="workspace-section recipe-version-workspace-section recipe-version-import-workspace">
        <div className="section-header recipe-version-section-header">
          <div className="section-title card-title">
            <h2>資料作業</h2>
            <p>可直接匯入 Excel 配方比例資料，或把目前查詢結果匯出成表格檔。</p>
          </div>
          <button
            type="button"
            className="btn btn-sm"
            onClick={() => setShowImportPanel((current) => !current)}
            aria-expanded={showImportPanel}
          >
            {showImportPanel ? '隱藏資料作業' : '顯示資料作業'}
          </button>
        </div>

        {showImportPanel ? (
          <div className="section-body recipe-version-section-body">
            <div className="recipe-data-ops-grid">
              <div className="recipe-import-surface recipe-version-import-surface">
                <div className="recipe-operation-title">批次匯入配方比例資料</div>
                <div className="recipe-import-file-row recipe-version-import-controls">
                  <input
                    ref={importInputRef}
                    type="file"
                    accept=".xlsx,.xls"
                    className="form-control recipe-version-file-input"
                    onChange={(event) => setImportFile(event.target.files?.[0] ?? null)}
                  />
                  <button type="button" className="btn btn-primary" onClick={() => void importExcel()} disabled={importing}>
                    {importing ? '匯入中...' : '開始匯入'}
                  </button>
                  <button type="button" className="btn btn-danger" onClick={() => setImportFile(null)} disabled={importing || !importFile}>
                    清除檔案
                  </button>
                </div>
                <div className="recipe-operation-note">支援 .xlsx / .xls，欄位需包含配方代號、版本日期、原料代號與比例。</div>
              </div>
              <div className="recipe-export-surface">
                <div className="recipe-operation-title">將目前配方比例資料下載至本機</div>
                <button type="button" className="btn recipe-export-button" onClick={() => void exportCsv()} disabled={exporting}>
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <path d="M12 3v12" /><path d="m7 10 5 5 5-5" /><path d="M5 21h14" />
                  </svg>
                  {exporting ? '匯出中...' : '匯出所有資料'}
                </button>
              </div>
            </div>
            {notice ? <div className="notice-banner">{notice}</div> : null}
            {error ? <div className="error-banner">{error}</div> : null}
          </div>
        ) : null}
      </section>

      <section className="recipe-version-workspace-section recipe-version-query-panel">
        <div className="card-title">
          <h2>配方比例清單明細</h2>
          <p>共 {querySummary.itemCount} 筆 / {querySummary.recipeCount} 個配方 / {querySummary.versionCount} 個版本</p>
        </div>

        <div className="query-search-row">
          <label className="field query-search-field">
            <span>搜尋</span>
            <input
              value={querySearchText}
              onChange={(event) => setQuerySearchText(event.target.value)}
              placeholder="輸入配方、版本日期、狀態、原料代號或名稱"
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  event.preventDefault()
                  void loadQueryItems({ search: querySearchText })
                }
              }}
            />
          </label>

          <div className="action-row query-actions">
            <button type="button" className="primary-btn" onClick={() => void loadQueryItems({ search: querySearchText })} disabled={loading}>
              {loading ? '查詢中...' : '查詢'}
            </button>
            <button
              type="button"
              className="ghost-btn"
              onClick={() => {
                setQuerySearchText('')
                void loadQueryItems({ search: '' })
              }}
            >
              重設
            </button>
            <button type="button" className="ghost-btn" onClick={() => void exportCsv()} disabled={exporting}>
              {exporting ? '匯出中...' : '匯出 CSV'}
            </button>
          </div>
        </div>

        <div className="table-wrap query-hierarchy-wrap">
          <table className="query-hierarchy-table">
            <thead>
              <tr>
                <th>配方</th>
                <th>版本</th>
                <th>原料</th>
                <th>比例</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {queryGroups.length ? (
                queryGroups.map((recipeGroup) => {
                  const recipeExpanded = expandedRecipeIds.includes(recipeGroup.recipeId)
                  const recipeEditing = recipeEditingId === recipeGroup.recipeId
                  const recipe = recipes.find((item) => item.id === recipeGroup.recipeId) ?? null

                  return (
                    <Fragment key={recipeGroup.recipeId}>
                      <tr className={`query-hierarchy-row query-recipe-row ${recipeEditing ? 'selected-row' : ''}`}>
                        {recipeEditing ? (
                          <>
                            <td className="query-row-main-cell">
                              <div className="query-edit-stack">
                                <input
                                  value={recipeEditForm.recipeCode}
                                  onChange={(event) => setRecipeEditForm((current) => ({ ...current, recipeCode: event.target.value }))}
                                />
                                <input
                                  value={recipeEditForm.recipeName}
                                  onChange={(event) => setRecipeEditForm((current) => ({ ...current, recipeName: event.target.value }))}
                                />
                              </div>
                            </td>
                            <td>
                              <select
                                value={recipeEditForm.active ? '1' : '0'}
                                onChange={(event) => setRecipeEditForm((current) => ({ ...current, active: event.target.value === '1' }))}
                              >
                                <option value="1">啟用</option>
                                <option value="0">停用</option>
                              </select>
                            </td>
                            <td colSpan={2}>
                              <input
                                value={recipeEditForm.description}
                                onChange={(event) => setRecipeEditForm((current) => ({ ...current, description: event.target.value }))}
                                placeholder="配方備註"
                              />
                            </td>
                            <td className="query-row-actions">
                              <div className="table-actions query-table-actions">
                                <button type="button" className="mini-btn" onClick={() => void saveRecipeEdit()} disabled={saving}>
                                  {saving ? '儲存中' : '儲存'}
                                </button>
                                <button type="button" className="mini-btn" onClick={cancelRecipeEdit} disabled={saving}>
                                  取消
                                </button>
                                <button type="button" className="mini-btn danger" onClick={() => void deleteRecipe(recipeGroup.recipeId, recipeGroup.recipeCode)} disabled={saving}>
                                  刪除
                                </button>
                              </div>
                            </td>
                          </>
                        ) : (
                          <>
                            <td className="query-row-main-cell">
                              <button type="button" className="query-expand-btn" onClick={() => toggleRecipeExpanded(recipeGroup.recipeId)} aria-expanded={recipeExpanded}>
                                <span className="query-expand-mark" aria-hidden="true">
                                  {recipeExpanded ? '收合' : '展開'}
                                </span>
                                <span className="query-row-text">
                                  <strong>{recipeGroup.recipeCode}</strong>
                                  <span>{recipeGroup.recipeName}</span>
                                </span>
                              </button>
                            </td>
                            <td>
                              <span className="query-count-pill">版本 {recipeGroup.versions.length}</span>
                            </td>
                            <td>
                              <span className="query-count-pill muted">
                                明細 {recipeGroup.versions.reduce((total, version) => total + version.items.length, 0)}
                              </span>
                            </td>
                            <td>
                              <DataStatus active={recipe?.active ?? false} />
                            </td>
                            <td className="query-row-actions">
                              <div className="table-actions query-table-actions">
                                <button type="button" className="mini-btn" onClick={() => void startRecipeEdit(recipeGroup.recipeId)} disabled={saving}>
                                  編輯
                                </button>
                                <button type="button" className="mini-btn danger" onClick={() => void deleteRecipe(recipeGroup.recipeId, recipeGroup.recipeCode)} disabled={saving}>
                                  刪除
                                </button>
                              </div>
                            </td>
                          </>
                        )}
                      </tr>

                      {recipeExpanded
                        ? recipeGroup.versions.map((versionGroup) => {
                            const versionExpanded = expandedVersionIds.includes(versionGroup.recipeVersionId)
                            const versionEditing = versionEditingId === versionGroup.recipeVersionId

                            return (
                              <Fragment key={versionGroup.recipeVersionId}>
                                <tr className={`query-hierarchy-row query-version-row ${versionEditing ? 'selected-row' : ''}`}>
                                  {versionEditing ? (
                                    <>
                                      <td />
                                      <td className="query-row-main-cell">
                                        <input
                                          type="date"
                                          value={versionEditForm.versionDate}
                                          onChange={(event) => setVersionEditForm((current) => ({ ...current, versionDate: event.target.value }))}
                                        />
                                      </td>
                                      <td>
                                        <input
                                          type="number"
                                          min="0"
                                          step="0.001"
                                          value={versionEditForm.baseWeightG}
                                          onChange={(event) => setVersionEditForm((current) => ({ ...current, baseWeightG: event.target.value }))}
                                        />
                                      </td>
                                      <td>
                                        <select
                                          value={versionEditForm.status}
                                          onChange={(event) => setVersionEditForm((current) => ({ ...current, status: event.target.value }))}
                                        >
                                          <option value="ACTIVE">啟用</option>
                                          <option value="ARCHIVED">停用</option>
                                          <option value="DRAFT">草稿</option>
                                        </select>
                                      </td>
                                      <td className="query-row-actions">
                                        <div className="table-actions query-table-actions">
                                          <button type="button" className="mini-btn" onClick={() => void saveVersionEdit(versionGroup)} disabled={saving}>
                                            {saving ? '儲存中' : '儲存'}
                                          </button>
                                          <button type="button" className="mini-btn" onClick={cancelVersionEdit} disabled={saving}>
                                            取消
                                          </button>
                                          <button type="button" className="mini-btn danger" onClick={() => void deleteVersion(versionGroup.recipeVersionId, versionGroup.versionDate)} disabled={saving}>
                                            刪除
                                          </button>
                                        </div>
                                      </td>
                                    </>
                                  ) : (
                                    <>
                                      <td />
                                      <td className="query-row-main-cell">
                                        <button type="button" className="query-expand-btn" onClick={() => toggleVersionExpanded(versionGroup.recipeVersionId)} aria-expanded={versionExpanded}>
                                          <span className="query-expand-mark" aria-hidden="true">
                                            {versionExpanded ? '收合' : '展開'}
                                          </span>
                                          <span className="query-row-text">
                                            <strong>{versionGroup.versionDate}</strong>
                                            <span>
                                              {formatVersionStatus(versionGroup.versionStatus)} / 基準重量 {formatDecimal(versionGroup.baseWeightG)} g
                                            </span>
                                          </span>
                                        </button>
                                      </td>
                                      <td>
                                        <span className="query-count-pill">原料 {versionGroup.items.length}</span>
                                      </td>
                                      <td>
                                        <span className="query-version-meta">
                                          <DataStatus status={versionGroup.versionStatus} />
                                          <span>基準重量 {formatDecimal(versionGroup.baseWeightG)} g</span>
                                        </span>
                                      </td>
                                      <td className="query-row-actions">
                                        <div className="table-actions query-table-actions">
                                          <button type="button" className="mini-btn" onClick={() => void startVersionEdit(versionGroup.recipeVersionId)} disabled={saving}>
                                            編輯
                                          </button>
                                          <button type="button" className="mini-btn danger" onClick={() => void deleteVersion(versionGroup.recipeVersionId, versionGroup.versionDate)} disabled={saving}>
                                            刪除
                                          </button>
                                        </div>
                                      </td>
                                    </>
                                  )}
                                </tr>

                                {versionExpanded
                                  ? versionGroup.items.map((item) => {
                                      const editing = queryEditingId === item.id
                                      return (
                                        <tr key={item.id} className={`query-hierarchy-row query-item-row ${editing ? 'selected-row' : ''}`}>
                                          <td />
                                          <td />
                                          <td className="query-row-main-cell">
                                            {editing ? (
                                              <select
                                                value={queryEditForm.materialId}
                                                onChange={(event) => setQueryEditForm((current) => ({ ...current, materialId: event.target.value }))}
                                              >
                                                <option value="">請選擇原料</option>
                                                {materials.map((material) => (
                                                  <option key={material.id} value={material.id}>
                                                    {material.materialCode} - {material.materialName}
                                                  </option>
                                                ))}
                                              </select>
                                            ) : (
                                              <span className="query-row-text query-item-text">
                                                <strong>{item.materialCode}</strong>
                                                <span>{item.materialName}</span>
                                              </span>
                                            )}
                                          </td>
                                          <td className="query-ratio-cell">
                                            {editing ? (
                                              <input
                                                type="number"
                                                min="0"
                                                step="0.001"
                                                value={queryEditForm.ratio}
                                                onChange={(event) => setQueryEditForm((current) => ({ ...current, ratio: event.target.value }))}
                                              />
                                            ) : (
                                              <strong className="query-ratio-text">{item.ratio}</strong>
                                            )}
                                          </td>
                                          <td className="query-row-actions">
                                            {editing ? (
                                              <div className="table-actions query-table-actions">
                                                <button type="button" className="mini-btn" onClick={() => void saveQueryEdit()} disabled={saving}>
                                                  {saving ? '儲存中' : '儲存'}
                                                </button>
                                                <button type="button" className="mini-btn" onClick={cancelQueryEdit} disabled={saving}>
                                                  取消
                                                </button>
                                                <button type="button" className="mini-btn danger" onClick={() => void deleteQueryItem(item)} disabled={saving}>
                                                  刪除
                                                </button>
                                              </div>
                                            ) : (
                                              <div className="table-actions query-table-actions">
                                                <button type="button" className="mini-btn" onClick={() => startQueryEdit(item)} disabled={saving}>
                                                  編輯
                                                </button>
                                                <button type="button" className="mini-btn danger" onClick={() => void deleteQueryItem(item)} disabled={saving}>
                                                  刪除
                                                </button>
                                              </div>
                                            )}
                                          </td>
                                        </tr>
                                      )
                                    })
                                  : null}
                              </Fragment>
                            )
                          })
                        : null}
                    </Fragment>
                  )
                })
              ) : (
                <tr>
                  <td colSpan={5}>
                    <div className="empty-table">目前沒有查詢結果。</div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </section>
  )
}
