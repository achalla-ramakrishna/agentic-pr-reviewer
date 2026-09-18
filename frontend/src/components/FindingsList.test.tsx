import { describe, expect, it } from 'vitest'
import type { Finding } from '../api/reviews'
import { groupBySeverityThenCategory } from './FindingsList'

function makeFinding(overrides: Partial<Finding>): Finding {
  return {
    id: crypto.randomUUID(),
    practiceId: null,
    practiceCode: null,
    practiceTitle: null,
    source: 'RULE',
    category: 'CORRECTNESS',
    severity: 'MEDIUM',
    filePath: 'Foo.java',
    lineStart: 1,
    lineEnd: 1,
    message: 'msg',
    status: 'OPEN',
    feedbackReason: null,
    decidedBy: null,
    decidedAt: null,
    createdAt: new Date().toISOString(),
    ...overrides,
  }
}

describe('groupBySeverityThenCategory', () => {
  it('groups findings by severity then category, preserving insertion order within a group', () => {
    const findings = [
      makeFinding({ id: '1', severity: 'HIGH', category: 'SECURITY' }),
      makeFinding({ id: '2', severity: 'CRITICAL', category: 'CORRECTNESS' }),
      makeFinding({ id: '3', severity: 'HIGH', category: 'SECURITY' }),
      makeFinding({ id: '4', severity: 'HIGH', category: 'STYLE' }),
    ]

    const grouped = groupBySeverityThenCategory(findings)

    expect(Array.from(grouped.keys())).toEqual(['HIGH', 'CRITICAL'])
    expect(grouped.get('CRITICAL')!.get('CORRECTNESS')!.map((f) => f.id)).toEqual(['2'])
    expect(grouped.get('HIGH')!.get('SECURITY')!.map((f) => f.id)).toEqual(['1', '3'])
    expect(grouped.get('HIGH')!.get('STYLE')!.map((f) => f.id)).toEqual(['4'])
  })

  it('returns an empty map for no findings', () => {
    expect(groupBySeverityThenCategory([]).size).toBe(0)
  })
})
