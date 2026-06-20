export default function Banner({
  type = 'info',
  message,
}: {
  type?: 'info' | 'error' | 'success';
  message: string;
}) {
  const styles = {
    info: 'bg-clinic-50 text-clinic-700 border-clinic-200',
    error: 'bg-rose-50 text-rose-700 border-rose-200',
    success: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  }[type];

  return (
    <div className={`rounded-xl border px-4 py-3 text-sm ${styles}`}>
      {message}
    </div>
  );
}
