import { useEffect, useState } from 'react'
import './App.css'
import { MetricsDashboard } from './components/MetricsDashboard'
import { PracticesLibrary } from './components/PracticesLibrary'
import { ReviewsDashboard } from './components/ReviewsDashboard'

type HealthStatus = 'checking' | 'ok' | 'unreachable'
type Tab = 'reviews' | 'metrics' | 'practices'

function App() {
  const [backendStatus, setBackendStatus] = useState<HealthStatus>('checking')
  const [tab, setTab] = useState<Tab>('reviews')

  useEffect(() => {
    fetch('/api/health')
      .then((res) => (res.ok ? setBackendStatus('ok') : setBackendStatus('unreachable')))
      .catch(() => setBackendStatus('unreachable'))
  }, [])

  return (
    <main className="app-shell">
      <h1>Agentic PR Reviewer</h1>
      <p>
        Backend status: <strong>{backendStatus}</strong>
      </p>
      <nav className="app-tabs">
        <button
          type="button"
          className={tab === 'reviews' ? 'app-tabs__item app-tabs__item--active' : 'app-tabs__item'}
          onClick={() => setTab('reviews')}
        >
          Reviews
        </button>
        <button
          type="button"
          className={tab === 'metrics' ? 'app-tabs__item app-tabs__item--active' : 'app-tabs__item'}
          onClick={() => setTab('metrics')}
        >
          Metrics
        </button>
        <button
          type="button"
          className={tab === 'practices' ? 'app-tabs__item app-tabs__item--active' : 'app-tabs__item'}
          onClick={() => setTab('practices')}
        >
          Practices library
        </button>
      </nav>
      {tab === 'reviews' && <ReviewsDashboard />}
      {tab === 'metrics' && <MetricsDashboard />}
      {tab === 'practices' && <PracticesLibrary />}
    </main>
  )
}

export default App
