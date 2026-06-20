'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  getAvailableSlots,
  getMyAppointments,
  createAppointment,
  cancelAppointment,
  getRecentWorkerEvents,
  SlotResponse,
  AppointmentResponse,
  WorkerEvent,
  ApiError,
} from '@/lib/api';
import { useAppointmentSocket } from '@/lib/useAppointmentSocket';
import { isAuthenticated } from '@/lib/auth';
import Navbar from '@/components/Navbar';
import SlotPicker from '@/components/SlotPicker';
import AppointmentCard from '@/components/AppointmentCard';
import ActivityFeed from '@/components/ActivityFeed';
import Banner from '@/components/Banner';

const API_BASE    = process.env.NEXT_PUBLIC_API_BASE_URL    || 'http://localhost:8080';
const WORKER_BASE = process.env.NEXT_PUBLIC_WORKER_BASE_URL || 'http://localhost:8000';

export default function DashboardPage() {
  const router = useRouter();
  const [slots,        setSlots]        = useState<SlotResponse[]>([]);
  const [appointments, setAppointments] = useState<AppointmentResponse[]>([]);
  const [polledEvents, setPolledEvents] = useState<WorkerEvent[]>([]);
  const [loading,      setLoading]      = useState(true);
  const [bookingId,    setBookingId]    = useState<number | null>(null);
  const [cancellingId, setCancellingId] = useState<number | null>(null);
  const [message,      setMessage]      = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const loadData = useCallback(async () => {
    const [slotData, appointmentData] = await Promise.all([
      getAvailableSlots(),
      getMyAppointments(),
    ]);
    setSlots(slotData);
    setAppointments(appointmentData);
  }, []);

  const loadPolledEvents = useCallback(async () => {
    try {
      const result = await getRecentWorkerEvents();
      setPolledEvents(result.events);
    } catch {

    }
  }, []);

  useEffect(() => {
    if (!isAuthenticated()) {
      router.push('/login');
      return;
    }
    setLoading(true);
    loadData().finally(() => setLoading(false));
    loadPolledEvents();

    const interval = setInterval(() => {
      loadData();
      loadPolledEvents();
    }, 15_000);  
    return () => clearInterval(interval);
  }, [loadData, loadPolledEvents, router]);

  const appointmentIds = useMemo(
    () => appointments.map(a => a.id),
    [appointments],
  );

  const { statusMap, liveEvents, connected } = useAppointmentSocket(
    appointmentIds,
    API_BASE,
  );

  useEffect(() => {
    const terminalStatuses = ['PROCESSED', 'FAILED'];
    const latest = liveEvents[0];
    if (latest && terminalStatuses.includes(latest.status)) {

      const t = setTimeout(() => loadData(), 1500);
      return () => clearTimeout(t);
    }
  }, [liveEvents, loadData]);

  async function handleBook(slotId: number) {
    setBookingId(slotId);
    setMessage(null);
    try {
      await createAppointment(slotId);
      setMessage({ type: 'success', text: '⏳ Appointment booked. Waiting for confirmation…' });
      await loadData();
    } catch (err) {
      setMessage({
        type: 'error',
        text: err instanceof ApiError ? err.message : 'Could not book this slot.',
      });
    } finally {
      setBookingId(null);
    }
  }

  async function handleCancel(id: number) {
    setCancellingId(id);
    setMessage(null);
    try {
      await cancelAppointment(id);
      setMessage({ type: 'success', text: 'Appointment cancelled.' });
      await loadData();
    } catch (err) {
      setMessage({
        type: 'error',
        text: err instanceof ApiError ? err.message : 'Could not cancel this appointment.',
      });
    } finally {
      setCancellingId(null);
    }
  }

  return (
    <>
      <Navbar />
      <main className="mx-auto max-w-5xl px-6 py-10">
        {message && (
          <div className="mb-6">
            <Banner type={message.type} message={message.text} />
          </div>
        )}

        <div className="grid grid-cols-1 gap-10 lg:grid-cols-3">
          {}
          <section className="lg:col-span-2">
            <h2 className="font-display text-xl text-clinic-700">Available slots</h2>
            <p className="mt-1 text-sm text-ink/60">Pick a time that works for you.</p>
            <div className="mt-5">
              {loading ? (
                <p className="text-sm text-ink/50">Fetching available slots…</p>
              ) : (
                <SlotPicker slots={slots} onBook={handleBook} bookingId={bookingId} />
              )}
            </div>
          </section>

          {}
          <section>
            <h2 className="font-display text-xl text-clinic-700">Live activity</h2>
            <p className="mt-1 text-sm text-ink/60">
              Real-time event processing status.
            </p>
            <div className="card mt-5 p-4">
              <ActivityFeed
                liveEvents={liveEvents}
                polledEvents={polledEvents}
                connected={connected}
              />
            </div>
          </section>
        </div>

        {}
        <section className="mt-12">
          <h2 className="font-display text-xl text-clinic-700">Your appointments</h2>
          <p className="mt-1 text-sm text-ink/60">
            Status updates in real time — no refresh needed.
          </p>
          <div className="mt-5 space-y-3">
            {loading ? (
              <p className="text-sm text-ink/50">Loading your appointments…</p>
            ) : appointments.length === 0 ? (
              <div className="card px-6 py-10 text-center text-sm text-ink/50">
                You haven&apos;t booked any appointments yet.
              </div>
            ) : (
              appointments.map((appt) => (
                <AppointmentCard
                  key={appt.id}
                  appointment={appt}
                  liveStatus={statusMap[appt.id]}
                  onCancel={handleCancel}
                  cancelling={cancellingId === appt.id}
                />
              ))
            )}
          </div>
        </section>
      </main>
    </>
  );
}
