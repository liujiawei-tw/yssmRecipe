export type Product = {
  id: number
  productCode: string
  productName: string
  safetyStock: number
  maxStock: number
  erpUnit: string
  active: boolean
  recipeId: number | null
  recipeCode: string | null
  recipeName: string | null
  packagingErpUnit: string | null
  gramWeightPerErpUnit: string | null
  packagingDescription: string | null
}

export type Recipe = {
  id: number
  recipeCode: string
  recipeName: string
  active: boolean
  description: string | null
}

export type Material = {
  id: number
  materialCode: string
  materialName: string
  baseUnit: string
  active: boolean
  description: string | null
}

export type RecipeVersionItem = {
  id: number
  materialId: number
  materialCode: string
  materialName: string
  ratio: string
  displayOrder: number
}

export type RecipeVersion = {
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

export type CalculationItem = {
  materialCode: string
  materialName: string
  ratio: string
  percentage: string
  actualWeightG: string
  formula: string
}

export type CalculationResult = {
  recipeName: string
  versionDate: string
  baseWeightG: string
  targetWeightG: string
  totalRatio: string
  totalWeightG: string
  items: CalculationItem[]
}

export type PageKey =
  | 'calculator'
  | 'materials'
  | 'products'
  | 'recipes'
  | 'items'
  | 'productionAdvice'
  | 'inventory'

export type EntityState<T> = {
  loading: boolean
  saving: boolean
  error: string | null
  notice: string | null
  rows: T[]
}
