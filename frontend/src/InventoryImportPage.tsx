import { useRef, useState } from 'react'
import { readErrorMessage } from './shared/format'

type InventoryImportResponse = {
  sourceFileName: string
  inventoryType: string
  importedCount: number
  importedAt: string
}

type InventoryFileKey = 'product' | 'material'

const importConfigs: Record<InventoryFileKey, { title: string; description: string; fieldLabel: string; endpoint: string }> = {
  product: {
    title: '商品庫存匯入',
    description: '匯入 ERP 商品代號與現有庫存數量，建立商品庫存快照。',
    fieldLabel: '必需欄位：product_code、stock_quantity',
    endpoint: '/api/inventory-imports/product-stock',
  },
  material: {
    title: '原料盤點匯入',
    description: '匯入現場原料代號與盤點數量，供請購與缺料判斷使用。',
    fieldLabel: '必需欄位：material_code、stock_quantity',
    endpoint: '/api/inventory-imports/material-stock',
  },
}

function UploadSurface({
  type,
  file,
  onFileChange,
}: {
  type: InventoryFileKey
  file: File | null
  onFileChange: (file: File | null) => void
}) {
  const config = importConfigs[type]
  const inputRef = useRef<HTMLInputElement | null>(null)

  return (
    <div className="inventory-import-surface">
      <div className="inventory-operation-title">{config.title}</div>
      <div className="inventory-operation-description">{config.description}</div>
      <div className="inventory-file-row">
        <input
          ref={inputRef}
          type="file"
          accept=".csv,.xlsx,.xls"
          onChange={(event) => onFileChange(event.target.files?.[0] ?? null)}
        />
        <span className="inventory-file-name">{file ? file.name : '請選擇 CSV / Excel 檔案'}</span>
        <button type="button" className="btn" onClick={() => inputRef.current?.click()}>
          選擇檔案
        </button>
      </div>
      <div className="inventory-operation-note">{config.fieldLabel}</div>
    </div>
  )
}

export function InventoryImportPage() {
  const [files, setFiles] = useState<Record<InventoryFileKey, File | null>>({ product: null, material: null })
  const [uploading, setUploading] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const importBoth = async () => {
    if (!files.product || !files.material) {
      setError('請先選擇商品庫存與原料盤點兩個檔案')
      setNotice(null)
      return
    }

    try {
      setUploading(true)
      setError(null)
      setNotice(null)
      const responses = await Promise.all(
        (['product', 'material'] as InventoryFileKey[]).map(async (type) => {
          const formData = new FormData()
          formData.append('file', files[type] as File)
          const response = await fetch(importConfigs[type].endpoint, { method: 'POST', body: formData })
          if (!response.ok) {
            throw new Error(await readErrorMessage(response, `${importConfigs[type].title}失敗`))
          }
          return (await response.json()) as InventoryImportResponse
        }),
      )
      setNotice(`商品庫存已匯入 ${responses[0].importedCount} 筆，原料盤點已匯入 ${responses[1].importedCount} 筆`)
    } catch (err) {
      setError(err instanceof Error ? err.message : '庫存資料匯入失敗')
    } finally {
      setUploading(false)
    }
  }

  const clearFiles = () => {
    setFiles({ product: null, material: null })
    setNotice(null)
    setError(null)
  }

  return (
    <section className="inventory-import-page">
      <div className="top-bar unified-top-bar inventory-top-bar">
        <div className="page-title">
          <h1>ERP 商品庫存 / 原料盤點匯入</h1>
          <p>一次選擇兩份資料並匯入，讓後續生產計畫與缺料判斷使用同一批最新庫存來源。</p>
        </div>
      </div>

      <section className="workspace-section inventory-workspace-section inventory-data-workspace">
        <div className="section-header inventory-section-header">
          <div className="section-title">
            <h2>資料作業</h2>
            <p>請同時選擇商品庫存與原料盤點檔案，再一次完成匯入。</p>
          </div>
        </div>

        <div className="section-body inventory-section-body">
          <div className="inventory-data-ops-grid">
            <UploadSurface type="product" file={files.product} onFileChange={(file) => setFiles((current) => ({ ...current, product: file }))} />
            <UploadSurface type="material" file={files.material} onFileChange={(file) => setFiles((current) => ({ ...current, material: file }))} />
          </div>

          <div className="inventory-data-actions">
            <button type="button" className="btn btn-primary" onClick={() => void importBoth()} disabled={uploading}>
              {uploading ? '匯入中...' : '一次匯入兩份資料'}
            </button>
            <button type="button" className="btn" onClick={clearFiles} disabled={uploading}>
              清空
            </button>
          </div>

          {notice ? <div className="notice-banner">{notice}</div> : null}
          {error ? <div className="error-banner">{error}</div> : null}
        </div>
      </section>
    </section>
  )
}
