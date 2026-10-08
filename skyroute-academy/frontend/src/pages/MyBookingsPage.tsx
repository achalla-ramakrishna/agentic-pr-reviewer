import { useCallback, useEffect, useState } from 'react';
import { Navigate } from 'react-router-dom';
import { api, session } from '../api/client';
import type { Booking } from '../api/types';
import { ErrorBanner } from '../components/ErrorBanner';

export function MyBookingsPage() {
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [error, setError] = useState<unknown>(null);
  const loggedIn = session.get() !== null;

  const load = useCallback(() => {
    api.myBookings().then(setBookings).catch(setError);
  }, []);

  useEffect(() => {
    if (loggedIn) load();
  }, [loggedIn, load]);

  if (!loggedIn) return <Navigate to="/login" />;

  async function cancel(pnr: string) {
    try {
      await api.cancelBooking(pnr);
      load();
    } catch (err) {
      setError(err);
    }
  }

  return (
    <>
      <ErrorBanner error={error} />
      <table>
        <thead><tr><th>PNR</th><th>Route</th><th>Status</th><th>Total</th><th /></tr></thead>
        <tbody>
          {bookings.map((b) => (
            <tr key={b.recordLocator} data-testid="booking-row">
              <td>{b.recordLocator}</td>
              <td>{b.segments.map((s) => `${s.origin}-${s.destination}`).join(', ')}</td>
              <td>{b.status}</td>
              <td>{b.totalAmount.toFixed(2)} {b.currency}</td>
              <td>{b.status === 'CONFIRMED' && <button onClick={() => cancel(b.recordLocator)}>Cancel</button>}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}
