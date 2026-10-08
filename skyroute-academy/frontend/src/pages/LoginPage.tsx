import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, session } from '../api/client';
import { ErrorBanner } from '../components/ErrorBanner';

export function LoginPage() {
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<unknown>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      session.set(await api.login(username, password));
      navigate('/');
    } catch (err) {
      setError(err);
    }
  }

  return (
    <form onSubmit={onSubmit} className="card">
      <h2>Agent sign in</h2>
      <label>Username <input value={username} onChange={(e) => setUsername(e.target.value)} required /></label>
      <label>Password <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required /></label>
      <button type="submit">Sign in</button>
      <ErrorBanner error={error} />
    </form>
  );
}
