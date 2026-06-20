'use client';

import { SlotResponse } from '@/lib/api';

export default function SlotPicker({
  slots,
  onBook,
  bookingId,
}: {
  slots: SlotResponse[];
  onBook: (slotId: number) => void;
  bookingId: number | null;
}) {
  if (slots.length === 0) {
    return (
      <div className="card flex flex-col items-center justify-center px-6 py-12 text-center">
        <p className="text-sm text-ink/60">No open slots right now. Check back shortly.</p>
      </div>
    );
  }

  const byDate = slots.reduce<Record<string, SlotResponse[]>>((acc, slot) => {
    (acc[slot.slotDate] ||= []).push(slot);
    return acc;
  }, {});

  return (
    <div className="space-y-6">
      {Object.entries(byDate).map(([date, daySlots]) => (
        <div key={date}>
          <h3 className="mb-2 text-xs font-medium uppercase tracking-wide text-clinic-700/60">
            {new Date(date).toLocaleDateString(undefined, { weekday: 'long', month: 'short', day: 'numeric' })}
          </h3>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            {daySlots.map((slot) => (
              <button
                key={slot.id}
                onClick={() => onBook(slot.id)}
                disabled={bookingId !== null}
                className="card flex flex-col items-start gap-1 px-4 py-3 text-left transition hover:border-clinic-500 hover:shadow-md disabled:cursor-not-allowed disabled:opacity-60"
              >
                <span className="text-sm font-semibold text-ink">{slot.startTime.slice(0, 5)}</span>
                <span className="text-xs text-ink/60">{slot.doctorName}</span>
                <span className="text-[11px] text-clinic-600">{slot.specialization}</span>
                {bookingId === slot.id && <span className="text-[11px] text-clinic-500">Booking...</span>}
              </button>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
