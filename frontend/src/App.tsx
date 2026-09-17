import { useEffect, useState } from 'react'
import './App.css'

type HealthStatus = 'checking' | 'ok' | 'unreachable'

function App() {
  const [backendStatus, setBackendStatus] = useState<HealthStatus>('checking')

  useEffect(() => {
    fetch('/api/health')
      .then((res) => (res.ok ? setBackendStatus('ok') : setBackendStatus('unreachable')))
      .catch(() => setBackendStatus('unreachable'))
  }, [])

  return (
    <main className="app-shell">
      <h1>Agentic PR Reviewer</h1>
      <p>Dashboard, review submission, and practices library land here as the build progresses.</p>
      <p>
        Backend status: <strong>{backendStatus}</strong>
      </p>
    </main>
  )
}

export default App
