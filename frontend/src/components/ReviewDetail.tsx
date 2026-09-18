import { getReview, type Finding, type Review } from '../api/reviews'
import { FindingsList } from './FindingsList'

interface Props {
  review: Review
  onReviewChanged: (review: Review) => void
}

export function ReviewDetail({ review, onReviewChanged }: Props) {
  async function refresh() {
    const fresh = await getReview(review.id)
    onReviewChanged(fresh)
  }

  function handleFeedbackRecorded(_updated: Finding) {
    refresh()
  }

  return (
    <div className="review-detail">
      <header className="review-detail__header">
        <h3>
          {review.repoUrl}
          {review.prNumber != null ? ` #${review.prNumber}` : ''}
        </h3>
        <dl className="review-detail__meta">
          <div>
            <dt>Status</dt>
            <dd>{review.status}</dd>
          </div>
          <div>
            <dt>Total findings</dt>
            <dd>{review.totalFindings}</dd>
          </div>
          <div>
            <dt>Rule-confirmed</dt>
            <dd>{review.summary.ruleConfirmed}</dd>
          </div>
          <div>
            <dt>LLM-only</dt>
            <dd>{review.summary.llmOnly}</dd>
          </div>
          {review.requestedBy && (
            <div>
              <dt>Requested by</dt>
              <dd>{review.requestedBy}</dd>
            </div>
          )}
          <div>
            <dt>Completed</dt>
            <dd>{review.completedAt ? new Date(review.completedAt).toLocaleString() : '—'}</dd>
          </div>
        </dl>
      </header>
      {review.findings.length === 0 ? (
        <p>No findings — clean review.</p>
      ) : (
        <FindingsList findings={review.findings} onFeedbackRecorded={handleFeedbackRecorded} />
      )}
    </div>
  )
}
