import type { PageKey } from './types'

type SidebarItem = {
  page: PageKey
  label: string
  icon:
    | 'calculator'
    | 'package'
    | 'archive'
    | 'fileText'
    | 'gitBranch'
    | 'database'
    | 'clipboard'
}

const SIDEBAR_GROUPS: SidebarItem[][] = [
  [{ page: 'calculator', label: '配方試算', icon: 'calculator' }],
  [
    { page: 'materials', label: '原料維護', icon: 'package' },
    { page: 'recipes', label: '配方維護', icon: 'fileText' },
    { page: 'items', label: '配方版本項目維護', icon: 'gitBranch' },
    { page: 'products', label: '商品維護', icon: 'archive' },
  ],
  [
      { page: 'inventory', label: '庫存匯入', icon: 'database' },
      { page: 'productionAdvice', label: '生產請購建議', icon: 'clipboard' },
  ],
]

function SidebarIcon({ name }: { name: SidebarItem['icon'] }) {
  const commonProps = {
    width: 16,
    height: 16,
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 2,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
    'aria-hidden': true,
  }

  switch (name) {
    case 'calculator':
      return (
        <svg {...commonProps}>
          <rect x="4" y="3" width="16" height="18" rx="2" />
          <path d="M8 7h8" />
          <path d="M8 12h.01" />
          <path d="M12 12h.01" />
          <path d="M16 12h.01" />
          <path d="M8 16h.01" />
          <path d="M12 16h.01" />
          <path d="M16 16h.01" />
        </svg>
      )
    case 'package':
      return (
        <svg {...commonProps}>
          <path d="m3 7 9-4 9 4-9 4-9-4Z" />
          <path d="m3 7 9 4 9-4" />
          <path d="M12 11v10" />
          <path d="M6 10.5v5L12 19l6-3.5v-5" />
        </svg>
      )
    case 'archive':
      return (
        <svg {...commonProps}>
          <rect x="4" y="5" width="16" height="4" rx="1" />
          <path d="M6 9v10h12V9" />
          <path d="M10 13h4" />
        </svg>
      )
    case 'fileText':
      return (
        <svg {...commonProps}>
          <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" />
          <path d="M14 3v5h5" />
          <path d="M9 13h6" />
          <path d="M9 17h6" />
        </svg>
      )
    case 'gitBranch':
      return (
        <svg {...commonProps}>
          <circle cx="6" cy="6" r="2" />
          <circle cx="18" cy="6" r="2" />
          <circle cx="18" cy="18" r="2" />
          <path d="M6 8v8c0 1.1.9 2 2 2h8" />
          <path d="M18 8v10" />
        </svg>
      )
    case 'database':
      return (
        <svg {...commonProps}>
          <ellipse cx="12" cy="5" rx="7" ry="3" />
          <path d="M5 5v6c0 1.7 3.1 3 7 3s7-1.3 7-3V5" />
          <path d="M5 11v6c0 1.7 3.1 3 7 3s7-1.3 7-3v-6" />
        </svg>
      )
    case 'clipboard':
      return (
        <svg {...commonProps}>
          <rect x="7" y="3" width="10" height="4" rx="1.5" />
          <path d="M9 3a2 2 0 0 1 6 0" />
          <path d="M8 5h8a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Z" />
          <path d="M10 11h4" />
          <path d="M10 15h4" />
        </svg>
      )
    default:
      return null
  }
}

export function AppSidebar({ activePage, navigate }: { activePage: PageKey; navigate: (page: PageKey) => void }) {
  return (
    <aside className="app-sidebar">
      <div className="sidebar-brand">
        <div className="sidebar-brand-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M9 3h6" />
            <path d="M10 3v4l-4.5 9A4 4 0 0 0 9 22h6a4 4 0 0 0 3.5-6l-4.5-9V3" />
            <path d="M8 14h8" />
          </svg>
        </div>
        <span>yssmRecipe</span>
      </div>

      <nav className="sidebar-nav" aria-label="主要導覽">
        {SIDEBAR_GROUPS.map((group, groupIndex) => (
          <div className="sidebar-nav-group" key={groupIndex}>
            {group.map((item) => {
              const isActive = item.page === activePage
              return (
                <button
                  key={item.page}
                  type="button"
                  className={`sidebar-nav-item ${isActive ? 'active' : ''}`}
                  onClick={() => navigate(item.page)}
                  aria-current={isActive ? 'page' : undefined}
                >
                  <span className="sidebar-nav-icon">
                    <SidebarIcon name={item.icon} />
                  </span>
                  <span className="sidebar-nav-label">{item.label}</span>
                </button>
              )
            })}
          </div>
        ))}
      </nav>

      <footer className="sidebar-footer">
        <span>系統版本</span>
        <strong>飲料配方管理平台 1.0.0</strong>
      </footer>
    </aside>
  )
}
