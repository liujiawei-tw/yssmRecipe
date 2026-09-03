import { Fragment, useEffect, useMemo, useState } from 'react'
import { formatDecimal, readErrorMessage } from './shared/format'

type MaterialRequirement = {
  materialId: number
  materialCode: string
  materialName: string
  recipeVersionId: number
  ratio: string
  percentage: string
  requiredWeightG: string
}

type ProductionPlan = {
  id: number
  productId: number
  productCode: string
  productName: string
  currentStock: number
  safetyStock: number
  targetStock: number
  suggestedQuantity: number
  plannedQuantity: number
  calculationMode: 'SYSTEM' | 'MANUAL'
  erpUnit: string
  gramWeightPerErpUnit: string
  calculationWeightG: string
  recipeVersionId: number
  recipeName: string
  versionDate: string
  materialRequirements: MaterialRequirement[]
}

type PurchaseSuggestionSource = {
  productionPlanId: number
  materialRequirementId: number
  productId: number
  productCode: string
  productName: string
  recipeId: number
  recipeCode: string
  recipeName: string
  recipeVersionId: number
  recipeVersionDate: string
  plannedQuantity: number
  calculationMode: string
  calculationWeightG: string
  requiredWeightG: string
}

type PurchaseSuggestionItem = {
  id: number
  materialId: number
  materialCode: string
  materialName: string
  requiredWeightG: string
  stockWeightG: string
  shortageWeightG: string
  purchaseSuggestionWeightG: string
  inventoryAvailable: boolean
  inventoryImportedAt: string | null
  inventorySourceFileName: string | null
  sources: PurchaseSuggestionSource[]
}

type PurchaseSuggestionAnalysis = {
  id: number
  generatedAt: string
  totalRequiredWeightG: string
  totalStockWeightG: string
  totalShortageWeightG: string
  items: PurchaseSuggestionItem[]
}

const STORAGE_KEYS = {
  productionClearWatermark: 'production-procurement:production-clear-watermark',
  analysisClearWatermark: 'production-procurement:analysis-clear-watermark',
} as const

const formatCalculationMode = (value: string) => (value === 'SYSTEM' ? '系統計算' : value === 'MANUAL' ? '手動輸入' : value)

const formatDateTime = (value: string) => new Date(value).toLocaleString('zh-TW')

const csvEscape = (value: string | number | boolean | null | undefined) => {
  const text = value == null ? '' : String(value)
  return `"${text.replace(/"/g, '""')}"`
}

const toCsv = (headers: string[], rows: Array<Array<string | number | boolean | null | undefined>>) =>
  [headers, ...rows].map((row) => row.map(csvEscape).join(',')).join('\r\n')

const downloadText = (text: string, fileName: string) => {
  const url = window.URL.createObjectURL(new Blob(['\ufeff', text], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.URL.revokeObjectURL(url)
}

const dedupeBy = <T,>(rows: T[], getKey: (row: T) => string | number) =>
  Array.from(new Map(rows.map((row) => [getKey(row), row])).values())

const readStoredNumber = (key: string) => {
  const raw = window.localStorage.getItem(key)
  if (!raw) {
    return null
  }
  const value = Number(raw)
  return Number.isFinite(value) ? value : null
}

const writeStoredNumber = (key: string, value: number | null) => {
  if (value == null) {
    window.localStorage.removeItem(key)
    return
  }
  window.localStorage.setItem(key, String(value))
}

function ProductionProcurementPage() {
  const [productionPlans, setProductionPlans] = useState<ProductionPlan[]>([])
  const [expandedProductionIds, setExpandedProductionIds] = useState<number[]>([])
  const [productionLoading, setProductionLoading] = useState(false)
  const [productionExporting, setProductionExporting] = useState(false)
  const [productionError, setProductionError] = useState<string | null>(null)
  const [productionClearWatermark, setProductionClearWatermark] = useState<number | null>(null)

  const [latestAnalysis, setLatestAnalysis] = useState<PurchaseSuggestionAnalysis | null>(null)
  const [expandedAnalysisItemIds, setExpandedAnalysisItemIds] = useState<number[]>([])
  const [analysisLoading, setAnalysisLoading] = useState(false)
  const [analysisExporting, setAnalysisExporting] = useState(false)
  const [analysisError, setAnalysisError] = useState<string | null>(null)
  const [analysisNotice, setAnalysisNotice] = useState<string | null>(null)
  const [analysisClearWatermark, setAnalysisClearWatermark] = useState<number | null>(null)

  useEffect(() => {
    setProductionClearWatermark(readStoredNumber(STORAGE_KEYS.productionClearWatermark))
    setAnalysisClearWatermark(readStoredNumber(STORAGE_KEYS.analysisClearWatermark))
  }, [])

  const visibleProductionPlans = useMemo(() => {
    const latestPlans = dedupeBy(productionPlans, (plan) => plan.productCode)
    if (!latestPlans.length) {
      return []
    }
    const latestBatchId = latestPlans[0]?.id ?? null
    if (productionClearWatermark != null && latestBatchId === productionClearWatermark) {
      return []
    }
    return latestPlans
  }, [productionClearWatermark, productionPlans])

  const visibleAnalysis = useMemo(() => {
    if (!latestAnalysis) {
      return null
    }
    if (analysisClearWatermark != null && latestAnalysis.id === analysisClearWatermark) {
      return null
    }
    return latestAnalysis
  }, [analysisClearWatermark, latestAnalysis])

  const analysisSummary = useMemo(() => {
    if (!visibleAnalysis) {
      return {
        totalItems: 0,
        totalRequired: 0,
        totalStock: 0,
        totalShortage: 0,
        expandedItems: 0,
      }
    }

    return {
      totalItems: visibleAnalysis.items.length,
      totalRequired: Number(visibleAnalysis.totalRequiredWeightG),
      totalStock: Number(visibleAnalysis.totalStockWeightG),
      totalShortage: Number(visibleAnalysis.totalShortageWeightG),
      expandedItems: expandedAnalysisItemIds.length,
    }
  }, [expandedAnalysisItemIds.length, visibleAnalysis])

  const loadProductionPlans = async () => {
    try {
      setProductionLoading(true)
      setProductionError(null)
      const response = await fetch('/api/production-plans/latest')
      if (!response.ok) {
        throw new Error(await readErrorMessage(response, '無法載入生產計畫'))
      }

      const data = (await response.json()) as ProductionPlan[]
      setProductionPlans(data.map((plan) => ({
        ...plan,
        materialRequirements: dedupeBy(plan.materialRequirements, (item) => item.materialId),
      })))
      setExpandedProductionIds([])
    } catch (err) {
      setProductionError(err instanceof Error ? err.message : '無法載入生產計畫')
    } finally {
      setProductionLoading(false)
    }
  }

  const loadLatestAnalysis = async () => {
    try {
      setAnalysisLoading(true)
      setAnalysisError(null)
      const response = await fetch('/api/purchase-suggestions/latest')
      if (!response.ok) {
        if (response.status === 404) {
          setLatestAnalysis(null)
          setExpandedAnalysisItemIds([])
          return
        }
        throw new Error(await readErrorMessage(response, '無法載入最新請購分析'))
      }

      const analysis = (await response.json()) as PurchaseSuggestionAnalysis
      const normalized = {
        ...analysis,
        items: analysis.items.map((item) => ({
          ...item,
          sources: dedupeBy(item.sources, (source) => source.materialRequirementId),
        })),
      }
      setLatestAnalysis(normalized)
      setExpandedAnalysisItemIds([])
    } catch (err) {
      setAnalysisError(err instanceof Error ? err.message : '無法載入最新請購分析')
    } finally {
      setAnalysisLoading(false)
    }
  }

  useEffect(() => {
    void loadProductionPlans()
    void loadLatestAnalysis()
  }, [])

  const clearProductionPlans = () => {
    const watermark = productionPlans[0]?.id ?? null
    setProductionClearWatermark(watermark)
    writeStoredNumber(STORAGE_KEYS.productionClearWatermark, watermark)
    setExpandedProductionIds([])
    setProductionError(null)
  }

  const clearAnalysis = () => {
    const watermark = latestAnalysis?.id ?? null
    setAnalysisClearWatermark(watermark)
    writeStoredNumber(STORAGE_KEYS.analysisClearWatermark, watermark)
    setExpandedAnalysisItemIds([])
    setAnalysisError(null)
    setAnalysisNotice(null)
  }

  const exportProductionPlans = async () => {
    try {
      setProductionExporting(true)
      setProductionError(null)
      const response = await fetch('/api/production-plans/latest')
      if (!response.ok) {
        throw new Error(await readErrorMessage(response, '無法匯出生產計畫'))
      }

      const plans = (await response.json()) as ProductionPlan[]
      const csv = toCsv(
        ['生產計畫編號', '商品代號', '商品名稱', '目前庫存', '安全庫存', '目標庫存', '規劃數量', 'ERP 單位', '配方', '版本日期', '計算模式'],
        plans.map((plan) => [
          plan.id,
          plan.productCode,
          plan.productName,
          plan.currentStock,
          plan.safetyStock,
          plan.targetStock,
          plan.plannedQuantity,
          plan.erpUnit,
          plan.recipeName,
          plan.versionDate,
          formatCalculationMode(plan.calculationMode),
        ]),
      )
      downloadText(csv, 'production-plans.csv')
    } catch (err) {
      setProductionError(err instanceof Error ? err.message : '無法匯出生產計畫')
    } finally {
      setProductionExporting(false)
    }
  }

  const exportAnalysisCsv = () => {
    if (!visibleAnalysis) {
      setAnalysisError('目前沒有可匯出的最新請購分析')
      return
    }

    try {
      setAnalysisExporting(true)
      setAnalysisError(null)

      const csv = toCsv(
        ['分析編號', '產生時間', '原料代號', '原料名稱', '需求(g)', '庫存(g)', '缺料(g)', '請購建議(g)', '來源數'],
        visibleAnalysis.items.map((item) => [
          visibleAnalysis.id,
          formatDateTime(visibleAnalysis.generatedAt),
          item.materialCode,
          item.materialName,
          item.requiredWeightG,
          item.stockWeightG,
          item.shortageWeightG,
          item.purchaseSuggestionWeightG,
          item.sources.length,
        ]),
      )

      downloadText(csv, `purchase-suggestion-${visibleAnalysis.id}.csv`)
      setAnalysisNotice(`已匯出 CSV，分析編號 ${visibleAnalysis.id}`)
    } catch (err) {
      setAnalysisError(err instanceof Error ? err.message : '匯出 CSV 失敗')
    } finally {
      setAnalysisExporting(false)
    }
  }

  const toggleProductionPlan = (planId: number) => {
    setExpandedProductionIds((current) =>
      current.includes(planId) ? current.filter((id) => id !== planId) : [...current, planId],
    )
  }

  const toggleAnalysisItem = (itemId: number) => {
    setExpandedAnalysisItemIds((current) =>
      current.includes(itemId) ? current.filter((id) => id !== itemId) : [...current, itemId],
    )
  }

  return (
    <section className="management-shell production-procurement-shell">
      <header className="top-bar unified-top-bar procurement-topbar">
        <div className="page-title">
          <h1>生產請購決策工作台</h1>
          <p>系統自動聯動最新匯入之分析編號 {visibleAnalysis ? `#${visibleAnalysis.id}` : '--'}，提供零時差的補貨與生產排程對比。</p>
        </div>
        <div className="top-bar-actions procurement-engine-status"><span />{productionLoading || analysisLoading ? '資料同步中' : '分析引擎就緒'}</div>
      </header>

      <div className="procurement-metrics">
        <div className="procurement-metric"><span>分析編號</span><strong>{visibleAnalysis ? `#${visibleAnalysis.id}` : '--'}</strong></div>
        <div className="procurement-metric"><span>產生時間</span><strong className="metric-date">{visibleAnalysis ? formatDateTime(visibleAnalysis.generatedAt) : '--'}</strong></div>
        <div className="procurement-metric"><span>總需求量</span><strong>{visibleAnalysis ? `${formatDecimal(visibleAnalysis.totalRequiredWeightG)} g` : '--'}</strong></div>
        <div className="procurement-metric"><span>現有總庫存</span><strong>{visibleAnalysis ? `${formatDecimal(visibleAnalysis.totalStockWeightG)} g` : '--'}</strong></div>
        <div className="procurement-metric alert"><span>總缺料量</span><strong>{visibleAnalysis ? `${formatDecimal(visibleAnalysis.totalShortageWeightG)} g` : '--'}</strong></div>
        <div className="procurement-metric"><span>異常項目</span><strong>{visibleAnalysis ? `${analysisSummary.totalItems} 項` : '--'}</strong></div>
      </div>

      <section className="procurement-workspace-section">
        <div className="procurement-section-header">
          <div><h2>請購決策與來源追溯矩陣</h2><p>點擊展開可直接檢視該原料背後的生產計劃與消耗來源明細。</p></div>
          <div className="procurement-toolbar">
            <button type="button" className="procurement-btn" onClick={() => void loadLatestAnalysis()}>重新整理</button>
            <button type="button" className="procurement-btn primary" onClick={exportAnalysisCsv} disabled={analysisExporting || !visibleAnalysis}>{analysisExporting ? '匯出中...' : '匯出 CSV'}</button>
            <button type="button" className="procurement-btn danger" onClick={clearAnalysis} disabled={!visibleAnalysis}>一鍵清空</button>
          </div>
        </div>
        {analysisError ? <div className="error-banner">{analysisError}</div> : null}
        {analysisNotice ? <div className="notice-banner">{analysisNotice}</div> : null}

        <div className="table-wrap procurement-table-wrap">
          <table className="merged-table procurement-merged-table">
            <thead><tr><th>操作</th><th>原料品項</th><th className="num">需求量</th><th className="num">現有庫存</th><th className="num">缺料數量</th><th className="num">建議請購</th><th>來源狀態</th></tr></thead>
            <tbody>
              {visibleAnalysis ? visibleAnalysis.items.map((item) => {
                const expanded = expandedAnalysisItemIds.includes(item.id)
                return <Fragment key={item.id}>
                  <tr className={expanded ? 'selected-row' : ''}>
                    <td><button type="button" className="procurement-btn compact table-collapse-btn" onClick={() => toggleAnalysisItem(item.id)} aria-expanded={expanded}>{expanded ? '收合' : '展開'}</button></td>
                    <td><span className="procurement-code">{item.materialCode}</span><strong>{item.materialName}</strong></td>
                    <td className="num">{formatDecimal(item.requiredWeightG)} g</td>
                    <td className="num">{formatDecimal(item.stockWeightG)} g<span className="subtext">{item.inventoryAvailable ? '盤點確認' : '無盤點資料'}</span></td>
                    <td className="num"><span className="procurement-danger-badge">{formatDecimal(item.shortageWeightG)} g</span></td>
                    <td className="num"><strong>{formatDecimal(item.purchaseSuggestionWeightG)} g</strong></td>
                    <td><strong>{item.sources.length} 筆來源</strong><span className="procurement-link">{expanded ? '已展開' : '檢視明細'}</span></td>
                  </tr>
                  {expanded ? <tr className="merged-detail-row"><td colSpan={7}><div className="procurement-detail-panel"><strong className="detail-kicker">[{item.materialCode} {item.materialName}] 需求來源拆解明細</strong><div className="table-wrap nested-table-wrap"><table className="nested-table purchase-table"><thead><tr><th>來源商品</th><th>配方名稱</th><th>版本日期</th><th className="num">規劃產量</th><th className="num">總重換算</th><th className="num">原料需求</th></tr></thead><tbody>{item.sources.map((source) => <tr key={source.materialRequirementId}><td><span className="procurement-code">{source.productCode}</span><strong>{source.productName}</strong></td><td>{source.recipeName}<span className="subtext">{source.recipeCode}</span></td><td>{source.recipeVersionDate}</td><td className="num">{formatDecimal(source.plannedQuantity)}</td><td className="num">{formatDecimal(source.calculationWeightG)} g</td><td className="num">{formatDecimal(source.requiredWeightG)} g</td></tr>)}{!item.sources.length ? <tr><td colSpan={6}><div className="empty-table">這筆原料沒有來源資料。</div></td></tr> : null}</tbody></table></div></div></td></tr> : null}
                </Fragment>
              }) : <tr><td colSpan={7}><div className="empty-table">目前沒有可顯示的請購分析。</div></td></tr>}
            </tbody>
          </table>
        </div>
      </section>

      <section className="procurement-workspace-section">
        <div className="merged-section-header">
          <div className="card-title">
            <h2>生產計畫排程看板</h2>
            <p>即時掌握最新匯入之生產目標與配方版本佔比。</p>
          </div>
          <div className="procurement-toolbar">
            <button type="button" className="procurement-btn" onClick={() => void loadProductionPlans()}>重新整理</button>
            <button
              type="button"
              className="procurement-btn primary"
              onClick={() => void exportProductionPlans()}
              disabled={productionExporting || !visibleProductionPlans.length}
            >
              {productionExporting ? '匯出中...' : '匯出 CSV'}
            </button>
            <button type="button" className="procurement-btn danger" onClick={clearProductionPlans} disabled={!visibleProductionPlans.length}>一鍵清空</button>
          </div>
        </div>
        {productionError ? <div className="error-banner">{productionError}</div> : null}

        <div className="table-wrap procurement-table-wrap">
          <table className="merged-table production-merged-table">
            <thead><tr><th>操作</th><th>生產商品計畫</th><th>配方版本資訊</th><th>安全/目標庫存</th><th>規劃生產資訊</th><th>計算狀態</th></tr></thead>
            <tbody>
              {visibleProductionPlans.map((plan) => {
                const expanded = expandedProductionIds.includes(plan.id)

                return (
                  <Fragment key={plan.id}>
                    <tr className={expanded ? 'selected-row' : ''}>
                      <td>
                        <button
                          type="button"
                          className="mini-btn merged-toggle-btn table-collapse-btn"
                          onClick={() => toggleProductionPlan(plan.id)}
                          aria-expanded={expanded}
                        >
                          {expanded ? '收合' : '展開'}
                        </button>
                      </td>
                      <td>
                        <strong>{plan.productCode}</strong>
                        <span>{plan.productName}</span>
                      </td>
                      <td>
                        <strong>{plan.recipeName}</strong>
                        <span>{plan.versionDate}</span>
                      </td>
                      <td>
                        <strong>
                          {formatDecimal(plan.currentStock)} {plan.erpUnit}
                        </strong>
                        <span>
                          安全 {formatDecimal(plan.safetyStock)} / 目標 {formatDecimal(plan.targetStock)}
                        </span>
                      </td>
                      <td>
                        <strong>
                          {formatDecimal(plan.plannedQuantity)} {plan.erpUnit}
                        </strong>
                        <span>{formatDecimal(plan.calculationWeightG)} g</span>
                      </td>
                      <td>
                      <strong>{formatCalculationMode(plan.calculationMode)}</strong>
                        <span>{plan.materialRequirements.length} 筆原料</span>
                      </td>
                    </tr>
                    {expanded ? (
                      <tr className="merged-detail-row production-detail-row">
                        <td colSpan={6}>
                          <div className="merged-detail-panel">
                            <div className="result-summary merged-summary-grid">
                              <div>
                                <span>商品</span>
                                <strong>{plan.productCode}</strong>
                              </div>
                              <div>
                                <span>配方</span>
                                <strong>{plan.recipeName}</strong>
                              </div>
                              <div>
                                <span>規劃數量</span>
                                <strong>
                                  {formatDecimal(plan.plannedQuantity)} {plan.erpUnit}
                                </strong>
                              </div>
                              <div>
                                <span>換算重量</span>
                                <strong>{formatDecimal(plan.calculationWeightG)} g</strong>
                              </div>
                            </div>

                            <div className="table-wrap nested-table-wrap">
                              <table className="nested-table">
                                <thead>
                                  <tr>
                                    <th>原料</th>
                                    <th>比例</th>
                                    <th>百分比</th>
                                    <th>需求重量</th>
                                  </tr>
                                </thead>
                                <tbody>
                                  {plan.materialRequirements.map((item) => (
                                    <tr key={`${plan.id}-${item.materialId}`}>
                                      <td>
                                        <strong>{item.materialCode}</strong>
                                        <span>{item.materialName}</span>
                                      </td>
                                      <td>{formatDecimal(item.ratio)}</td>
                                      <td>{formatDecimal(item.percentage)}%</td>
                                      <td>{formatDecimal(item.requiredWeightG)} g</td>
                                    </tr>
                                  ))}
                                  {!plan.materialRequirements.length ? (
                                    <tr>
                                      <td colSpan={4}>
                                        <div className="empty-table">這筆生產計畫沒有原料明細。</div>
                                      </td>
                                    </tr>
                                  ) : null}
                                </tbody>
                              </table>
                            </div>
                          </div>
                        </td>
                      </tr>
                    ) : null}
                  </Fragment>
                )
              })}
              {!visibleProductionPlans.length ? (
                <tr>
                  <td colSpan={6}>
                    <div className="empty-table">目前沒有可顯示的生產計畫。</div>
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </section>
    </section>
  )
}

export default ProductionProcurementPage
