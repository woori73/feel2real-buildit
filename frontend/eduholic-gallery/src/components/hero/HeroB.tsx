import { useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { Play, X } from 'lucide-react';
import { eduholic } from '@/data/eduholic';
import { VideoThumb } from '@/components/VideoThumb';

/**
 * HERO B — Video dialog hero.
 * Spec: @dillionverma/hero-video-dialog (Magic UI, 21st.dev, MIT).
 * 썸네일 탭 → 다이얼로그에서 실제 원장 소개 영상 재생.
 */
export function HeroB() {
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false);
    document.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
    };
  }, [open ]);

  return (
    <div className="bg-ink px-4 py-10 sm:py-14">
      <div className="mx-auto max-w-3xl text-center">
        <p className="text-xs font-bold uppercase tracking-widest text-white/60">{eduholic.director}</p>
        <h2 className="mt-2 text-2xl font-extrabold text-white sm:text-4xl">{eduholic.academy}</h2>
        <p className="mx-auto mt-3 max-w-xl text-sm leading-relaxed text-white/70 sm:text-base">{eduholic.heroQuote}</p>
        <button
          type="button"
          onClick={() => setOpen(true)}
          className="group relative mx-auto mt-6 block w-full overflow-hidden rounded-2xl border border-white/15 shadow-2xl"
          aria-label="원장 소개 영상 재생"
        >
          <VideoThumb seek={2} label="박정민 원장 소개 영상 썸네일" className="aspect-video w-full" />
          <span className="absolute inset-0 bg-black/30 transition group-active:bg-black/45" />
          <span className="absolute inset-0 flex items-center justify-center">
            <span className="flex h-16 w-16 items-center justify-center rounded-full bg-white text-ink shadow-xl transition group-hover:scale-105 sm:h-20 sm:w-20">
              <Play className="ml-1 h-7 w-7 fill-current" />
            </span>
          </span>
          <span className="absolute bottom-3 left-3 rounded-full bg-black/60 px-3 py-1 text-xs font-semibold text-white">원장 소개 영상 · 탭하여 재생</span>
        </button>
      </div>

      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/85 p-4"
            onClick={() => setOpen(false)}
            role="dialog"
            aria-modal="true"
            aria-label="원장 소개 영상"
          >
            <motion.div
              initial={{ scale: 0.92, y: 16 }}
              animate={{ scale: 1, y: 0 }}
              exit={{ scale: 0.95, y: 8 }}
              className="relative w-full max-w-3xl overflow-hidden rounded-2xl bg-black"
              onClick={(e) => e.stopPropagation()}
            >
              <video src={eduholic.heroVideo} poster={eduholic.principalPhoto} controls autoPlay playsInline className="aspect-video w-full" />
              <button
                type="button"
                onClick={() => setOpen(false)}
                className="absolute right-3 top-3 rounded-full bg-black/60 p-2 text-white"
                aria-label="영상 닫기"
              >
                <X className="h-5 w-5" />
              </button>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
