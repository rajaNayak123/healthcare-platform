const STATUS_STYLES: Record<string, string> = {
  PENDING:    'bg-amber-100 text-amber-800',
  BOOKED:     'bg-clinic-100 text-clinic-700',
  NOTIFIED:   'bg-emerald-100 text-emerald-700',
  CANCELLED:  'bg-rose-100 text-rose-700',
  COMPLETED:  'bg-slate-200 text-slate-700',

  PROCESSING: 'bg-amber-100 text-amber-700 animate-pulse',
  PROCESSED:  'bg-emerald-100 text-emerald-700',
  FAILED:     'bg-rose-100 text-rose-700',
};

const STATUS_LABELS: Record<string, string> = {
  PENDING:    '⏳ Processing',
  BOOKED:     '✓ Confirmed',
  NOTIFIED:   '✅ Confirmed & Notified',
  CANCELLED:  '✕ Cancelled',
  COMPLETED:  '✓ Completed',

  PROCESSING: '⏳ Processing…',
  PROCESSED:  '✅ Notified',
  FAILED:     '❌ Failed',
};

export default function StatusBadge({
  status,
  live = false,
}: {
  status: string;
  live?: boolean;
}) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium ${
        STATUS_STYLES[status] || 'bg-slate-100 text-slate-600'
      }`}
    >
      {live && (
        <span className="h-1.5 w-1.5 flex-shrink-0 rounded-full bg-current opacity-80 animate-pulse" />
      )}
      {STATUS_LABELS[status] || status}
    </span>
  );
}
