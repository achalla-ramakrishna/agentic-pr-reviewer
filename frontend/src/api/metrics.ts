import type { Category } from './practices'
import type { FindingSource } from './reviews'

export type { Category, FindingSource }

export interface ReviewCountByDay {
  date: string
  count: number
}

export interface CategoryCount {
  category: Category
  count: number
}

export interface PrecisionStats {
  accepted: number
  rejected: number
  open: number
  precision: number | null
}

export interface Metrics {
  totalReviews: number
  totalFindings: number
  reviewsOverTime: ReviewCountByDay[]
  overallPrecision: PrecisionStats
  precisionBySource: Partial<Record<FindingSource, PrecisionStats>>
  topCategories: CategoryCount[]
}

async function parseJsonOrThrow<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = await response.json().catch(() => ({ error: response.statusText }))
    throw new Error(body.error ?? `Request failed: ${response.status}`)
  }
  return response.json() as Promise<T>
}

export function getMetrics(): Promise<Metrics> {
  return fetch('/api/metrics').then((res) => parseJsonOrThrow<Metrics>(res))
}
