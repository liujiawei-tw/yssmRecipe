import type { ChangeEvent } from 'react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { InventoryImportPage } from './InventoryImportPage'
import ProductionProcurementPage from './ProductionProcurementPage'
import { RecipeVersionBatchManager } from './RecipeVersionItemsPage'
import { downloadCsv, formatDecimal, formatNumber, formatVersionStatus, matchesRowSearch, readErrorMessage, sortRowsByCode } from './shared/format'
import { DataStatus } from './shared/DataStatus'
import { AppSidebar } from './shared/navigation'
import { usePageRouting } from './shared/routing'
import type { CalculationResult, EntityState, Material, Product, Recipe, RecipeVersion } from './shared/types'
import './App.css'

function CalculationPanel() {
  const [products, setProducts] = useState<Product[]>([])
  const [versions, setVersions] = useState<RecipeVersion[]>([])
  const [selectedProductId, setSelectedProductId] = useState<number | null>(null)
  const [selectedVersionId, setSelectedVersionId] = useState<number | null>(null)
  const [targetWeightG, setTargetWeightG] = useState('500')
  const [result, setResult] = useState<CalculationResult | null>(null)
  const [loadingProducts, setLoadingProducts] = useState(false)
  const [loadingVersions, setLoadingVersions] = useState(false)
  const [calculating, setCalculating] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const selectedProduct = useMemo(
    () => products.find((product) => product.id === selectedProductId) ?? null,
    [products, selectedProductId],
  )

  const selectedVersion = useMemo(
    () => versions.find((version) => version.id === selectedVersionId) ?? null,
    [versions, selectedVersionId],
  )

  const chartSegments = useMemo(() => {
    const colors = ['#f59e0b', '#ef4444', '#22c55e', '#3b82f6', '#e879f9', '#06b6d4']
    const items = result?.items ?? []

    return items.map((item, index) => {
      const value = Number(item.percentage)
      const start = items
        .slice(0, index)
        .reduce((total, currentItem) => total + Number(currentItem.percentage), 0)
      const end = start + value
      const mid = start + value / 2
      const angle = (mid / 100) * Math.PI * 2 - Math.PI / 2
      const labelRadius = 132
      const labelCenter = 160
      const x = labelCenter + Math.cos(angle) * labelRadius
      const y = labelCenter + Math.sin(angle) * labelRadius
      const isLeft = Math.cos(angle) < -0.25
      const isRight = Math.cos(angle) > 0.25

      return {
        ...item,
        color: colors[index % colors.length],
        start,
        end,
        x,
        y,
        align: isLeft ? 'right' : isRight ? 'left' : 'center',
      }
    })
  }, [result])

  useEffect(() => {
    const loadProducts = async () => {
      try {
        setLoadingProducts(true)
        setError(null)
        const response = await fetch('/api/products')
        if (!response.ok) {
          throw new Error(await readErrorMessage(response, '無法載入商品清單'))
        }
        const data = (await response.json()) as Product[]
        setProducts(data)
        setSelectedProductId(data[0]?.id ?? null)
      } catch (err) {
        setError(err instanceof Error ? err.message : '無法載入商品清單')
      } finally {
        setLoadingProducts(false)
      }
    }

    void loadProducts()
  }, [])

  useEffect(() => {
    const loadVersions = async () => {
      if (!selectedProduct?.recipeId) {
        setVersions([])
        setSelectedVersionId(null)
        setResult(null)
        return
      }

      try {
        setLoadingVersions(true)
        setError(null)
        const response = await fetch(`/api/recipes/${selectedProduct.recipeId}/versions`)
        if (!response.ok) {
          throw new Error(await readErrorMessage(response, '無法載入配方版本'))
        }
        const data = (await response.json()) as RecipeVersion[]
        setVersions(data)
        const latest = data[0] ?? null
        setSelectedVersionId(latest?.id ?? null)
        if (latest) {
          setTargetWeightG(latest.baseWeightG)
          setResult(null)
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : '無法載入配方版本')
      } finally {
        setLoadingVersions(false)
      }
    }

    void loadVersions()
  }, [selectedProduct])

  const calculate = async () => {
    if (!selectedVersionId) {
      setError('請先選擇配方版本')
      return
    }

    try {
      setCalculating(true)
      setError(null)
      const response = await fetch('/api/recipe-calculations', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          recipeVersionId: selectedVersionId,
          targetWeightG: Number(targetWeightG),
        }),
      })

      if (!response.ok) {
        throw new Error(await readErrorMessage(response, '試算失敗'))
      }

      setResult((await response.json()) as CalculationResult)
    } catch (err) {
      setError(err instanceof Error ? err.message : '試算失敗')
      setResult(null)
    } finally {
      setCalculating(false)
    }
  }

  useEffect(() => {
    if (selectedVersionId) {
      void calculate()
    }
  }, [selectedVersionId])

  const chartStyle = useMemo(() => {
    if (!chartSegments.length) {
      return { background: 'conic-gradient(#21304f 0 100%)' }
    }

    const segments = chartSegments.map((segment) => `${segment.color} ${segment.start}% ${segment.end}%`)

    return { background: `conic-gradient(${segments.join(', ')})` }
  }, [chartSegments])

  return (
    <section className="calculator-management-page">
      <div className="top-bar unified-top-bar calculator-management-top-bar">
        <div className="page-title">
          <h1>配方試算工作台</h1>
          <p>依商品與有效版本直接從配方版本比例明細抓資料計算，結果會回寫比例、百分比與實際克數。</p>
        </div>
        <div className="top-bar-actions global-status calculator-management-status">
          資料來源：recipe_version_item
          <span className="badge-success">{loadingProducts || loadingVersions || calculating ? '載入中' : '就緒'}</span>
        </div>
      </div>

      <div className="workspace-grid calculator-workspace-grid">
        <section className="workspace-section calculator-workspace-section calculator-control-section">
          <div className="section-header">
            <div className="section-title">
              <h2>計算條件</h2>
              <p>先選商品，再選版本與重量。</p>
            </div>
          </div>

          <form className="form-grid calculator-form-grid" onSubmit={(event) => { event.preventDefault(); void calculate() }}>
            <label className="form-group full-width">
              <span className="form-label">商品</span>
              <select
                className="form-control"
                value={selectedProductId ?? ''}
                onChange={(event) => setSelectedProductId(Number(event.target.value))}
              >
                {!products.length ? <option value="">請選擇商品...</option> : null}
                {products.map((product) => (
                  <option key={product.id} value={product.id}>
                    {product.productCode} - {product.productName}
                  </option>
                ))}
              </select>
            </label>

            <label className="form-group full-width">
              <span className="form-label">有效配方</span>
              <input className="form-control readonly" value={selectedProduct?.recipeName ?? '未設定'} readOnly />
            </label>

            <label className="form-group full-width">
              <span className="form-label">包裝換算</span>
              <input
                className="form-control readonly"
                value={
                  selectedProduct?.gramWeightPerErpUnit
                    ? `${formatDecimal(selectedProduct.gramWeightPerErpUnit)} g / ${selectedProduct.packagingErpUnit ?? selectedProduct.erpUnit}`
                    : '未設定'
                }
                readOnly
              />
            </label>

            <label className="form-group">
              <span className="form-label">安全庫存</span>
              <input className="form-control readonly" value={selectedProduct ? formatNumber(selectedProduct.safetyStock) : '-'} readOnly />
            </label>

            <label className="form-group">
              <span className="form-label">最大庫存</span>
              <input className="form-control readonly" value={selectedProduct ? formatNumber(selectedProduct.maxStock) : '-'} readOnly />
            </label>

            <label className="form-group full-width">
              <span className="form-label">版本</span>
              <select
                className="form-control"
                value={selectedVersionId ?? ''}
                onChange={(event) => {
                  const versionId = Number(event.target.value)
                  const nextVersion = versions.find((version) => version.id === versionId)
                  setSelectedVersionId(versionId)
                  if (nextVersion) {
                    setTargetWeightG(nextVersion.baseWeightG)
                    setResult(null)
                  }
                }}
              >
                {!versions.length ? <option value="">尚無版本</option> : null}
                {versions.map((version) => (
                  <option key={version.id} value={version.id}>
                    {version.versionDate} / {formatVersionStatus(version.status)}
                  </option>
                ))}
              </select>
            </label>

            <label className="form-group">
              <span className="form-label">基準重量</span>
              <input className="form-control readonly" value={selectedVersion ? `${formatDecimal(selectedVersion.baseWeightG)} g` : '-'} readOnly />
            </label>

            <label className="form-group">
              <span className="form-label">本次試算重量 (g)</span>
              <input
                className="form-control calculator-target-input"
                type="number"
                min="0"
                step="0.001"
                value={targetWeightG}
                onChange={(event) => setTargetWeightG(event.target.value)}
              />
            </label>

            <div className="form-group full-width calculator-action-group">
              <button type="submit" className="btn btn-primary calculator-start-button" disabled={calculating || !selectedVersionId}>
                <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <polygon points="5 3 19 12 5 21 5 3" />
                </svg>
                {calculating ? '計算中...' : '開始計算'}
              </button>
              <button
                type="button"
                className="btn btn-secondary calculator-base-button"
                onClick={() => selectedVersion && setTargetWeightG(selectedVersion.baseWeightG)}
              >
                套用基準
              </button>
            </div>
          </form>

          {error ? <div className="error-banner calculator-page-message">{error}</div> : null}
        </section>

        <section className="workspace-section calculator-workspace-section calculator-results-section">
          <div className="section-header">
            <div className="section-title">
              <h2>計算結果</h2>
              <p>結果與明細版本一致，並以 BigDecimal 高精度計算。</p>
            </div>
          </div>

          {result ? (
            <>
              <div className="metrics-row calculator-metrics-row">
                <div className="metric-card">
                  <div className="metric-label">配方</div>
                  <div className="metric-val">{result.recipeName}</div>
                </div>
                <div className="metric-card">
                  <div className="metric-label">版本</div>
                  <div className="metric-val">{result.versionDate}</div>
                </div>
                <div className="metric-card">
                  <div className="metric-label">比例總和</div>
                  <div className="metric-val">{formatDecimal(result.totalRatio)}</div>
                </div>
                <div className="metric-card">
                  <div className="metric-label">重量總和</div>
                  <div className="metric-val">{formatDecimal(result.totalWeightG)} g</div>
                </div>
              </div>

              <div className="chart-container calculator-chart-container">
                <div className="donut-chart" style={chartStyle}>
                  <div className="donut-inner">
                    <span>總重</span>
                    <strong>{formatDecimal(result.totalWeightG)}g</strong>
                  </div>
                </div>

                <div className="chart-legend">
                  {chartSegments.map((item) => (
                    <div key={item.materialCode} className="legend-item">
                      <div className="legend-left">
                        <span className="legend-dot" style={{ backgroundColor: item.color }}></span>
                        <div className="legend-info">
                          <strong>{item.materialCode}</strong>
                          <span>{item.materialName}</span>
                        </div>
                      </div>
                      <div className="legend-percent" style={{ color: item.color }}>
                        {formatDecimal(item.percentage)}%
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              <div className="table-container calculator-table-container">
                <table>
                  <thead>
                    <tr>
                      <th>原料代號 / 名稱</th>
                      <th className="num">比例</th>
                      <th className="num">百分比</th>
                      <th className="num">重量</th>
                      <th>計算公式</th>
                    </tr>
                  </thead>
                  <tbody>
                    {result.items.map((item) => (
                      <tr key={item.materialCode}>
                        <td><span className="code-tag">{item.materialCode}</span>{item.materialName}</td>
                        <td className="num">{formatDecimal(item.ratio)}</td>
                        <td className="num">{formatDecimal(item.percentage)} %</td>
                        <td className="num calculator-weight-cell">{formatDecimal(item.actualWeightG)} g</td>
                        <td className="calculator-formula-cell">{item.formula}</td>
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr className="table-footer">
                      <td className="calculator-subtotal-label">小計：</td>
                      <td className="num">{formatDecimal(result.totalRatio)}</td>
                      <td className="num">100 %</td>
                      <td className="num calculator-total-cell">{formatDecimal(result.totalWeightG)} g</td>
                      <td></td>
                    </tr>
                  </tfoot>
                </table>
              </div>
            </>
          ) : (
            <div className="empty-state calculator-empty-state">
              <h3>準備好就可以開始試算</h3>
              <p>選好商品與版本後按一下「開始計算」。</p>
            </div>
          )}
        </section>
      </div>
    </section>
  )
}

function RecipeVersionItemManager() {
  return <RecipeVersionBatchManager />
}
function MaterialManager() {
  const [state, setState] = useState<EntityState<Material>>({
    loading: false,
    saving: false,
    error: null,
    notice: null,
    rows: [],
  })
  const [editingId, setEditingId] = useState<number | null>(null)
  const [createForm, setCreateForm] = useState({
    materialCode: '',
    materialName: '',
    baseUnit: '',
    active: true,
    description: '',
  })
  const [editForm, setEditForm] = useState({
    materialCode: '',
    materialName: '',
    baseUnit: '',
    active: true,
    description: '',
  })
  const [searchText, setSearchText] = useState('')
  const importInputRef = useRef<HTMLInputElement | null>(null)
  const [importing, setImporting] = useState(false)
  const [importNotice, setImportNotice] = useState<string | null>(null)
  const [importError, setImportError] = useState<string | null>(null)
  const [showCreatePanel, setShowCreatePanel] = useState(false)
  const [showDataActionsPanel, setShowDataActionsPanel] = useState(false)

  const loadRows = async () => {
    try {
      setState((current) => ({ ...current, loading: true, error: null }))
      const response = await fetch('/api/materials')
      if (!response.ok) throw new Error(await readErrorMessage(response, '無法載入原料'))
      const data = (await response.json()) as Material[]
      setState((current) => ({ ...current, rows: data }))
    } catch (err) {
      setState((current) => ({ ...current, error: err instanceof Error ? err.message : '無法載入原料' }))
    } finally {
      setState((current) => ({ ...current, loading: false }))
    }
  }

  useEffect(() => {
    void loadRows()
  }, [])

  const filteredRows = useMemo(
    () => sortRowsByCode(state.rows, 'materialCode').filter((row) => matchesRowSearch(row, searchText)),
    [state.rows, searchText],
  )

  const startCreate = () => {
    setEditingId(null)
    setCreateForm({ materialCode: '', materialName: '', baseUnit: '', active: true, description: '' })
    setState((current) => ({ ...current, notice: null, error: null }))
  }

  const startEdit = (row: Material) => {
    setEditingId(row.id)
    setEditForm({
      materialCode: row.materialCode,
      materialName: row.materialName,
      baseUnit: row.baseUnit,
      active: row.active,
      description: row.description ?? '',
    })
    setState((current) => ({ ...current, notice: null, error: null }))
  }

  const cancelEdit = () => {
    setEditingId(null)
    setEditForm({ materialCode: '', materialName: '', baseUnit: '', active: true, description: '' })
  }

  const saveCreate = async () => {
    if (!createForm.materialCode.trim() || !createForm.materialName.trim() || !createForm.baseUnit.trim()) {
      setState((current) => ({ ...current, error: '請填寫原料代號、名稱與單位' }))
      return
    }

    try {
      setState((current) => ({ ...current, saving: true, error: null }))
      const response = await fetch('/api/materials', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          materialCode: createForm.materialCode,
          materialName: createForm.materialName,
          baseUnit: createForm.baseUnit,
          active: createForm.active,
          description: createForm.description || null,
        }),
      })
      if (!response.ok) throw new Error(await readErrorMessage(response, '儲存原料失敗'))
      await loadRows()
      startCreate()
      setState((current) => ({ ...current, notice: '已新增原料' }))
    } catch (err) {
      setState((current) => ({ ...current, error: err instanceof Error ? err.message : '儲存原料失敗' }))
    } finally {
      setState((current) => ({ ...current, saving: false }))
    }
  }

  const saveEdit = async () => {
    if (!editingId) {
      return
    }
    if (!editForm.materialCode.trim() || !editForm.materialName.trim() || !editForm.baseUnit.trim()) {
      setState((current) => ({ ...current, error: '請填寫原料代號、名稱與單位' }))
      return
    }

    try {
      setState((current) => ({ ...current, saving: true, error: null }))
      const response = await fetch(`/api/materials/${editingId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          materialCode: editForm.materialCode,
          materialName: editForm.materialName,
          baseUnit: editForm.baseUnit,
          active: editForm.active,
          description: editForm.description || null,
        }),
      })
      if (!response.ok) throw new Error(await readErrorMessage(response, '更新原料失敗'))
      await loadRows()
      cancelEdit()
      setState((current) => ({ ...current, notice: '已更新原料' }))
    } catch (err) {
      setState((current) => ({ ...current, error: err instanceof Error ? err.message : '更新原料失敗' }))
    } finally {
      setState((current) => ({ ...current, saving: false }))
    }
  }

  const importMaterials = async (file: File) => {
    if (!file) {
      setImportError('請先選擇 Excel 檔案')
      return
    }

    try {
      setImporting(true)
      setImportError(null)
      setImportNotice(null)
      const formData = new FormData()
      formData.append('file', file)
      const response = await fetch('/api/materials/import', {
        method: 'POST',
        body: formData,
      })
      if (!response.ok) {
        throw new Error(await readErrorMessage(response, '匯入原料失敗'))
      }
      const result = (await response.json()) as {
        sourceFileName: string
        importedCount: number
        createdCount: number
        updatedCount: number
        importedAt: string
      }
      setImportNotice(
        `已匯入 ${result.importedCount} 筆，新增 ${result.createdCount} 筆，更新 ${result.updatedCount} 筆`,
      )
      await loadRows()
    } catch (err) {
      setImportError(err instanceof Error ? err.message : '匯入原料失敗')
    } finally {
      setImporting(false)
    }
  }

  const onImportPick = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] ?? null
    event.target.value = ''
    if (!file) {
      return
    }
    setImportError(null)
    setImportNotice(null)
    void importMaterials(file)
  }

  const exportAll = () => {
    downloadCsv(
      'materials-export.csv',
      ['id', 'materialCode', 'materialName', 'baseUnit', 'active', 'description'],
      state.rows.map((row) => [row.id, row.materialCode, row.materialName, row.baseUnit, row.active ? '啟用' : '停用', row.description ?? '']),
    )
    setState((current) => ({ ...current, notice: '已匯出全部原料資料' }))
  }

  const setActiveState = async (row: Material, active: boolean) => {
    const response = await fetch(`/api/materials/${row.id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        materialCode: row.materialCode,
        materialName: row.materialName,
        baseUnit: row.baseUnit,
        active,
        description: row.description,
      }),
    })

    if (!response.ok && response.status !== 204) {
      throw new Error(await readErrorMessage(response, active ? '更新原料失敗' : '停用原料失敗'))
    }

    await loadRows()
    if (editingId === row.id) startCreate()
  }

  const destroy = async (row: Material) => {
    if (!window.confirm(`確定永久刪除原料 ${row.materialCode} 嗎？`)) return
    try {
      setState((current) => ({ ...current, saving: true, error: null }))
      const response = await fetch(`/api/materials/${row.id}`, { method: 'DELETE' })
      if (!response.ok && response.status !== 204) {
        if (response.status === 400 || response.status === 409) {
          await setActiveState(row, false)
          setState((current) => ({
            ...current,
            notice: '原料已被歷史資料引用，已改為停用',
          }))
          return
        }
        throw new Error(await readErrorMessage(response, '刪除原料失敗'))
      }
      await loadRows()
      if (editingId === row.id) startCreate()
      setState((current) => ({ ...current, notice: '已刪除原料' }))
    } catch (err) {
      setState((current) => ({ ...current, error: err instanceof Error ? err.message : '刪除原料失敗' }))
    } finally {
      setState((current) => ({ ...current, saving: false }))
    }
  }

  return (
    <section className="material-management-page">
      <div className="top-bar unified-top-bar material-management-top-bar">
        <div className="page-title">
          <h1>原料主檔維護</h1>
          <p>管理原料代號、名稱、單位與停用狀態。</p>
        </div>
        <div className="top-bar-actions global-status material-management-status">
          <span>總筆數：{state.rows.length}</span>
          <span className="status-divider" aria-hidden="true"></span>
          <span className="status-dot" aria-hidden="true"></span>
          就緒
        </div>
      </div>

      <section className="workspace-section material-workspace-section">
        <div className="section-header material-section-header">
          <div className="section-title">
            <h2>新增原料</h2>
            <p>原料代號會進入配方比例與試算結果。</p>
          </div>
          <button type="button" className="btn btn-sm" onClick={() => setShowCreatePanel((current) => !current)}>
            {showCreatePanel ? '隱藏新增資料' : '顯示新增資料'}
          </button>
        </div>

        {showCreatePanel ? (
          <div className="section-body material-section-body">
            <div className="form-grid material-reference-form-grid">
              <label className="form-group">
                <span className="form-label">原料代號</span>
                <input
                  className="form-control"
                  value={createForm.materialCode}
                  placeholder="例：MAT-TEA"
                  onChange={(event) => setCreateForm((current) => ({ ...current, materialCode: event.target.value }))}
                />
              </label>
              <label className="form-group">
                <span className="form-label">原料名稱</span>
                <input
                  className="form-control"
                  value={createForm.materialName}
                  placeholder="例：紅茶茶葉"
                  onChange={(event) => setCreateForm((current) => ({ ...current, materialName: event.target.value }))}
                />
              </label>
              <label className="form-group">
                <span className="form-label">基準單位</span>
                <input
                  className="form-control"
                  value={createForm.baseUnit}
                  placeholder="例：g"
                  onChange={(event) => setCreateForm((current) => ({ ...current, baseUnit: event.target.value }))}
                />
              </label>
              <label className="form-group">
                <span className="form-label">啟用狀態</span>
                <select
                  className="form-control"
                  value={createForm.active ? '1' : '0'}
                  onChange={(event) => setCreateForm((current) => ({ ...current, active: event.target.value === '1' }))}
                >
                  <option value="1">啟用</option>
                  <option value="0">停用</option>
                </select>
              </label>
              <label className="form-group full-width">
                <span className="form-label">說明</span>
                <input
                  className="form-control"
                  value={createForm.description}
                  placeholder="請輸入原料的來源、供應商或其他備註說明..."
                  onChange={(event) => setCreateForm((current) => ({ ...current, description: event.target.value }))}
                />
              </label>
            </div>

            <div className="material-form-actions">
              <button type="button" className="btn btn-primary" onClick={() => void saveCreate()} disabled={state.saving}>
                {state.saving ? '新增中...' : '新增'}
              </button>
              <button type="button" className="btn" onClick={() => void loadRows()} disabled={state.saving}>
                重新整理
              </button>
              <button type="button" className="btn btn-danger material-clear-button" onClick={startCreate}>
                清空
              </button>
            </div>
          </div>
        ) : null}
      </section>

      <section className="workspace-section material-workspace-section">
        <div className="section-header material-section-header">
          <div className="section-title">
            <h2>資料作業</h2>
            <p>可直接匯入 Excel，或把全部原料資料匯出成表格檔。</p>
          </div>
          <button type="button" className="btn btn-sm" onClick={() => setShowDataActionsPanel((current) => !current)}>
            {showDataActionsPanel ? '隱藏資料作業' : '顯示資料作業'}
          </button>
        </div>

        {showDataActionsPanel ? (
          <div className="section-body material-section-body">
            <div className="material-data-ops-grid">
              <div className="material-import-surface">
                <div className="material-operation-title">批次匯入原料主檔</div>
                <div className="material-import-file-row">
                  <input ref={importInputRef} className="form-control" type="file" accept=".xlsx,.xls" onChange={onImportPick} />
                  <button type="button" className="btn btn-primary" onClick={() => importInputRef.current?.click()} disabled={importing}>
                    {importing ? '匯入中...' : 'Excel 匯入'}
                  </button>
                </div>
              </div>

              <div className="material-export-surface">
                <div className="material-operation-title">將系統內所有原料資料下載至本機</div>
                <button type="button" className="btn material-export-button" onClick={exportAll}>
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                    <polyline points="7 10 12 15 17 10" />
                    <line x1="12" y1="15" x2="12" y2="3" />
                  </svg>
                  匯出所有資料
                </button>
              </div>
            </div>
            {importNotice ? <div className="notice-banner material-page-message">{importNotice}</div> : null}
            {importError ? <div className="error-banner material-page-message">{importError}</div> : null}
          </div>
        ) : null}
      </section>

      <section className="workspace-section material-workspace-section material-list-workspace">
        <div className="section-title material-list-header">
          <h2>原料清單</h2>
          <p>點擊操作可編輯原料基本資料或進行刪除。</p>
        </div>

        <div className="search-row material-search-row">
          <input
            className="search-input"
            value={searchText}
            onChange={(event) => setSearchText(event.target.value)}
            placeholder="搜尋原料代號、名稱或說明..."
          />
          <button type="button" className="btn btn-primary" onClick={() => undefined}>
            查詢
          </button>
          <button type="button" className="btn" onClick={() => setSearchText('')}>
            重設
          </button>
        </div>

        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th style={{ width: '20%' }}>原料代號</th>
                <th style={{ width: '20%' }}>原料名稱</th>
                <th style={{ width: '15%' }}>基準單位</th>
                <th style={{ width: '25%' }}>說明</th>
                <th style={{ width: '10%' }}>狀態</th>
                <th style={{ width: '10%', textAlign: 'right' }}>操作</th>
              </tr>
            </thead>
            <tbody>
              {filteredRows.map((row) => (
                <tr key={row.id} className={editingId === row.id ? 'selected-row' : ''}>
                  <td>
                    {editingId === row.id ? (
                      <input
                        className="form-control"
                        value={editForm.materialCode}
                        onChange={(event) => setEditForm((current) => ({ ...current, materialCode: event.target.value }))}
                      />
                    ) : (
                      <span className="code-tag">{row.materialCode}</span>
                    )}
                  </td>
                  <td>
                    {editingId === row.id ? (
                      <input
                        className="form-control"
                        value={editForm.materialName}
                        onChange={(event) => setEditForm((current) => ({ ...current, materialName: event.target.value }))}
                      />
                    ) : (
                      <strong>{row.materialName}</strong>
                    )}
                  </td>
                  <td>
                    {editingId === row.id ? (
                      <input
                        className="form-control"
                        value={editForm.baseUnit}
                        onChange={(event) => setEditForm((current) => ({ ...current, baseUnit: event.target.value }))}
                      />
                    ) : (
                      row.baseUnit
                    )}
                  </td>
                  <td>
                    {editingId === row.id ? (
                      <input
                        className="form-control"
                        value={editForm.description}
                        onChange={(event) => setEditForm((current) => ({ ...current, description: event.target.value }))}
                      />
                    ) : (
                      <span className="material-description-cell">{row.description ?? '-'}</span>
                    )}
                  </td>
                  <td>
                    {editingId === row.id ? (
                      <select
                        className="form-control"
                        value={editForm.active ? '1' : '0'}
                        onChange={(event) => setEditForm((current) => ({ ...current, active: event.target.value === '1' }))}
                      >
                        <option value="1">啟用</option>
                        <option value="0">停用</option>
                      </select>
                    ) : (
                      <DataStatus active={row.active} />
                    )}
                  </td>
                  <td>
                    <div className="table-actions material-table-actions">
                      {editingId === row.id ? (
                        <>
                          <button type="button" className="btn btn-sm" onClick={() => void saveEdit()} disabled={state.saving}>
                            {state.saving ? '儲存中' : '儲存'}
                          </button>
                          <button type="button" className="btn btn-sm" onClick={cancelEdit} disabled={state.saving}>
                            取消
                          </button>
                        </>
                      ) : (
                        <>
                          <button type="button" className="table-action-btn" onClick={() => startEdit(row)} title="編輯" aria-label={`編輯原料 ${row.materialCode}`} disabled={state.saving}>
                            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
                            </svg>
                            編輯
                          </button>
                          <button type="button" className="table-action-btn danger" onClick={() => void destroy(row)} title="刪除" aria-label={`刪除原料 ${row.materialCode}`} disabled={state.saving}>
                            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                              <polyline points="3 6 5 6 21 6" />
                              <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                            </svg>
                            刪除
                          </button>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
              {!filteredRows.length ? (
                <tr>
                  <td colSpan={6}>
                    <div className="empty-table">{state.rows.length ? '沒有符合搜尋條件的原料資料。' : '目前沒有原料資料。'}</div>
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </section>

      {state.error ? <div className="error-banner material-page-message">{state.error}</div> : null}
      {state.notice ? <div className="notice-banner material-page-message">{state.notice}</div> : null}
    </section>
  )
}

function RecipeManager() {
  const [state, setState] = useState<EntityState<Recipe>>({
    loading: false,
    saving: false,
    error: null,
    notice: null,
    rows: [],
  })
  const [editingId, setEditingId] = useState<number | null>(null)
  const [createForm, setCreateForm] = useState({ recipeCode: '', recipeName: '', active: true, description: '' })
  const [editForm, setEditForm] = useState({ recipeCode: '', recipeName: '', active: true, description: '' })
  const [searchText, setSearchText] = useState('')
  const importInputRef = useRef<HTMLInputElement | null>(null)
  const [importing, setImporting] = useState(false)
  const [importNotice, setImportNotice] = useState<string | null>(null)
  const [importError, setImportError] = useState<string | null>(null)
  const [showCreatePanel, setShowCreatePanel] = useState(false)
  const [showDataActionsPanel, setShowDataActionsPanel] = useState(false)

  const loadRows = async () => {
    try {
      setState((c) => ({ ...c, loading: true, error: null }))
      const response = await fetch('/api/recipes')
      if (!response.ok) throw new Error(await readErrorMessage(response, '無法載入配方'))
      const data = (await response.json()) as Recipe[]
      setState((c) => ({ ...c, rows: data }))
    } catch (err) {
      setState((c) => ({ ...c, error: err instanceof Error ? err.message : '無法載入配方' }))
    } finally {
      setState((c) => ({ ...c, loading: false }))
    }
  }

  useEffect(() => {
    void loadRows()
  }, [])

  const filteredRows = useMemo(
    () => sortRowsByCode(state.rows, 'recipeCode').filter((row) => matchesRowSearch(row, searchText)),
    [state.rows, searchText],
  )

  const startCreate = () => {
    setEditingId(null)
    setCreateForm({ recipeCode: '', recipeName: '', active: true, description: '' })
    setState((c) => ({ ...c, notice: null, error: null }))
  }

  const startEdit = (row: Recipe) => {
    setEditingId(row.id)
    setEditForm({ recipeCode: row.recipeCode, recipeName: row.recipeName, active: row.active, description: row.description ?? '' })
    setState((c) => ({ ...c, notice: null, error: null }))
  }

  const cancelEdit = () => {
    setEditingId(null)
    setEditForm({ recipeCode: '', recipeName: '', active: true, description: '' })
  }

  const saveCreate = async () => {
    if (!createForm.recipeCode.trim() || !createForm.recipeName.trim()) {
      setState((c) => ({ ...c, error: '請填寫配方代號與名稱' }))
      return
    }
    try {
      setState((c) => ({ ...c, saving: true, error: null }))
      const response = await fetch('/api/recipes', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...createForm, description: createForm.description || null }),
      })
      if (!response.ok) throw new Error(await readErrorMessage(response, '儲存配方失敗'))
      await loadRows()
      startCreate()
      setState((c) => ({ ...c, notice: '已新增配方' }))
    } catch (err) {
      setState((c) => ({ ...c, error: err instanceof Error ? err.message : '儲存配方失敗' }))
    } finally {
      setState((c) => ({ ...c, saving: false }))
    }
  }

  const saveEdit = async () => {
    if (!editingId) {
      return
    }
    if (!editForm.recipeCode.trim() || !editForm.recipeName.trim()) {
      setState((c) => ({ ...c, error: '請填寫配方代號與名稱' }))
      return
    }

    try {
      setState((c) => ({ ...c, saving: true, error: null }))
      const response = await fetch(`/api/recipes/${editingId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...editForm, description: editForm.description || null }),
      })
      if (!response.ok) throw new Error(await readErrorMessage(response, '更新配方失敗'))
      await loadRows()
      cancelEdit()
      setState((c) => ({ ...c, notice: '已更新配方' }))
    } catch (err) {
      setState((c) => ({ ...c, error: err instanceof Error ? err.message : '更新配方失敗' }))
    } finally {
      setState((c) => ({ ...c, saving: false }))
    }
  }

  const importRecipes = async (file: File) => {
    if (!file) {
      setImportError('請先選擇 Excel 檔案')
      return
    }

    try {
      setImporting(true)
      setImportError(null)
      setImportNotice(null)
      const formData = new FormData()
      formData.append('file', file)
      const response = await fetch('/api/recipes/import', {
        method: 'POST',
        body: formData,
      })
      if (!response.ok) {
        throw new Error(await readErrorMessage(response, '匯入配方失敗'))
      }
      const result = (await response.json()) as {
        sourceFileName: string
        importedCount: number
        createdCount: number
        updatedCount: number
        importedAt: string
      }
      setImportNotice(
        `已匯入 ${result.importedCount} 筆，新增 ${result.createdCount} 筆，更新 ${result.updatedCount} 筆`,
      )
      await loadRows()
    } catch (err) {
      setImportError(err instanceof Error ? err.message : '匯入配方失敗')
    } finally {
      setImporting(false)
    }
  }

  const onImportPick = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] ?? null
    event.target.value = ''
    if (!file) {
      return
    }
    setImportError(null)
    setImportNotice(null)
    void importRecipes(file)
  }

  const exportAll = () => {
    downloadCsv(
      'recipes-export.csv',
      ['id', 'recipeCode', 'recipeName', 'active', 'description'],
      state.rows.map((row) => [row.id, row.recipeCode, row.recipeName, row.active ? '啟用' : '停用', row.description ?? '']),
    )
    setState((c) => ({ ...c, notice: '已匯出全部配方資料' }))
  }

  const destroy = async (row: Recipe) => {
    if (!window.confirm(`確定永久刪除配方 ${row.recipeCode} 嗎？`)) return
    try {
      setState((c) => ({ ...c, saving: true, error: null }))
      const response = await fetch(`/api/recipes/${row.id}`, { method: 'DELETE' })
      if (!response.ok && response.status !== 204) throw new Error(await readErrorMessage(response, '刪除配方失敗'))
      await loadRows()
      if (editingId === row.id) startCreate()
      setState((c) => ({ ...c, notice: '已刪除配方' }))
    } catch (err) {
      setState((c) => ({ ...c, error: err instanceof Error ? err.message : '刪除配方失敗' }))
    } finally {
      setState((c) => ({ ...c, saving: false }))
    }
  }

  return (
    <section className="recipe-management-page">
      <div className="top-bar unified-top-bar recipe-management-top-bar">
        <div className="page-title">
          <h1>配方主檔維護</h1>
          <p>管理配方代號、名稱與啟用狀態，版本管理由配方版本頁獨立處理。</p>
        </div>
        <div className="top-bar-actions global-status recipe-management-status">
          <span>總筆數：{formatNumber(state.rows.length)}</span>
          <span className="status-divider" />
          <span className="status-dot" />
          {state.loading ? '載入中...' : '就緒'}
        </div>
      </div>

      <section className="workspace-section recipe-workspace-section">
        <div className="section-header recipe-section-header">
          <div className="section-title">
            <h2>新增配方</h2>
            <p>配方版本與比例明細會掛在這個主檔底下。</p>
          </div>
          <button type="button" className="btn btn-sm" onClick={() => setShowCreatePanel((current) => !current)} aria-expanded={showCreatePanel}>
            {showCreatePanel ? '隱藏新增資料' : '顯示新增資料'}
          </button>
        </div>
        {showCreatePanel ? (
          <div className="section-body recipe-section-body">
            <div className="form-grid recipe-reference-form-grid">
              <label className="field recipe-reference-field">
                <span>配方代號</span>
                <input placeholder="例：REC-NEW" value={createForm.recipeCode} onChange={(e) => setCreateForm((c) => ({ ...c, recipeCode: e.target.value }))} />
              </label>
              <label className="field recipe-reference-field">
                <span>配方名稱</span>
                <input placeholder="例：特調水果茶" value={createForm.recipeName} onChange={(e) => setCreateForm((c) => ({ ...c, recipeName: e.target.value }))} />
              </label>
              <label className="field recipe-reference-field">
                <span>啟用狀態</span>
                <select value={createForm.active ? '1' : '0'} onChange={(e) => setCreateForm((c) => ({ ...c, active: e.target.value === '1' }))}>
                  <option value="1">啟用</option>
                  <option value="0">停用</option>
                </select>
              </label>
              <label className="field recipe-reference-field recipe-form-description">
                <span>說明</span>
                <input placeholder="請輸入配方相關備註說明..." value={createForm.description} onChange={(e) => setCreateForm((c) => ({ ...c, description: e.target.value }))} />
              </label>
            </div>
            <div className="action-row recipe-reference-actions">
              <button type="button" className="btn btn-primary" onClick={() => void saveCreate()} disabled={state.saving}>
                {state.saving ? '新增中...' : '新增'}
              </button>
              <button type="button" className="btn" onClick={() => void loadRows()} disabled={state.saving}>重新整理</button>
              <button type="button" className="btn btn-danger recipe-clear-button" onClick={startCreate}>清空</button>
            </div>
          </div>
        ) : null}
      </section>

      <section className="workspace-section recipe-workspace-section">
        <div className="section-header recipe-section-header">
          <div className="section-title">
            <h2>資料作業</h2>
            <p>可直接匯入 Excel，或把全部配方資料匯出成表格檔。</p>
          </div>
          <button type="button" className="btn btn-sm" onClick={() => setShowDataActionsPanel((current) => !current)} aria-expanded={showDataActionsPanel}>
            {showDataActionsPanel ? '隱藏資料作業' : '顯示資料作業'}
          </button>
        </div>
        {showDataActionsPanel ? (
          <div className="section-body recipe-section-body">
            <div className="recipe-data-ops-grid">
              <div className="recipe-import-surface">
                <div className="recipe-operation-title">批次匯入配方主檔</div>
                <div className="recipe-import-file-row">
                  <input ref={importInputRef} className="form-control" type="file" accept=".xlsx,.xls" onChange={onImportPick} />
                  <button type="button" className="btn btn-primary" onClick={() => importInputRef.current?.click()} disabled={importing}>
                    {importing ? '匯入中...' : 'Excel 匯入'}
                  </button>
                </div>
              </div>
              <div className="recipe-export-surface">
                <div className="recipe-operation-title">將系統內所有配方主檔下載至本機</div>
                <button type="button" className="btn recipe-export-button" onClick={exportAll}>
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <path d="M12 3v12" /><path d="m7 10 5 5 5-5" /><path d="M5 21h14" />
                  </svg>
                  匯出所有資料
                </button>
              </div>
            </div>
            {importNotice ? <div className="notice-banner">{importNotice}</div> : null}
            {importError ? <div className="error-banner">{importError}</div> : null}
          </div>
        ) : null}
      </section>

      <section className="workspace-section recipe-workspace-section recipe-list-workspace">
        <div className="section-title">
          <h2>配方主檔清單</h2>
          <p>點擊操作可編輯配方基本資料或進行刪除。</p>
        </div>
        <div className="recipe-search-row">
          <input className="search-input" value={searchText} onChange={(event) => setSearchText(event.target.value)} placeholder="輸入配方代號或名稱搜尋..." />
          <button type="button" className="btn btn-primary" onClick={() => setSearchText(searchText.trim())}>查詢</button>
          <button type="button" className="btn" onClick={() => setSearchText('')}>重設</button>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th style={{ width: '20%' }}>配方代號</th>
                <th style={{ width: '25%' }}>配方名稱</th>
                <th style={{ width: '30%' }}>說明</th>
                <th style={{ width: '10%' }}>狀態</th>
                <th style={{ width: '15%', textAlign: 'right' }}>操作</th>
              </tr>
            </thead>
            <tbody>
              {filteredRows.map((row) => (
                <tr key={row.id} className={editingId === row.id ? 'selected-row' : ''}>
                  <td>
                    {editingId === row.id ? (
                      <input
                        value={editForm.recipeCode}
                        onChange={(event) => setEditForm((current) => ({ ...current, recipeCode: event.target.value }))}
                      />
                    ) : (
                      <span className="code-tag">{row.recipeCode}</span>
                    )}
                  </td>
                  <td>
                    {editingId === row.id ? (
                      <input
                        value={editForm.recipeName}
                        onChange={(event) => setEditForm((current) => ({ ...current, recipeName: event.target.value }))}
                      />
                    ) : (
                      <strong>{row.recipeName}</strong>
                    )}
                  </td>
                  <td>
                    {editingId === row.id ? (
                      <input
                        value={editForm.description}
                        onChange={(event) => setEditForm((current) => ({ ...current, description: event.target.value }))}
                      />
                    ) : (
                      <span className="recipe-description-cell">{row.description ?? '-'}</span>
                    )}
                  </td>
                  <td>
                    {editingId === row.id ? (
                      <select
                        value={editForm.active ? '1' : '0'}
                        onChange={(event) => setEditForm((current) => ({ ...current, active: event.target.value === '1' }))}
                      >
                        <option value="1">啟用</option>
                        <option value="0">停用</option>
                      </select>
                    ) : (
                      <DataStatus active={row.active} />
                    )}
                  </td>
                  <td>
                    <div className="table-actions recipe-table-actions">
                      {editingId === row.id ? (
                        <>
                          <button type="button" className="btn btn-sm" onClick={() => void saveEdit()} disabled={state.saving}>
                            {state.saving ? '儲存中' : '儲存'}
                          </button>
                          <button type="button" className="btn btn-sm" onClick={cancelEdit} disabled={state.saving}>
                            取消
                          </button>
                        </>
                      ) : (
                        <>
                          <button type="button" className="btn btn-sm" onClick={() => startEdit(row)} disabled={state.saving}>
                            編輯
                          </button>
                          <button
                            type="button"
                            className="btn btn-sm btn-danger"
                            onClick={() => void destroy(row)}
                            aria-label={`刪除配方 ${row.recipeCode}`}
                            disabled={state.saving}
                          >
                            刪除
                          </button>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
              {!filteredRows.length ? (
                <tr><td colSpan={5}><div className="empty-table">{state.rows.length ? '沒有符合搜尋條件的配方資料。' : '目前沒有配方資料。'}</div></td></tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </section>
      {state.error ? <div className="error-banner recipe-page-message">{state.error}</div> : null}
      {state.notice ? <div className="notice-banner recipe-page-message">{state.notice}</div> : null}
    </section>
  )
}

function ProductManager() {
  const [state, setState] = useState<EntityState<Product>>({
    loading: false,
    saving: false,
    error: null,
    notice: null,
    rows: [],
  })
  const [recipes, setRecipes] = useState<Recipe[]>([])
  const [editingId, setEditingId] = useState<number | null>(null)
  const [createForm, setCreateForm] = useState({
    productCode: '',
    productName: '',
    safetyStock: '',
    maxStock: '',
    erpUnit: '',
    active: true,
    recipeId: '',
    packagingErpUnit: '',
    gramWeightPerErpUnit: '',
    packagingDescription: '',
  })
  const [editForm, setEditForm] = useState({
    productCode: '',
    productName: '',
    safetyStock: '',
    maxStock: '',
    erpUnit: '',
    active: true,
    recipeId: '',
    packagingErpUnit: '',
    gramWeightPerErpUnit: '',
    packagingDescription: '',
  })
  const [searchText, setSearchText] = useState('')
  const importInputRef = useRef<HTMLInputElement | null>(null)
  const [importing, setImporting] = useState(false)
  const [importNotice, setImportNotice] = useState<string | null>(null)
  const [importError, setImportError] = useState<string | null>(null)
  const [showCreatePanel, setShowCreatePanel] = useState(false)
  const [showDataActionsPanel, setShowDataActionsPanel] = useState(false)

  const loadRows = async () => {
    try {
      setState((c) => ({ ...c, loading: true, error: null }))
      const [productResponse, recipeResponse] = await Promise.all([fetch('/api/products'), fetch('/api/recipes')])
      if (!productResponse.ok) throw new Error(await readErrorMessage(productResponse, '無法載入商品'))
      if (!recipeResponse.ok) throw new Error(await readErrorMessage(recipeResponse, '無法載入配方'))
      const productData = (await productResponse.json()) as Product[]
      const recipeData = (await recipeResponse.json()) as Recipe[]
      setState((c) => ({ ...c, rows: productData }))
      setRecipes(recipeData)
    } catch (err) {
      setState((c) => ({ ...c, error: err instanceof Error ? err.message : '無法載入商品' }))
    } finally {
      setState((c) => ({ ...c, loading: false }))
    }
  }

  useEffect(() => {
    void loadRows()
  }, [])

  const filteredRows = useMemo(
    () => sortRowsByCode(state.rows, 'productCode').filter((row) => matchesRowSearch(row, searchText)),
    [state.rows, searchText],
  )

  const startCreate = () => {
    setEditingId(null)
    setCreateForm({
      productCode: '',
      productName: '',
      safetyStock: '',
      maxStock: '',
      erpUnit: '',
      active: true,
      recipeId: '',
      packagingErpUnit: '',
      gramWeightPerErpUnit: '',
      packagingDescription: '',
    })
    setState((c) => ({ ...c, notice: null, error: null }))
  }

  const startEdit = (row: Product) => {
    setEditingId(row.id)
    setEditForm({
      productCode: row.productCode,
      productName: row.productName,
      safetyStock: String(row.safetyStock),
      maxStock: String(row.maxStock),
      erpUnit: row.erpUnit,
      active: row.active,
      recipeId: row.recipeId ? String(row.recipeId) : '',
      packagingErpUnit: row.packagingErpUnit ?? '',
      gramWeightPerErpUnit: row.gramWeightPerErpUnit != null ? String(row.gramWeightPerErpUnit) : '',
      packagingDescription: row.packagingDescription ?? '',
    })
    setState((c) => ({ ...c, notice: null, error: null }))
  }

  const cancelEdit = () => {
    setEditingId(null)
    setEditForm({
      productCode: '',
      productName: '',
      safetyStock: '',
      maxStock: '',
      erpUnit: '',
      active: true,
      recipeId: '',
      packagingErpUnit: '',
      gramWeightPerErpUnit: '',
      packagingDescription: '',
    })
  }

  const saveCreate = async () => {
    if (
      !createForm.productCode.trim() ||
      !createForm.productName.trim() ||
      !createForm.erpUnit.trim() ||
      !createForm.safetyStock.trim() ||
      !createForm.maxStock.trim() ||
      !createForm.gramWeightPerErpUnit.trim()
    ) {
      setState((c) => ({ ...c, error: '請完整填寫商品必要欄位' }))
      return
    }

    try {
      setState((c) => ({ ...c, saving: true, error: null }))
      const response = await fetch('/api/products', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          productCode: createForm.productCode,
          productName: createForm.productName,
          safetyStock: Number(createForm.safetyStock),
          maxStock: Number(createForm.maxStock),
          erpUnit: createForm.erpUnit,
          active: createForm.active,
          recipeId: createForm.recipeId ? Number(createForm.recipeId) : null,
          packagingErpUnit: createForm.packagingErpUnit || createForm.erpUnit,
          gramWeightPerErpUnit: Number(createForm.gramWeightPerErpUnit),
          packagingDescription: createForm.packagingDescription || null,
        }),
      })
      if (!response.ok) throw new Error(await readErrorMessage(response, '儲存商品失敗'))
      await loadRows()
      startCreate()
      setState((c) => ({ ...c, notice: '已新增商品' }))
    } catch (err) {
      setState((c) => ({ ...c, error: err instanceof Error ? err.message : '儲存商品失敗' }))
    } finally {
      setState((c) => ({ ...c, saving: false }))
    }
  }

  const saveEdit = async () => {
    if (!editingId) {
      return
    }
    if (
      !editForm.productCode.trim() ||
      !editForm.productName.trim() ||
      !editForm.erpUnit.trim() ||
      !editForm.safetyStock.trim() ||
      !editForm.maxStock.trim() ||
      !editForm.gramWeightPerErpUnit.trim()
    ) {
      setState((c) => ({ ...c, error: '請完整填寫商品必要欄位' }))
      return
    }

    try {
      setState((c) => ({ ...c, saving: true, error: null }))
      const response = await fetch(`/api/products/${editingId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          productCode: editForm.productCode,
          productName: editForm.productName,
          safetyStock: Number(editForm.safetyStock),
          maxStock: Number(editForm.maxStock),
          erpUnit: editForm.erpUnit,
          active: editForm.active,
          recipeId: editForm.recipeId ? Number(editForm.recipeId) : null,
          packagingErpUnit: editForm.packagingErpUnit || editForm.erpUnit,
          gramWeightPerErpUnit: Number(editForm.gramWeightPerErpUnit),
          packagingDescription: editForm.packagingDescription || null,
        }),
      })
      if (!response.ok) throw new Error(await readErrorMessage(response, '更新商品失敗'))
      await loadRows()
      cancelEdit()
      setState((c) => ({ ...c, notice: '已更新商品' }))
    } catch (err) {
      setState((c) => ({ ...c, error: err instanceof Error ? err.message : '更新商品失敗' }))
    } finally {
      setState((c) => ({ ...c, saving: false }))
    }
  }

  const importProducts = async (file: File) => {
    if (!file) {
      setImportError('請先選擇 Excel 檔案')
      return
    }

    try {
      setImporting(true)
      setImportError(null)
      setImportNotice(null)
      const formData = new FormData()
      formData.append('file', file)
      const response = await fetch('/api/products/import', {
        method: 'POST',
        body: formData,
      })
      if (!response.ok) {
        throw new Error(await readErrorMessage(response, '匯入商品失敗'))
      }
      const result = (await response.json()) as {
        sourceFileName: string
        importedCount: number
        createdCount: number
        updatedCount: number
        importedAt: string
      }
      setImportNotice(
        `已匯入 ${result.importedCount} 筆，新增 ${result.createdCount} 筆，更新 ${result.updatedCount} 筆`,
      )
      await loadRows()
    } catch (err) {
      setImportError(err instanceof Error ? err.message : '匯入商品失敗')
    } finally {
      setImporting(false)
    }
  }

  const onImportPick = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] ?? null
    event.target.value = ''
    if (!file) {
      return
    }
    setImportError(null)
    setImportNotice(null)
    void importProducts(file)
  }

  const exportAll = () => {
    downloadCsv(
      'products-export.csv',
      ['id', 'productCode', 'productName', 'safetyStock', 'maxStock', 'erpUnit', 'recipeCode', 'recipeName', 'packagingErpUnit', 'gramWeightPerErpUnit', 'packagingDescription', 'active'],
      state.rows.map((row) => [
        row.id,
        row.productCode,
        row.productName,
        row.safetyStock,
        row.maxStock,
        row.erpUnit,
        row.recipeCode ?? '',
        row.recipeName ?? '',
        row.packagingErpUnit ?? '',
        row.gramWeightPerErpUnit ?? '',
        row.packagingDescription ?? '',
        row.active ? '啟用' : '停用',
      ]),
    )
    setState((c) => ({ ...c, notice: '已匯出全部商品資料' }))
  }

  const destroy = async (row: Product) => {
    if (!window.confirm(`確定永久刪除商品 ${row.productCode} 嗎？`)) return
    try {
      setState((c) => ({ ...c, saving: true, error: null }))
      const response = await fetch(`/api/products/${row.id}`, { method: 'DELETE' })
      if (!response.ok && response.status !== 204) throw new Error(await readErrorMessage(response, '刪除商品失敗'))
      await loadRows()
      if (editingId === row.id) startCreate()
      setState((c) => ({ ...c, notice: '已刪除商品' }))
    } catch (err) {
      setState((c) => ({ ...c, error: err instanceof Error ? err.message : '刪除商品失敗' }))
    } finally {
      setState((c) => ({ ...c, saving: false }))
    }
  }

  return (
    <section className="product-management-page">
      <div className="top-bar unified-top-bar product-management-top-bar">
        <div className="page-title"><h1>商品主檔維護</h1><p>管理商品代號、安全/最大庫存、ERP 單位與包裝換算。</p></div>
        <div className="top-bar-actions global-status product-management-status"><span>總筆數：{formatNumber(state.rows.length)}</span><span className="status-divider" /><span className="status-dot" />{state.loading ? '載入中...' : '就緒'}</div>
      </div>

      <section className="workspace-section product-workspace-section">
        <div className="section-header product-section-header">
          <div className="section-title"><h2>新增商品資料</h2><p>建立新商品並設定庫存門檻與配方對應關係。</p></div>
          <button type="button" className="btn btn-sm" onClick={() => setShowCreatePanel((current) => !current)}>{showCreatePanel ? '隱藏新增資料' : '顯示新增資料'}</button>
        </div>
        {showCreatePanel ? (
          <div className="section-body product-section-body">
            <div className="product-reference-form-grid">
              <label className="field"><span>商品代號</span><input value={createForm.productCode} onChange={(e) => setCreateForm((c) => ({ ...c, productCode: e.target.value }))} /></label>
              <label className="field"><span>商品名稱</span><input value={createForm.productName} onChange={(e) => setCreateForm((c) => ({ ...c, productName: e.target.value }))} /></label>
              <label className="field"><span>安全庫存 / 最大庫存</span><div className="product-stock-pair"><input type="number" placeholder="Min" min="0" step="1" value={createForm.safetyStock} onChange={(e) => setCreateForm((c) => ({ ...c, safetyStock: e.target.value }))} /><b>/</b><input type="number" placeholder="Max" min="0" step="1" value={createForm.maxStock} onChange={(e) => setCreateForm((c) => ({ ...c, maxStock: e.target.value }))} /></div></label>
              <label className="field"><span>ERP 單位</span><input value={createForm.erpUnit} onChange={(e) => setCreateForm((c) => ({ ...c, erpUnit: e.target.value }))} /></label>
              <label className="field">
                <span>對應配方</span>
                <select value={createForm.recipeId} onChange={(e) => setCreateForm((c) => ({ ...c, recipeId: e.target.value }))}>
                  <option value="">請選擇配方</option>
                  {recipes.map((recipe) => (
                    <option key={recipe.id} value={recipe.id}>
                      {recipe.recipeCode} - {recipe.recipeName}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field"><span>換算比例 (g / 單位)</span><input type="number" placeholder="例：500" min="0.000001" step="0.001" value={createForm.gramWeightPerErpUnit} onChange={(e) => setCreateForm((c) => ({ ...c, gramWeightPerErpUnit: e.target.value }))} /></label>
              <label className="field"><span>包裝說明</span><input value={createForm.packagingDescription} onChange={(e) => setCreateForm((c) => ({ ...c, packagingDescription: e.target.value }))} /></label>
              <label className="field"><span>狀態</span><select value={createForm.active ? '1' : '0'} onChange={(e) => setCreateForm((c) => ({ ...c, active: e.target.value === '1' }))}><option value="1">啟用</option><option value="0">停用</option></select></label>
            </div>
            <div className="product-reference-actions">
              <button type="button" className="btn btn-primary" onClick={() => void saveCreate()} disabled={state.saving}>{state.saving ? '新增中...' : '新增商品'}</button>
              <button type="button" className="btn btn-danger product-clear-button" onClick={startCreate}>清空</button>
            </div>
          </div>
        ) : null}
      </section>

      <section className="workspace-section product-workspace-section">
        <div className="section-header product-section-header">
          <div className="section-title"><h2>資料作業</h2><p>批次匯入商品主檔或匯出系統資料。</p></div>
          <button type="button" className="btn btn-sm" onClick={() => setShowDataActionsPanel((current) => !current)}>{showDataActionsPanel ? '隱藏資料作業' : '顯示資料作業'}</button>
        </div>
        {showDataActionsPanel ? (
          <div className="section-body product-section-body">
            <div className="product-data-ops-grid">
              <div className="product-import-surface"><div className="product-operation-title">Excel 批次匯入</div><div className="product-import-file-row"><input ref={importInputRef} className="form-control" type="file" accept=".xlsx,.xls" onChange={onImportPick} /><button type="button" className="btn btn-primary" onClick={() => importInputRef.current?.click()} disabled={importing}>{importing ? '匯入中...' : '開始匯入'}</button></div></div>
              <div className="product-export-surface"><div className="product-operation-title">將所有商品資料下載至本機</div><button type="button" className="btn product-export-button" onClick={exportAll}><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><path d="m7 10 5 5 5-5" /><path d="M12 15V3" /></svg>匯出 CSV</button></div>
            </div>
            {importNotice ? <div className="notice-banner">{importNotice}</div> : null}
            {importError ? <div className="error-banner">{importError}</div> : null}
          </div>
        ) : null}
      </section>

      <section className="workspace-section product-workspace-section product-list-workspace">
        <div className="section-header product-list-header"><div className="section-title"><h2>商品清單</h2><p>這裡同時可看安全庫存、最大庫存、ERP 單位與包裝換算。</p></div></div>
        <div className="product-search-row"><input className="search-input" value={searchText} onChange={(event) => setSearchText(event.target.value)} placeholder="搜尋代號、名稱、門檻、單位、配方、包裝說明、狀態" /><button type="button" className="btn btn-primary" onClick={() => setSearchText(searchText.trim())}>查詢</button><button type="button" className="btn" onClick={() => setSearchText('')}>重設</button></div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>代號</th>
                <th>名稱</th>
                <th>安全/最大庫存</th>
                <th>ERP 單位</th>
                <th>配方</th>
                <th>換算</th>
                <th>包裝說明</th>
                <th>狀態</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {filteredRows.map((row) => (
                <tr key={row.id} className={editingId === row.id ? 'selected-row' : ''}>
                  <td>
                    {editingId === row.id ? (
                      <input
                        value={editForm.productCode}
                        onChange={(event) => setEditForm((current) => ({ ...current, productCode: event.target.value }))}
                      />
                    ) : (
                      <span className="code-tag">{row.productCode}</span>
                    )}
                  </td>
                  <td>
                    {editingId === row.id ? (
                      <input
                        value={editForm.productName}
                        onChange={(event) => setEditForm((current) => ({ ...current, productName: event.target.value }))}
                      />
                    ) : (
                      <strong>{row.productName}</strong>
                    )}
                  </td>
                  <td>
                    {editingId === row.id ? (
                      <div className="inline-grid-two">
                        <input
                          type="number"
                          min="0"
                          step="1"
                          value={editForm.safetyStock}
                          onChange={(event) => setEditForm((current) => ({ ...current, safetyStock: event.target.value }))}
                        />
                        <input
                          type="number"
                          min="0"
                          step="1"
                          value={editForm.maxStock}
                          onChange={(event) => setEditForm((current) => ({ ...current, maxStock: event.target.value }))}
                        />
                      </div>
                    ) : (
                      <span className="num">{row.safetyStock} / {row.maxStock}</span>
                    )}
                  </td>
                  <td>
                    {editingId === row.id ? (
                      <input
                        value={editForm.erpUnit}
                        onChange={(event) => setEditForm((current) => ({ ...current, erpUnit: event.target.value }))}
                      />
                    ) : (
                      <span className="product-center-cell">{row.erpUnit}</span>
                    )}
                  </td>
                  <td>
                    {editingId === row.id ? (
                      <select
                        value={editForm.recipeId}
                        onChange={(event) => setEditForm((current) => ({ ...current, recipeId: event.target.value }))}
                      >
                        <option value="">未設定</option>
                        {recipes.map((recipe) => (
                          <option key={recipe.id} value={recipe.id}>
                            {recipe.recipeCode} - {recipe.recipeName}
                          </option>
                        ))}
                      </select>
                    ) : (
                      row.recipeName ?? '-'
                    )}
                  </td>
                  <td>
                    {editingId === row.id ? (
                      <div className="inline-grid-two">
                        <input
                          value={editForm.packagingErpUnit}
                          onChange={(event) => setEditForm((current) => ({ ...current, packagingErpUnit: event.target.value }))}
                        />
                        <input
                          type="number"
                          min="0.000001"
                          step="0.001"
                          value={editForm.gramWeightPerErpUnit}
                          onChange={(event) => setEditForm((current) => ({ ...current, gramWeightPerErpUnit: event.target.value }))}
                        />
                      </div>
                    ) : (
                      <span className="num">{row.gramWeightPerErpUnit ? `${formatDecimal(row.gramWeightPerErpUnit)} g / ${row.packagingErpUnit}` : '-'}</span>
                    )}
                  </td>
                  <td>
                    {editingId === row.id ? (
                      <input
                        value={editForm.packagingDescription}
                        onChange={(event) => setEditForm((current) => ({ ...current, packagingDescription: event.target.value }))}
                      />
                    ) : (
                      row.packagingDescription ?? '-'
                    )}
                  </td>
                  <td>
                    {editingId === row.id ? (
                      <select
                        value={editForm.active ? '1' : '0'}
                        onChange={(event) => setEditForm((current) => ({ ...current, active: event.target.value === '1' }))}
                      >
                        <option value="1">啟用</option>
                        <option value="0">停用</option>
                      </select>
                    ) : (
                      <DataStatus active={row.active} />
                    )}
                  </td>
                  <td>
                    <div className="table-actions product-table-actions">
                      {editingId === row.id ? (
                        <>
                          <button type="button" className="btn btn-sm" onClick={() => void saveEdit()} disabled={state.saving}>
                            {state.saving ? '儲存中' : '儲存'}
                          </button>
                          <button type="button" className="btn btn-sm" onClick={cancelEdit} disabled={state.saving}>
                            取消
                          </button>
                        </>
                      ) : (
                        <>
                          <button type="button" className="btn btn-sm" onClick={() => startEdit(row)} disabled={state.saving}>
                            編輯
                          </button>
                          <button
                            type="button"
                            className="table-action-btn danger"
                            onClick={() => void destroy(row)}
                            title="刪除"
                            aria-label={`刪除商品 ${row.productCode}`}
                            disabled={state.saving}
                          >
                            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                              <path d="M3 6h18" /><path d="M8 6V4h8v2" /><path d="M6 6l1 14h10l1-14" />
                            </svg>
                            刪除
                          </button>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
              {!filteredRows.length ? <tr><td colSpan={9}><div className="empty-table">{state.rows.length ? '沒有符合搜尋條件的商品資料。' : '目前沒有商品資料。'}</div></td></tr> : null}
            </tbody>
          </table>
        </div>
      </section>
      {state.error ? <div className="error-banner product-page-message">{state.error}</div> : null}
      {state.notice ? <div className="notice-banner product-page-message">{state.notice}</div> : null}
    </section>
  )
}

function App() {
  const { page, navigate } = usePageRouting()

  return (
    <div className="app-layout procurement-app-layout">
      <AppSidebar activePage={page} navigate={navigate} />
      <main className="page-shell">
        {page === 'calculator' ? <CalculationPanel /> : null}
        {page === 'materials' ? <MaterialManager /> : null}
        {page === 'products' ? <ProductManager /> : null}
        {page === 'recipes' ? <RecipeManager /> : null}
        {page === 'items' ? <RecipeVersionItemManager /> : null}
        {page === 'productionAdvice' ? <ProductionProcurementPage /> : null}
        {page === 'inventory' ? <InventoryImportPage /> : null}
      </main>
    </div>
  )
}

export default App
