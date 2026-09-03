import { useRef, useState } from 'react'
import { readErrorMessage } from './shared/format'

type InventoryImportResponse = {
  sourceFileName: string
  inventoryType: string
  importedCount: number
  importedAt: string
}

function UploadPanel({
  title,
  description,
  fieldName,
  endpoint,
  accept,
  className,
}: {
  title: string
  description: string
  fieldName: string
  endpoint: string
  accept: string
  className?: string
}) {
  const [file, setFile] = useState<File | null>(null)
  const [uploading, setUploading] = useState(false)
  const [result, setResult] = useState<InventoryImportResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const inputRef = useRef<HTMLInputElement | null>(null)

  const upload = async () => {
    if (!file) {
      setError('請先選擇檔案')
      return
    }

    try {
      setUploading(true)
      setError(null)
      const formData = new FormData()
      formData.append(fieldName, file)
      const response = await fetch(endpoint, {
        method: 'POST',
        body: formData,
      })
      if (!response.ok) {
        throw new Error(await readErrorMessage(response, '匯入失敗'))
      }
      setResult((await response.json()) as InventoryImportResponse)
    } catch (err) {
      setResult(null)
      setError(err instanceof Error ? err.message : '匯入失敗')
    } finally {
      setUploading(false)
    }
  }

  const clearFile = () => {
    setFile(null)
    setResult(null)
    setError(null)
    if (inputRef.current) {
      inputRef.current.value = ''
    }
  }

  return (
    <section className={`workspace-section inventory-workspace-section ${className ?? ''}`.trim()}>
      <div className="section-title">
        <h2>{title}</h2>
        <p>{description}</p>
      </div>

      <label className="upload-zone">
        <input
          ref={inputRef}
          type="file"
          accept={accept}
          onChange={(event) => {
            setFile(event.target.files?.[0] ?? null)
            setResult(null)
            setError(null)
          }}
        />
        <span className="upload-icon" aria-hidden="true">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
            <polyline points="17 8 12 3 7 8" />
            <line x1="12" y1="3" x2="12" y2="15" />
          </svg>
        </span>
        <h3>{file ? file.name : '點一下或拖曳檔案到這裡'}</h3>
        <p>支援 .csv, .xlsx, .xls 等逗號分隔或 Excel 檔</p>
      </label>

      <div className="inventory-action-row">
        <button type="button" className="btn btn-primary" onClick={() => void upload()} disabled={uploading}>
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <polygon points="5 3 19 12 5 21 5 3" />
          </svg>
          {uploading ? '上傳中...' : '開始匯入'}
        </button>
        <button type="button" className="btn" onClick={clearFile}>
          清空
        </button>
      </div>

      {error ? <div className="error-banner">{error}</div> : null}

      {result ? (
        <div className="notice-banner">
          已匯入 {result.importedCount} 筆，來源檔案：{result.sourceFileName}，時間：
          {new Date(result.importedAt).toLocaleString('zh-TW')}
        </div>
      ) : null}
    </section>
  )
}

export function InventoryImportPage() {
  const [activeTab, setActiveTab] = useState<'product' | 'material'>('product')
  const activeConfig =
    activeTab === 'product'
      ? {
          title: '商品庫存匯入',
          description: '檔案欄位建議使用商品代號與現有庫存數量，系統會把結果寫成商品庫存快照。',
          endpoint: '/api/inventory-imports/product-stock',
        }
      : {
          title: '原料盤點匯入',
          description: '檔案欄位建議使用原料代號與盤點數量，匯入後會作為請購與缺料判斷來源。',
          endpoint: '/api/inventory-imports/material-stock',
        }

  return (
    <section className="inventory-import-page">
      <div className="top-bar unified-top-bar inventory-top-bar">
        <div className="page-title">
          <h1>ERP 商品庫存 / 原料盤點匯入</h1>
          <p>先把 ERP 匯出的商品庫存與現場原料盤點匯進來，後續生產計畫與缺料判斷才會有可靠來源。</p>
        </div>

        <div className="top-bar-actions segmented-control" role="tablist" aria-label="庫存匯入類型">
          <button
            type="button"
            className={activeTab === 'product' ? 'active' : ''}
            onClick={() => setActiveTab('product')}
          >
            商品庫存匯入
          </button>
          <button
            type="button"
            className={activeTab === 'material' ? 'active' : ''}
            onClick={() => setActiveTab('material')}
          >
            原料盤點匯入
          </button>
        </div>
      </div>

      <section className="workspace-grid inventory-workspace-grid">
        <UploadPanel
          key={activeTab}
          title={activeConfig.title}
          description={activeConfig.description}
          fieldName="file"
          endpoint={activeConfig.endpoint}
          accept=".csv,.xlsx,.xls"
          className="inventory-upload-card"
        />

        <section className="workspace-section inventory-workspace-section guideline-section">
          <div className="section-title">
            <h2>格式提醒</h2>
            <p>只要欄位名稱能對上即可直接匯入。</p>
          </div>

          <div className="info-group">
            <div className="info-label">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
                <line x1="3" y1="9" x2="21" y2="9" />
                <line x1="9" y1="21" x2="9" y2="9" />
              </svg>
              商品庫存必需欄位
            </div>
            <div className="info-desc">
              <span className="code-tag">product_code</span>
              <span className="code-tag">stock_quantity</span>
            </div>
          </div>

          <div className="info-group">
            <div className="info-label">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
                <line x1="3" y1="9" x2="21" y2="9" />
                <line x1="9" y1="21" x2="9" y2="9" />
              </svg>
              原料盤點必需欄位
            </div>
            <div className="info-desc">
              <span className="code-tag">material_code</span>
              <span className="code-tag">stock_quantity</span>
            </div>
          </div>

          <div className="info-group">
            <div className="info-label">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#3b82f6" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <circle cx="12" cy="12" r="10" />
                <line x1="12" y1="8" x2="12" y2="12" />
                <line x1="12" y1="16" x2="12.01" y2="16" />
              </svg>
              數值檢查
            </div>
            <div className="info-desc">庫存數值不可小於 0，若偵測到負數將會於匯入前提示。</div>
          </div>

          <div className="info-group">
            <div className="info-label">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <circle cx="12" cy="12" r="10" />
                <polyline points="12 6 12 12 16 14" />
              </svg>
              來源追蹤
            </div>
            <div className="info-desc">每次成功匯入，系統都會於後台保留原始檔名與匯入時間紀錄。</div>
          </div>
        </section>
      </section>
    </section>
  )
}
