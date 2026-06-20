'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { clearSession, getCurrentUser } from '@/lib/auth';
import { useEffect, useState } from 'react';

export default function Navbar() {
  const router = useRouter();
  const [name, setName] = useState<string | null>(null);

  useEffect(() => {
    const user = getCurrentUser();
    setName(user?.fullName ?? null);
  }, []);

  function handleLogout() {
    clearSession();
    router.push('/login');
  }

  return (
    <header className="border-b border-clinic-100 bg-paper/90 backdrop-blur-sm sticky top-0 z-20">
      <div className="mx-auto flex max-w-5xl items-center justify-between px-6 py-4">
        <Link href="/dashboard" className="flex items-center gap-2">
          <span className="flex h-8 w-8 items-center justify-center rounded-full bg-clinic-600 text-paper font-display text-sm">
            +
          </span>
          <span className="font-display text-lg tracking-tight text-clinic-700">Meridian Health</span>
        </Link>
        {name && (
          <div className="flex items-center gap-4 text-sm">
            <span className="text-ink/70">Hi, {name.split(' ')[0]}</span>
            <button onClick={handleLogout} className="btn-secondary !py-1.5 !px-4">
              Log out
            </button>
          </div>
        )}
      </div>
    </header>
  );
}
