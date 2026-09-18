import type { Category, Severity } from './practices'

export type { Category, Severity }

export type FindingSource = 'RULE' | 'LLM' | 'BOTH'
export type FindingStatus = 'OPEN' | 'ACCEPTED' | 'REJECTED'
export type ReviewStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED'

export interface Finding {
  id: string
  practiceId: string | null
  practiceCode: string | null
  practiceTitle: string | null
  source: FindingSource
  category: Category
  severity: Severity
  filePath: string
  lineStart: number | null
  lineEnd: number | null
  message: string
  status: FindingStatus
  feedbackReason: string | null
  decidedBy: string | null
  decidedAt: string | null
  createdAt: string
}

export interface ReviewSummary {
  bySeverity: Partial<Record<Severity, number>>
  byCategory: Partial<Record<Category, number>>
  ruleConfirmed: number
  llmOnly: number
}

export interface Review {
  id: string
  repoUrl: string
  prNumber: number | null
  commitSha: string | null
  requestedBy: string | null
  status: ReviewStatus
  totalFindings: number
  createdAt: string
  completedAt: string | null
  findings: Finding[]
  summary: ReviewSummary
}

export interface ReviewInput {
  url: string
  token?: string
  requestedBy?: string
}

async function parseJsonOrThrow<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = await response.json().catch(() => ({ error: response.statusText }))
    throw new Error(body.error ?? `Request failed: ${response.status}`)
  }
  return response.json() as Promise<T>
}

export function submitReview(input: ReviewInput): Promise<Review> {
  return fetch('/api/reviews', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  }).then((res) => parseJsonOrThrow<Review>(res))
}

export function getReview(id: string): Promise<Review> {
  return fetch(`/api/reviews/${id}`).then((res) => parseJsonOrThrow<Review>(res))
}

export function listReviews(): Promise<Review[]> {
  return fetch('/api/reviews').then((res) => parseJsonOrThrow<Review[]>(res))
}

export function recordFindingFeedback(
  id: string,
  status: Extract<FindingStatus, 'ACCEPTED' | 'REJECTED' | 'OPEN'>,
  feedbackReason?: string,
  decidedBy?: string,
): Promise<Finding> {
  return fetch(`/api/findings/${id}/feedback`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status, feedbackReason, decidedBy }),
  }).then((res) => parseJsonOrThrow<Finding>(res))
}
