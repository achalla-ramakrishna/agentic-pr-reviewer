import { Fragment, useEffect, useMemo, useState, type FormEvent } from 'react'
import {
  createPractice,
  deletePractice,
  listPractices,
  setPracticeActive,
  type Category,
  type Practice,
  type Severity,
  type Technology,
} from '../api/practices'

const CATEGORIES: Category[] = [
  'CORRECTNESS',
  'SECURITY',
  'PERFORMANCE',
  'STYLE',
  'TEST_QUALITY',
  'ACCESSIBILITY',
]
const SEVERITIES: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO']
const TECHNOLOGIES: Technology[] = [
  'JAVA',
  'SPRING',
  'HIBERNATE',
  'SQL',
  'MYSQL',
  'JAVASCRIPT',
  'TYPESCRIPT',
  'REACT',
  'HTML',
  'CSS',
  'BOOTSTRAP',
  'GENERAL',
]

const emptyForm = {
  practiceCode: '',
  title: '',
  description: '',
  category: 'CORRECTNESS' as Category,
  subcategory: '',
  severity: 'MEDIUM' as Severity,
  technology: 'JAVA' as Technology,
  code: '',
  solution: '',
  risk: '',
  detectionPattern: '',
}

/** technology -> subcategory (topic) -> practices, matching how the knowledge base is curated. */
function groupByTechnologyAndTopic(practices: Practice[]) {
  const byTechnology = new Map<Technology, Map<string, Practice[]>>()
  for (const practice of practices) {
    const topic = practice.subcategory ?? '(uncategorized)'
    if (!byTechnology.has(practice.technology)) {
      byTechnology.set(practice.technology, new Map())
    }
    const topics = byTechnology.get(practice.technology)!
    if (!topics.has(topic)) {
      topics.set(topic, [])
    }
    topics.get(topic)!.push(practice)
  }
  return byTechnology
}

export function PracticesLibrary() {
  const [practices, setPractices] = useState<Practice[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState(emptyForm)
  const [submitting, setSubmitting] = useState(false)

  function refresh() {
    setLoading(true)
    listPractices()
      .then(setPractices)
      .catch((err: Error) => setError(err.message))
      .finally(() => setLoading(false))
  }

  useEffect(refresh, [])

  const grouped = useMemo(() => groupByTechnologyAndTopic(practices), [practices])

  async function handleToggleActive(practice: Practice) {
    try {
      await setPracticeActive(practice.id, !practice.active)
      refresh()
    } catch (err) {
      setError((err as Error).message)
    }
  }

  async function handleDelete(practice: Practice) {
    if (!window.confirm(`Delete "${practice.title}" (${practice.practiceCode})?`)) return
    try {
      await deletePractice(practice.id)
      refresh()
    } catch (err) {
      setError((err as Error).message)
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await createPractice(form)
      setForm(emptyForm)
      setShowForm(false)
      refresh()
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="practices-library">
      <div className="practices-library__header">
        <h2>Practices library ({practices.length})</h2>
        <button type="button" onClick={() => setShowForm((v) => !v)}>
          {showForm ? 'Cancel' : 'Add practice'}
        </button>
      </div>

      {error && <p className="practices-library__error">{error}</p>}

      {showForm && (
        <form className="practice-form" onSubmit={handleSubmit}>
          <div className="practice-form__row">
            <label>
              Practice code
              <input
                required
                placeholder="e.g. JAVA-EXC-004"
                value={form.practiceCode}
                onChange={(e) => setForm({ ...form, practiceCode: e.target.value })}
              />
            </label>
            <label>
              Title
              <input
                required
                value={form.title}
                onChange={(e) => setForm({ ...form, title: e.target.value })}
              />
            </label>
          </div>
          <label>
            Description
            <textarea
              required
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
            />
          </label>
          <div className="practice-form__row">
            <label>
              Technology
              <select
                value={form.technology}
                onChange={(e) => setForm({ ...form, technology: e.target.value as Technology })}
              >
                {TECHNOLOGIES.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Category
              <select
                value={form.category}
                onChange={(e) => setForm({ ...form, category: e.target.value as Category })}
              >
                {CATEGORIES.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Severity
              <select
                value={form.severity}
                onChange={(e) => setForm({ ...form, severity: e.target.value as Severity })}
              >
                {SEVERITIES.map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <label>
            Topic (subcategory)
            <input
              placeholder="e.g. Exception Handling"
              value={form.subcategory}
              onChange={(e) => setForm({ ...form, subcategory: e.target.value })}
            />
          </label>
          <label>
            Risk
            <textarea
              placeholder="What actually goes wrong if this ships?"
              value={form.risk}
              onChange={(e) => setForm({ ...form, risk: e.target.value })}
            />
          </label>
          <label>
            Code (bad example)
            <textarea value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} />
          </label>
          <label>
            Solution (good example)
            <textarea
              value={form.solution}
              onChange={(e) => setForm({ ...form, solution: e.target.value })}
            />
          </label>
          <button type="submit" disabled={submitting}>
            {submitting ? 'Saving...' : 'Save practice'}
          </button>
        </form>
      )}

      {loading ? (
        <p>Loading practices...</p>
      ) : (
        Array.from(grouped.entries())
          .sort(([a], [b]) => a.localeCompare(b))
          .map(([technology, topics]) => (
            <div key={technology} className="technology-group">
              <h3>{technology}</h3>
              {Array.from(topics.entries())
                .sort(([a], [b]) => a.localeCompare(b))
                .map(([topic, topicPractices]) => (
                  <div key={topic} className="topic-group">
                    <h4>{topic}</h4>
                    <table className="practices-table">
                      <thead>
                        <tr>
                          <th>Code</th>
                          <th>Title</th>
                          <th>Severity</th>
                          <th>Active</th>
                          <th></th>
                        </tr>
                      </thead>
                      <tbody>
                        {topicPractices.map((practice) => (
                          <Fragment key={practice.id}>
                            <tr className={practice.active ? '' : 'practices-table__inactive'}>
                              <td>{practice.practiceCode}</td>
                              <td title={practice.description}>{practice.title}</td>
                              <td>{practice.severity}</td>
                              <td>{practice.active ? 'yes' : 'no'}</td>
                              <td>
                                <button type="button" onClick={() => handleToggleActive(practice)}>
                                  {practice.active ? 'Deactivate' : 'Activate'}
                                </button>
                                <button type="button" onClick={() => handleDelete(practice)}>
                                  Delete
                                </button>
                              </td>
                            </tr>
                            <tr>
                              <td colSpan={5}>
                                <details>
                                  <summary>Risk, code, and solution</summary>
                                  {practice.risk && (
                                    <p>
                                      <strong>Risk:</strong> {practice.risk}
                                    </p>
                                  )}
                                  {practice.code && (
                                    <>
                                      <strong>Code (bad):</strong>
                                      <pre>{practice.code}</pre>
                                    </>
                                  )}
                                  {practice.solution && (
                                    <>
                                      <strong>Solution (good):</strong>
                                      <pre>{practice.solution}</pre>
                                    </>
                                  )}
                                </details>
                              </td>
                            </tr>
                          </Fragment>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ))}
            </div>
          ))
      )}
    </section>
  )
}
