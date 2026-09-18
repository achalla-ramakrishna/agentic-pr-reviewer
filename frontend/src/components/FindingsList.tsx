import { useState } from 'react'
import { recordFindingFeedback, type Finding, type Severity } from '../api/reviews'

const SEVERITY_ORDER: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO']

/** severity -> category -> findings, in that priority order -- matches SPEC's "grouped by severity/category". */
export function groupBySeverityThenCategory(findings: Finding[]): Map<Severity, Map<string, Finding[]>> {
  const grouped = new Map<Severity, Map<string, Finding[]>>()
  for (const finding of findings) {
    if (!grouped.has(finding.severity)) {
      grouped.set(finding.severity, new Map())
    }
    const byCategory = grouped.get(finding.severity)!
    if (!byCategory.has(finding.category)) {
      byCategory.set(finding.category, [])
    }
    byCategory.get(finding.category)!.push(finding)
  }
  return grouped
}

interface ListProps {
  findings: Finding[]
  onFeedbackRecorded: (finding: Finding) => void
}

export function FindingsList({ findings, onFeedbackRecorded }: ListProps) {
  const grouped = groupBySeverityThenCategory(findings)

  return (
    <div className="findings-list">
      {SEVERITY_ORDER.filter((severity) => grouped.has(severity)).map((severity) => (
        <div key={severity} className="findings-list__severity-group">
          <h4 className={`severity-badge severity-badge--${severity.toLowerCase()}`}>{severity}</h4>
          {Array.from(grouped.get(severity)!.entries()).map(([category, categoryFindings]) => (
            <div key={category} className="findings-list__category-group">
              <h5>{category}</h5>
              {categoryFindings.map((finding) => (
                <FindingRow key={finding.id} finding={finding} onFeedbackRecorded={onFeedbackRecorded} />
              ))}
            </div>
          ))}
        </div>
      ))}
    </div>
  )
}

interface RowProps {
  finding: Finding
  onFeedbackRecorded: (finding: Finding) => void
}

export function FindingRow({ finding, onFeedbackRecorded }: RowProps) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function decide(status: 'ACCEPTED' | 'REJECTED') {
    setBusy(true)
    setError(null)
    try {
      const updated = await recordFindingFeedback(finding.id, status)
      onFeedbackRecorded(updated)
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(false)
    }
  }

  const location =
    finding.lineStart != null
      ? `${finding.filePath}:${finding.lineStart}${
          finding.lineEnd && finding.lineEnd !== finding.lineStart ? `-${finding.lineEnd}` : ''
        }`
      : finding.filePath

  return (
    <div className={`finding-row finding-row--${finding.status.toLowerCase()}`}>
      <div className="finding-row__meta">
        <span className={`source-badge source-badge--${finding.source.toLowerCase()}`}>{finding.source}</span>
        <span className="finding-row__location">{location}</span>
      </div>
      <p className="finding-row__message">{finding.message}</p>
      {finding.practiceCode && (
        <p className="finding-row__evidence">
          Evidence: <strong>{finding.practiceCode}</strong> — {finding.practiceTitle}
        </p>
      )}
      <div className="finding-row__actions">
        {finding.status === 'OPEN' ? (
          <>
            <button type="button" disabled={busy} onClick={() => decide('ACCEPTED')}>
              Accept
            </button>
            <button type="button" disabled={busy} onClick={() => decide('REJECTED')}>
              Reject
            </button>
          </>
        ) : (
          <span className="finding-row__decision">
            {finding.status === 'ACCEPTED' ? 'Accepted' : 'Rejected'}
            {finding.decidedBy && ` by ${finding.decidedBy}`}
          </span>
        )}
      </div>
      {error && <p className="finding-row__error">{error}</p>}
    </div>
  )
}
