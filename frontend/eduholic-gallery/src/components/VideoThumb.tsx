import { useEffect, useRef, useState } from 'react';
import { eduholic } from '@/data/eduholic';
import { cn } from '@/lib/utils';

/**
 * Real-content thumbnail: still from the actual academy intro video
 * (principal-intro.mp4 — the only eduholic media served cross-origin;
 * /api/academies/* blocks cross-origin fetch). Different #t per slot.
 * Lazy-mounts on near-viewport to avoid 20+ parallel streams on mobile.
 */
export function VideoThumb({ seek = 1, className, label }: { seek?: number; className?: string; label: string }) {
  const ref = useRef<HTMLDivElement>(null);
  const [show, setShow] = useState(false);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    if (typeof IntersectionObserver === 'undefined') {
      setShow(true);
      return;
    }
    const io = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting) {
          setShow(true);
          io.disconnect();
        }
      },
      { rootMargin: '240px' },
    );
    io.observe(el);
    return () => io.disconnect();
  }, []);

  return (
    <div ref={ref} className={cn('overflow-hidden bg-gradient-to-br from-navy-dark via-navy to-navy-light', className)}>
      {show ? (
        <video
          className="h-full w-full object-cover"
          src={`${eduholic.heroVideo}#t=${seek}`}
          muted
          playsInline
          preload="metadata"
          aria-label={label}
        />
      ) : (
        <div className="flex h-full w-full items-center justify-center p-2" aria-hidden>
          <span className="text-center text-[11px] font-bold text-white/60">{label}</span>
        </div>
      )}
    </div>
  );
}
