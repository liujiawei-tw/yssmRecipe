import { useEffect, useState } from 'react'
import { resolvePageFromPathname, ROUTES } from './route-config'
import type { PageKey } from './types'

export function usePageRouting() {
  const [page, setPage] = useState<PageKey>(() => resolvePageFromPathname(window.location.pathname))

  useEffect(() => {
    const sync = () => setPage(resolvePageFromPathname(window.location.pathname))
    window.addEventListener('popstate', sync)
    window.addEventListener('pageshow', sync)
    sync()
    return () => {
      window.removeEventListener('popstate', sync)
      window.removeEventListener('pageshow', sync)
    }
  }, [])

  const navigate = (nextPage: PageKey) => {
    const nextPath = ROUTES[nextPage]
    if (window.location.pathname !== nextPath) {
      window.history.pushState({}, '', nextPath)
    }
    window.scrollTo({ top: 0, behavior: 'smooth' })
    setPage(nextPage)
  }

  return { page, navigate }
}
