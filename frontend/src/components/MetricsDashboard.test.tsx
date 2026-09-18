import { render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getMetrics, type Metrics } from '../api/metrics'
import { MetricsDashboard } from './MetricsDashboard'

vi.mock('../api/metrics', () => ({
  getMetrics: vi.fn(),
}))

const mockedGetMetrics = vi.mocked(getMetrics)

function makeMetrics(overrides: Partial<Metrics> = {}): Metrics {
  return {
    totalReviews: 2,
    totalFindings: 10,
    reviewsOverTime: [
      { date: '2026-01-01', count: 1 },
      { date: '2026-01-02', count: 1 },
    ],
    overallPrecision: { accepted: 3, rejected: 1, open: 6, precision: 0.75 },
    precisionBySource: {
      RULE: { accepted: 2, rejected: 0, open: 3, precision: 1 },
      LLM: { accepted: 1, rejected: 1, open: 3, precision: 0.5 },
      BOTH: { accepted: 0, rejected: 0, open: 0, precision: null },
    },
    topCategories: [
      { category: 'SECURITY', count: 5 },
      { category: 'STYLE', count: 2 },
    ],
    ...overrides,
  }
}

describe('MetricsDashboard', () => {
  beforeEach(() => {
    mockedGetMetrics.mockReset()
  })

  it('renders stat tiles, reviews-over-time, top categories, and precision by source', async () => {
    mockedGetMetrics.mockResolvedValue(makeMetrics())

    render(<MetricsDashboard />)

    const statValues = await screen.findAllByText(/^(2|10)$/, { selector: '.stat-tile__value' })
    expect(statValues.map((el) => el.textContent)).toEqual(['2', '10'])
    expect(screen.getByText('75%')).toBeInTheDocument()
    expect(screen.getByText('3 accepted / 1 rejected')).toBeInTheDocument()

    expect(screen.getAllByText('SECURITY').length).toBeGreaterThan(0)

    expect(screen.getByText('RULE')).toBeInTheDocument()
    expect(screen.getByText('LLM')).toBeInTheDocument()
    expect(screen.getByText('BOTH')).toBeInTheDocument()
    expect(screen.getByText('No feedback yet.')).toBeInTheDocument()
  })

  it('shows an error message when the metrics request fails', async () => {
    mockedGetMetrics.mockRejectedValue(new Error('boom'))

    render(<MetricsDashboard />)

    expect(await screen.findByText('boom')).toBeInTheDocument()
  })

  it('renders empty states when there are no reviews or findings yet', async () => {
    mockedGetMetrics.mockResolvedValue(
      makeMetrics({
        totalReviews: 0,
        totalFindings: 0,
        reviewsOverTime: [],
        topCategories: [],
        overallPrecision: { accepted: 0, rejected: 0, open: 0, precision: null },
      }),
    )

    render(<MetricsDashboard />)

    await waitFor(() => expect(screen.getAllByText('No reviews yet.')[0]).toBeInTheDocument())
    expect(screen.getByText('No findings yet.')).toBeInTheDocument()
    expect(screen.getAllByText('—').length).toBeGreaterThan(0)
  })
})
