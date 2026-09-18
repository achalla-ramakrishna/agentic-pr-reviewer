import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Finding } from '../api/reviews'
import { recordFindingFeedback } from '../api/reviews'
import { FindingRow } from './FindingsList'

vi.mock('../api/reviews', () => ({
  recordFindingFeedback: vi.fn(),
}))

const mockedRecordFindingFeedback = vi.mocked(recordFindingFeedback)

function makeFinding(overrides: Partial<Finding> = {}): Finding {
  return {
    id: 'finding-1',
    practiceId: null,
    practiceCode: null,
    practiceTitle: null,
    source: 'RULE',
    category: 'CORRECTNESS',
    severity: 'MEDIUM',
    filePath: 'src/Foo.java',
    lineStart: 10,
    lineEnd: 10,
    message: 'Something is off',
    status: 'OPEN',
    feedbackReason: null,
    decidedBy: null,
    decidedAt: null,
    createdAt: new Date().toISOString(),
    ...overrides,
  }
}

describe('FindingRow', () => {
  beforeEach(() => {
    mockedRecordFindingFeedback.mockReset()
  })

  it('accepts a finding and reports the updated finding back', async () => {
    const finding = makeFinding()
    const updated = { ...finding, status: 'ACCEPTED' as const, decidedBy: 'alice' }
    mockedRecordFindingFeedback.mockResolvedValue(updated)
    const onFeedbackRecorded = vi.fn()
    const user = userEvent.setup()

    render(<FindingRow finding={finding} onFeedbackRecorded={onFeedbackRecorded} />)

    await user.click(screen.getByRole('button', { name: /accept/i }))

    await waitFor(() => expect(onFeedbackRecorded).toHaveBeenCalledWith(updated))
    expect(mockedRecordFindingFeedback).toHaveBeenCalledWith('finding-1', 'ACCEPTED')
  })

  it('rejects a finding and reports the updated finding back', async () => {
    const finding = makeFinding()
    const updated = { ...finding, status: 'REJECTED' as const }
    mockedRecordFindingFeedback.mockResolvedValue(updated)
    const onFeedbackRecorded = vi.fn()
    const user = userEvent.setup()

    render(<FindingRow finding={finding} onFeedbackRecorded={onFeedbackRecorded} />)

    await user.click(screen.getByRole('button', { name: /reject/i }))

    await waitFor(() => expect(onFeedbackRecorded).toHaveBeenCalledWith(updated))
    expect(mockedRecordFindingFeedback).toHaveBeenCalledWith('finding-1', 'REJECTED')
  })

  it('renders a decision instead of buttons once a finding is no longer open', () => {
    const finding = makeFinding({ status: 'ACCEPTED', decidedBy: 'bob' })

    render(<FindingRow finding={finding} onFeedbackRecorded={vi.fn()} />)

    expect(screen.getByText(/accepted by bob/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /accept/i })).not.toBeInTheDocument()
  })

  it('shows an error message when recording feedback fails', async () => {
    const finding = makeFinding()
    mockedRecordFindingFeedback.mockRejectedValue(new Error('network error'))
    const user = userEvent.setup()

    render(<FindingRow finding={finding} onFeedbackRecorded={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: /accept/i }))

    expect(await screen.findByText('network error')).toBeInTheDocument()
  })
})
