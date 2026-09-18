import { useState, type FormEvent } from 'react'
import { submitReview, type Review } from '../api/reviews'

interface Props {
  onSubmitted: (review: Review) => void
}

export function ReviewSubmitForm({ onSubmitted }: Props) {
  const [url, setUrl] = useState('')
  const [token, setToken] = useState('')
  const [requestedBy, setRequestedBy] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      const review = await submitReview({
        url,
        token: token || undefined,
        requestedBy: requestedBy || undefined,
      })
      onSubmitted(review)
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className="review-submit-form" onSubmit={handleSubmit}>
      <h2>Review a repo or PR</h2>
      <div className="review-submit-form__row">
        <label className="review-submit-form__url">
          Repo or PR URL
          <input
            required
            placeholder="https://github.com/owner/repo or .../pull/123"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
          />
        </label>
        <label>
          GitHub token (optional)
          <input
            type="password"
            placeholder="ghp_..."
            value={token}
            onChange={(e) => setToken(e.target.value)}
          />
        </label>
        <label>
          Your name (optional)
          <input value={requestedBy} onChange={(e) => setRequestedBy(e.target.value)} />
        </label>
        <button type="submit" disabled={submitting}>
          {submitting ? 'Reviewing...' : 'Run review'}
        </button>
      </div>
      {error && <p className="review-submit-form__error">{error}</p>}
    </form>
  )
}
