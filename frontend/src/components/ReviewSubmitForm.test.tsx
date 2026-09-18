import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Review } from '../api/reviews'
import { submitReview } from '../api/reviews'
import { ReviewSubmitForm } from './ReviewSubmitForm'

vi.mock('../api/reviews', () => ({
  submitReview: vi.fn(),
}))

const mockedSubmitReview = vi.mocked(submitReview)

function makeReview(): Review {
  return {
    id: 'review-1',
    repoUrl: 'https://github.com/owner/repo',
    prNumber: null,
    commitSha: null,
    requestedBy: null,
    status: 'COMPLETED',
    totalFindings: 0,
    createdAt: new Date().toISOString(),
    completedAt: new Date().toISOString(),
    findings: [],
    summary: { bySeverity: {}, byCategory: {}, ruleConfirmed: 0, llmOnly: 0 },
  }
}

describe('ReviewSubmitForm', () => {
  beforeEach(() => {
    mockedSubmitReview.mockReset()
  })

  it('submits the url and calls onSubmitted with the resulting review', async () => {
    const review = makeReview()
    mockedSubmitReview.mockResolvedValue(review)
    const onSubmitted = vi.fn()
    const user = userEvent.setup()

    render(<ReviewSubmitForm onSubmitted={onSubmitted} />)

    await user.type(screen.getByLabelText(/repo or pr url/i), 'https://github.com/owner/repo')
    await user.click(screen.getByRole('button', { name: /run review/i }))

    await waitFor(() => expect(onSubmitted).toHaveBeenCalledWith(review))
    expect(mockedSubmitReview).toHaveBeenCalledWith({
      url: 'https://github.com/owner/repo',
      token: undefined,
      requestedBy: undefined,
    })
  })

  it('shows an error message when submission fails', async () => {
    mockedSubmitReview.mockRejectedValue(new Error('boom'))
    const onSubmitted = vi.fn()
    const user = userEvent.setup()

    render(<ReviewSubmitForm onSubmitted={onSubmitted} />)

    await user.type(screen.getByLabelText(/repo or pr url/i), 'https://github.com/owner/repo')
    await user.click(screen.getByRole('button', { name: /run review/i }))

    expect(await screen.findByText('boom')).toBeInTheDocument()
    expect(onSubmitted).not.toHaveBeenCalled()
  })
})
