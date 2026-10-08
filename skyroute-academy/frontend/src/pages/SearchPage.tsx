import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { CabinClass, FlightSummary } from '../api/types';
import { ErrorBanner } from '../components/ErrorBanner';

const tomorrow = () => new Date(Date.now() + 86_400_000).toISOString().slice(0, 10);
const time = (iso: string) => new Date(iso).toISOString().slice(11, 16);

export function SearchPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState({ origin: 'DFW', destination: 'JFK', date: tomorrow(), cabin: 'ECONOMY' as CabinClass, passengers: 1, sort: 'DEPARTURE' });
  const [results, setResults] = useState<FlightSummary[] | null>(null);
  const [error, setError] = useState<unknown>(null);

  async function onSearch(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      setResults(await api.searchFlights(form));
    } catch (err) {
      setError(err);
    }
  }

  return (
    <>
      <form onSubmit={onSearch} className="card row">
        <label>From <input aria-label="From" value={form.origin} maxLength={3} onChange={(e) => setForm({ ...form, origin: e.target.value.toUpperCase() })} /></label>
        <label>To <input aria-label="To" value={form.destination} maxLength={3} onChange={(e) => setForm({ ...form, destination: e.target.value.toUpperCase() })} /></label>
        <label>Date <input aria-label="Date" type="date" value={form.date} onChange={(e) => setForm({ ...form, date: e.target.value })} /></label>
        <label>Cabin
          <select aria-label="Cabin" value={form.cabin} onChange={(e) => setForm({ ...form, cabin: e.target.value as CabinClass })}>
            <option>ECONOMY</option><option>PREMIUM_ECONOMY</option><option>BUSINESS</option>
          </select>
        </label>
        <label>Sort
          <select aria-label="Sort" value={form.sort} onChange={(e) => setForm({ ...form, sort: e.target.value })}>
            <option>DEPARTURE</option><option>FARE</option><option>DURATION</option>
          </select>
        </label>
        <button type="submit">Search</button>
      </form>
      <ErrorBanner error={error} />
      {results && results.length === 0 && <p>No flights found.</p>}
      {results && results.length > 0 && (
        <table>
          <thead><tr><th>Flight</th><th>Departs</th><th>Arrives</th><th>Seats</th><th>Fare</th><th /></tr></thead>
          <tbody>
            {results.map((f) => (
              <tr key={f.id} data-testid="flight-row">
                <td>{f.flightNumber}</td>
                <td>{time(f.departureTime)}</td>
                <td>{time(f.arrivalTime)}</td>
                <td>{f.seatsAvailable}</td>
                <td>{f.fare.toFixed(2)} {f.currency}</td>
                <td><button onClick={() => navigate('/book', { state: { flight: f } })}>Book</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  );
}
