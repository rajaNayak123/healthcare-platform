const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080';
const WORKER_BASE = process.env.NEXT_PUBLIC_WORKER_BASE_URL || 'http://localhost:8000';

export class ApiError extends Error {
  status: number;
  details?: string[];
  constructor(message: string, status: number, details?: string[]) {
    super(message);
    this.status = status;
    this.details = details;
  }
}

function getToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem('hc_token');
}

async function request<T>(path: string, options: RequestInit = {}, useWorker = false): Promise<T> {
  const base = useWorker ? WORKER_BASE : API_BASE;
  const token = getToken();

  const res = await fetch(`${base}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers || {}),
    },
  });

  if (res.status === 204) {
    return undefined as T;
  }

  const data = await res.json().catch(() => ({}));

  if (!res.ok) {
    throw new ApiError(data.message || 'Something went wrong', res.status, data.details);
  }

  return data as T;
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  userId: number;
  fullName: string;
  email: string;
  role: string;
}

export interface SlotResponse {
  id: number;
  doctorName: string;
  specialization: string;
  slotDate: string;
  startTime: string;
  endTime: string;
  isBooked: boolean;
}

export interface AppointmentResponse {
  id: number;
  userId: number;
  patientName: string;
  slot: SlotResponse;
  status: 'PENDING' | 'BOOKED' | 'NOTIFIED' | 'CANCELLED' | 'COMPLETED';
  createdAt: string;
  updatedAt: string;
}

export function register(payload: { fullName: string; email: string; password: string; phone: string }) {
  return request<AuthResponse>('/api/auth/register', { method: 'POST', body: JSON.stringify(payload) });
}

export function login(payload: { email: string; password: string }) {
  return request<AuthResponse>('/api/auth/login', { method: 'POST', body: JSON.stringify(payload) });
}

export function getAvailableSlots(date?: string) {
  const qs = date ? `?date=${date}` : '';
  return request<SlotResponse[]>(`/api/slots/available${qs}`, { method: 'GET' });
}

export function createAppointment(slotId: number) {
  return request<AppointmentResponse>('/api/appointments', {
    method: 'POST',
    body: JSON.stringify({ slotId }),
  });
}

export function cancelAppointment(id: number) {
  return request<void>(`/api/appointments/${id}`, { method: 'DELETE' });
}

export function getMyAppointments() {
  return request<AppointmentResponse[]>('/api/appointments/me', { method: 'GET' });
}

export interface WorkerEvent {
  appointmentId: number;
  eventType: string;
  status: string;
  note: string;
  timestamp: number;
}

export function getRecentWorkerEvents() {
  return request<{ events: WorkerEvent[] }>('/events/recent', { method: 'GET' }, true);
}
