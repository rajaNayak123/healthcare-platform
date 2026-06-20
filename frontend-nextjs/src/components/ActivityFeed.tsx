'use client';

import { AppointmentStatusMessage } from '@/lib/useAppointmentSocket';
import { WorkerEvent } from '@/lib/api';

const STATUS_ICON: Record<string, string> = {
  PROCESSING: '⏳',
  PROCESSED:  '✅',
  FAILED:     '❌',
  DUPLICATE:  '⏭',
};

const STATUS_DOT: Record<string, string> = {
  PROCESSING: 'bg-amber-400 animate-pulse',
  PROCESSED:  'bg-emerald-500',
  FAILED:     'bg-rose-500',
  DUPLICATE:  'bg-slate-400',
};

type FeedItem = AppointmentStatusMessage | WorkerEvent;

function isLiveMsg(item: FeedItem): item is AppointmentStatusMessage {
  return typeof (item as AppointmentStatusMessage).timestamp === 'string';
}

function formatTime(item: FeedItem): string {
  const raw = isLiveMsg(item) ? item.timestamp : item.timestamp;
  if (!raw) return '';

  const d = typeof raw === 'number' ? new Date(raw * 1000) : new Date(raw);
  return d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

function getStatus(item: FeedItem): string {
  return (item as AppointmentStatusMessage).status ?? (item as WorkerEvent).status;
}

function getNote(item: FeedItem): string {
  return (item as AppointmentStatusMessage).note ?? (item as WorkerEvent).note;
}

function getEventType(item: FeedItem): string {
  const t = (item as AppointmentStatusMessage).eventType ?? (item as WorkerEvent).eventType ?? '';
  return t.replace('APPOINTMENT_', '').toLowerCase();
}

function getAppointmentId(item: FeedItem): number {
  return (item as AppointmentStatusMessage).appointmentId ?? (item as WorkerEvent).appointmentId;
}

export default function ActivityFeed({
  liveEvents,
  polledEvents,
  connected,
}: {
  liveEvents: AppointmentStatusMessage[];
  polledEvents: WorkerEvent[];
  connected: boolean;
}) {

  const items: FeedItem[] = liveEvents.length > 0 ? liveEvents : polledEvents;

  return (
    <div>
      {}
      <div className="mb-3 flex items-center gap-1.5">
        <span
          className={`h-2 w-2 rounded-full ${
            connected ? 'bg-emerald-400 animate-pulse' : 'bg-slate-300'
          }`}
        />
        <span className="text-[10px] font-medium uppercase tracking-wide text-ink/40">
          {connected ? 'Live' : 'Polling'}
        </span>
      </div>

      {items.length === 0 ? (
        <p className="text-xs text-ink/50">
          No processing activity yet. Book an appointment to see live events.
        </p>
      ) : (
        <ul className="space-y-2.5">
          {items.slice(0, 10).map((item, idx) => {
            const status = getStatus(item);
            return (
              <li key={idx} className="flex items-start gap-2 text-xs">
                {}
                <span
                  className={`mt-1 h-2 w-2 flex-shrink-0 rounded-full ${STATUS_DOT[status] ?? 'bg-slate-300'}`}
                />
                {}
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-1">
                    <span className="text-sm leading-none">{STATUS_ICON[status] ?? '●'}</span>
                    <span className="font-semibold text-ink">
                      #{getAppointmentId(item)}
                    </span>
                    <span className="text-ink/50">·</span>
                    <span className="text-ink/60 capitalize">{getEventType(item)}</span>
                    {}
                    <span className="ml-auto text-[10px] text-ink/30 tabular-nums">
                      {formatTime(item)}
                    </span>
                  </div>
                  <p className="mt-0.5 truncate text-ink/60">{getNote(item)}</p>
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
