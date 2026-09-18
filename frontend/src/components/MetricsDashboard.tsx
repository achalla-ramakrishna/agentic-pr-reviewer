import { useEffect, useState } from 'react'
import { getMetrics, type Metrics } from '../api/metrics'
import type { FindingSource } from '../api/reviews'

const SOURCE_ORDER: FindingSource[] = ['RULE', 'LLM', 'BOTH']

function formatPercent(precision: number | null): string {
  return precision == null ? '—' : `${Math.round(precision * 100)}%`
}

function formatShortDate(isoDate: string): string {
  const [, month, day] = isoDate.split('-')
  return `${month}/${day}`
}

export function MetricsDashboard() {
  const [metrics, setMetrics] = useState<Metrics | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getMetrics()
      .then(setMetrics)
      .catch((err: Error) => setError(err.message))
  }, [])

  if (error) {
    return <p className="metrics-dashboard__error">{error}</p>
  }
  if (!metrics) {
    return <p>Loading metrics...</p>
  }

  const maxDailyCount = Math.max(1, ...metrics.reviewsOverTime.map((d) => d.count))
  const maxCategoryCount = Math.max(1, ...metrics.topCategories.map((c) => c.count))

  return (
    <section className="metrics-dashboard">
      <div className="metrics-dashboard__tiles">
        <div className="stat-tile">
          <span className="stat-tile__label">Total reviews</span>
          <span className="stat-tile__value">{metrics.totalReviews}</span>
        </div>
        <div className="stat-tile">
          <span className="stat-tile__label">Total findings</span>
          <span className="stat-tile__value">{metrics.totalFindings}</span>
        </div>
        <div className="stat-tile">
          <span className="stat-tile__label">Overall precision</span>
          <span className="stat-tile__value">{formatPercent(metrics.overallPrecision.precision)}</span>
          <span className="stat-tile__sub">
            {metrics.overallPrecision.accepted} accepted / {metrics.overallPrecision.rejected} rejected
          </span>
        </div>
      </div>

      <div className="metrics-chart">
        <h3>Reviews over time</h3>
        {metrics.reviewsOverTime.length === 0 ? (
          <p className="metrics-chart__empty">No reviews yet.</p>
        ) : (
          <>
            <div className="bar-chart bar-chart--vertical" role="img" aria-label="Reviews submitted per day">
              {metrics.reviewsOverTime.map((day) => (
                <div className="bar-chart__column" key={day.date}>
                  <span className="bar-chart__value">{day.count}</span>
                  <div
                    className="bar-chart__bar bar-chart__bar--vertical"
                    style={{ height: `${(day.count / maxDailyCount) * 100}%` }}
                    title={`${day.date}: ${day.count} review${day.count === 1 ? '' : 's'}`}
                  />
                  <span className="bar-chart__tick">{formatShortDate(day.date)}</span>
                </div>
              ))}
            </div>
            <details className="metrics-chart__table">
              <summary>View as table</summary>
              <table>
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Reviews</th>
                  </tr>
                </thead>
                <tbody>
                  {metrics.reviewsOverTime.map((day) => (
                    <tr key={day.date}>
                      <td>{day.date}</td>
                      <td>{day.count}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </details>
          </>
        )}
      </div>

      <div className="metrics-chart">
        <h3>Top recurring categories</h3>
        {metrics.topCategories.length === 0 ? (
          <p className="metrics-chart__empty">No findings yet.</p>
        ) : (
          <>
            <div className="bar-chart bar-chart--horizontal" role="img" aria-label="Findings by category">
              {metrics.topCategories.map((c) => (
                <div className="bar-chart__row" key={c.category}>
                  <span className="bar-chart__row-label">{c.category}</span>
                  <div className="bar-chart__track">
                    <div
                      className="bar-chart__bar bar-chart__bar--horizontal"
                      style={{ width: `${(c.count / maxCategoryCount) * 100}%` }}
                      title={`${c.category}: ${c.count} finding${c.count === 1 ? '' : 's'}`}
                    />
                  </div>
                  <span className="bar-chart__value">{c.count}</span>
                </div>
              ))}
            </div>
            <details className="metrics-chart__table">
              <summary>View as table</summary>
              <table>
                <thead>
                  <tr>
                    <th>Category</th>
                    <th>Findings</th>
                  </tr>
                </thead>
                <tbody>
                  {metrics.topCategories.map((c) => (
                    <tr key={c.category}>
                      <td>{c.category}</td>
                      <td>{c.count}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </details>
          </>
        )}
      </div>

      <div className="metrics-chart">
        <h3>Precision by source</h3>
        <ul className="precision-legend">
          <li>
            <span className="precision-legend__swatch precision-legend__swatch--accepted" /> Accepted
          </li>
          <li>
            <span className="precision-legend__swatch precision-legend__swatch--rejected" /> Rejected
          </li>
        </ul>
        <div className="precision-groups">
          {SOURCE_ORDER.map((source) => {
            const stats = metrics.precisionBySource[source]
            const decided = stats ? stats.accepted + stats.rejected : 0
            const maxSide = Math.max(1, stats?.accepted ?? 0, stats?.rejected ?? 0)
            return (
              <div className="precision-group" key={source}>
                <div className="precision-group__header">
                  <span className="precision-group__source">{source}</span>
                  <span className="precision-group__rate">{stats ? formatPercent(stats.precision) : '—'}</span>
                </div>
                {decided === 0 ? (
                  <p className="metrics-chart__empty">No feedback yet.</p>
                ) : (
                  <div className="precision-bars">
                    <div className="precision-bars__row">
                      <span className="precision-bars__label">Accepted</span>
                      <div className="bar-chart__track">
                        <div
                          className="bar-chart__bar bar-chart__bar--horizontal bar-chart__bar--good"
                          style={{ width: `${((stats?.accepted ?? 0) / maxSide) * 100}%` }}
                        />
                      </div>
                      <span className="bar-chart__value">{stats?.accepted ?? 0}</span>
                    </div>
                    <div className="precision-bars__row">
                      <span className="precision-bars__label">Rejected</span>
                      <div className="bar-chart__track">
                        <div
                          className="bar-chart__bar bar-chart__bar--horizontal bar-chart__bar--critical"
                          style={{ width: `${((stats?.rejected ?? 0) / maxSide) * 100}%` }}
                        />
                      </div>
                      <span className="bar-chart__value">{stats?.rejected ?? 0}</span>
                    </div>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      </div>
    </section>
  )
}
