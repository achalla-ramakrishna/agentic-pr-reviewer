import { useEffect, useState } from 'react'
import { listReviews, type Review } from '../api/reviews'

interface Props {
  selectedId: string | null
  onSelect: (review: Review) => void
}

export function shortRepoName(repoUrl: string): string {
  const match = repoUrl.match(/github\.com\/([^/]+\/[^/]+?)(?:\.git)?(?:\/|$)/)
  return match ? match[1] : repoUrl
}

export function ReviewHistory({ selectedId, onSelect }: Props) {
  const [reviews, setReviews] = useState<Review[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    listReviews()
      .then(setReviews)
      .catch((err: Error) => setError(err.message))
      .finally(() => setLoading(false))
  }, [])

  if (loading) {
    return (
      <aside className="review-history">
        <p>Loading reviews...</p>
      </aside>
    )
  }
  if (error) {
    return (
      <aside className="review-history">
        <p className="review-history__error">{error}</p>
      </aside>
    )
  }

  return (
    <aside className="review-history">
      <h3>Past reviews ({reviews.length})</h3>
      {reviews.length === 0 ? (
        <p>No reviews yet.</p>
      ) : (
        <ul>
          {reviews.map((review) => (
            <li key={review.id}>
              <button
                type="button"
                className={
                  review.id === selectedId
                    ? 'review-history__item review-history__item--active'
                    : 'review-history__item'
                }
                onClick={() => onSelect(review)}
              >
                <span className="review-history__repo">{shortRepoName(review.repoUrl)}</span>
                {review.prNumber != null && <span className="review-history__pr">#{review.prNumber}</span>}
                <span className="review-history__count">{review.totalFindings} findings</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </aside>
  )
}
