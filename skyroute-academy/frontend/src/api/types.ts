// Mirrors the backend DTOs (FlightDtos, BookingDtos, AuthController).
export type CabinClass = 'ECONOMY' | 'PREMIUM_ECONOMY' | 'BUSINESS' | 'FIRST';
export type PassengerType = 'ADT' | 'CHD' | 'INF';

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  username: string;
  roles: string[];
}

export interface FlightSummary {
  id: string;
  flightNumber: string;
  origin: string;
  destination: string;
  departureTime: string;
  arrivalTime: string;
  durationMinutes: number;
  aircraftType: string;
  status: string;
  cabin: CabinClass;
  seatsAvailable: number;
  fare: number;
  currency: string;
}

export interface Passenger {
  firstName: string;
  lastName: string;
  type: PassengerType;
}

export interface Booking {
  recordLocator: string;
  status: 'CONFIRMED' | 'CANCELLED';
  contactEmail: string;
  passengers: Passenger[];
  segments: { flightNumber: string; origin: string; destination: string; departureTime: string; cabinClass: CabinClass; amount: number }[];
  totalAmount: number;
  currency: string;
  createdAt: string;
}

/** RFC 9457 problem detail as produced by GlobalExceptionHandler. */
export interface Problem {
  status: number;
  detail: string;
  errorCode: string;
  correlationId?: string;
  fieldErrors?: Record<string, string>;
}
