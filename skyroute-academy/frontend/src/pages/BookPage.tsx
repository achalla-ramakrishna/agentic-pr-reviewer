import { FormEvent, useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { api, session } from '../api/client';
import type { Booking, FlightSummary } from '../api/types';
import { ErrorBanner } from '../components/ErrorBanner';

export function BookPage() {
  const flight = (useLocation().state as { flight?: FlightSummary } | null)?.flight;
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [booking, setBooking] = useState<Booking | null>(null);
  const [error, setError] = useState<unknown>(null);

  if (!session.get()) return <Navigate to="/login" />;
  if (!flight) return <Navigate to="/" />;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      setBooking(await api.createBooking({
        contactEmail: email,
        passengers: [{ firstName, lastName, type: 'ADT' }],
        segments: [{ flightId: flight!.id, cabin: flight!.cabin }],
      }));
    } catch (err) {
      setError(err);
    }
  }

  if (booking) {
    return (
      <div className="card">
        <h2>Booked!</h2>
        <p>Record locator: <strong data-testid="pnr">{booking.recordLocator}</strong></p>
        <p>Total: {booking.totalAmount.toFixed(2)} {booking.currency}</p>
      </div>
    );
  }

  return (
    <form onSubmit={onSubmit} className="card">
      <h2>{flight.flightNumber} {flight.origin} → {flight.destination}</h2>
      <label>First name <input value={firstName} onChange={(e) => setFirstName(e.target.value)} required /></label>
      <label>Last name <input value={lastName} onChange={(e) => setLastName(e.target.value)} required /></label>
      <label>Email <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required /></label>
      <button type="submit">Confirm booking</button>
      <ErrorBanner error={error} />
    </form>
  );
}
