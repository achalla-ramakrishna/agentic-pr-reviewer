import { useState } from 'react'
import type { Review } from '../api/reviews'
import { ReviewSubmitForm } from './ReviewSubmitForm'
import { ReviewHistory } from './ReviewHistory'
import { ReviewDetail } from './ReviewDetail'

export function ReviewsDashboard() {
  const [selectedReview, setSelectedReview] = useState<Review | null>(null)
  const [historyVersion, setHistoryVersion] = useState(0)

  function handleSubmitted(review: Review) {
    setSelectedReview(review)
    setHistoryVersion((v) => v + 1)
  }

  function handleFindingUpdated(updated: Review) {
    setSelectedReview(updated)
  }

  return (
    <section className="reviews-dashboard">
      <ReviewSubmitForm onSubmitted={handleSubmitted} />
      <div className="reviews-dashboard__body">
        <ReviewHistory key={historyVersion} selectedId={selectedReview?.id ?? null} onSelect={setSelectedReview} />
        {selectedReview ? (
          <ReviewDetail review={selectedReview} onReviewChanged={handleFindingUpdated} />
        ) : (
          <p className="reviews-dashboard__empty">Submit a repo or PR URL above, or pick a past review from the list.</p>
        )}
      </div>
    </section>
  )
}
