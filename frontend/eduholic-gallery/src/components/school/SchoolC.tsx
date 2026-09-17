import { useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { ChevronLeft, ChevronRight, X } from 'lucide-react';
import { cn } from '@/lib/utils';
import { eduholic } from '@/data/eduholic';
import { VideoThumb } from '@/components/VideoThumb';

const DURATION = 5000;

/**
 * SCHOOL C — Stories viewer.
 * Spec: @haydenbleasel/stories-carousel (Kibo UI, 21st.dev, 라이선스 미표기 — 코드 반입 시 확인 필요).
 * 실제 수업 현장 썸네일을 스토리 링으로, 탭하면 프로그레스 뷰어.
 */
export function SchoolC() {
  const [open, setOpen] = useState<number | null>(null);
  const videos = eduholic.classVideos;

  useEffect(() => {
    if (open === null) return;
    const t = setTimeout(() => setOpen((i) => (i === null ? null : (i + 1) % videos.length)), DURATION);
    document.body.style.overflow = 'hidden';
    return () => {
      clearTimeout(t);
      document.body.style.overflow = '';
    };
  }, [open, videos.length]);

  return (
    <div className="bg-white px-4 py-8">
      <p className="text-center text-xs font-extrabold uppercase tracking-widest text-accent">에듀홀릭의 실제 수업 현장</p>
      <div className="no-scrollbar mx-auto mt-4 flex max-w-xl gap-4 overflow-x-auto px-2 py-1">
        {videos.map((v, i) => (
          <button key={v.id} type="button" onClick={() => setOpen(i)} className="group w-20 shrink-0" aria-label={`${v.title} 스토리 보기`}>
            <span className="block rounded-full bg-gradient-to-tr from-amber-400 via-rose-500 to-violet-600 p-[3px]">
              <span className="block overflow-hidden rounded-full border-2 border-white">
                <VideoThumb seek={1 + i * 4} label="" className="aspect-square w-full" />
              </span>
            </span>
            <span className="mt-1 block truncate text-[11px] font-semibold">{v.title}</span>
            <span className="block text-[11px] text-muted-foreground">{v.duration}</span>
          </button>
        ))}
        <button type="button" onClick={() => setOpen(0)} className="w-20 shrink-0" aria-label="내신 LIVE 스토리 보기">
          <span className="block rounded-full bg-navy p-[3px]">
            <span className="flex aspect-square w-full flex-col items-center justify-center rounded-full border-2 border-white text-white">
              <span className="text-lg font-extrabold">LIVE</span>
              <span className="px-1 text-[10px]">신구중3 · 53초</span>
            </span>
          </span>
          <span className="mt-1 block truncate text-[11px] font-semibold">내신 LIVE</span>
          <span className="block text-[11px] text-muted-foreground">0:53</span>
        </button>
      </div>

      <AnimatePresence>
        {open !== null && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/90 p-4"
            role="dialog"
            aria-modal="true"
            aria-label="수업 현장 스토리"
          >
            <div className="relative w-full max-w-sm overflow-hidden rounded-2xl bg-ink">
              <div className="flex gap-1 p-3">
                {videos.map((v, i) => (
                  <div key={v.id} className="h-1 flex-1 overflow-hidden rounded-full bg-white/25">
                    {i < open && <div className="h-full w-full bg-white" />}
                    {i === open && (
                      <motion.div
                        key={open}
                        className="h-full bg-white"
                        initial={{ width: '0%' }}
                        animate={{ width: '100%' }}
                        transition={{ duration: DURATION / 1000, ease: 'linear' }}
                      />
                    )}
                  </div>
                ))}
              </div>
              <div className="relative">
                <VideoThumb seek={1 + open * 4} label={videos[open].title} className="aspect-[3/4] w-full" />
                <button type="button" aria-label="이전" onClick={() => setOpen((open - 1 + videos.length) % videos.length)} className="absolute inset-y-0 left-0 w-1/3" />
                <button type="button" aria-label="다음" onClick={() => setOpen((open + 1) % videos.length)} className="absolute inset-y-0 right-0 w-1/3" />
              </div>
              <div className="flex items-center justify-between p-3 text-white">
                <p className="text-sm font-bold">{videos[open].title} <span className="font-medium text-white/60">· {videos[open].duration}</span></p>
                <div className="flex gap-1">
                  <button type="button" aria-label="이전 스토리" onClick={() => setOpen((open - 1 + videos.length) % videos.length)} className="rounded-full bg-white/15 p-2"><ChevronLeft className="h-4 w-4" /></button>
                  <button type="button" aria-label="다음 스토리" onClick={() => setOpen((open + 1) % videos.length)} className="rounded-full bg-white/15 p-2"><ChevronRight className={cn('h-4 w-4')} /></button>
                  <button type="button" aria-label="닫기" onClick={() => setOpen(null)} className="rounded-full bg-white/15 p-2"><X className="h-4 w-4" /></button>
                </div>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
