import { useEffect, useState, type FormEvent } from 'react'
import {
  createPractice,
  deletePractice,
  listPractices,
  setPracticeActive,
  type Category,
  type Language,
  type Practice,
  type Severity,
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
const LANGUAGES: Language[] = ['JAVA', 'TYPESCRIPT', 'JAVASCRIPT', 'GENERAL']

const emptyForm = {
  title: '',
  description: '',
  category: 'CORRECTNESS' as Category,
  severity: 'MEDIUM' as Severity,
  language: 'JAVA' as Language,
  badExample: '',
  goodExample: '',
  detectionPattern: '',
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

  async function handleToggleActive(practice: Practice) {
    try {
      await setPracticeActive(practice.id, !practice.active)
      refresh()
    } catch (err) {
      setError((err as Error).message)
    }
  }

  async function handleDelete(practice: Practice) {
    if (!window.confirm(`Delete "${practice.title}"?`)) return
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
        <h2>Practices library</h2>
        <button type="button" onClick={() => setShowForm((v) => !v)}>
          {showForm ? 'Cancel' : 'Add practice'}
        </button>
      </div>

      {error && <p className="practices-library__error">{error}</p>}

      {showForm && (
        <form className="practice-form" onSubmit={handleSubmit}>
          <label>
            Title
            <input
              required
              value={form.title}
              onChange={(e) => setForm({ ...form, title: e.target.value })}
            />
          </label>
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
            <label>
              Language
              <select
                value={form.language}
                onChange={(e) => setForm({ ...form, language: e.target.value as Language })}
              >
                {LANGUAGES.map((l) => (
                  <option key={l} value={l}>
                    {l}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <label>
            Bad example
            <textarea
              value={form.badExample}
              onChange={(e) => setForm({ ...form, badExample: e.target.value })}
            />
          </label>
          <label>
            Good example
            <textarea
              value={form.goodExample}
              onChange={(e) => setForm({ ...form, goodExample: e.target.value })}
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
        <table className="practices-table">
          <thead>
            <tr>
              <th>Title</th>
              <th>Category</th>
              <th>Severity</th>
              <th>Language</th>
              <th>Active</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {practices.map((practice) => (
              <tr key={practice.id} className={practice.active ? '' : 'practices-table__inactive'}>
                <td title={practice.description}>{practice.title}</td>
                <td>{practice.category}</td>
                <td>{practice.severity}</td>
                <td>{practice.language}</td>
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
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
