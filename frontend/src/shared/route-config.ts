import type { PageKey } from './types'

export const ROUTES: Record<PageKey, string> = {
  calculator: '/',
  materials: '/materials',
  products: '/products',
  recipes: '/recipes',
  items: '/recipe-version-items',
  productionAdvice: '/production-procurement',
  inventory: '/inventory-imports',
}

const PATH_TO_PAGE: Record<string, PageKey> = {
  '/': 'calculator',
  '/materials': 'materials',
  '/products': 'products',
  '/recipes': 'recipes',
  '/recipe-version-items': 'items',
  '/production-procurement': 'productionAdvice',
  '/production-plans': 'productionAdvice',
  '/inventory-imports': 'inventory',
  '/purchase-suggestions': 'productionAdvice',
}

export const resolvePageFromPathname = (pathname: string): PageKey => {
  const normalizedPath = pathname.replace(/\/+$/, '') || '/'
  if (normalizedPath in PATH_TO_PAGE) {
    return PATH_TO_PAGE[normalizedPath]
  }

  const knownRoute = Object.entries(PATH_TO_PAGE).find(([route]) =>
    normalizedPath === route || normalizedPath.endsWith(route),
  )

  return knownRoute ? knownRoute[1] : 'calculator'
}
