'use client';

import { AppointmentResponse } from '@/lib/api';
import { AppointmentStatusMessage } from '@/lib/useAppointmentSocket';
import StatusBadge from './StatusBadge';

export default function AppointmentCard({
  appointment,
  liveStatus,
  onCancel,
  cancelling,
}: {
  appointment: AppointmentResponse;

  liveStatus?: AppointmentStatusMessage;
  onCancel: (id: number) => void;
  cancelling: boolean;
}) {
  const { slot } = appointment;
  const canCancel = appointment.status !== 'CANCELLED' && appointment.status !== 'COMPLETED';

  const displayStatus = liveStatus?.status ?? appointment.status;
  const isLive = !!liveStatus;

  return (
    <div
      className={`card flex items-center justify-between gap-4 px-5 py-4 transition-all duration-300 ${
        liveStatus?.status === 'PROCESSING' ? 'ring-1 ring-amber-300' :
        liveStatus?.status === 'PROCESSED'  ? 'ring-1 ring-emerald-300' :
        liveStatus?.status === 'FAILED'     ? 'ring-1 ring-rose-300'   : ''
      }`}
    >
      <div className="min-w-0 flex-1">
        <p className="text-sm font-semibold text-ink">{slot.doctorName}</p>
        <p className="text-xs text-ink/60">{slot.specialization}</p>
        <p className="mt-1 text-xs text-ink/50">
          {new Date(slot.slotDate).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })} ·{' '}
          {slot.startTime.slice(0, 5)} – {slot.endTime.slice(0, 5)}
        </p>
        {}
        {liveStatus?.note && (
          <p className="mt-1.5 text-[11px] text-ink/50 italic truncate max-w-xs">
            {liveStatus.note}
          </p>
        )}
      </div>

      <div className="flex flex-col items-end gap-2 flex-shrink-0">
        <StatusBadge status={displayStatus} live={isLive} />
        {canCancel && (
          <button
            onClick={() => onCancel(appointment.id)}
            disabled={cancelling}
            className="text-xs font-medium text-clay hover:underline disabled:opacity-50"
          >
            {cancelling ? 'Cancelling...' : 'Cancel'}
          </button>
        )}
      </div>
    </div>
  );
}
