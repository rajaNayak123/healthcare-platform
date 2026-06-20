import Link from 'next/link';

export default function HomePage() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center px-6 text-center">
      <span className="mb-6 flex h-14 w-14 items-center justify-center rounded-full bg-clinic-600 text-paper font-display text-2xl">
        +
      </span>
      <h1 className="max-w-xl font-display text-4xl leading-tight text-clinic-700 sm:text-5xl">
        Healthcare appointments, without the hold music.
      </h1>
      <p className="mt-4 max-w-md text-ink/70">
        Book, track, and manage your doctor visits in a few clicks. Built on an
        event-driven backend so confirmations and notifications happen in real time.
      </p>
      <div className="mt-8 flex gap-3">
        <Link href="/register" className="btn-primary">Get started</Link>
        <Link href="/login" className="btn-secondary">I have an account</Link>
      </div>
    </main>
  );
}
