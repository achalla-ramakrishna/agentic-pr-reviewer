export type Category =
  | 'CORRECTNESS'
  | 'SECURITY'
  | 'PERFORMANCE'
  | 'STYLE'
  | 'TEST_QUALITY'
  | 'ACCESSIBILITY'

export type Severity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO'

export type Language = 'JAVA' | 'TYPESCRIPT' | 'JAVASCRIPT' | 'GENERAL'

export interface Practice {
  id: string
  title: string
  description: string
  category: Category
  severity: Severity
  language: Language
  badExample: string | null
  goodExample: string | null
  detectionPattern: string | null
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface PracticeInput {
  title: string
  description: string
  category: Category
  severity: Severity
  language: Language
  badExample?: string
  goodExample?: string
  detectionPattern?: string
}

async function parseJsonOrThrow<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = await response.json().catch(() => ({ error: response.statusText }))
    throw new Error(body.error ?? `Request failed: ${response.status}`)
  }
  return response.json() as Promise<T>
}

export function listPractices(): Promise<Practice[]> {
  return fetch('/api/practices').then((res) => parseJsonOrThrow<Practice[]>(res))
}

export function createPractice(input: PracticeInput): Promise<Practice> {
  return fetch('/api/practices', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  }).then((res) => parseJsonOrThrow<Practice>(res))
}

export function setPracticeActive(id: string, active: boolean): Promise<Practice> {
  return fetch(`/api/practices/${id}/active`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ active }),
  }).then((res) => parseJsonOrThrow<Practice>(res))
}

export function deletePractice(id: string): Promise<void> {
  return fetch(`/api/practices/${id}`, { method: 'DELETE' }).then((res) => {
    if (!res.ok) {
      throw new Error(`Request failed: ${res.status}`)
    }
  })
}
