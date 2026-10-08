import type { Booking, CabinClass, FlightSummary, LoginResponse, Passenger, Problem } from './types';

/** All HTTP goes through here: one place for auth headers and error mapping. */
export class ApiError extends Error {
  constructor(public readonly problem: Problem) {
    super(problem.detail);
  }
}

const TOKEN_KEY = 'skyroute.token';

export const session = {
  get: (): LoginResponse | null => {
    const raw = sessionStorage.getItem(TOKEN_KEY);
    return raw ? (JSON.parse(raw) as LoginResponse) : null;
  },
  set: (login: LoginResponse) => sessionStorage.setItem(TOKEN_KEY, JSON.stringify(login)),
  clear: () => sessionStorage.removeItem(TOKEN_KEY),
};

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('Content-Type', 'application/json');
  const token = session.get()?.accessToken;
  if (token) headers.set('Authorization', `Bearer ${token}`);

  const res = await fetch(path, { ...init, headers });
  if (!res.ok) {
    const problem = (await res.json().catch(() => ({ status: res.status, detail: res.statusText, errorCode: 'HTTP_ERROR' }))) as Problem;
    if (res.status === 401) session.clear();
    throw new ApiError(problem);
  }
  return (await res.json()) as T;
}

export const api = {
  login: (username: string, password: string) =>
    request<LoginResponse>('/api/v1/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) }),

  searchFlights: (p: { origin: string; destination: string; date: string; cabin: CabinClass; passengers: number; sort: string }) =>
    request<FlightSummary[]>(`/api/v1/flights/search?${new URLSearchParams({ ...p, passengers: String(p.passengers) })}`),

  createBooking: (body: { contactEmail: string; passengers: Passenger[]; segments: { flightId: string; cabin: CabinClass }[] }) =>
    request<Booking>('/api/v1/bookings', { method: 'POST', body: JSON.stringify(body) }),

  myBookings: () => request<Booking[]>('/api/v1/bookings'),

  cancelBooking: (pnr: string) => request<Booking>(`/api/v1/bookings/${encodeURIComponent(pnr)}/cancel`, { method: 'POST' }),
};
